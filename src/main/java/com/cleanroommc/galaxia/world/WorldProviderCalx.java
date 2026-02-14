package com.cleanroommc.galaxia.world;

import com.cleanroommc.galaxia.dimension.WorldProviderSpace;
import net.minecraft.world.biome.BiomeGenBase;

public class WorldProviderCalx extends WorldProviderSpace {

    @Override
    public String getDimensionName() {
        return "Calx (Moon)";
    }

    @Override
    protected BiomeGenBase getBiome() {
        return new BiomeGenCalx(150);
    }
}
