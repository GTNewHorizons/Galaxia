package com.gtnewhorizons.galaxia.rocketmodules.rocket.validators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumModuleCategory;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.EnumTiers;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.RocketCoreModule;

public class ModulesFitInCoreValidator implements IRocketValidator {

    // Map of form:
    // <int Tier: {int EngineSlots, int TankSlots, int FunctionalSlots, int
    // StructuralSlots}
    HashMap<Integer, ArrayList<Integer>> moduleSlotMap = new HashMap<>() {

        {
            put(1, new ArrayList<>(Arrays.asList(1, 1, 1, 0)));
            put(2, new ArrayList<>(Arrays.asList(3, 3, 2, 1)));
        }
    };

    HashMap<EnumModuleCategory, String> moduleConversions = new HashMap<>() {

        {
            put(EnumModuleCategory.FUEL_TANK, "Fuel Tanks");
            put(EnumModuleCategory.ENGINE, "Engines");
            put(EnumModuleCategory.FUNCTIONAL, "Functional Modules");
            put(EnumModuleCategory.STRUCTURAL, "Structural Modules");
            put(EnumModuleCategory.CORE, "Rocket Core");
            put(EnumModuleCategory.PAYLOAD, "Payload");
            put(EnumModuleCategory.LANDER, "Lander");
        }
    };

    HashMap<EnumTiers, String> tierConversions = new HashMap<>() {

        {
            put(EnumTiers.TIER_1, "Tier 1");
            put(EnumTiers.TIER_2, "Tier 2");
        }
    };

    @Override
    public ValidationResult validate(RocketAssembly assembly) {
        List<RocketCoreModule> cores = assembly.getCoreModules();
        if (cores.size() == 0) return ValidationResult.success();
        RocketCoreModule core = cores.get(0);

        Map<EnumModuleCategory, Integer> counts = assembly.getModules()
            .stream()
            .collect(Collectors.groupingBy(RocketModule::getCategory, Collectors.summingInt(m -> 1)));

        for (Map.Entry<EnumModuleCategory, Integer> entry : counts.entrySet()) {
            int limit = core.getTier()
                .getLimitFor(entry.getKey());
            if (entry.getValue() > limit) return new ValidationResult(
                false,
                StatCollector.translateToLocalFormatted(
                    "galaxia.gui.rocket_silo.validator.too_many_modules",
                    limit,
                    moduleConversions.get(entry.getKey()),
                    tierConversions.get(core.getTier())));
        }
        return ValidationResult.success();
    }
}
