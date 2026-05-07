package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;

public class BlockGantry extends BlockUpdatable implements ITileEntityProvider {

    public BlockGantry() {
        super(Material.iron);
        this.setHardness(1.5F);
        this.setResistance(10.0f);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantry();
    }

    /**
     * Handles logic to be ran on block placing - in this case, connecting to other
     * gantries
     *
     * @param world  The world placed in
     * @param x      X position of placed block
     * @param y      Y position of placed block
     * @param z      Z position of placed block
     * @param placer The placer of the block
     * @param stack  The item stack being used to place
     */
    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityGantry teg)) {
            return;
        }

        // Check if there's a gantry directly above or below
        TileEntityGantry conflictGantry = null;
        if (world.getTileEntity(x, y - 1, z) instanceof TileEntityGantry g) conflictGantry = g;
        else if (world.getTileEntity(x, y + 1, z) instanceof TileEntityGantry g) conflictGantry = g;

        if (conflictGantry != null) {
            Vec3 redirect = getLineEndDirection(conflictGantry);
            int nx = x + (redirect != null ? (int) redirect.xCoord : 1);
            int nz = z + (redirect != null ? (int) redirect.zCoord : 0);

            // Remove from wrong position and place at redirected position
            world.setBlock(x, y, z, Blocks.air, 0, 2);
            if (world.getBlock(nx, y, nz)
                .isReplaceable(world, nx, y, nz)) {
                world.setBlock(nx, y, nz, this, 0, 3);
                // Manually re-trigger this same logic for the new position
                onBlockPlacedBy(world, nx, y, nz, placer, stack);
            }
            return;
        }

        // Check valid directions and connect to others
        for (Vec3 check_offset : GantryAPI.CHECK_OFFSETS) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTe = world.getTileEntity(cx, cy, cz);
            if (checkTe instanceof TileEntityGantry checkGantry) {
                teg.connect(checkGantry);
            }
        }

    }

    /**
     * Handles logic on block break - in this case disconnecting from other gantries
     *
     * @param world The world placed in
     * @param x     X position of placed block
     * @param y     Y position of placed block
     * @param z     Z position of placed block
     * @param block The block to break
     * @param meta  The metadata of the block being broken
     */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        TileEntity gantry = world.getTileEntity(x, y, z);
        if (!(gantry instanceof TileEntityGantry terminal)) {
            return;
        }

        // Iterate through neighbours and disconnect them
        for (Vec3 check_offset : new ArrayList<>(terminal.neighbourDirs)) {
            int cx = x + (int) check_offset.xCoord;
            int cy = y + (int) check_offset.yCoord;
            int cz = z + (int) check_offset.zCoord;

            TileEntity checkTileEntity = world.getTileEntity(cx, cy, cz);
            if (checkTileEntity instanceof TileEntityGantry checkGantry) {
                terminal.disconnect(checkGantry);
            }
        }

    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, int x, int y, int z) {
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World worldIn, int x, int y, int z) {
        TileEntityGantry conflictGantry = null;
        if (worldIn.getTileEntity(x, y - 1, z) instanceof TileEntityGantry g) conflictGantry = g;
        else if (worldIn.getTileEntity(x, y + 1, z) instanceof TileEntityGantry g) conflictGantry = g;

        if (conflictGantry != null) {
            Vec3 redirect = getLineEndDirection(conflictGantry);
            int nx = x + (redirect != null ? (int) redirect.xCoord : 1);
            int nz = z + (redirect != null ? (int) redirect.zCoord : 0);

            // check we have no gantries above or below the diagonal
            return (!(worldIn.getTileEntity(nx, y - 1, nz) instanceof TileEntityGantry))
                && (!(worldIn.getTileEntity(nx, y + 1, nz) instanceof TileEntityGantry));
        }
        return true;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    /**
     * Overrides the render type to not use the block render engine, but instead
     * solely use TESR
     *
     * @return The render type (always -1 in this case)
     */
    @Override
    public int getRenderType() {
        return -1;
    }

    /**
     * Finds the "free-end" direction of the gantry line that the gantry
     * belongs to.
     * <p>
     * We sum the unit vectors toward every connected neighbor to get the
     * net line direction, then return the opposite (i.e. the direction pointing
     * away from the bulk of the line, toward the open end).
     *
     * @param gantry The gantry whose line end we want to find
     * @return A unit Vec3 in the free-end direction, or null if undetermined
     */
    public static Vec3 getLineEndDirection(TileEntityGantry gantry) {
        double sumX = 0, sumZ = 0;

        for (Vec3 dir : gantry.neighbourDirs) {
            sumX += dir.xCoord;
            sumZ += dir.zCoord;
        }

        if (sumX == 0 && sumZ == 0) {
            // Gantry has balanced neighbors (middle of line) or no neighbors
            return null;
        }

        // The free end is opposite to the net neighbor direction
        return Vec3.createVectorHelper(-Math.signum(sumX), 0, -Math.signum(sumZ));
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list,
        Entity entity) {
        DiagonalType slope = checkDiagonalType(world, x, y, z);

        if (slope == DiagonalType.SLOPE_X_ASCENDING) {
            this.setBlockBounds(0f, 0f, 0f, 0.33f, 0.33f, 1.0f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0.33f, 0.33f, 0f, 0.66f, 0.66f, 1.0f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0.66F, 0.66F, 0.0F, 1.0F, 1.0F, 1.0F);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        } else if (slope == DiagonalType.SLOPE_X_DESCENDING) {
            this.setBlockBounds(0.66F, 0.0F, 0.0F, 1.0F, 0.33F, 1.0F);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0.33F, 0.0F, 0.0F, 0.66F, 0.66F, 1.0F);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0.0F, 0.33F, 0.0F, 0.33F, 1.0F, 1.0F);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        } else if (slope == DiagonalType.SLOPE_Z_ASCENDING) {
            this.setBlockBounds(0f, 0f, 0f, 1.0f, 0.33f, 0.33f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0f, 0.33f, 0f, 0.66f, 0.66f, 0.66f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0f, 0.66f, 0.33f, 1.0f, 1f, 1f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

        } else if (slope == DiagonalType.SLOPE_Z_DESCENDING) {
            this.setBlockBounds(0f, 0f, 0.5f, 1f, 0.33f, 1f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0f, 0f, 0.5f, 1f, 0.66f, 0.66f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);

            this.setBlockBounds(0f, 0f, 0.0f, 1f, 1f, 0.33f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        } else {
            this.setBlockBounds(0f, 0f, 0f, 1f, 1f, 1f);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        }
        this.setBlockBounds(0f, 0f, 0f, 1f, 1f, 1f);
    }

    public enum DiagonalType {
        NONE,
        SLOPE_X_ASCENDING,
        SLOPE_X_DESCENDING,
        SLOPE_Z_ASCENDING,
        SLOPE_Z_DESCENDING
    }

    protected DiagonalType checkDiagonalType(IBlockAccess world, int x, int y, int z) {
        Block thisBlock = world.getBlock(x, y, z);

        boolean posX_posY = world.getBlock(x + 1, y + 1, z) == thisBlock; // Up-East
        boolean negX_negY = world.getBlock(x - 1, y - 1, z) == thisBlock; // Down-West
        if (posX_posY || negX_negY) {
            return DiagonalType.SLOPE_X_ASCENDING;
        }

        boolean negX_posY = world.getBlock(x - 1, y + 1, z) == thisBlock; // Up-West
        boolean posX_negY = world.getBlock(x + 1, y - 1, z) == thisBlock; // Down-East
        if (negX_posY || posX_negY) {
            return DiagonalType.SLOPE_X_DESCENDING;
        }
        // 2. Check Z-Y Plane (South / North slopes)
        boolean posZ_posY = world.getBlock(x, y + 1, z + 1) == thisBlock; // Up-South
        boolean negZ_negY = world.getBlock(x, y - 1, z - 1) == thisBlock; // Down-North
        if (posZ_posY || negZ_negY) {
            return DiagonalType.SLOPE_Z_ASCENDING;
        }

        boolean negZ_posY = world.getBlock(x, y + 1, z - 1) == thisBlock; // Up-North
        boolean posZ_negY = world.getBlock(x, y - 1, z + 1) == thisBlock; // Down-South
        if (negZ_posY || posZ_negY) {
            return DiagonalType.SLOPE_Z_DESCENDING;
        }

        // Default: Not a diagonal
        return DiagonalType.NONE;
    }

}
