package de.teamalbin.spigotmc.easyportals;

import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.Iterator;

public class Utilities {

    /**
     * Utility class that implements a block scan. It returns all blocks within
     * some distance around a location, in a pattern that's optimized to find
     * things near the center block and nearly on the same y level first.
     */
    public static class BlockScan implements Iterator<Block>, Iterable<Block> {
        private Location around;
        private int distance;
        private int rx;
        private int ry;
        private int rz;

        public BlockScan(Location around, int distance) {
            this.around = around;
            this.distance = distance;
            this.rx = -distance;
            this.ry = -distance;
            this.rz = -distance;
        }

        @Override
        public boolean hasNext() {
            return this.ry <= this.distance;
        }

        @Override
        public Block next() {
            // TODO: optimize search (spiral search on x and z axes, alternating y layers)
            Block b = around.getBlock().getRelative(this.rx, this.ry, this.rz);
            this.rx++;
            if (this.rx > this.distance) { this.rx = - this.distance; this.rz++; }
            if (this.rz > this.distance) { this.rz = - this.distance; this.ry++; }
            return b;
        }

        @Override
        public Iterator<Block> iterator() {
            return this;
        }
    }

    /**
     * Returns which cardinal direction a given location is mostly facing.
     */
    public static BlockFace cardinalDirection(Location loc) {
        float rot = (loc.getYaw() - 90) % 360;
        if (rot < 0) rot += 360;
        if (0 <= rot && rot < 45) {
            return BlockFace.NORTH;
        } else if (45 <= rot && rot < 135) {
            return BlockFace.EAST;
        } else if (135 <= rot && rot < 225) {
            return BlockFace.SOUTH;
        } else if (225 <= rot && rot < 315) {
            return BlockFace.WEST;
        } else if (315 <= rot && rot <= 360) {
            return BlockFace.NORTH;
        } else {
            return BlockFace.NORTH;
        }
    }

    public static Vector rotateVector(Vector in, float degrees) {
        double rad = Math.toRadians(degrees);
        double currentX = in.getX();
        double currentZ = in.getZ();
        double cosine = Math.cos(rad);
        double sine = Math.sin(rad);
        return new Vector((cosine * currentX - sine * currentZ), in.getY(), (sine * currentX + cosine * currentZ));
    }

}
