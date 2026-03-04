package com.gtnewhorizons.galaxia.registry.block.planet;

import java.util.Random;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;

public class BlockPlanetGalaxiaFalling extends BlockFalling {

    protected final String blockName;
    protected final String texturePath;
    protected final Item drop;
    protected final int harvestLevel;
    protected final float hardness;
    protected final float slipperiness;
    protected final String harvestTool;

    public BlockPlanetGalaxiaFalling(String name, Item drop, float hardness, int harvestLevel, String harvestTool,
        float slipperiness) {
        super(Material.rock);

        this.texturePath = name;
        int last = name.lastIndexOf('/');
        this.blockName = (last >= 0) ? name.substring(last + 1) : name;

        this.drop = drop;
        this.harvestLevel = harvestLevel;
        this.harvestTool = harvestTool;
        this.hardness = hardness;
        this.slipperiness = slipperiness;

        this.setBlockName(blockName);
        this.setBlockTextureName(Galaxia.TEXTURE_PREFIX + texturePath);
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public String getHarvestTool(int meta) {
        return harvestTool;
    }

    @Override
    public int getHarvestLevel(int meta) {
        return harvestLevel;
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        return hardness;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        return drop != null ? drop : Item.getItemFromBlock(this);
    }
}
