package de.teamalbin.spigotmc.easyportals;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.List;

public class Portal {
    private static Sound teleportSound = Sound.ENTITY_ENDERMEN_TELEPORT;

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

    public void setLink(Player player, Portal link) {
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

    public void setRandom(Player player) {
        if (this.link != null) {
            this.unlink(player);
            this.link.unlink(player);
        }
        this.link = null;
        this.target = "random";
        if (player != null) player.sendMessage("Portal " + this.niceName() + " is now a random warp.");
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

    /**
     * Check if a certain block has enough room to teleport a player
     * there without suffocating.
     */
    private boolean checkFree(Block b) {
        return b.getType() == Material.AIR && b.getRelative(BlockFace.UP).getType() == Material.AIR;
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
            exitDirection.normalize().setY(0);

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
            this.location.getWorld().playSound(this.location, Portal.teleportSound, 0.5f, 1.0f);
            this.link.location.getWorld().playSound(this.link.location, Portal.teleportSound, 0.5f, 1.0f);
            this.enableCooldown();

        } else if (tgt[0].equals("random")) {
            // teleport to random place in the world
        }
    }
}
