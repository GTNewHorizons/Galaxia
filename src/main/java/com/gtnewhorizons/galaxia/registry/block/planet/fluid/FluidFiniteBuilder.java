package com.gtnewhorizons.galaxia.registry.block.planet.fluid;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;

import cpw.mods.fml.common.registry.GameRegistry;

public class FluidFiniteBuilder {

    private final String name;
    private final String texturePrefix;

    // Fluid
    private int density = 1000; // same as water
    private int viscosity = 1000;
    private int temperature = 300;
    private boolean gaseous = false;
    private int luminosity = 0;

    // Block
    private Material material = Material.water;
    private float hardness = 100.0F;
    private int lightOpacity = 3;
    private float lightLevel = 0.0F;
    private boolean distinctStillAndFlowTextures = true;

    // Bucket
    private boolean registerBucket = true;

    private FluidCustom fluid;
    private BlockFluidFiniteCustom block;
    private ItemBucketCustom bucket;

    private FluidFiniteBuilder(String fullPath) {
        this.texturePrefix = fullPath;
        int lastSlash = fullPath.lastIndexOf('/');
        this.name = (lastSlash >= 0) ? fullPath.substring(lastSlash + 1) : fullPath;
    }

    public static FluidFiniteBuilder create(String name) {
        return new FluidFiniteBuilder(name);
    }

    // Fluid setters
    public FluidFiniteBuilder density(int density) {
        this.density = density;
        return this;
    }

    public FluidFiniteBuilder viscosity(int viscosity) {
        this.viscosity = viscosity;
        return this;
    }

    public FluidFiniteBuilder temperature(int temperature) {
        this.temperature = temperature;
        return this;
    }

    public FluidFiniteBuilder luminosity(int luminosity) {
        this.luminosity = luminosity;
        return this;
    }

    // Block setters
    public FluidFiniteBuilder material(Material material) {
        this.material = material;
        return this;
    }

    public FluidFiniteBuilder hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    public FluidFiniteBuilder lightOpacity(int lightOpacity) {
        this.lightOpacity = lightOpacity;
        return this;
    }

    public FluidFiniteBuilder lightLevel(float lightLevel) {
        this.lightLevel = lightLevel;
        return this;
    }

    public FluidFiniteBuilder distinctStillAndFlowTextures(boolean distinctStillAndFlowTextures) {
        this.distinctStillAndFlowTextures = distinctStillAndFlowTextures;
        return this;
    }

    // Bucket setters
    public FluidFiniteBuilder noBucket() {
        this.registerBucket = false;
        return this;
    }

    public FluidFiniteBuilder buildAndRegister() {
        // Fluid
        fluid = new FluidCustom(name, density, viscosity, temperature, luminosity);
        FluidRegistry.registerFluid(fluid);

        // Block
        block = new BlockFluidFiniteCustom(fluid, material, texturePrefix, distinctStillAndFlowTextures);
        block.setHardness(hardness);
        block.setLightOpacity(lightOpacity);
        block.setLightLevel(lightLevel);
        GameRegistry.registerBlock(block, name + "_block");

        // Bucket
        if (registerBucket) {
            bucket = new ItemBucketCustom(block);
            GameRegistry.registerItem(bucket, name + "_bucket");

            FluidContainerRegistry
                .registerFluidContainer(fluid, new ItemStack(bucket), FluidContainerRegistry.EMPTY_BUCKET);
        }

        return this;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public Block getBlock() {
        return block;
    }

    public Item getBucket() {
        return bucket;
    }
}
