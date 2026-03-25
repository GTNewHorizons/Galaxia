package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import java.util.List;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumTiers;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.RocketCoreModule;

public class TierMatchesDestinationValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        int destinationId = assembly.getDestination();
        if (destinationId == -1) return new ValidationResult(
            false,
            StatCollector.translateToLocal("galaxia.gui.rocket_silo.validator.destination_none"));
        List<RocketCoreModule> cores = assembly.getCoreModules();
        if (cores.isEmpty()) return ValidationResult.success();
        if (destinationId == 0) return ValidationResult.success();
        EnumTiers tier = cores.get(0)
            .getTier();
        String destinationName = SolarSystemRegistry.getById(destinationId)
            .name();
        EnumTiers destinationTier = SolarSystemRegistry.getById(destinationId)
            .tier();
        boolean ok = tier.isGreaterThanOrEqual(
            SolarSystemRegistry.getById(destinationId)
                .tier());
        return ok ? ValidationResult.success()
            : new ValidationResult(
                false,
                StatCollector.translateToLocalFormatted(
                    "galaxia.gui.rocket_silo.validator.wrong_tier",
                    tier.toInt(),
                    destinationName,
                    destinationTier.toInt()));
    }
}
