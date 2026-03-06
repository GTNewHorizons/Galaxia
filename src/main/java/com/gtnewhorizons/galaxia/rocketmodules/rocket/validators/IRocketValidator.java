package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;

public interface IRocketValidator {

    ValidationResult validate(RocketAssembly assembly);
}
