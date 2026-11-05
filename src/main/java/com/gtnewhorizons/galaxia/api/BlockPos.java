package com.gtnewhorizons.galaxia.api;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public record BlockPos(int x, int y, int z) {

    @SuppressWarnings("unchecked")
    public <T extends TileEntity> T getTE(World world) {
        return (T) world.getTileEntity(x, y, z);
    }

    /**
     * Tile entity at this position if it is of the given type
     *
     * @param world The world to look in
     * @param type  The expected tile entity class
     *
     * @return The tile entity, or null when there is none or it has another type
     */
    public <T> T getTE(World world, Class<T> type) {
        TileEntity te = world.getTileEntity(x, y, z);
        return type.isInstance(te) ? type.cast(te) : null;
    }

    public Block getBlock(World world) {
        return world.getBlock(x, y, z);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", this.x);
        tag.setInteger("y", this.y);
        tag.setInteger("z", this.z);
        return tag;
    }

    public static BlockPos fromNBT(NBTTagCompound tag) {
        return new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
    }

    public static NBTTagList listToNBT(List<BlockPos> positions) {
        NBTTagList tagList = new NBTTagList();
        for (BlockPos pos : positions) {
            tagList.appendTag(pos.toNBT());
        }
        return tagList;
    }

    public static List<BlockPos> listFromNBT(NBTTagList tagList) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < tagList.tagCount(); i++) {
            positions.add(fromNBT(tagList.getCompoundTagAt(i)));
        }
        return positions;
    }
}
