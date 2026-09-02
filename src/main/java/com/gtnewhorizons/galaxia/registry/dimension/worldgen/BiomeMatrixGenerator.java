package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.HashMap;

/**
 * Creates a biome matrix using a character matrix like a shaped crafting recipe
 */
public class BiomeMatrixGenerator {

    private final String[] characterMatrix;
    private final HashMap<Character, BiomeGenBase> biomeEntries = new HashMap<>();

    /**
     * Creates a biome matrix using a character matrix
     * @param characterMatrix Character matrix to parse into biomes
     */
    public BiomeMatrixGenerator(String[] characterMatrix) {
        this.characterMatrix = characterMatrix;
    }

    /**
     * Assigns a biome to a character
     * @param key Character to assign the biome to
     * @param biome Biome to be assigned
     * @return The object (this is a builder method)
     */
    public BiomeMatrixGenerator addBiomeEntry(Character key, BiomeGenBase biome) {
        biomeEntries.put(key, biome);
        return this;
    }

    /**
     * Creates a biome matrix from previously specified values
     * @return The finished biome matrix
     */
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
