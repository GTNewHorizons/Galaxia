package com.gtnewhorizons.galaxia.rocketmodules.validators;

import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;

public interface IRocketValidator {

    ValidationResult validate(RocketAssembly assembly);
}
