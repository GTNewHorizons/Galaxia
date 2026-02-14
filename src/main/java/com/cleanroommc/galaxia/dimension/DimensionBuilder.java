package com.cleanroommc.galaxia.dimension;

import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.function.Supplier;

public class DimensionBuilder {
    private int ID;
    private String name;
    private Class<? extends WorldProvider> providerClass;
    private Supplier<BiomeGenBase> biomeSupplier;
    private Supplier<IChunkProvider> chunkGenSupplier;
    private int dimensionId;
    private boolean keepLoaded = true;

    public DimensionBuilder name(String name) {
        this.name = name;
        return this;
    }

    public DimensionBuilder id(int ID) {
        this.ID = ID;
        return this;
    }

    public DimensionBuilder provider(Class<? extends WorldProvider> providerClass) {
        this.providerClass = providerClass;
        return this;
    }

    public DimensionBuilder biome(Supplier<BiomeGenBase> biomeSupplier) {
        this.biomeSupplier = biomeSupplier;
        return this;
    }

    public DimensionBuilder chunkGenerator(Supplier<IChunkProvider> chunkGenSupplier) {
        this.chunkGenSupplier = chunkGenSupplier;
        return this;
    }

    public DimensionBuilder dimensionId(int id) {
        this.dimensionId = id;
        return this;
    }

    public DimensionBuilder keepLoaded(boolean keep) {
        this.keepLoaded = keep;
        return this;
    }

    public void build() {
        if (providerClass == null) throw new IllegalStateException("Provider class required for dimension " + name);
        if (ID == 0) throw new IllegalStateException("Invalid ID for " + name);

        int providerType = ID;

        DimensionManager.registerProviderType(providerType, providerClass, keepLoaded);

        if (!DimensionManager.isDimensionRegistered(ID)) {
            DimensionManager.registerDimension(ID, providerType);
            System.out.println("[Galaxia] Registered dimension: " + name + " (ID: " + ID + ")");
        } else {
            System.out.println("[Galaxia] Dimension ID " + ID + " already taken for " + name);
        }
    }
}
