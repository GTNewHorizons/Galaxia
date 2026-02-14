package com.cleanroommc.galaxia.world;

import net.minecraft.world.biome.BiomeGenBase;

public class BiomeGenCalx extends BiomeGenBase {
    public BiomeGenCalx(int id) {
        super(id);
        this.setBiomeName("Calx Surface");
        this.setHeight(new Height(0.1F, 0.3F));
        this.setTemperatureRainfall(0.4F, 0.0F);
        this.topBlock = net.minecraft.init.Blocks.stone;
        this.fillerBlock = net.minecraft.init.Blocks.stone;
    }
}
