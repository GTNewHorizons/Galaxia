package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.CapsuleModule;

public class CapsuleRequiredValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        boolean hasCapsule = assembly.getModules()
            .stream()
            .anyMatch(m -> m instanceof CapsuleModule);
        return hasCapsule ? ValidationResult.success()
            : new ValidationResult(false, "Requires at least one Capsule module");
    }
}
