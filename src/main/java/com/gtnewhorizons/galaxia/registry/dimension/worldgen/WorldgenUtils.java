package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.cardinalstar.cubicchunks.util.Mods;
import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import cpw.mods.fml.common.Loader;

public class WorldgenUtils {

    private static final boolean isEIDPresent = Loader.isModLoaded("endlessids");

    public static void setBiomes(Chunk chunk, BiomeGenBase[] biomes) {
        if (isEIDPresent) {
            short[] biomeOut = ((ChunkBiomeHook) chunk).getBiomeShortArray();

            for (int i = 0; i < 256; i++) {
                biomeOut[i] = (short) biomes[i].biomeID;
            }
        } else {
            byte[] biomeOut = chunk.getBiomeArray();

            for (int i = 0; i < 256; i++) {
                biomeOut[i] = (byte) biomes[i].biomeID;
            }
        }
    }
}
