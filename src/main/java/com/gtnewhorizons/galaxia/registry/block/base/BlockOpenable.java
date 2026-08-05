package com.gtnewhorizons.galaxia.registry.block.base;

import net.minecraft.block.material.Material;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public abstract class BlockOpenable extends BlockUpdatable {

    public static final int META_CLOSED = 0;
    public static final int META_OPEN = 1;

    protected BlockOpenable(Material materialIn) {
        super(materialIn);
    }

    @Override
    public final boolean isOpaqueCube() {
        return false;
    }

    @Override
    public final boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public final int getLightOpacity() {
        return 0;
    }

    @Override
    public final boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        if (isOpen(
            world,
            x - Facing.offsetsXForSide[side],
            y - Facing.offsetsYForSide[side],
            z - Facing.offsetsZForSide[side])) {
            return false;
        }

        return super.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    public final AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        if (isOpen(world, x, y, z)) {
            return null;
        }

        setBlockBoundsBasedOnState(world, x, y, z);
        return AxisAlignedBB
            .getBoundingBox(x + this.minX, y + this.minY, z + this.minZ, x + this.maxX, y + this.maxY, z + this.maxZ);
    }

    @Override
    public final boolean canCollideCheck(int meta, boolean hitIfLiquid) {
        return true;
    }

    public void setOpen(World world, int x, int y, int z, boolean open) {
        int meta = open ? META_OPEN : META_CLOSED;

        world.setBlockMetadataWithNotify(x, y, z, meta, 3);
    }

    public boolean isOpen(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == META_OPEN;
    }

    @Override
    public final MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        if (isOpen(world, x, y, z)) {
            return null;
        }

        return super.collisionRayTrace(world, x, y, z, start, end);
    }
}
