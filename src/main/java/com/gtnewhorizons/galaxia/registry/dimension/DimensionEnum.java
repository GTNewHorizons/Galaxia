package com.gtnewhorizons.galaxia.registry.dimension;

import com.gtnewhorizon.gtnhlib.util.data.BlockMeta;
import com.gtnewhorizons.galaxia.registry.block.PlanetBlocks;
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
    MOON(20, "Moon", "galaxia.dimension.moon",
        new MantleRules().setFillerBlock(new BlockMeta(PlanetBlocks.MOON_BRECCIA)),
        new MantleRules().setFillerBlock(new BlockMeta(PlanetBlocks.MOON_MAGMA))),
    MARS(21, "Mars", "galaxia.dimension.mars"),
    FROZEN_BELT(22, "Frozen_Belt", "galaxia.dimension.frozen_belt"),
    TENEBRAE(23, "Tenebrae", "galaxia.dimension.tenebrae");

    final int id;
    final String name;
    final String translationKey;
    final MantleRules upperMantleRules;
    final MantleRules lowerMantleRules;

    DimensionEnum(int id, String name, String translationKey, MantleRules upperMantleRules, MantleRules lowerMantleRules) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
        this.upperMantleRules = upperMantleRules;
        this.lowerMantleRules = lowerMantleRules;
    }

    DimensionEnum(int id, String name, String translationKey) {
        this(id, name, translationKey, null, null);
    }

    public static DimensionEnum fromId(int id) {
        for (DimensionEnum e : values()) {
            if (e.id == id) return e;
        }

        return null;
    }
}
