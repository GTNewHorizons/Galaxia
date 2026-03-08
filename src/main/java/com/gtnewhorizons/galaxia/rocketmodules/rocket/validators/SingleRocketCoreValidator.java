package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;

public class SingleRocketCoreValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        boolean ok = assembly.getCoreModules()
            .size() == 1;
        return ok ? ValidationResult.success() : new ValidationResult(false, "Only one rocket core per rocket");
    }
}
