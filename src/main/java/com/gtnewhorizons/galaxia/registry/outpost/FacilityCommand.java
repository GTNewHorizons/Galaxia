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
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

public sealed interface FacilityCommand permits FacilityCommand.AdjustInventory,FacilityCommand.ClearInventoryResource,FacilityCommand.SetInventoryBound,FacilityCommand.ClearInventoryBound,FacilityCommand.ReplaceFilters,FacilityCommand.PutLogisticsConfig,FacilityCommand.RemoveLogisticsConfig,FacilityCommand.BuildModules,FacilityCommand.CopyBuildModules,FacilityCommand.RequestModuleDeconstruction,FacilityCommand.CancelModuleOperation,FacilityCommand.ReplaceRecipeBook,FacilityCommand.ModuleConfiguration,FacilityCommand.ModuleOperationRequest,FacilityCommand.ModuleSettingsCommand {

    CelestialAsset.ID facilityId();

    record AdjustInventory(CelestialAsset.ID facilityId, InventoryKey resource, InventoryAdjustment direction,
        long amount) implements FacilityCommand {}

    record ClearInventoryResource(CelestialAsset.ID facilityId, InventoryKey resource) implements FacilityCommand {}

    record SetInventoryBound(CelestialAsset.ID facilityId, BoundKind kind, InventoryKey resource, long amount)
        implements FacilityCommand {}

    record ClearInventoryBound(CelestialAsset.ID facilityId, BoundKind kind, InventoryKey resource)
        implements FacilityCommand {}

    record ReplaceFilters(CelestialAsset.ID facilityId, FilterKind kind, @Nullable List<String> filterKeys)
        implements FacilityCommand {

        public ReplaceFilters {
            filterKeys = filterKeys == null ? null : Collections.unmodifiableList(new ArrayList<>(filterKeys));
        }
    }

    record PutLogisticsConfig(CelestialAsset.ID facilityId, InventoryKey resource, LogisticsResourceConfig config,
        LogisticsConfigAccessMode accessMode) implements FacilityCommand {}

    record RemoveLogisticsConfig(CelestialAsset.ID facilityId, InventoryKey resource) implements FacilityCommand {}

    record BuildModules(CelestialAsset.ID facilityId, FacilityModuleKind kind, ModuleShape shape,
        IModuleComponent.BuildPhysicalSpec physicalSpec, @Nullable SettingsGroup.ID settingsGroupId,
        boolean instantBuild, @Nullable List<ModulePlacement> placements) implements FacilityCommand {

        public BuildModules {
            placements = placements == null ? null : List.copyOf(placements);
        }
    }

    record CopyBuildModules(CelestialAsset.ID facilityId, ModuleInstance.ID sourceModuleId, boolean instantBuild,
        @Nullable List<ModulePlacement> placements) implements FacilityCommand {

        public CopyBuildModules {
            placements = placements == null ? null : List.copyOf(placements);
        }
    }

    record RequestModuleDeconstruction(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId)
        implements FacilityCommand {}

    record CancelModuleOperation(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId) implements FacilityCommand {}

    record ReplaceRecipeBook(CelestialAsset.ID facilityId, RecipeBookOwner owner, RecipeBook replacement)
        implements FacilityCommand {}

    record CreateSettingsGroup(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, String displayName)
        implements ModuleSettingsCommand {}

    record RenameSettingsGroup(CelestialAsset.ID facilityId, SettingsGroup.ID groupId, String displayName)
        implements ModuleSettingsCommand {}

    record JoinSettingsGroup(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, SettingsGroup.ID groupId)
        implements ModuleSettingsCommand {}

    record LeaveSettingsGroup(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId)
        implements ModuleSettingsCommand {}

    record CopyModuleSettings(CelestialAsset.ID facilityId, ModuleInstance.ID sourceModuleId,
        List<ModuleInstance.ID> targetModuleIds) implements ModuleSettingsCommand {

        public CopyModuleSettings {
            targetModuleIds = targetModuleIds == null ? null : List.copyOf(targetModuleIds);
        }

    }

    record SetMinerOreBlacklisted(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, String oreKey,
        boolean blacklisted) implements ModuleSettingsCommand {}

    record SetHammerShootingConfig(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, AllowShootingConfig config)
        implements ModuleConfiguration {}

    record SetHammerRoutePriority(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId,
        OrbitalTransferPlanner.RoutePriority priority) implements ModuleConfiguration {}

    record SetMinerFocusOre(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, @Nullable String oreKey)
        implements ModuleConfiguration {}

    record ConfigureDebugDataGenerator(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId,
        ModuleDebugDataGenerator.Config config) implements ModuleConfiguration {}

    record PlanHammerUpgrade(CelestialAsset.ID facilityId, List<ModuleInstance.ID> targetModuleIds,
        HammerVariant targetVariant, ModuleTier targetTier, boolean reserveItems, boolean voidCompletionRefund)
        implements ModuleOperationRequest {

        public PlanHammerUpgrade {
            targetModuleIds = targetModuleIds == null ? null : List.copyOf(targetModuleIds);
        }
    }

    record PlanTierUpgrade(CelestialAsset.ID facilityId, List<ModuleInstance.ID> targetModuleIds, ModuleTier targetTier,
        boolean reserveItems) implements ModuleOperationRequest {

        public PlanTierUpgrade {
            targetModuleIds = targetModuleIds == null ? null : List.copyOf(targetModuleIds);
        }
    }

    record PlanMinerFocusUpgrade(CelestialAsset.ID facilityId, ModuleInstance.ID moduleId, ModuleTier targetModuleTier,
        MinerFocusTier targetFocusTier) implements ModuleOperationRequest {}

    sealed interface ModuleConfiguration extends
        FacilityCommand permits SetHammerShootingConfig,SetHammerRoutePriority,SetMinerFocusOre,ConfigureDebugDataGenerator {

        ModuleInstance.ID moduleId();
    }

    sealed interface ModuleSettingsCommand extends
        FacilityCommand permits CreateSettingsGroup,RenameSettingsGroup,JoinSettingsGroup,LeaveSettingsGroup,CopyModuleSettings,SetMinerOreBlacklisted {
    }

    sealed interface ModuleOperationRequest
        extends FacilityCommand permits PlanHammerUpgrade,PlanTierUpgrade,PlanMinerFocusUpgrade {
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
