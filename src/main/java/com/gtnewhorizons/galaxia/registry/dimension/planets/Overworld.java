package com.gtnewhorizons.galaxia.registry.dimension.planets;

import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.EnumTiers;

public class Overworld extends BasePlanet {

    @Override
    public DimensionEnum getPlanetEnum() {
        return DimensionEnum.OVERWORLD;
    }

    @Override
    protected DimensionBuilder customizeDimension(DimensionBuilder builder) {
        return builder.orbitalRadius(1 * earthRadiusToAU)
            .effects(
                EffectBuilder.builder()
                    .build())
            .addValidSpaceStationBlocks(
                GalaxiaBlocksEnum.RUSTY_SCAFFOLDING.get(),
                GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
                GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
                GalaxiaBlocksEnum.SPACE_STATION_GLASS.get())
            .tier(EnumTiers.TIER_1);
    }
}
