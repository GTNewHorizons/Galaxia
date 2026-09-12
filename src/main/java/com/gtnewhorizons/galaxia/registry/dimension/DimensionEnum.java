package com.gtnewhorizons.galaxia.registry.dimension;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShape;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeCracks;
import com.gtnewhorizons.galaxia.registry.dimension.cave.CaveShapeTubes;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainConfiguration;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.TerrainPreset;
import com.gtnewhorizons.galaxia.registry.dimension.worldgen.mantle.MantleRules;

import lombok.Getter;

/**
 * ENUM for storing all dimensions
 */
@Getter
public enum DimensionEnum {

    // Format: ENUMNAME(int ID, String name)
    // This is just the overworld
    OVERWORLD(0, "Overworld", "galaxia.dimension.overworld"),
    OVERWORLD_ORBIT(-19, "Overworld_Orbit_Stations", "galaxia.dimension.overworld_orbit"),
    MOON(20, "Moon", "galaxia.dimension.moon", new MantleRules(
        TerrainConfiguration.builder()
            .feature(TerrainPreset.BASE_HEIGHT)
            .height(16)
            .endFeature()
            .feature(TerrainPreset.MOUNTAIN_RANGES)
            .width(8)
            .height(48)
            .feature(TerrainPreset.MOUNTAIN_RANGES)
            .width(1)
            .height(48)
            .endFeature()
            .build(),
        TerrainConfiguration.builder()
            .feature(TerrainPreset.BASE_HEIGHT)
            .height(16)
            .endFeature()
            .feature(TerrainPreset.SHIELD_VOLCANOES)
            .width(8)
            .height(48)
            .endFeature()
            .feature(TerrainPreset.CANYONS)
            .height(64)
            .width(12)
            .endFeature()
            .build()).setFillerBlock(new BlockMeta(PlanetBlocks.MOON_BRECCIA)),
        new MantleRules(
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(16)
                .endFeature()
                .feature(TerrainPreset.SHIELD_VOLCANOES)
                .width(8)
                .height(48)
                .feature(TerrainPreset.CANYONS)
                .height(64)
                .width(12)
                .endFeature()
                .build(),
            TerrainConfiguration.builder()
                .feature(TerrainPreset.BASE_HEIGHT)
                .height(8)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(64)
                .width(0.25)
                .endFeature()
                .feature(TerrainPreset.MOUNTAIN_RANGES)
                .height(48)
                .width(2)
                .endFeature()
                .build()).setFillerBlock(new BlockMeta(PlanetBlocks.MOON_OBSIDIAN)),
        new CaveShapeTubes((byte) 48, (byte) 16, (short) 192, 2.5F), new CaveShapeCracks(128)),
    MARS(21, "Mars", "galaxia.dimension.mars"),
    FROZEN_BELT(22, "Frozen_Belt", "galaxia.dimension.frozen_belt"),
    TENEBRAE(23, "Tenebrae", "galaxia.dimension.tenebrae");

    final int id;
    final String name;
    final String translationKey;
    final MantleRules upperMantleRules;
    final MantleRules lowerMantleRules;
    final CaveShape upperIntermediaryCaves;
    final CaveShape lowerIntermediaryCaves;

    DimensionEnum(int id, String name, String translationKey, MantleRules upperMantleRules,
        MantleRules lowerMantleRules, CaveShape upperIntermediaryCaves, CaveShape lowerIntermediaryCaves) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
        this.upperMantleRules = upperMantleRules;
        this.lowerMantleRules = lowerMantleRules;
        this.upperIntermediaryCaves = upperIntermediaryCaves;
        this.lowerIntermediaryCaves = lowerIntermediaryCaves;
    }

    DimensionEnum(int id, String name, String translationKey) {
        this(id, name, translationKey, null, null, null, null);
    }

    public static DimensionEnum fromId(int id) {
        for (DimensionEnum e : values()) {
            if (e.id == id) return e;
        }

        return null;
    }
}
