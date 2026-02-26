package com.gtnewhorizons.galaxia.worldgen;

/**
 * ENUM to hold all terrain presets
 */
public enum TerrainPreset {

    // ====================== MACRO ======================
    MOUNTAIN_RANGES(Scale.MACRO),
    SHIELD_VOLCANOES(Scale.MACRO),
    LAVA_PLATEAUS(Scale.MACRO),
    PLATEAUS_AND_ESCARPMENTS(Scale.MACRO),
    TECTONIC_RIFTS(Scale.MACRO),
    BASE_HEIGHT(Scale.MACRO),

    // ====================== MESO ======================
    IMPACT_CRATERS(Scale.MESO),
    CENTRAL_PEAK_CRATERS(Scale.MESO),
    MULTI_RING_BASINS(Scale.MESO),
    RIVER_VALLEYS(Scale.MESO),
    CANYONS(Scale.MESO),
    SAND_DUNES(Scale.MESO),
    GLACIAL_VALLEYS(Scale.MESO),

    // ====================== MICRO ======================
    YARDANGS(Scale.MICRO),
    LAVA_TUBES(Scale.MICRO),
    CRYOVOLCANOES(Scale.MICRO),
    ICE_FISSURES(Scale.MICRO),
    KARST_SINKHOLES(Scale.MICRO),
    SALT_FLATS(Scale.MICRO),
    LAYERED_SEDIMENTARY_ROCKS(Scale.MICRO);

    public enum Scale {
        MACRO,
        MESO,
        MICRO
    }

    public final Scale scale;

    TerrainPreset(Scale scale) {
        this.scale = scale;
    }
}
