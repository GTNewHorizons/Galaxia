package com.gtnewhorizons.galaxia.rocketmodules.validators;

import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class WeightLimitValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        double weight = assembly.getTotalWeight();
        double thrust = assembly.getModules()
            .stream()
            .mapToDouble(RocketModule::getThrust)
            .sum();
        boolean ok = thrust > 0 && weight <= thrust;
        return ok ? ValidationResult.success() : new ValidationResult(false, "This rocket is too heavy to lift off");
    }
}
