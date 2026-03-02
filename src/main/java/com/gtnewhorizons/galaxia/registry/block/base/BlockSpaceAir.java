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
    public static final int[][] adjacents = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    public static boolean isDepressurized(World worldIn, int x, int y, int z) {
        return worldIn.getBlock(x, y, z) == Block.getBlockFromName("galaxia:space_air")
            && worldIn.getBlockMetadata(x, y, z) == 1;
    }

    public static boolean isVoid(World worldIn, int x, int y, int z) {
        return worldIn.getBlock(x, y, z) == Block.getBlockFromName("galaxia:space_air")
            && worldIn.getBlockMetadata(x, y, z) == 0;
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

        switch (worldIn.getBlockMetadata(x, y, z)) {
            case 0:
                if (neighbor == this) break;
                for (int[] d : adjacents) {
                    if (worldIn.getBlock(x + d[0], y + d[1], z + d[2]) == Blocks.air) {
                        worldIn.setBlock(x + d[0], y + d[1], z + d[2], this, 1, 2);
                        worldIn.notifyBlockOfNeighborChange(x + d[0], y + d[1], z + d[2], this);
                    }
                }
                break;
            case 1:
            case 2:
                worldIn.scheduleBlockUpdate(x, y, z, this, this.tickRate(worldIn));
                break;
        }
    }

    @Override
    public void updateTick(World worldIn, int x, int y, int z, Random random) {
        switch (worldIn.getBlockMetadata(x, y, z)) {
            case 0:
            case 1:
                for (int[] d : adjacents) {
                    if (worldIn.getBlock(x + d[0], y + d[1], z + d[2]) == Blocks.air) {
                        worldIn.setBlock(x + d[0], y + d[1], z + d[2], this, 1, 2);
                        worldIn.notifyBlockOfNeighborChange(x + d[0], y + d[1], z + d[2], this);
                    }
                }
                break;
            case 2:
                worldIn.setBlockToAir(x, y, z);
                if (isVoid(worldIn, x + 1, y, z) || isVoid(worldIn, x - 1, y, z)
                    || isVoid(worldIn, x, y + 1, z)
                    || isVoid(worldIn, x, y - 1, z)
                    || isVoid(worldIn, x, y, z + 1)
                    || isVoid(worldIn, x, y, z - 1)) {
                    worldIn.setBlock(x, y, z, this, 1, 2);
                    worldIn.notifyBlockOfNeighborChange(x, y, z, this);
                    break;
                }
                for (int[] d : adjacents) {
                    if (isDepressurized(worldIn, x + d[0], y + d[1], z + d[2])) {
                        worldIn.setBlock(x + d[0], y + d[1], z + d[2], this, 2, 2);
                        worldIn.notifyBlocksOfNeighborChange(x + d[0], y + d[1], z + d[2], Blocks.air);
                    }
                }
                break;
        }
    }

    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World worldIn, int x, int y, int z, Random random) {
        if (worldIn.getBlockMetadata(x, y, z) != 1) return;
        double[] smokeDir = { 0, 0, 0 };
        boolean doSmoke = random.nextFloat() > 0.98;
        for (int[] d : adjacents) {
            if (worldIn.getBlock(x + d[0], y + d[1], z + d[2]) == Blocks.air) {
                smokeDir = new double[] { smokeDir[0] - d[0], smokeDir[1] - d[1], smokeDir[2] - d[2] };
                doSmoke = true;
            }
        }
        if (doSmoke) {
            for (int i = 0; i < 16; ++i) worldIn.spawnParticle(
                "explode",
                x + 0.5D,
                y + 0.5D,
                z + 0.5D,
                Math.random() * 0.3 * smokeDir[0],
                Math.random() * 0.3 * smokeDir[1],
                Math.random() * 0.3 * smokeDir[2]);
        }
    }

    @SideOnly(Side.CLIENT)
    public int getRenderBlockPass() {
        return 1;
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
