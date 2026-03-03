package com.gtnewhorizons.galaxia.registry.block.planet;

import static com.gtnewhorizons.galaxia.core.Galaxia.TEXTURE_PREFIX;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.base.BlockVariant;

public class BlockPlanetGalaxia extends BlockFalling {

    private final String planetName;
    private final BlockVariant[] variants;
    private final Item[] drops;
    private IIcon[] icons;

    public BlockPlanetGalaxia(String planetName, Item[] drops, BlockVariant... variants) {
        super(Material.rock);
        this.planetName = planetName;
        this.variants = variants;
        this.drops = drops;

        if (drops.length != variants.length) {
            throw new IllegalArgumentException("Drops array must match variants length");
        }

        setStepSound(soundTypeStone);
        setCreativeTab(Galaxia.creativeTab);
    }

    public String getPlanetName() {
        return planetName;
    }

    public int getVariantCount() {
        return variants.length;
    }

    @Override
    public String getHarvestTool(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return variants[meta].harvestTool();
    }

    @Override
    public int getHarvestLevel(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return variants[meta].harvestLevel();
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        Item dropItem = drops[meta];
        if (dropItem != null && variants[meta].dropsDust()) {
            return dropItem;
        }
        return Item.getItemFromBlock(this);
    }

    @Override
    public int damageDropped(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return drops[meta] != null && variants[meta].dropsDust() ? 0 : meta;
    }

    public String getVariantSuffix(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return variants[meta].suffix();
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        int meta = MathHelper.clamp_int(world.getBlockMetadata(x, y, z), 0, variants.length - 1);
        return variants[meta].hardness();
    }

    @Override
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[variants.length];
        String planetFolder = planetName.toLowerCase();
        for (int i = 0; i < variants.length; i++) {
            String suffixSnake = toSnakeCase(variants[i].suffix());
            String texturePath = TEXTURE_PREFIX + planetFolder + "/" + planetFolder + "_" + suffixSnake;
            icons[i] = reg.registerIcon(texturePath);
        }
    }

    private static String toSnakeCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2")
            .toLowerCase();
    }

    @Override
    public IIcon getIcon(int side, int meta) {
        meta = MathHelper.clamp_int(meta, 0, icons.length - 1);
        return icons[meta];
    }

    private boolean shouldFall(int meta) {
        meta = MathHelper.clamp_int(meta, 0, variants.length - 1);
        return variants[meta].falling();
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
