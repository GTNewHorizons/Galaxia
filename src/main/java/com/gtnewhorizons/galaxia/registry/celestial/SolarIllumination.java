package com.gtnewhorizons.galaxia.registry.celestial;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

/** Solar availability normalized to the Overworld surface in daylight. */
public final class SolarIllumination {

    public static final double EARTH_PRESSURE_PA = 101325.0;
    public static final ForgeDirection ORBITAL_SUN_DIRECTION = ForgeDirection.EAST;
    public static final float ORBITAL_SKY_ANGLE = 0.75f;
    private static final double EARTH_TRANSMISSION = 0.5;

    private SolarIllumination() {}

    public static double intensity(double distanceRelativeToEarth, double luminosityRelativeToSun, double pressurePa,
        double illumination, double weatherTransmission, double resourceModifier) {
        for (double value : new double[] { distanceRelativeToEarth, luminosityRelativeToSun, pressurePa, illumination,
            weatherTransmission, resourceModifier }) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid solar input");
        }
        if (distanceRelativeToEarth == 0) return 0;
        double transmission = Math.exp(Math.log(EARTH_TRANSMISSION) * pressurePa / EARTH_PRESSURE_PA);
        return luminosityRelativeToSun * transmission
            * illumination
            * weatherTransmission
            * resourceModifier
            / (EARTH_TRANSMISSION * distanceRelativeToEarth * distanceRelativeToEarth);
    }

    public static ForgeDirection collectionDirection(World world) {
        return OrbitDimensions.isOrbit(world.provider.dimensionId) ? ORBITAL_SUN_DIRECTION : ForgeDirection.UP;
    }

    /** Uses actual authored orbital geometry, including a moon's parent, without a dimension multiplier table. */
    public static double currentIntensity(World world) {
        var orbit = OrbitDimensions.byDimension(world.provider.dimensionId)
            .orElse(null);
        CelestialObject body = orbit == null
            ? CelestialRegistry.findByDimension(DimensionEnum.fromId(world.provider.dimensionId))
                .orElse(null)
            : orbit.body();
        if (body == null) return 0;
        CelestialObject star = body;
        while (star != null && star.objectClass() != CelestialObject.Class.STAR) {
            star = star.parentKey() == null ? null
                : CelestialRegistry.get(star.parentKey())
                    .orElse(null);
        }
        if (star == null) return 0;
        CelestialObject earth = CelestialRegistry.get(CelestialObjectId.OVERWORLD)
            .orElseThrow();
        CelestialObject sun = CelestialRegistry.get(CelestialObjectId.SOL)
            .orElseThrow();
        double time = world.getWorldTime();
        double reference = sourceDistance(sun, earth, time);
        double distance = sourceDistance(star, body, time);
        if (!(reference > 0) || !(distance > 0)) return 0;
        double lit = orbit != null || world.isDaytime() ? 1 : 0;
        double weather = orbit != null ? 1 : (1 - world.getRainStrength(1)) * (1 - world.getWeightedThunderStrength(1));
        double referenceYield = intensity(
            1,
            sun.properties()
                .solarResourceModifier(),
            earth.properties()
                .surfacePressurePa(),
            1,
            1,
            earth.properties()
                .solarResourceModifier());
        if (!(referenceYield > 0)) return 0;
        return intensity(
            distance / reference,
            star.properties()
                .solarResourceModifier(),
            orbit == null ? body.properties()
                .surfacePressurePa() : 0,
            lit,
            weather,
            body.properties()
                .solarResourceModifier())
            / referenceYield;
    }

    private static double sourceDistance(CelestialObject source, CelestialObject body, double time) {
        var position = OrbitalMechanics.resolveWorldState(source, body, time);
        return position == null ? 0 : Math.hypot(position.x(), position.y());
    }
}
