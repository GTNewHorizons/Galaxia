package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.HashMap;

public class BiomeMatrixGenerator {

    private final String[] characterMatrix;
    private final HashMap<Character, BiomeGenBase> biomeEntries = new HashMap<>();

    public BiomeMatrixGenerator(String[] characterMatrix) {
        this.characterMatrix = characterMatrix;
    }

    public BiomeMatrixGenerator addBiomeEntry(Character key, BiomeGenBase biome) {
        biomeEntries.put(key, biome);
        return this;
    }

    public BiomeGenBase[][] createBiomeMatrix() {
        BiomeGenBase[][] biomeMatrix = new BiomeGenBase[characterMatrix.length][characterMatrix[0].length()];
        for (int i = 0; i < characterMatrix.length; i++) {
            String characterLine = characterMatrix[i];
            for (int j = 0; j < characterLine.length(); j++) {
                biomeMatrix[i][j] = biomeEntries.get(characterLine.charAt(j));
            }
        }
        return biomeMatrix;
    }
}
