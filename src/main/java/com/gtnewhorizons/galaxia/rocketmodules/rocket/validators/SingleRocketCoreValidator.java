package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;

public class SingleRocketCoreValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        boolean ok = assembly.getCoreModules()
            .size() == 1;
        return ok ? ValidationResult.success()
            : new ValidationResult(
                false,
                StatCollector.translateToLocal("galaxia.gui.rocket_silo.validator.multi_core"));
    }
}
