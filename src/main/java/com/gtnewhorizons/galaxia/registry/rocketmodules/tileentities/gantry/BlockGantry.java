package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.block.base.BlockUpdatable;

/**
 * Gantry rail. Connects to every gantry or terminal it touches, like a pipe, and only occupies the beam it draws
 */
public class BlockGantry extends BlockUpdatable implements ITileEntityProvider {

    /** Half the size of the beam, the bounds grow from the centre to every connected face */
    private static final float BEAM = 0.25f;

    public BlockGantry() {
        super(Material.iron);
        this.setHardness(3.0F);
        this.setHarvestLevel("pickaxe", 1);
        this.setResistance(10.0f);
        this.setBlockTextureName("galaxia:machine/launchpad_sheeting");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityGantry();
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        TileEntityGantry.onRailChanged(world, x, y, z);
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntityGantry.onRailChanged(world, x, y, z);
        super.breakBlock(world, x, y, z, block, meta);
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
     * Sides the drawn rail reaches. A rail with a single connection is drawn as a full beam through the block, a rail
     * without any as a beam along X
     */
    private static int shapeMask(IBlockAccess world, int x, int y, int z) {
        int mask = TileEntityGantry.connectionMask(world, x, y, z);
        if (mask == 0) return (1 << ForgeDirection.EAST.ordinal()) | (1 << ForgeDirection.WEST.ordinal());
        if (Integer.bitCount(mask) == 1) {
            ForgeDirection only = ForgeDirection.getOrientation(Integer.numberOfTrailingZeros(mask));
            mask |= 1 << only.getOpposite()
                .ordinal();
        }
        return mask;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        int mask = shapeMask(world, x, y, z);
        float min = 0.5f - BEAM, max = 0.5f + BEAM;
        setBlockBounds(
            has(mask, ForgeDirection.WEST) ? 0 : min,
            has(mask, ForgeDirection.DOWN) ? 0 : min,
            has(mask, ForgeDirection.NORTH) ? 0 : min,
            has(mask, ForgeDirection.EAST) ? 1 : max,
            has(mask, ForgeDirection.UP) ? 1 : max,
            has(mask, ForgeDirection.SOUTH) ? 1 : max);
    }

    /**
     * Collides with the beam in the centre and one arm per connection, so players can walk past a rail
     */
    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list,
        Entity entity) {
        int connections = shapeMask(world, x, y, z);
        float min = 0.5f - BEAM, max = 0.5f + BEAM;
        setBlockBounds(min, min, min, max, max, max);
        super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (!has(connections, side)) continue;
            setBlockBounds(
                side.offsetX < 0 ? 0 : side.offsetX > 0 ? max : min,
                side.offsetY < 0 ? 0 : side.offsetY > 0 ? max : min,
                side.offsetZ < 0 ? 0 : side.offsetZ > 0 ? max : min,
                side.offsetX < 0 ? min : side.offsetX > 0 ? 1 : max,
                side.offsetY < 0 ? min : side.offsetY > 0 ? 1 : max,
                side.offsetZ < 0 ? min : side.offsetZ > 0 ? 1 : max);
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        }
        setBlockBoundsBasedOnState(world, x, y, z);
    }

    private static boolean has(int mask, ForgeDirection side) {
        return (mask & (1 << side.ordinal())) != 0;
    }
}
