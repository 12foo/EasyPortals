package de.teamalbin.spigotmc.easyportals.nms;

import net.minecraft.server.v1_12_R1.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

import java.util.List;

public class NMSInterface_1_12 implements NMSInterface {

    @Override
    public void massSetBlockType(List<Block> blocks, byte data, Material blockType) {
        if (blocks.isEmpty()) return;
        World world = ((CraftWorld) blocks.get(0).getWorld()).getHandle();
        @SuppressWarnings("deprecation")
        IBlockData blockData = net.minecraft.server.v1_12_R1.Block.getByCombinedId(blockType.getId() + (data << 12));
        for (Block b : blocks) {
            Chunk chunk = world.getChunkAt(b.getX() >> 4, b.getZ() >> 4);
            BlockPosition pos = new BlockPosition(b.getX(), b.getY(), b.getZ());
            world.setTypeAndData(pos, blockData, 2);
            chunk.a(pos, blockData);
        }
    }
}
