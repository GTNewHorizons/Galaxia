package com.gtnewhorizons.galaxia.rocketmodules.validators;

import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;

public class EngineToTankRatioValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        long tanks = assembly.getModules()
            .stream()
            .filter(m -> m.getFuelCapacity() > 0)
            .count();
        long engines = assembly.getModules()
            .stream()
            .filter(m -> m.getThrust() > 0)
            .count();
        boolean ok = engines > 0 && tanks % engines == 0;
        return ok ? ValidationResult.success() : new ValidationResult(false, "Requires 1 engine per tank stack");
    }
}
