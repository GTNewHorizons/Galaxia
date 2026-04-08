package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.validators;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly;

public interface IRocketValidator {

    ValidationResult validate(RocketAssembly assembly);

}
