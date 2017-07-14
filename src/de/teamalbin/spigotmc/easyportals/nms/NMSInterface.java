package de.teamalbin.spigotmc.easyportals.nms;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;

public interface NMSInterface {
    /**
     * Sets many blocks' types at once, bypassing spigot/bukkit.
     * @param blocks List of blocks to change
     * @param data Block data (most often: 0-3 for rotation).
     * @param blockType The new block type
     */
    void massSetBlockType(List<Block> blocks, byte data, Material blockType);
}
