package com.gtnewhorizons.galaxia.orbitalGUI;

import static com.gtnewhorizons.galaxia.dimension.planets.BasePlanet.earthRadiusToAU;

import java.util.Optional;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.CelestialType;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

public final class GalaxiaRegistry {

    // spotless:off
    public static final OrbitalCelestialBody ROOT =
        OrbitalCelestialBody.builder()
        .dimension(DimensionEnum.FROZEN_BELT)
        .apogeePerigee(2 * earthRadiusToAU, 2.6 * earthRadiusToAU)
        .type(CelestialType.BLACK_HOLE)
        .textureAndSize(EnumTextures.EGORA.get(), 400)
        .addChild(star -> star
                .dimension(DimensionEnum.PANSPIRA)
                .apogeePerigee(.9 * earthRadiusToAU, .7 * earthRadiusToAU)
                .type(CelestialType.STAR)
                .textureAndSize(EnumTextures.EGORA.get(), 300)
                .addChild(planet -> planet
                    .dimension(DimensionEnum.HEMATERIA)
                    .type(CelestialType.PLANET)
                    .apogeePerigee(.3 * earthRadiusToAU, .02 * earthRadiusToAU)
                    .textureAndSize(EnumTextures.EGORA.get(), 200)
                    .addChild(moon -> moon
                        .dimension(DimensionEnum.THEIA)
                        .type(CelestialType.MOON)
                        .textureAndSize(EnumTextures.EGORA.get(), 100)
                        .apogeePerigee(.01 * earthRadiusToAU, .005 * earthRadiusToAU)
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
