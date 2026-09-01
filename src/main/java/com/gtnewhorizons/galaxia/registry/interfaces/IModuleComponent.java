package com.gtnewhorizons.galaxia.registry.interfaces;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepDemand;

public interface IModuleComponent {

    sealed interface BuildPhysicalSpec {

        ModuleTier tier();

        record Tier(ModuleTier tier) implements BuildPhysicalSpec {}

        record Hammer(ModuleTier tier, HammerVariant variant) implements BuildPhysicalSpec {}

        record Miner(ModuleTier tier, MinerFocusTier focusTier) implements BuildPhysicalSpec {}
    }

    default void applyBuildPhysicalSpec(ModuleInstance module, BuildPhysicalSpec spec) {
        if (!(spec instanceof BuildPhysicalSpec.Tier) || spec.tier() == null || spec.tier() != module.tier()) {
            throw new IllegalArgumentException(
                getClass().getSimpleName() + " cannot apply build physical spec " + spec);
        }
    }

    default BuildPhysicalSpec buildPhysicalSpec(ModuleInstance module) {
        return new BuildPhysicalSpec.Tier(module.tier());
    }

    default boolean applyConfigurationTransition(ModuleInstance module,
        FacilityCommand.ModuleConfiguration configuration) {
        throw unsupportedCommand(configuration);
    }

    default IModuleOperation prepareOperationTarget(ModuleInstance module,
        FacilityCommand.ModuleOperationRequest request) {
        throw unsupportedCommand(request);
    }

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

    default UpkeepDemand upkeepFor(ModuleInstance module) {
        return UpkeepDemand.EMPTY;
    }

    default IllegalStateException unsupportedSettingsGroups(ModuleInstance module) {
        return new IllegalStateException(
            getClass().getSimpleName() + " does not support settings groups for module kind " + module.kind());
    }

    default UnsupportedOperationException unsupportedCommand(FacilityCommand command) {
        return new UnsupportedOperationException(
            getClass().getSimpleName() + " does not support command "
                + command.getClass()
                    .getSimpleName());
    }
}
