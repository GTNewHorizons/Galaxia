package com.gtnewhorizons.galaxia.rocketmodules.validators;

import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;

public class CapsuleRequiredValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        boolean hasCapsule = assembly.getModules()
            .stream()
            .anyMatch(m -> m.getPassengerCapacity() > 0);
        return hasCapsule ? ValidationResult.success()
            : new ValidationResult(false, "Requires at least one Capsule module");
    }
}
