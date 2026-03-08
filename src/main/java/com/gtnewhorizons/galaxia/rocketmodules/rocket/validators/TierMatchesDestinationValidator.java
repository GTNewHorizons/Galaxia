package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;

public class TierMatchesDestinationValidator implements IRocketValidator {

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        int destinationId = assembly.getDestination();
        if (destinationId == 0)
            return new ValidationResult(false, "Destination not selected");
        int tier = assembly.getCoreModules().stream().mapToInt(m -> m.getTier()).sum();
        String destinationName = SolarSystemRegistry.getById(destinationId).name();
        int destinationTier = SolarSystemRegistry.getById(destinationId).tier();
        boolean ok = tier >= SolarSystemRegistry.getById(destinationId).tier();
        return ok ? ValidationResult.success()
                : new ValidationResult(false, String.format("Rocket of tier %d cannot reach %s (Tier %d)", tier,
                        destinationName, destinationTier));
    }
}
