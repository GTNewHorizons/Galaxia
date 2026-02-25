package com.gtnewhorizons.galaxia.utility;

public class BiomeIdOffsetter {

    private static int biomeId = 100;

    public static int getBiomeId() {
        return biomeId++;
    }
}
