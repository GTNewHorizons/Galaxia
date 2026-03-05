package com.gtnewhorizons.galaxia.registry.block.planet;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;

import cpw.mods.fml.common.registry.GameRegistry;

public class PlanetBlockBuilder {

    private final String fullPath;
    private String name;

    private boolean isFalling = false;
    private boolean isTransparent = false;

    // drops self by default
    private Item dropItem = null;
    private float hardness = 2.0F;
    private float slipperiness = 0.6F;
    private int harvestLevel = 1;
    // pickaxe by default
    private String harvestTool = "pickaxe";
    private int dropAmountMax = 1;
    private int dropAmountMin = 1;

    private PlanetBlockBuilder(String fullPath) {
        this.fullPath = fullPath;
        int lastSlash = fullPath.lastIndexOf('/');
        this.name = (lastSlash >= 0) ? fullPath.substring(lastSlash + 1) : fullPath;
    }

    public static PlanetBlockBuilder create(String fullPath) {
        return new PlanetBlockBuilder(fullPath);
    }

    public PlanetBlockBuilder slipperiness(float slipperiness) {
        this.slipperiness = slipperiness;
        return this;
    }

    public PlanetBlockBuilder falling() {
        this.isFalling = true;
        this.isTransparent = false;
        return this;
    }

    public PlanetBlockBuilder transparent() {
        this.isTransparent = true;
        this.isFalling = false;
        return this;
    }

    public PlanetBlockBuilder drop(Item drop) {
        this.dropItem = drop;
        return this;
    }

    public PlanetBlockBuilder drop(GalaxiaItemList drop) {
        return drop(drop != null ? drop.getItem() : null);
    }

    public PlanetBlockBuilder dropAmount(int min, int max) {
        this.dropAmountMin = min;
        this.dropAmountMax = max;
        return this;
    }

    public PlanetBlockBuilder dropSelf() {
        this.dropItem = null;
        return this;
    }

    public PlanetBlockBuilder hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    public PlanetBlockBuilder shovel() {
        this.harvestTool = "shovel";
        return this;
    }

    public PlanetBlockBuilder harvest(int level) {
        this.harvestLevel = level;
        return this;
    }

    public Block build() {
        Block block;

        if (isFalling) {
            block = new BlockPlanetGalaxiaFalling(
                fullPath,
                dropItem,
                dropAmountMin,
                dropAmountMax,
                hardness,
                harvestLevel,
                harvestTool,
                slipperiness);
        } else if (isTransparent) {
            block = new BlockPlanetTransparentGalaxia(
                fullPath,
                dropItem,
                dropAmountMin,
                dropAmountMax,
                hardness,
                harvestLevel,
                harvestTool,
                slipperiness);
        } else {
            block = new BlockPlanetGalaxiaGeneral(
                fullPath,
                dropItem,
                dropAmountMin,
                dropAmountMax,
                hardness,
                harvestLevel,
                harvestTool,
                slipperiness);
        }

        GameRegistry.registerBlock(block, name);
        block.setCreativeTab(Galaxia.creativeTab);

        return block;
    }
}
