package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;

/**
 * A tank frame with a core for one fuel. Each frame/fuel pair is a separate module so cores can cost different
 * amounts, while the frame and its model are shared
 */
public class FuelTankPartDef extends RocketPartDef {

    private final FuelTankFrame frame;
    private final RocketFuel fuel;

    public FuelTankPartDef(FuelTankFrame frame, RocketFuel fuel) {
        super(keyOf(frame, fuel), frame.width, frame.height, frame.weight, frame.assetFolder);
        this.frame = frame;
        this.fuel = fuel;
        withRecipe(
            frame.recipe.plus(
                fuel.coreRecipe()
                    .times(frame.cells())));
    }

    public static String keyOf(FuelTankFrame frame, RocketFuel fuel) {
        return "fuel_tank_" + frame.label + "_" + fuel.key();
    }

    public FuelTankFrame frame() {
        return frame;
    }

    public RocketFuel fuel() {
        return fuel;
    }

    /** Fuel capacity in mB */
    public int fuelCapacity() {
        return frame.capacity();
    }

    @Override
    public String name() {
        return StatCollector.translateToLocalFormatted(LANG_PREFIX + "fuel_tank", frame.label, fuel.name());
    }

    @Override
    public RocketPartCategory category() {
        return RocketPartCategory.FUEL_TANKS;
    }

    @Override
    public int coreColor() {
        return fuel.color();
    }
}
