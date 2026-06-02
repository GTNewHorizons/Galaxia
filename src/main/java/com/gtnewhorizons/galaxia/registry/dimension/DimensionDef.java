package com.gtnewhorizons.galaxia.registry.dimension;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldProvider;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.sky.CelestialBody;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;

/**
 * Record to hold characteristics of the dimension (effectively a posh
 * dataclass)
 */

public record DimensionDef(String name, int id, Class<? extends WorldProvider> provider, boolean keepLoaded,
    double gravity, double airResistance, boolean removeSpeedCancelation, List<CelestialBody> celestialBodies,
    EffectBuilder effects, double mass, double orbitalRadius, double radius, EnumTiers tier,
    ResourceLocation[] skyboxTexture, List<Block> validSpaceStationBlocks) {

    public DimensionDef {
        celestialBodies = celestialBodies == null ? null : Collections.unmodifiableList(celestialBodies);
        validSpaceStationBlocks = validSpaceStationBlocks == null ? null
            : Collections.unmodifiableList(validSpaceStationBlocks);
    }
}
