package com.gtnewhorizons.galaxia.registry.dimension.worldgen;

import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

/**
 * ENUM to hold all terrain presets
 */
public enum TerrainPreset {

    // ====================== MACRO ======================
    MOUNTAIN_RANGES(Scale.MACRO),
    SHIELD_VOLCANOES(Scale.MACRO, new DimensionEnum[] { DimensionEnum.THEIA },
        new Block[] { PlanetBlocks.THEIA_MAGMA }),
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
    private final HashMap<DimensionEnum, Block> replacementMap = new HashMap<>();

    public Block getReplacementBlock(DimensionEnum dimension) {
        Block replacementBlock = replacementMap.get(dimension);
        if (replacementBlock == null) {
            return Blocks.stone;
        }
        return replacementBlock;
    }

    TerrainPreset(Scale scale) {
        this.scale = scale;
    }

    TerrainPreset(Scale scale, DimensionEnum[] replacementDimensions, Block[] replacementBlocks) {
        this(scale);
        for (int i = 0; i < replacementDimensions.length; i++) {
            replacementMap.put(replacementDimensions[i], replacementBlocks[i]);
        }
    }
}
