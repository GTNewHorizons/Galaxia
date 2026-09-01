package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

public sealed interface FacilityCommand permits FacilityCommand.BuildCommand,FacilityCommand.ModuleCommand,FacilityCommand.InventoryCommand,FacilityCommand.LogisticsCommand {

    CelestialAsset.ID facilityId();

    record AdjustInventory(CelestialAsset.ID facilityId, InventoryKey resource, InventoryAdjustment direction,
        long amount) implements InventoryCommand {}

    record ClearInventoryResource(CelestialAsset.ID facilityId, InventoryKey resource) implements InventoryCommand {}

    record SetInventoryBound(CelestialAsset.ID facilityId, BoundKind kind, InventoryKey resource, long amount)
        implements InventoryCommand {}

    record ClearInventoryBound(CelestialAsset.ID facilityId, BoundKind kind, InventoryKey resource)
        implements InventoryCommand {}

    record ReplaceFilters(CelestialAsset.ID facilityId, FilterKind kind, @Nullable List<String> filterKeys)
        implements LogisticsCommand {

        public ReplaceFilters {
            filterKeys = filterKeys == null ? null : Collections.unmodifiableList(new ArrayList<>(filterKeys));
        }
    }

    record PutLogisticsConfig(CelestialAsset.ID facilityId, InventoryKey resource, LogisticsResourceConfig config,
        LogisticsConfigAccessMode accessMode) implements LogisticsCommand {}

    record RemoveLogisticsConfig(CelestialAsset.ID facilityId, InventoryKey resource) implements LogisticsCommand {}

    record BuildModules(CelestialAsset.ID facilityId, FacilityModuleKind kind, ModuleShape shape,
        IModuleComponent.BuildPhysicalSpec physicalSpec, @Nullable SettingsGroup.ID settingsGroupId,
        boolean instantBuild, @Nullable List<ModulePlacement> placements) implements BuildCommand {

        public BuildModules {
            placements = placements == null ? null : List.copyOf(placements);
        }
    }

    record CopyBuildModules(CelestialAsset.ID facilityId, ModuleInstance.ID sourceModuleId, boolean instantBuild,
        @Nullable List<ModulePlacement> placements) implements BuildCommand {

        public CopyBuildModules {
            placements = placements == null ? null : List.copyOf(placements);
        }
    }

    record RequestModuleDeconstruction(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId)
        implements ModuleCommand {}

    record CancelModuleOperation(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId) implements ModuleCommand {}

    record ReplaceRecipeBook(CelestialAsset.ID facilityId, RecipeBookOwner owner, RecipeBook replacement)
        implements ModuleCommand {}

    record CreateSettingsGroup(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, String displayName)
        implements ModuleSettingsCommand {}

    record RenameSettingsGroup(CelestialAsset.ID facilityId, SettingsGroup.ID groupId, String displayName)
        implements ModuleSettingsCommand {}

    record SetSettingsGroup(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId,
        @Nullable SettingsGroup.ID groupId) implements ModuleSettingsCommand {}

    record CopyModuleSettings(CelestialAsset.ID facilityId, ModuleInstance.ID sourceModuleId,
        List<ModuleInstance.ID> targetModuleIds) implements ModuleSettingsCommand {

        public CopyModuleSettings {
            targetModuleIds = targetModuleIds == null ? null : List.copyOf(targetModuleIds);
        }

    }

    record ReplaceMinerSettings(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, MinerSettings replacement)
        implements ModuleSettingsCommand {}

    record ConfigureHammer(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, AllowShootingConfig config,
        OrbitalTransferPlanner.RoutePriority priority) implements ModuleConfiguration {}

    record SetMinerFocusOre(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, @Nullable String oreKey)
        implements ModuleConfiguration {}

    record ConfigureDebugDataGenerator(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId,
        ModuleDebugDataGenerator.Config config) implements ModuleConfiguration {}

    record PlanHammerUpgrade(CelestialAsset.ID facilityId, List<ModuleInstance.ID> targetModuleIds,
        HammerVariant targetVariant, ModuleTier targetTier, boolean reserveItems, boolean voidCompletionRefund)
        implements ModuleCommand {

        public PlanHammerUpgrade {
            targetModuleIds = targetModuleIds == null ? null : List.copyOf(targetModuleIds);
        }
    }

    record PlanTierUpgrade(CelestialAsset.ID facilityId, List<ModuleInstance.ID> targetModuleIds, ModuleTier targetTier,
        boolean reserveItems) implements ModuleCommand {

        public PlanTierUpgrade {
            targetModuleIds = targetModuleIds == null ? null : List.copyOf(targetModuleIds);
        }
    }

    record PlanMinerFocusUpgrade(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, ModuleTier targetModuleTier,
        MinerFocusTier targetFocusTier) implements ModuleCommand {}

    sealed interface BuildCommand extends FacilityCommand permits BuildModules,CopyBuildModules {
    }

    sealed interface ModuleCommand extends
        FacilityCommand permits RequestModuleDeconstruction,CancelModuleOperation,ReplaceRecipeBook,ModuleConfiguration,ModuleSettingsCommand,PlanHammerUpgrade,PlanTierUpgrade,PlanMinerFocusUpgrade {
    }

    sealed interface InventoryCommand
        extends FacilityCommand permits AdjustInventory,ClearInventoryResource,SetInventoryBound,ClearInventoryBound {
    }

    sealed interface LogisticsCommand
        extends FacilityCommand permits ReplaceFilters,PutLogisticsConfig,RemoveLogisticsConfig {
    }

    sealed interface ModuleConfiguration
        extends ModuleCommand permits ConfigureHammer,SetMinerFocusOre,ConfigureDebugDataGenerator {

        ModuleInstance.ID moduleId();
    }

    sealed interface ModuleSettingsCommand extends
        ModuleCommand permits CreateSettingsGroup,RenameSettingsGroup,SetSettingsGroup,CopyModuleSettings,ReplaceMinerSettings {
    }

    enum FilterKind {

        ITEM,
        FLUID;

        boolean isItem() {
            return this == ITEM;
        }
    }

    record Authority(boolean creativeMode, boolean debugAuthorized) {

        public static final Authority NONE = new Authority(false, false);
    }

    record Result(Status status, @Nullable Rejection rejection) {

        public static final Result CHANGED = new Result(Status.CHANGED, null);
        public static final Result UNCHANGED = new Result(Status.UNCHANGED, null);

        public static Result rejected(Rejection rejection) {
            return new Result(Status.REJECTED, rejection);
        }
    }

    enum Status {
        CHANGED,
        UNCHANGED,
        REJECTED
    }

    enum InventoryAdjustment {
        INSERT,
        EXTRACT
    }

    enum Rejection {
        FACILITY_NOT_FOUND,
        FACILITY_ID_MISMATCH,
        NOT_AUTHORIZED,
        MALFORMED_COMMAND,
        INVALID_RESOURCE,
        INVALID_INVENTORY_ADJUSTMENT,
        CREATIVE_MODE_REQUIRED,
        INVALID_RESOURCE_KIND,
        INVALID_BOUND,
        INVALID_FILTERS,
        INVALID_LOGISTICS_CONFIG,
        INVALID_MODULE_SPEC,
        DEBUG_AUTHORIZATION_REQUIRED,
        MODULE_KIND_NOT_ALLOWED,
        INVALID_MODULE_PLACEMENT,
        INVALID_SETTINGS_GROUP,
        MODULE_NOT_FOUND,
        INVALID_MODULE_TARGETS,
        INVALID_MODULE_COMPONENT,
        INVALID_MODULE_CONFIG,
        INVALID_RECIPE_BOOK_OWNER,
        INVALID_RECIPE_BOOK,
        INVALID_MODULE_UPGRADE,
        MODULE_OPERATION_ACTIVE,
        INSUFFICIENT_MODULE_MATERIALS,
        INVALID_DECONSTRUCTION_REFUND,
        CAPACITY_EXCEEDED,
        MODULE_OPERATION_NOT_CANCELLABLE
    }
}
