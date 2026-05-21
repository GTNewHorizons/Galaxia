package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;

public interface IModuleComponent {

    default void applyOperationTarget(IModuleOperation spec, ModuleInstance module) {
        throw new IllegalStateException(
            getClass().getSimpleName() + " does not support operation "
                + spec.getClass()
                    .getSimpleName());
    }

    default int cooldownTicks(ModuleInstance module, ModuleTierData data) {
        return data.cooldownTicks();
    }

    default ModuleSettings createPrivateSettings(ModuleInstance module) {
        throw unsupportedSettingsGroups(module);
    }

    default ModuleSettings copySettings(ModuleInstance module, ModuleSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " cannot copy null settings");
        }
        return settings.copy();
    }

    default void applySettings(ModuleInstance module, ModuleSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " cannot apply null settings");
        }
        settings.applyTo(module);
    }

    default void validateSettingsCopyTarget(ModuleInstance source, ModuleInstance target) {}

    default void afterSettingsCopied(ModuleInstance source, ModuleInstance target) {}

    default FeatureContribution featureContribution(ModuleInstance module, PlanetaryFeatureKey feature,
        int coveredTiles, int totalTiles) {
        return null;
    }

    default void tickOperational(ModuleInstance module, CelestialAsset outpost) {}

    default IllegalStateException unsupportedSettingsGroups(ModuleInstance module) {
        return new IllegalStateException(
            getClass().getSimpleName() + " does not support settings groups for module kind " + module.kind());
    }
}
