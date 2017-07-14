package de.teamalbin.spigotmc.easyportals;

import de.teamalbin.spigotmc.easyportals.nms.NMSInterface;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPhysicsEvent;

import javax.sound.sampled.Port;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class PortalManager {
    // For performance reasons, only blocks within this radius around an active portal
    // center will be protected from breaking or physics.
    private static int PROTECTION_RADIUS = 16;

    public class PortalManagerError {
        private String message;

        public PortalManagerError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private NMSInterface nms;
    private HashMap<String, Portal> portals = new HashMap<>();
    private Material buildMarker = Material.EMERALD_BLOCK;
    private Material portalBlockType = Material.PORTAL;
    private File configFile;

    public PortalManager(NMSInterface nms) {
        this.nms = nms;
    }

    private void saveConfig() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        for (Portal p : portals.values()) {
            Location ploc = p.getLocation();
            config.set("portals." + p.getName() + ".world", ploc.getWorld().getName());
            config.set("portals." + p.getName() + ".target", p.getTarget());
            config.set("portals." + p.getName() + ".location", ploc.toVector());
            config.set("portals." + p.getName() + ".is_east_west", p.isEW());
            config.set("portals." + p.getName() + ".flipped", p.isFlipped());
        }
        config.save(this.configFile);
    }

    public void loadConfig(File configFile) throws IOException, InvalidConfigurationException {
        this.configFile = configFile;
        boolean reloading = false;
        if (!this.portals.isEmpty()) {
            for (Portal p : portals.values()) {
                for (Block b : p.getBlocks()) b.setType(Material.AIR);
            }
            this.portals.clear();
            reloading = true;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.load(configFile);
        ConfigurationSection cportals = config.getConfigurationSection("portals");
        if (cportals == null) return; // no portals defined
        for (String pname : cportals.getKeys(false)) {
            ConfigurationSection cp = cportals.getConfigurationSection(pname);
            World w = Bukkit.getWorld(cp.getString("world"));
            if (w == null) throw new InvalidConfigurationException("Portal '" + pname + "' is not in a known world.");
            Location ploc = cp.getVector("location").toLocation(w);
            boolean isEW = cp.getBoolean("is_east_west");
            PortalBuildSite pbs = findPortalBlocks(ploc.getBlock(), (isEW ? traverseEW : traverseNS));
            if (pbs == null) throw new InvalidConfigurationException("Could not rebuild portal '" + pname + "' from configuration!");
            Portal p = new Portal(ploc, pname, isEW, cp.getBoolean("flipped"), cp.getString("target"), null, pbs.portalBlocks);
            portals.put(pname, p);
        }
        // after all portals are loaded, initialize the links
        for (Portal p : portals.values()) {
            if (p.getTarget() == null) continue;
            String[] tgt = p.getTarget().split(":");
            if (tgt[0].equals("portal")) {
                Portal link = portals.get(tgt[1]);
                if (link == null) throw new InvalidConfigurationException("Portal '" + p.getName() + "' links to '" + tgt[1] + "', but that doesn't exist!");
                p.link = link;
            }
        }
    }

    private class PortalBuildSite {
        public boolean isColumn;
        public boolean isEW;
        public Location portalCenter;
        public List<Block> portalBlocks;
    }

    private boolean isInsideBuild(Block b) {
        return b.getType() == Material.AIR || b.getType() == buildMarker || b.getType() == portalBlockType;
    }

    // 2d traversal functions for portal block search (since portals are 2-dimensional).
    private BiFunction<Block, Integer, Block> traverseEW = (tb, d) -> tb.getRelative(d, 0, 0);
    private BiFunction<Block, Integer, Block> traverseNS = (tb, d) -> tb.getRelative(0, 0, d);

    private PortalBuildSite findPortalBlocks(Block start, BiFunction<Block, Integer, Block> traverse) {
        // we use an abridged scanline flood fill algorithm to detect blocks that should be part of the
        // portal. we can simplify the algorithm since we know we start on a flat bottom and can only go up.
        ArrayList<Block> pblocks = new ArrayList<>();
        Stack<Block> ranges = new Stack<>();
        ranges.push(start);
        Location center = null;
        while (!ranges.empty()) {
            int moved = 0;
            Block rb = ranges.pop();
            // first go left...
            while (isInsideBuild(rb) && moved < PROTECTION_RADIUS) {
                rb = traverse.apply(rb, -1);
                moved++;
            }
            if (isInsideBuild(rb)) return null;
            boolean haveRange = false;
            moved = 0;
            // then go right, adding blocks to the portal
            // mark free blocks above for next ranges
            rb = traverse.apply(rb, 1);
            while (isInsideBuild(rb) && moved < PROTECTION_RADIUS) {
                if (!haveRange && isInsideBuild(rb.getRelative(BlockFace.UP))) {
                    haveRange = true;
                    ranges.push(rb.getRelative(BlockFace.UP));
                } else haveRange = false;
                pblocks.add(rb);
                moved++;
                rb = traverse.apply(rb, 1);
            }
            if (isInsideBuild(rb)) return null;
            if (center == null) center = traverse.apply(rb, -Math.round(moved / 2)).getLocation();
        }
        PortalBuildSite pbs = new PortalBuildSite();
        pbs.portalBlocks = pblocks;
        pbs.portalCenter = center;
        return pbs;
    }

    private PortalBuildSite detectBuildSiteNear(Player player, int distance) {
        scan: for (Block b : new Utilities.BlockScan(player.getLocation(), distance)) {
            if (b.getType() == buildMarker) {
                if (b.getRelative(BlockFace.DOWN).getType() == buildMarker || b.getRelative(BlockFace.UP).getType() == buildMarker) {
                    // might be a column-type portal site
                } else {
                    // might be a regular portal site
                    Material frame = b.getRelative(BlockFace.DOWN).getType();
                    if (frame == Material.AIR) continue;
                    // probe if the portal is supposed to go east/west or north/south
                    boolean isEW = false;
                    boolean isNS = false;
                    if (b.getRelative(BlockFace.EAST).getType() == buildMarker || b.getRelative(BlockFace.WEST).getType() == buildMarker) isEW = true;
                    if (b.getRelative(BlockFace.NORTH).getType() == buildMarker || b.getRelative(BlockFace.SOUTH).getType() == buildMarker) isNS = true;
                    if ((isNS && isEW) || (!isNS && !isEW)) continue; // both or neither? makes no sense

                    // now that we know the direction, we can traverse the portal 2-dimensionally and find the blocks
                    PortalBuildSite pbs = findPortalBlocks(b, isEW ? traverseEW : traverseNS);
                    if (pbs == null) continue scan;

                    // found something!
                    pbs.isColumn = false;
                    pbs.isEW = isEW;
                    return pbs;
                }
            }
        }
        return null;
    }

    /**
     * Given a block (most usefully a PORTAL block), returns the portal it belongs to
     * if we manage it. Returns null if otherwise (regular nether portals).
     */
    public Portal findPortalFor(Block b) {
        for (Portal p : this.portals.values()) {
            if (p.getLocation().getWorld() != b.getLocation().getWorld()) continue;
            if (p.getLocation().distance(b.getLocation()) > PROTECTION_RADIUS) continue;
            if (p.getBlocks().contains(b)) return p;
        }
        return null;
    }

    /**
     * Returns whether the thing in question is part of a portal managed by this manager.
     */
    public boolean manages(BlockPhysicsEvent bpe) {
        Block b = bpe.getBlock();
        if (b.getType() == this.portalBlockType || bpe.getChangedType() == this.portalBlockType)
            if (findPortalFor(b) != null) return true;
        return false;
    }

    public boolean manages(Block b) {
        if (b.getType() != this.portalBlockType) return false;
        if (findPortalFor(b) != null) return true;
        return false;
    }

    public boolean manages(List<Block> blocks) {
        List<Block> check = blocks.stream().filter((b) -> b.getType() != this.portalBlockType).collect(Collectors.toList());
        if (check.isEmpty()) return false;
        for (Portal p : this.portals.values()) {
            for (Block b : check) {
                if (p.getLocation().getWorld() != b.getLocation().getWorld()) continue;
                if (p.getLocation().distance(b.getLocation()) <= PROTECTION_RADIUS && p.getBlocks().contains(b)) return true;
            }
        }
        return false;
    }

    /**
     * Creates a portal near the specified player, as long as a portal building site
     * is within 5 blocks of the player. A portal building site is either:
     *
     * 1. an enclosing frame of any (same) material with at least 2 build marker blocks
     *    at the bottom. This will create a regular portal up to a maximum height of 16 blocks.
     *
     * 2. Two or more build marker blocks atop each other. This creates a free-standing
     *    portal column. Turns the bottom build marker into an ender portal block.
     *    (NOT IMPLEMENTED YET)
     *
     * @param player
     * @param portalName The name of the new portal.
     */
    public PortalManagerError createPortalNear(Player player, String portalName) {
        if (this.portals.containsKey(portalName)) return new PortalManagerError("A portal with this name already exists.");
        PortalBuildSite buildsite = this.detectBuildSiteNear(player, 5);
        if (buildsite == null) return new PortalManagerError("Could not detect a suitable portal site within 5 blocks. Please check the user guide.");
        nms.massSetBlockType(buildsite.portalBlocks, (byte)(buildsite.isEW ? 0 : 2), Material.PORTAL);
        this.portals.put(portalName, new Portal(buildsite.portalCenter, portalName, buildsite.isEW,
                false, null, null, buildsite.portalBlocks));
        // boom! zoosh
        player.getWorld().playSound(buildsite.portalCenter, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, new Random().nextFloat() * 0.4F + 0.8F);
        try { saveConfig(); } catch (IOException ioex) { player.sendMessage(ChatColor.RED + "[!!!] Could not save portal configuration!"); }
        return null;
    }

    public PortalManagerError linkPortals(Player player, String portal1, String portal2) {
        if (!this.portals.containsKey(portal1)) return new PortalManagerError("Portal '" + portal1 + "' does not exist.");
        if (portal2.equals("random") && !this.portals.containsKey(portal2)) return new PortalManagerError("Portal '" + portal2 + "' does not exist.");
        Portal p1 = portals.get(portal1);
        if (portal2.equals("random")) {
            p1.setRandom(player);
        } else {
            Portal p2 = portals.get(portal2);
            p1.setLink(player, p2);
        }
        try { saveConfig(); } catch (IOException ioex) { player.sendMessage(ChatColor.RED + "[!!!] Could not save portal configuration!"); }
        return null;
    }

    public PortalManagerError unlinkPortal(Player player, String portal) {
        if (!this.portals.containsKey(portal)) return new PortalManagerError("Portal '" + portal + "' does not exist.");
        Portal p1 = portals.get(portal);
        p1.unlink(player);
        try { saveConfig(); } catch (IOException ioex) { player.sendMessage(ChatColor.RED + "[!!!] Could not save portal configuration!"); }
        return null;
    }

    public PortalManagerError destroyPortal(Player player, String pname) {
        if (!this.portals.containsKey(pname)) return new PortalManagerError("Portal '" + pname + "' does not exist.");
        Portal portal = portals.get(pname);
        if (portal.link != null) {
            portal.link.unlink(player);
            portal.unlink(player);
        }
        for (Block b : portal.getBlocks()) {
            b.setType(Material.AIR);
        }
        player.sendMessage("Portal " + portal.niceName() + " has been destroyed.");
        this.portals.remove(pname);
        try { saveConfig(); } catch (IOException ioex) { player.sendMessage(ChatColor.RED + "[!!!] Could not save portal configuration!"); }
        return null;
    }

    public PortalManagerError list(Player player) {
        if (portals.keySet().isEmpty()) {
            player.sendMessage("There are no active portals.");
            return null;
        }
        String[] names = portals.keySet().toArray(new String[portals.keySet().size()]);
        Arrays.sort(names);
        for (String pn : names) {
            player.sendMessage(portals.get(pn).getListEntry());
        }
        return null;
    }
}
