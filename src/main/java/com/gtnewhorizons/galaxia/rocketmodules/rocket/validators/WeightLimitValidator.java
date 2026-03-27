package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.EngineModule;

public class WeightLimitValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        double weight = assembly.getTotalWeight();
        double thrust = assembly.getModules()
            .stream()
            .filter(EngineModule.class::isInstance)
            .map(EngineModule.class::cast)
            .mapToDouble(EngineModule::getThrust)
            .sum();
        boolean ok = thrust > 0 && weight <= thrust;
        return ok ? ValidationResult.success()
            : new ValidationResult(
                false,
                StatCollector.translateToLocal("galaxia.gui.rocket_silo.validator.too_heavy"));
    }
}
