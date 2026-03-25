package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.LanderModule;

public class LanderRequiredValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        boolean hasCapsule = assembly.getModules()
            .stream()
            .anyMatch(m -> m instanceof LanderModule);
        return hasCapsule ? ValidationResult.success()
            : new ValidationResult(
                false,
                StatCollector.translateToLocal("galaxia.gui.rocket_silo.validator.lander_none"));
    }
}
