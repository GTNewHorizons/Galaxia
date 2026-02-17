package com.gtnewhorizons.galaxia.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.Galaxia;

public class BlockPlanetGalaxia extends BlockFalling {

    private final String planetName;
    private final BlockVariant[] variants;
    private IIcon[] icons;

    public BlockPlanetGalaxia(String planetName, BlockVariant... variants) {
        super(Material.rock);
        this.planetName = planetName;
        this.variants = variants;

        setStepSound(soundTypeStone);
        setCreativeTab(Galaxia.creativeTab);
    }

    public String getPlanetName() {
        return planetName;
    }

    public int getVariantCount() {
        return variants.length;
    }

    public String getVariantSuffix(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return variants[meta].suffix;
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        int meta = MathHelper.clamp_int(world.getBlockMetadata(x, y, z), 0, variants.length - 1);
        return variants[meta].hardness;
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[variants.length];
        for (int i = 0; i < variants.length; i++) {
            String texture = "galaxia:" + planetName + capitalize(variants[i].suffix);
            icons[i] = reg.registerIcon(texture);
        }
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        meta = MathHelper.clamp_int(meta, 0, icons.length - 1);
        return icons[meta];
    }

    private boolean shouldFall(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return variants[meta].falling;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        if (shouldFall(world.getBlockMetadata(x, y, z))) {
            super.onBlockAdded(world, x, y, z);
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (shouldFall(world.getBlockMetadata(x, y, z))) {
            super.onNeighborBlockChange(world, x, y, z, neighbor);
        }
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        if (shouldFall(world.getBlockMetadata(x, y, z))) {
            super.updateTick(world, x, y, z, rand);
        }
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? ""
            : s.substring(0, 1)
                .toUpperCase() + s.substring(1);
    }
}
