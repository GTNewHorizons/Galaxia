package com.gtnewhorizons.galaxia.orbitalGUI;

import java.util.Optional;

import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.CelestialType;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;

public final class GalaxiaRegistry {

    // spotless:off
    public static final OrbitalCelestialBody ROOT =
        OrbitalCelestialBody.builder()
        .dimension(DimensionEnum.FROZEN_BELT)
        .type(CelestialType.BLACK_HOLE)
        .addChild(star -> star
                .dimension(DimensionEnum.SOL)
                .type(CelestialType.STAR)
                .addChild(planet -> planet
                    .dimension(DimensionEnum.HEMATERIA)
                    .type(CelestialType.PLANET)
                    .orbital(new OrbitalParams(1.0, 0.0167, 0, 0, 0, 0))
                    .addChild(moon -> moon
                        .dimension(DimensionEnum.THEIA)
                        .type(CelestialType.MOON)
                        .apogeePerigee(405_000, 363_000)
                    )
                )
        )
        .build();
    // spotless:on

    public static Optional<OrbitalCelestialBody> findByDimension(DimensionEnum dim) {
        return findByDimension(ROOT, dim);
    }

    private static Optional<OrbitalCelestialBody> findByDimension(OrbitalCelestialBody node, DimensionEnum target) {
        if (node.dimensionEnum() == target) return Optional.of(node);
        return node.children()
            .stream()
            .map(child -> findByDimension(child, target))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }
}
