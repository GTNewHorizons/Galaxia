package com.gtnewhorizons.galaxia.registry.dimension.planets;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.builder.DimensionBuilder;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.EnumTiers;

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
            .tier(EnumTiers.TIER_1);
    }
}
