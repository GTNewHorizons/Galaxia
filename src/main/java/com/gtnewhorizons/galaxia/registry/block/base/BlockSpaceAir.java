package com.gtnewhorizons.galaxia.registry.block.base;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockSpaceAir extends Block {

    private static final int METAS = 3;
    // 0 = void
    // 1 = depressurized air
    // 2 = repressurizing air

    public static boolean isBlockDepressurized(World worldIn, int x, int y, int z) {
        return worldIn.getBlock(x, y, z) instanceof BlockSpaceAir && worldIn.getBlockMetadata(x, y, z) == 1;
    }

    public static boolean isBlockVoid(World worldIn, int x, int y, int z) {
        return worldIn.getBlock(x, y, z) instanceof BlockSpaceAir && worldIn.getBlockMetadata(x, y, z) == 0;
    }

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        meta = MathHelper.clamp_int(meta, 0, METAS - 1);
        return icons[meta];
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[METAS];
        for (int i = 0; i < METAS; ++i) {
            icons[i] = reg.registerIcon("galaxia:space_air_" + i);
        }
    }

    public BlockSpaceAir() {
        super(Material.fire);
        this.setBlockName("space_air");
    }

    @Override
    public void onNeighborBlockChange(World worldIn, int x, int y, int z, Block neighbor) {
        super.onNeighborBlockChange(worldIn, x, y, z, neighbor);

        System.out.println("air update, neighbor: " + neighbor.getUnlocalizedName());

        boolean changed = false;
        switch (worldIn.getBlockMetadata(x, y, z)) {
            case 0:
                if (neighbor == this) {
                    break;
                }
            case 1:
                worldIn.spawnParticle("smoke", x, y, z, 0D, 0D, 0D);
                if (worldIn.getBlock(x - 1, y, z) == Blocks.air) {
                    worldIn.setBlock(x - 1, y, z, this, 1, 2);
                    changed = true;
                }
                if (worldIn.getBlock(x + 1, y, z) == Blocks.air) {
                    worldIn.setBlock(x + 1, y, z, this, 1, 2);
                    changed = true;
                }
                if (worldIn.getBlock(x, y, z - 1) == Blocks.air) {
                    worldIn.setBlock(x, y, z - 1, this, 1, 2);
                    changed = true;
                }
                if (worldIn.getBlock(x, y + 1, z) == Blocks.air) {
                    worldIn.setBlock(x, y + 1, z, this, 1, 2);
                    changed = true;
                }
                if (worldIn.getBlock(x, y - 1, z) == Blocks.air) {
                    worldIn.setBlock(x, y - 1, z, this, 1, 2);
                    changed = true;
                }
                if (worldIn.getBlock(x, y, z + 1) == Blocks.air) {
                    worldIn.setBlock(x, y, z + 1, this, 1, 2);
                    changed = true;
                }
                if (changed || worldIn.getBlockMetadata(x, y, z) == 0) {
                    worldIn.scheduleBlockUpdate(x, y, z, this, this.tickRate(worldIn));
                }
                break;
            case 2:
                if (isBlockVoid(worldIn, x + 1, y, z) || isBlockVoid(worldIn, x - 1, y, z)
                    || isBlockVoid(worldIn, x, y + 1, z)
                    || isBlockVoid(worldIn, x, y - 1, z)
                    || isBlockVoid(worldIn, x, y, z + 1)
                    || isBlockVoid(worldIn, x, y, z - 1)) {
                    worldIn.setBlock(x, y, z, this, 1, 2);
                    break;
                }
                if (isBlockDepressurized(worldIn, x + 1, y, z)) {
                    worldIn.setBlock(x + 1, y, z, this, 2, 2);
                }
                if (isBlockDepressurized(worldIn, x - 1, y, z)) {
                    worldIn.setBlock(x - 1, y, z, this, 2, 2);
                }
                if (isBlockDepressurized(worldIn, x, y + 1, z)) {
                    worldIn.setBlock(x, y + 1, z, this, 2, 2);
                }
                if (isBlockDepressurized(worldIn, x, y - 1, z)) {
                    worldIn.setBlock(x, y - 1, z, this, 2, 2);
                }
                if (isBlockDepressurized(worldIn, x, y, z + 1)) {
                    worldIn.setBlock(x, y, z + 1, this, 2, 2);
                }
                if (isBlockDepressurized(worldIn, x, y, z - 1)) {
                    worldIn.setBlock(x, y, z - 1, this, 2, 2);
                }
                worldIn.scheduleBlockUpdate(x, y, z, this, this.tickRate(worldIn));
                break;
        }
    }

    @Override
    public void updateTick(World worldIn, int x, int y, int z, Random random) {
        switch (worldIn.getBlockMetadata(x, y, z)) {
            case 0:
            case 1:
                worldIn.notifyBlocksOfNeighborChange(x, y, z, this);
                // worldIn.markAndNotifyBlock(x, y, z, worldIn.getChunkFromBlockCoords(x, z), Blocks.air, );
                break;
            case 2:
                worldIn.setBlockToAir(x, y, z);
                worldIn.notifyBlocksOfNeighborChange(x, y, z, Blocks.air);
                break;
        }
    }

    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 0;
    }

    /**
     * How many world ticks before ticking
     */
    @Override
    public int tickRate(World worldIn) {
        return 1;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean canCollideCheck(int meta, boolean includeLiquid) {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World worldIn, int x, int y, int z) {
        return null;
    }

}
