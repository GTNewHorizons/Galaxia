package com.gtnewhorizons.galaxia.worldgen;

/**
 * ENUM to hold all terrain presets
 */
public enum TerrainPreset {

    // ====================== MACRO ======================
    MOUNTAIN_RANGES(Scale.MACRO, 0.42, 1.25, 1.25, 75, 195),
    SHIELD_VOLCANOES(Scale.MACRO, 0.18, 2.6, 2.6, 45, 310),
    LAVA_PLATEAUS(Scale.MACRO, 0.65, 1.05, 1.05, 18, 55),
    PLATEAUS_AND_ESCARPMENTS(Scale.MACRO, 0.52, 1.55, 1.55, 28, 115),
    TECTONIC_RIFTS(Scale.MACRO, 0.28, 1.85, 1.85, -45, 85),

    // ====================== MESO ======================
    IMPACT_CRATERS(Scale.MESO, 0.28, 1.05, 1.05, 48, 195),
    CENTRAL_PEAK_CRATERS(Scale.MESO, 0.22, 0.85, 0.85, 75, 145),
    MULTI_RING_BASINS(Scale.MESO, 0.12, 2.1, 2.1, 115, 295),
    RIVER_VALLEYS(Scale.MESO, 0.75, 0.65, 0.65, -32, 38),
    CANYONS(Scale.MESO, 0.45, 1.35, 1.35, -55, 75),
    SAND_DUNES(Scale.MESO, 0.85, 0.72, 0.72, 4, 22),
    GLACIAL_VALLEYS(Scale.MESO, 0.38, 1.15, 1.15, -42, 58),

    // ====================== MICRO ======================
    YARDANGS(Scale.MICRO, 0.92, 0.55, 0.55),
    LAVA_TUBES(Scale.MICRO, 0.62, 1.05, 1.05),
    CRYOVOLCANOES(Scale.MICRO, 0.25, 1.55, 1.55),
    ICE_FISSURES(Scale.MICRO, 0.72, 0.82, 0.82),
    KARST_SINKHOLES(Scale.MICRO, 0.55, 1.05, 1.05),
    SALT_FLATS(Scale.MICRO, 0.85, 0.65, 0.65),
    LAYERED_SEDIMENTARY_ROCKS(Scale.MICRO, 1.0, 1, 1);

    public enum Scale {
        MACRO,
        MESO,
        MICRO
    }

    public final Scale scale;
    public final double defaultFrequency;
    public final double defaultHeight;
    public final double defaultWidth;
    public final int defaultMinHeight;
    public final int defaultVariation;

    TerrainPreset(Scale scale, double freq, double height, double width, int minH, int var) {
        this.scale = scale;
        this.defaultFrequency = freq;
        this.defaultHeight = height;
        this.defaultWidth = width;
        this.defaultMinHeight = minH;
        this.defaultVariation = var;
    }

    TerrainPreset(Scale scale, double freq, double height, double width) {
        this(scale, freq, height, width, 0, 0);
    }
}
