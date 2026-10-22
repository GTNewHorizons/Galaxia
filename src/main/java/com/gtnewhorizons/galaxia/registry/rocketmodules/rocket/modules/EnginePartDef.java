package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;

/**
 * Engine burning a single fuel. Only tanks holding the same fuel in its stage can feed it
 */
public class EnginePartDef extends RocketPartDef {

    private final RocketFuel fuel;

    public EnginePartDef(RocketFuel fuel) {
        super(
            "engine_" + fuel.key(),
            fuel.engine()
                .width(),
            fuel.engine()
                .height(),
            fuel.engine()
                .weight(),
            fuel.engine()
                .assetFolder());
        this.fuel = fuel;
        withRecipe(
            fuel.engine()
                .recipe());
    }

    public RocketFuel fuel() {
        return fuel;
    }

    /** Thrust in kgf */
    public double thrust() {
        return fuel.engine()
            .thrust();
    }

    /** Specific impulse in seconds */
    public double isp() {
        return fuel.engine()
            .isp();
    }

    @Override
    public String name() {
        return StatCollector.translateToLocalFormatted(LANG_PREFIX + "engine", fuel.name());
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.LIQUID_ENGINES;
    }

    @Override
    public int coreColor() {
        return fuel.color();
    }
}
