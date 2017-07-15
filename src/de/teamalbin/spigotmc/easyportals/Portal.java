package de.teamalbin.spigotmc.easyportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Portal {
    private static int randomRange = 6000;
    private static int randomMaxTries = 8;

    private String name;
    private Location location;
    private boolean isEW;
    private boolean flipped;

    // a bit janky, but it's easier/faster to load from config if we can set this from the PortalManager.
    protected Portal link;

    private String target;
    private List<Block> blocks;
    private Instant cooldown;

    public Portal(Location loc, String name, boolean isEW, boolean flipped, String target, Portal link, List<Block> blocks) {
        this.location = loc;
        this.name = name;
        this.isEW = isEW;
        this.flipped = flipped;
        this.link = link;
        this.target = target;
        this.blocks = blocks;
        this.enableCooldown();
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public String getTarget() {
        return target;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public boolean isEW() {
        return isEW;
    }

    public void setFlipped(Player player, boolean flipped) {
        this.flipped = flipped;
    }

    public Portal getLink() {
        return link;
    }

    public String niceName() {
        return ChatColor.BLUE + this.name + ChatColor.RESET;
    }

    public void unlink(Player player) {
        this.link = null;
        if (this.target != null && player != null) player.sendMessage("Portal " + this.niceName() + " has been unlinked.");
        this.target = null;
    }

    public void flip() {
        this.flipped = !this.flipped;
    }

    public void setLink(Player player, Portal link) {
        if (this.getLocation().getWorld() != link.location.getWorld()) {
            player.sendMessage(ChatColor.RED + "Error: " + ChatColor.RESET + "Can't use portals to link between different worlds.");
            return;
        }
        if (this.link != null) {
            this.link.unlink(player);
            link.unlink(player);
        }
        this.link = link;
        this.target = "portal:" + link.name;
        link.link = this;
        link.target = "portal:" + this.name;
        if (player != null) player.sendMessage("Portal " + this.niceName() + " linked to " + link.niceName() + ".");
    }

    public void makeRandom(Player player) {
        if (this.link != null) {
            this.unlink(player);
            this.link.unlink(player);
        }
        this.link = null;
        this.target = "random";
        if (player != null) player.sendMessage("Portal " + this.niceName() + " is now a random warp.");
    }

    private String pointToTarget(Location point) {
        return point.getBlockX() + ", " + point.getBlockY() + ", " + point.getBlockZ();
    }

    public void makePoint(Player player, Location point) {
        if (this.getLocation().getWorld() != point.getWorld()) {
            player.sendMessage(ChatColor.RED + "Error: " + ChatColor.RESET + "Can't use portals to link between different worlds.");
            return;
        }
        if (this.link != null) {
            this.link.unlink(player);
            this.unlink(player);
        }
        this.link = null;
        this.target = "point:" + pointToTarget(point) + ":" + point.getPitch() + ":" + point.getYaw();
        if (player != null) player.sendMessage("Portal " + this.niceName() + " now warps to fixed point (" + pointToTarget(point) + ").");
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    private void enableCooldown() {
        Instant cd = Instant.now().plusSeconds(3);
        this.cooldown = cd;
        if (this.link != null) {
            this.link.cooldown = cd;
        }
    }

    public String getListEntry() {
        if (this.target == null) return this.niceName() + ChatColor.DARK_RED + ChatColor.ITALIC + " (unlinked)";
        String[] tgt = this.target.split(":");
        switch (tgt[0]) {
            case "portal": return this.niceName() + ChatColor.DARK_GRAY + " -> " + tgt[1];
            case "random": return this.niceName() + ChatColor.DARK_GRAY + " -> " + ChatColor.DARK_GREEN + "(random warp)";
            case "point": return this.niceName() + ChatColor.DARK_GRAY + " -> " + ChatColor.DARK_AQUA + "(to " + tgt[1] + ")";
        }
        return this.niceName() + ChatColor.RED + "[Error] " + ChatColor.RESET + "Portal target is broken.";
    }

    public void visit(Player player) {
        Block exit = this.location.getBlock();
        if (this.isEW) {
            exit = exit.getRelative(0, 0, -2);
            if (!checkFree(exit)) exit = exit.getRelative(0, 0, 4);
        } else {
            exit = exit.getRelative(-2, 0, 0);
            if (!checkFree(exit)) exit = exit.getRelative(4, 0, 0);
        }
        if (!checkFree(exit)) {
            player.sendMessage("It seems there's no free space around the portal-- please check. Portal location: " + this.location.toVector());
            return;
        }
        player.teleport(exit.getLocation());
    }

    private boolean isUnsafeBlock(Block check) {
        if (check.getType() == Material.AIR || check.getType() == Material.WATER || check.getType() == Material.STATIONARY_WATER ||
                check.getType() == Material.STATIONARY_LAVA || check.getType() == Material.WEB || check.getType() == Material.LAVA ||
                check.getType() == Material.CACTUS || check.getType() == Material.ENDER_PORTAL || check.getType() == Material.PORTAL)
            return true;
        else return false;
    }

    /**
     * Check if a certain block has enough room to teleport a player
     * there without suffocating, and a safe floor below.
     */
    private boolean checkFree(Block b) {
        if (b.getType() != Material.AIR || b.getRelative(BlockFace.UP).getType() != Material.AIR) return false;
        int down = 0;
        while (down < 5) {
            b = b.getRelative(BlockFace.DOWN);
            if (!isUnsafeBlock(b)) return true;
            down++;
        }
        return false;
    }

    public void teleport(Player player) {
        if (this.target == null) return;
        if (this.cooldown.isAfter(Instant.now())) return;
        String[] tgt = this.target.split(":");

        if (this.link != null && tgt[0].equals("portal")) {
            // where is player moving? find a block in that direction from the exit
            Vector exitDirection = player.getLocation().getDirection().clone();

            // source and target portal have different orientations -> flip axes
            if (this.isEW != this.link.isEW) { exitDirection = Utilities.rotateVector(exitDirection, 90); }
            // portal is flipped -> flip directions
            if (this.flipped) { exitDirection.multiply(-1); }
            if (Math.abs(exitDirection.getX()) > Math.abs(exitDirection.getZ())) exitDirection.setZ(0);
            else exitDirection.setX(0);
            // multiply a little extra because ugh float rounding errors
            exitDirection.normalize().setY(0).multiply(1.01);

            Block exit = this.link.location.clone().add(exitDirection).getBlock();
            if (!this.checkFree(exit)) {
                player.setVelocity(player.getLocation().getDirection().clone().normalize().multiply(-0.7).setY(0));
                player.sendMessage("Something is blocking the other side...");
                this.enableCooldown();
                return;
            }

            Location tport = exit.getLocation();
            tport.setDirection(exitDirection);
            player.teleport(tport);
            player.setVelocity(tport.getDirection().normalize().multiply(0.7));
            this.enableCooldown();

        } else if (tgt[0].equals("random")) {
            int tries = 0;
            while (tries <= Portal.randomMaxTries) {
                tries++;
                int x = this.location.getBlockX() + ThreadLocalRandom.current().nextInt(-Portal.randomRange, Portal.randomRange);
                int z = this.location.getBlockZ() + ThreadLocalRandom.current().nextInt(-Portal.randomRange, Portal.randomRange);
                int y = player.getWorld().getHighestBlockYAt(x, z);
                Block check = player.getWorld().getBlockAt(x, y-1, z);
                if (isUnsafeBlock(check)) continue;
                player.teleport(check.getRelative(0, 2, 0).getLocation());
                this.enableCooldown();
                return;
            }
            player.setVelocity(player.getLocation().getDirection().clone().normalize().multiply(-0.7).setY(0));
            player.sendMessage("It seems there's no good place for you right now. Try again in a few seconds.");
            this.enableCooldown();

        } else if (tgt[0].equals("point")) {
            try {
                List<Double> vec = Arrays.stream(tgt[1].split(",")).map(s -> Double.parseDouble(s)).collect(Collectors.toList());
                Float pitch = Float.parseFloat(tgt[2]);
                Float yaw = Float.parseFloat(tgt[3]);
                Location loc = new Location(this.location.getWorld(), vec.get(0), vec.get(1), vec.get(2));
                loc.setYaw(yaw);
                loc.setPitch(pitch);
                player.teleport(loc);
                this.enableCooldown();

            } catch (NumberFormatException nfex) {
                player.sendMessage("Something about this portal feels wrong... " + ChatColor.DARK_GRAY +  "(misconfigured portal)");
                this.enableCooldown();
            }
        }
    }
}
