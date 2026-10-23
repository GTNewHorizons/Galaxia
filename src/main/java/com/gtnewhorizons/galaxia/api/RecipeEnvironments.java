package com.gtnewhorizons.galaxia.api;

import javax.annotation.Nullable;

import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipe.RecipeEnvironment;

/** Galaxia's physical conditions, registered with GT's native recipe environment system. */
public final class RecipeEnvironments {

    private static final double FULL_ROOM_PRESSURE_PA = 101325;

    public static final RecipeEnvironment MICROGRAVITY = new RecipeEnvironment(
        "galaxia:microgravity",
        "galaxia.environment.microgravity") {

        @Override
        public boolean isSatisfied(MetaTileEntity machine) {
            Conditions conditions = at(machine);
            return conditions != null && conditions.isMicrogravity();
        }
    };

    public static final RecipeEnvironment NO_ATMOSPHERE = new RecipeEnvironment(
        "galaxia:no_atmosphere",
        "galaxia.environment.no_atmosphere") {

        @Override
        public boolean isSatisfied(MetaTileEntity machine) {
            Conditions conditions = at(machine);
            return conditions != null && conditions.isVacuum();
        }
    };

    private RecipeEnvironments() {}

    /** Registers physical conditions during mod construction, before material and recipe registration. */
    public static void register() {
        RecipeEnvironment.register(MICROGRAVITY);
        RecipeEnvironment.register(NO_ATMOSPHERE);
    }

    @Nullable
    private static Conditions at(MetaTileEntity machine) {
        var tile = machine.getBaseMetaTileEntity();
        return tile == null ? null : at(tile.getWorld(), tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }

    /** Reads authored celestial facts and cached room membership. Unknown dimensions stay unknown. */
    @Nullable
    public static Conditions at(@Nullable World world, int x, int y, int z) {
        if (world == null || world.provider == null) return null;
        int dimension = world.provider.dimensionId;
        CelestialBodyProperties body = GalaxiaCelestialAPI.findByDimension(dimension)
            .map(object -> object.properties())
            .orElse(null);
        if (body == null) return null;
        boolean orbit = OrbitDimensions.isOrbit(dimension);
        double gravity = orbit ? 0 : body.localGravityG();
        double pressure = orbit ? 0 : body.surfacePressurePa();
        TileStation room = GalaxiaAPI.getStationContaining(world, dimension, x, y, z);
        if (room != null) pressure = room.getPressureLevel() * FULL_ROOM_PRESSURE_PA;
        return new Conditions(gravity, pressure);
    }

    /** Physical conditions at the machine controller, with pressure distinct from breathable oxygen. */
    public record Conditions(double gravityG, double pressurePa) {

        public Conditions {
            if (!Double.isFinite(gravityG) || gravityG < 0 || !Double.isFinite(pressurePa) || pressurePa < 0) {
                throw new IllegalArgumentException("Environment facts must be finite and non-negative");
            }
        }

        public boolean isMicrogravity() {
            return gravityG < 0.01;
        }

        public boolean isVacuum() {
            return pressurePa == 0;
        }
    }
}
