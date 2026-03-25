package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.EngineModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.FuelTankModule;

public class EngineToTankRatioValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        long tanks = assembly.getModules()
            .stream()
            .filter(m -> m instanceof FuelTankModule)
            .count();
        long engines = assembly.getModules()
            .stream()
            .filter(m -> m instanceof EngineModule)
            .count();
        boolean ok = engines > 0 && tanks % engines == 0;
        return ok ? ValidationResult.success()
            : new ValidationResult(
                false,
                StatCollector.translateToLocal("galaxia.gui.rocket_silo.validator.engine_tank_ratio"));
    }
}
