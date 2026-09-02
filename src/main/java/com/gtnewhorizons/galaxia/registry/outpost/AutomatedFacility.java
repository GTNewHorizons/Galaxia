package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifiers;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleState;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.LayoutCacheBundle;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepDemand;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepLedger;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;

public final class AutomatedFacility extends CelestialAsset {

    public enum DeconstructionResult {
        ACCEPTED,
        NOT_FOUND,
        ACTIVE_OPERATION,
        INVALID_REFUND,
        CAPACITY_EXCEEDED
    }

    private static final Logger LOG = LogManager.getLogger(AutomatedFacility.class);

    private final FacilityInventory inventory = new FacilityInventory();

    private final List<ModuleInstance> modules;
    private final StationLayout layout;
    private final LayoutCacheBundle layoutCache;
    private final FacilityModuleSettings moduleSettings;
    private final Map<ModuleInstance.ID, RecipeBook.ScheduleState> recipeScheduleStates = new LinkedHashMap<>();

    private final UpkeepLedger upkeepLedger;
    private UpkeepSettlement.Credits upkeepCredits = UpkeepSettlement.Credits.empty();
    private boolean settlingUpkeep;
    private boolean upkeepInventoryChanged;

    private long stationFeatureSalt;
    private final Map<ModuleInstance.ID, ModuleFeatureModifiers> featureModifiersByModule = new LinkedHashMap<>();
    private long featureModifiersLayoutVersion = Long.MIN_VALUE;
    private long featureModifiersStationFeatureSalt = Long.MIN_VALUE;

    private long energyStored;
    private long ticks;

    public static final long BASE_ENERGY_CAPACITY = 8_000_000L;
    public static final long BASE_ITEM_CAPACITY = 1000L;
    public static final int UPKEEP_INTERVAL_TICKS = 20 * 60;
    private static final int MAX_BUILD_TARGETS = 256;

    public AutomatedFacility(CelestialAsset.ID assetId, CelestialObjectKey celestialBodyKey, Kind kind, Status status) {
        super(assetId, celestialBodyKey, kind, status, null);
        if (kind != Kind.AUTOMATED_OUTPOST && kind != Kind.AUTOMATED_STATION) {
            throw new IllegalArgumentException(
                "AutomatedFacility kind must be AUTOMATED_OUTPOST or AUTOMATED_STATION, got: " + kind);
        }
        this.modules = new ArrayList<>();
        this.layout = ownsStationLayout(kind) ? new StationLayout() : null;
        this.layoutCache = new LayoutCacheBundle(layout);
        this.moduleSettings = new FacilityModuleSettings(modules);
        this.upkeepLedger = new UpkeepLedger();
        this.stationFeatureSalt = createStationFeatureSalt(assetId, celestialBodyKey);
        this.energyStored = 0;
        this.ticks = 0;
    }

    public AutomatedFacility(CelestialAsset.ID assetId, CelestialObjectId celestialBodyId, Kind kind, Status status) {
        this(assetId, CelestialObjectKey.registered(celestialBodyId), kind, status);
    }

    private static long createStationFeatureSalt(CelestialAsset.ID assetId, CelestialObjectKey bodyKey) {
        long value = assetId == null || assetId.id() == null ? 0L
            : assetId.id()
                .getMostSignificantBits()
                ^ assetId.id()
                    .getLeastSignificantBits();
        value ^= bodyKey == null ? 0L : (stationFeatureBodySalt(bodyKey) << 32);
        value ^= 0xD1B54A32D192ED03L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value;
    }

    private static long stationFeatureBodySalt(CelestialObjectKey bodyKey) {
        if (bodyKey.isRegistered()) return bodyKey.registeredBodyId()
            .ordinal();
        return (((long) bodyKey.minorBodyId()
            .parentBodyId()
            .ordinal()) << 32) ^ bodyKey.minorBodyId()
                .index();
    }

    public static boolean ownsStationLayout(Kind kind) {
        return kind == Kind.AUTOMATED_OUTPOST || kind == Kind.AUTOMATED_STATION;
    }

    public boolean hasStationLayout() {
        return layout != null;
    }

    public @Nullable StationLayout stationLayout() {
        return layout;
    }

    public List<SettingsGroup> settingsGroups() {
        return moduleSettings.groups();
    }

    public @Nullable SettingsGroup settingsGroup(SettingsGroup.ID groupId) {
        return moduleSettings.group(groupId);
    }

    public List<ModuleInstance> settingsGroupMembers(SettingsGroup.ID groupId) {
        return moduleSettings.members(groupId);
    }

    public long stationFeatureSalt() {
        return stationFeatureSalt;
    }

    public void setStationFeatureSalt(long stationFeatureSalt) {
        if (this.stationFeatureSalt == stationFeatureSalt) return;
        this.stationFeatureSalt = stationFeatureSalt;
        featureModifiersByModule.clear();
        featureModifiersStationFeatureSalt = Long.MIN_VALUE;
        markDirty();
    }

    public PlanetaryFeatureKey planetaryFeatureAt(StationTileCoord tile) {
        return firstPlanetaryFeature(planetaryFeaturesAt(tile));
    }

    public PlanetaryFeatureKey planetaryFeatureAt(int dx, int dy) {
        return firstPlanetaryFeature(planetaryFeaturesAt(dx, dy));
    }

    public List<PlanetaryFeatureKey> planetaryFeaturesAt(StationTileCoord tile) {
        if (tile == null) return Collections.emptyList();
        return planetaryFeaturesAt(tile.dx(), tile.dy());
    }

    public List<PlanetaryFeatureKey> planetaryFeaturesAt(int dx, int dy) {
        if (kind != Kind.AUTOMATED_OUTPOST) return Collections.emptyList();
        return GalaxiaCelestialAPI.get(planetaryAnchorBodyKey)
            .map(body -> PlanetaryFeatureGenerator.featuresAt(stationFeatureSalt, dx, dy, body))
            .orElse(Collections.emptyList());
    }

    private static PlanetaryFeatureKey firstPlanetaryFeature(List<PlanetaryFeatureKey> features) {
        return features.isEmpty() ? null : features.get(0);
    }

    public List<FeatureContribution> featureContributions(ModuleInstance module) {
        return featureModifiers(module).contributions();
    }

    public LayoutCacheBundle layoutCache() {
        return layoutCache;
    }

    public UpkeepLedger.UpkeepSummary upkeepSummary() {
        return upkeepLedger.summary(this);
    }

    public long upkeepReserve(ItemStackWrapper item) {
        if (item == null) return 0L;
        if (logisticsConfig.hasExplicit(item)) {
            return logisticsConfig.get(item)
                .minReserve();
        }
        UpkeepAmount perMinute = upkeepSummary().itemsPerMinute()
            .get(item);
        if (perMinute == null || perMinute.isZero()) return 0L;
        return UpkeepAmount.ofMicroUnits(Math.multiplyExact(perMinute.microUnitsPerMinute(), 10L))
            .wholeUnitsToCoverDeficit();
    }

    public boolean isUpkeepAutoOrderEnabled(ItemStackWrapper item) {
        return item != null && logisticsConfig.get(item)
            .isImportEnabled();
    }

    public long effectiveLowerBound(InventoryKey key) {
        long manualLowerBound = getBound(key).lowOrDefault();
        if (key instanceof ItemStackWrapper item) {
            return Math.addExact(manualLowerBound, upkeepReserve(item));
        }
        return manualLowerBound;
    }

    public boolean isAboveLow(InventoryKey key, long amount) {
        return (inventory.amount(key) - amount) >= effectiveLowerBound(key);
    }

    public boolean isBelowUpper(InventoryKey key) {
        return inventory.amount(key) < getBound(key).upperOrDefault();
    }

    public boolean hasLowerBound(InventoryKey key) {
        return key != null && getBound(key).hasLow();
    }

    public boolean hasUpperBound(InventoryKey key) {
        return key != null && getBound(key).hasUpper();
    }

    public InventoryBounds getBound(InventoryKey key) {
        return inventory.bound(key);
    }

    public boolean trySetBound(InventoryKey key, long amount, boolean low) {
        if (key == null) return false;
        InventoryBounds current = getBound(key);
        long nextLow = low ? amount : current.low();
        long nextUpper = low ? current.upper() : amount;
        boolean hasNextLow = low || current.hasLow();
        boolean hasNextUpper = !low || current.hasUpper();
        if (hasNextLow && hasNextUpper && nextLow > nextUpper) return false;
        InventoryBounds updated = new InventoryBounds(nextLow, nextUpper);
        if (updated.equals(current)) return false;
        inventory.setBound(key, updated);
        return true;
    }

    public boolean clearBound(InventoryKey key) {
        return inventory.clearBound(key);
    }

    public boolean clearBound(InventoryKey key, boolean low) {
        InventoryBounds current = inventory.bound(key);
        if (current.isInvalid() || (low ? !current.hasLow() : !current.hasUpper())) return false;
        if (low && current.hasUpper()) {
            inventory.setBound(key, InventoryBounds.upperBound(current.upper()));
        } else if (!low && current.hasLow()) {
            inventory.setBound(key, InventoryBounds.lowBound(current.low()));
        } else {
            inventory.clearBound(key);
        }
        return true;
    }

    public FacilityCommand.Result applyCommand(FacilityCommand command, FacilityCommand.Authority authority) {
        if (command == null || authority == null) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MALFORMED_COMMAND);
        }
        if (!assetId.equals(command.facilityId())) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.FACILITY_ID_MISMATCH);
        }
        if (command instanceof FacilityCommand.AdjustInventory adjustInventory) {
            return applyAdjustInventory(adjustInventory, authority);
        }
        if (command instanceof FacilityCommand.ClearInventoryResource clearInventoryResource) {
            return applyClearInventoryResource(clearInventoryResource);
        }
        if (command instanceof FacilityCommand.SetInventoryBound setBound) {
            return applySetInventoryBound(setBound);
        }
        if (command instanceof FacilityCommand.ClearInventoryBound clearBound) {
            return applyClearInventoryBound(clearBound);
        }
        if (command instanceof FacilityCommand.ReplaceFilters replaceFilters) {
            return applyReplaceFilters(replaceFilters);
        }
        if (command instanceof FacilityCommand.PutLogisticsConfig putConfig) {
            return applyPutLogisticsConfig(putConfig);
        }
        if (command instanceof FacilityCommand.RemoveLogisticsConfig removeConfig) {
            return applyRemoveLogisticsConfig(removeConfig);
        }
        if (command instanceof FacilityCommand.ReplaceRecipeBook replaceRecipeBook) {
            if (replaceRecipeBook.moduleId() == null || replaceRecipeBook.replacement() == null) {
                return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_RECIPE_BOOK);
            }
            ModuleInstance module = moduleById(replaceRecipeBook.moduleId());
            if (module == null) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
            try {
                return finishModuleSettingsCommand(
                    moduleSettings.replaceEffectiveSettings(module, replaceRecipeBook.replacement()));
            } catch (IllegalArgumentException | IllegalStateException invalidBook) {
                return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_RECIPE_BOOK);
            }
        }
        if (command instanceof FacilityCommand.ModuleSettingsCommand settingsCommand) {
            return applyModuleSettingsCommand(settingsCommand);
        }
        FacilityCommand.Result moduleResult = applyModuleCommand(command, authority);
        return moduleResult != null ? moduleResult
            : FacilityCommand.Result.rejected(FacilityCommand.Rejection.MALFORMED_COMMAND);
    }

    private FacilityCommand.Result applyModuleSettingsCommand(FacilityCommand.ModuleSettingsCommand command) {
        try {
            FacilityModuleSettings.Outcome outcome;
            if (command instanceof FacilityCommand.RenameSettingsGroup rename) {
                return finishModuleSettingsCommand(moduleSettings.renameGroup(rename.groupId(), rename.displayName()));
            }
            ModuleInstance.ID moduleId;
            if (command instanceof FacilityCommand.CreateSettingsGroup create) moduleId = create.moduleId();
            else if (command instanceof FacilityCommand.SetSettingsGroup setGroup) moduleId = setGroup.moduleId();
            else if (command instanceof FacilityCommand.CopyModuleSettings copy) moduleId = copy.sourceModuleId();
            else if (command instanceof FacilityCommand.ReplaceMinerSettings replace) moduleId = replace.moduleId();
            else return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MALFORMED_COMMAND);
            ModuleInstance module = moduleById(moduleId);
            if (module == null) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
            if (command instanceof FacilityCommand.CreateSettingsGroup create) {
                outcome = moduleSettings.createGroup(module, create.displayName());
            } else if (command instanceof FacilityCommand.SetSettingsGroup setGroup) {
                outcome = setGroup.groupId() == null ? moduleSettings.leaveGroup(module)
                    : moduleSettings.joinGroup(module, setGroup.groupId());
            } else if (command instanceof FacilityCommand.CopyModuleSettings copy) {
                return applyCopyModuleSettings(module, copy);
            } else if (command instanceof FacilityCommand.ReplaceMinerSettings replace) {
                outcome = moduleSettings.replaceEffectiveSettings(module, replace.replacement());
            } else {
                return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MALFORMED_COMMAND);
            }
            return finishModuleSettingsCommand(outcome);
        } catch (IllegalArgumentException | IllegalStateException invalidSettings) {
            FacilityCommand.Rejection rejection = command instanceof FacilityCommand.CopyModuleSettings
                ? FacilityCommand.Rejection.INVALID_MODULE_TARGETS
                : command instanceof FacilityCommand.ReplaceMinerSettings
                    ? FacilityCommand.Rejection.INVALID_MODULE_CONFIG
                    : FacilityCommand.Rejection.INVALID_SETTINGS_GROUP;
            return FacilityCommand.Result.rejected(rejection);
        }
    }

    private FacilityCommand.Result applyCopyModuleSettings(ModuleInstance source,
        FacilityCommand.CopyModuleSettings command) {
        if (command.targetModuleIds() == null || command.targetModuleIds()
            .isEmpty()) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_TARGETS);
        List<ModuleInstance> targets = new ArrayList<>(
            command.targetModuleIds()
                .size());
        Set<ModuleInstance.ID> uniqueTargets = new HashSet<>();
        for (ModuleInstance.ID targetId : command.targetModuleIds()) {
            if (targetId == null || !uniqueTargets.add(targetId)) {
                return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_TARGETS);
            }
            ModuleInstance target = moduleById(targetId);
            if (target == null) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
            targets.add(target);
        }
        return finishModuleSettingsCommand(moduleSettings.copySettings(source, targets));
    }

    private FacilityCommand.Result finishModuleSettingsCommand(FacilityModuleSettings.Outcome outcome) {
        if (!outcome.changed()) return FacilityCommand.Result.UNCHANGED;
        resetRecipeSchedules(outcome.affectedModuleIds());
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyAdjustInventory(FacilityCommand.AdjustInventory command,
        FacilityCommand.Authority authority) {
        if (command.resource() == null) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_RESOURCE);
        }
        if (command.direction() == null || command.amount() <= 0L) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_INVENTORY_ADJUSTMENT);
        }
        if (command.direction() == FacilityCommand.InventoryAdjustment.INSERT && !authority.creativeMode()) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.CREATIVE_MODE_REQUIRED);
        }
        long applied = command.direction() == FacilityCommand.InventoryAdjustment.INSERT
            ? insert(command.resource(), command.amount())
            : extract(command.resource(), command.amount());
        return applied == 0L ? FacilityCommand.Result.UNCHANGED : FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyClearInventoryResource(FacilityCommand.ClearInventoryResource command) {
        if (command.resource() == null) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_RESOURCE);
        }
        long stored = inventory.amount(command.resource());
        if (stored == 0L) return FacilityCommand.Result.UNCHANGED;
        return extract(command.resource(), stored) == 0L ? FacilityCommand.Result.UNCHANGED
            : FacilityCommand.Result.CHANGED;
    }

    private @Nullable FacilityCommand.Result applyModuleCommand(FacilityCommand command,
        FacilityCommand.Authority authority) {
        if (command instanceof FacilityCommand.BuildModules buildModules) {
            return applyModuleBuild(buildModules, null, authority);
        }
        if (command instanceof FacilityCommand.CopyBuildModules copyBuildModules) {
            return applyCopyBuildModules(copyBuildModules, authority);
        }
        if (command instanceof FacilityCommand.RequestModuleDeconstruction deconstruct) {
            return applyRequestModuleDeconstruction(deconstruct);
        }
        if (command instanceof FacilityCommand.CancelModuleOperation cancelOperation) {
            return applyCancelModuleOperation(cancelOperation);
        }
        if (command instanceof FacilityCommand.ModuleConfiguration configuration) {
            return applyModuleConfiguration(configuration, authority);
        }
        if (command instanceof FacilityCommand.PlanHammerUpgrade planHammerUpgrade) {
            return applyPlanHammerUpgrade(planHammerUpgrade, authority);
        }
        if (command instanceof FacilityCommand.PlanTierUpgrade planTierUpgrade) {
            return applyPlanTierUpgrade(planTierUpgrade, authority);
        }
        if (command instanceof FacilityCommand.PlanMinerFocusUpgrade planMinerFocusUpgrade) {
            return applyPlanMinerFocusUpgrade(planMinerFocusUpgrade, authority);
        }
        return null;
    }

    private FacilityCommand.Result applyModuleConfiguration(FacilityCommand.ModuleConfiguration configuration,
        FacilityCommand.Authority authority) {
        if (configuration instanceof FacilityCommand.ConfigureDebugDataGenerator && !authority.debugAuthorized()) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.DEBUG_AUTHORIZATION_REQUIRED);
        }
        ModuleInstance module = moduleById(configuration.moduleId());
        if (module == null) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
        boolean changed;
        try {
            changed = module.component()
                .applyConfigurationTransition(module, configuration);
        } catch (UnsupportedOperationException invalidComponent) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_COMPONENT);
        } catch (IllegalArgumentException | IllegalStateException invalidConfiguration) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_CONFIG);
        }
        if (!changed) return FacilityCommand.Result.UNCHANGED;
        if (configuration instanceof FacilityCommand.ConfigureDebugDataGenerator) {
            SatelliteNetworkService.refreshFacilityEndpoints(this);
        }
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyPlanHammerUpgrade(FacilityCommand.PlanHammerUpgrade command,
        FacilityCommand.Authority authority) {
        Map<ModuleInstance, ModuleOperationPlan> plans = new LinkedHashMap<>();
        FacilityCommand.Rejection targetRejection = resolveUpgradeTargets(command.targetModuleIds(), plans);
        if (targetRejection != null) return FacilityCommand.Result.rejected(targetRejection);
        for (ModuleInstance module : plans.keySet()) {
            FacilityCommand.Rejection rejection = prepareModuleOperationPlan(
                plans,
                module,
                command,
                command.reserveItems(),
                command.voidCompletionRefund());
            if (rejection != null) return FacilityCommand.Result.rejected(rejection);
        }
        return commitModuleOperationPlans(plans, authority.creativeMode(), command.reserveItems());
    }

    private FacilityCommand.Result applyPlanTierUpgrade(FacilityCommand.PlanTierUpgrade command,
        FacilityCommand.Authority authority) {
        Map<ModuleInstance, ModuleOperationPlan> plans = new LinkedHashMap<>();
        FacilityCommand.Rejection targetRejection = resolveUpgradeTargets(command.targetModuleIds(), plans);
        if (targetRejection != null) return FacilityCommand.Result.rejected(targetRejection);
        FacilityModuleKind targetKind = plans.keySet()
            .iterator()
            .next()
            .kind();
        if (plans.keySet()
            .stream()
            .anyMatch(module -> module.kind() != targetKind)) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_TARGETS);
        }
        for (ModuleInstance module : plans.keySet()) {
            FacilityCommand.Rejection rejection = prepareModuleOperationPlan(
                plans,
                module,
                command,
                command.reserveItems(),
                false);
            if (rejection != null) return FacilityCommand.Result.rejected(rejection);
        }
        return commitModuleOperationPlans(plans, authority.creativeMode(), command.reserveItems());
    }

    private FacilityCommand.Result applyPlanMinerFocusUpgrade(FacilityCommand.PlanMinerFocusUpgrade command,
        FacilityCommand.Authority authority) {
        Map<ModuleInstance, ModuleOperationPlan> plans = new LinkedHashMap<>();
        FacilityCommand.Rejection targetRejection = resolveUpgradeTargets(
            Collections.singletonList(command.moduleId()),
            plans);
        if (targetRejection != null) return FacilityCommand.Result.rejected(targetRejection);
        ModuleInstance module = plans.keySet()
            .iterator()
            .next();
        FacilityCommand.Rejection rejection = prepareModuleOperationPlan(plans, module, command, false, false);
        if (rejection != null) return FacilityCommand.Result.rejected(rejection);
        return commitModuleOperationPlans(plans, authority.creativeMode(), false);
    }

    private @Nullable FacilityCommand.Rejection prepareModuleOperationPlan(
        Map<ModuleInstance, ModuleOperationPlan> plans, ModuleInstance module, FacilityCommand.ModuleCommand request,
        boolean reserveItems, boolean voidCompletionRefund) {
        try {
            IModuleOperation operation = module.component()
                .prepareOperationTarget(module, request);
            ModuleTier targetTier = operation.targetTier();
            if (targetTier == null) return FacilityCommand.Rejection.INVALID_MODULE_UPGRADE;
            plans.put(module, moduleOperationPlan(module, targetTier, operation, reserveItems, voidCompletionRefund));
            return null;
        } catch (UnsupportedOperationException invalidComponent) {
            return FacilityCommand.Rejection.INVALID_MODULE_COMPONENT;
        } catch (IllegalArgumentException | IllegalStateException invalidUpgrade) {
            return FacilityCommand.Rejection.INVALID_MODULE_UPGRADE;
        }
    }

    private @Nullable FacilityCommand.Rejection resolveUpgradeTargets(List<ModuleInstance.ID> targetIds,
        Map<ModuleInstance, ModuleOperationPlan> plans) {
        if (targetIds == null || targetIds.isEmpty() || targetIds.size() > MAX_BUILD_TARGETS) {
            return FacilityCommand.Rejection.INVALID_MODULE_TARGETS;
        }
        Set<ModuleInstance.ID> uniqueIds = new HashSet<>();
        for (ModuleInstance.ID targetId : targetIds) {
            if (targetId == null || !uniqueIds.add(targetId)) {
                return FacilityCommand.Rejection.INVALID_MODULE_TARGETS;
            }
            ModuleInstance module = moduleById(targetId);
            if (module == null) return FacilityCommand.Rejection.MODULE_NOT_FOUND;
            ModuleOperationState operation = module.operationOrNull();
            if (operation != null && (!operation.phase()
                .isTerminal()
                || !operation.depositedResources()
                    .isEmpty()
                || !operation.refundBuffer()
                    .isEmpty())) {
                return FacilityCommand.Rejection.MODULE_OPERATION_ACTIVE;
            }
            plans.put(module, null);
        }
        return null;
    }

    private ModuleOperationPlan moduleOperationPlan(ModuleInstance module, ModuleTier targetTier,
        com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation operation, boolean reserveItems,
        boolean voidCompletionRefund) {
        ModuleTierData sourceData = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        ModuleTierData targetData = FacilityModuleRegistry.get(module.kind())
            .getTierData(targetTier);
        return new ModuleOperationPlan(
            operation,
            sourceData.buildTicks(),
            FacilityModuleRegistry.operationCost(targetData.constructionCost()),
            FacilityModuleRegistry.operationCost(sourceData.constructionCost()),
            sourceData.completionRefundPercent(),
            reserveItems,
            voidCompletionRefund);
    }

    private FacilityCommand.Result commitModuleOperationPlans(Map<ModuleInstance, ModuleOperationPlan> plans,
        boolean creative, boolean reserveItems) {
        if (creative) {
            for (Map.Entry<ModuleInstance, ModuleOperationPlan> entry : plans.entrySet()) {
                applyOperationTarget(entry.getKey(), entry.getValue());
                entry.getKey()
                    .clearOperation();
            }
            markDirty();
            return FacilityCommand.Result.CHANGED;
        }

        if (reserveItems) {
            Map<ItemStackWrapper, Long> aggregateCost = new LinkedHashMap<>();
            for (ModuleOperationPlan plan : plans.values()) {
                for (Map.Entry<ItemStackWrapper, Long> material : plan.materialCost()
                    .entrySet()) {
                    aggregateCost.merge(material.getKey(), material.getValue(), Math::addExact);
                }
            }
            if (!aggregateCost.isEmpty() && inventory.tryExchange(aggregateCost, Map.of(), itemCapacity())
                == FacilityInventory.ExchangeResult.REJECTED) {
                return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INSUFFICIENT_MODULE_MATERIALS);
            }
        }

        for (Map.Entry<ModuleInstance, ModuleOperationPlan> entry : plans.entrySet()) {
            ModuleOperationState operation = ModuleOperationState.waiting(entry.getValue());
            if (reserveItems && !entry.getValue()
                .materialCost()
                .isEmpty()) {
                Map<String, Long> deposited = new LinkedHashMap<>();
                for (Map.Entry<ItemStackWrapper, Long> material : entry.getValue()
                    .materialCost()
                    .entrySet()) {
                    deposited.merge(
                        material.getKey()
                            .toKey(),
                        material.getValue(),
                        Math::addExact);
                }
                operation.withDepositedResources(deposited);
            }
            entry.getKey()
                .setOperation(operation);
        }
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applySetInventoryBound(FacilityCommand.SetInventoryBound command) {
        FacilityCommand.Rejection resourceRejection = boundResourceRejection(command.kind(), command.resource());
        if (resourceRejection != null) return FacilityCommand.Result.rejected(resourceRejection);
        if (command.amount() < 0L) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_BOUND);
        }
        boolean lower = isLowerBound(command.kind());
        InventoryBounds current = getBound(command.resource());
        if (isSameBound(current, command.amount(), lower)) {
            return FacilityCommand.Result.UNCHANGED;
        }
        if (!trySetBound(command.resource(), command.amount(), lower)) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_BOUND);
        }
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyClearInventoryBound(FacilityCommand.ClearInventoryBound command) {
        FacilityCommand.Rejection resourceRejection = boundResourceRejection(command.kind(), command.resource());
        if (resourceRejection != null) return FacilityCommand.Result.rejected(resourceRejection);
        boolean lower = isLowerBound(command.kind());
        if (!clearBound(command.resource(), lower)) return FacilityCommand.Result.UNCHANGED;
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyReplaceFilters(FacilityCommand.ReplaceFilters command) {
        if (command.kind() == null || command.filterKeys() == null) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_FILTERS);
        }
        try {
            if (!inventory.setFilters(
                command.filterKeys(),
                command.kind()
                    .isItem())) {
                return FacilityCommand.Result.UNCHANGED;
            }
        } catch (IllegalArgumentException invalidFilters) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_FILTERS);
        }
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyPutLogisticsConfig(FacilityCommand.PutLogisticsConfig command) {
        if (command.resource() == null) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_RESOURCE);
        }
        if (command.config() == null || command.accessMode() == null
            || command.config()
                .minReserve() < 0
            || command.config()
                .orderSize() <= 0) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_LOGISTICS_CONFIG);
        }
        LogisticsResourceConfig updated = command.accessMode()
            .sanitize(command.config());
        if (logisticsConfig.hasExplicit(command.resource()) && logisticsConfig.get(command.resource())
            .equals(updated)) {
            return FacilityCommand.Result.UNCHANGED;
        }
        logisticsConfig.set(command.resource(), updated);
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyRemoveLogisticsConfig(FacilityCommand.RemoveLogisticsConfig command) {
        if (command.resource() == null) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_RESOURCE);
        }
        if (!logisticsConfig.hasExplicit(command.resource())) return FacilityCommand.Result.UNCHANGED;
        logisticsConfig.reset(command.resource());
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Result applyCopyBuildModules(FacilityCommand.CopyBuildModules command,
        FacilityCommand.Authority authority) {
        ModuleInstance source = moduleById(command.sourceModuleId());
        if (source == null) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
        FacilityCommand.BuildModules copiedBuild = new FacilityCommand.BuildModules(
            command.facilityId(),
            source.kind(),
            source.shape(),
            source.component()
                .buildPhysicalSpec(source),
            null,
            command.instantBuild(),
            command.placements());
        return applyModuleBuild(copiedBuild, source, authority);
    }

    private FacilityCommand.Result applyModuleBuild(FacilityCommand.BuildModules command,
        @Nullable ModuleInstance copySource, FacilityCommand.Authority authority) {
        FacilityModuleKind buildKind = command.kind();
        ModuleShape buildShape = command.shape();
        IModuleComponent.BuildPhysicalSpec physicalSpec = command.physicalSpec();
        List<ModulePlacement> placements = command.placements();
        if (command.instantBuild() && !authority.debugAuthorized()) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.DEBUG_AUTHORIZATION_REQUIRED);
        }
        FacilityCommand.Rejection specRejection = validateModuleBuildSpec(
            buildKind,
            buildShape,
            physicalSpec,
            authority);
        if (specRejection != null) return FacilityCommand.Result.rejected(specRejection);
        if (placements == null || placements.isEmpty()
            || !placements.equals(buildablePlacements(buildKind, buildShape, physicalSpec.tier(), placements))) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_PLACEMENT);
        }
        if (!validInitialSettingsGroup(buildKind, command.settingsGroupId(), copySource)) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
        }

        List<ModuleInstance> prepared = new ArrayList<>(placements.size());
        Map<ModuleInstance.ID, ModuleInstance.SettingsBinding> settingsPlans = new LinkedHashMap<>();
        boolean shouldInstantBuild = command.instantBuild() && authority.debugAuthorized();
        for (ModulePlacement placement : placements) {
            ModuleInstance module;
            try {
                module = buildKind.create(placement.anchor(), buildShape, physicalSpec.tier());
                module.component()
                    .applyBuildPhysicalSpec(module, physicalSpec);
            } catch (IllegalArgumentException | IllegalStateException invalidModule) {
                return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_MODULE_SPEC);
            }
            module.setRotation(placement.rotation());
            if (moduleSettings.supports(module)) {
                ModuleInstance.SettingsBinding settingsPlan = prepareModuleSettings(
                    module,
                    copySource,
                    command.settingsGroupId());
                if (settingsPlan == null) {
                    return FacilityCommand.Result.rejected(FacilityCommand.Rejection.INVALID_SETTINGS_GROUP);
                }
                settingsPlans.put(module.id, settingsPlan);
            }
            if (shouldInstantBuild) module.completeConstruction();
            prepared.add(module);
        }
        for (ModuleInstance module : prepared) {
            attachModuleWithoutRevision(module, settingsPlans.get(module.id));
            layout.place(module);
            layoutCache.applyMutation(MutationKind.PLACE, module.kind(), module);
        }
        markDirty();
        SatelliteNetworkService.refreshFacilityEndpoints(this);
        return FacilityCommand.Result.CHANGED;
    }

    private FacilityCommand.Rejection validateModuleBuildSpec(FacilityModuleKind buildKind, ModuleShape buildShape,
        IModuleComponent.BuildPhysicalSpec physicalSpec, FacilityCommand.Authority authority) {
        if (buildKind == null || buildShape == null || physicalSpec == null || physicalSpec.tier() == null) {
            return FacilityCommand.Rejection.INVALID_MODULE_SPEC;
        }
        if (buildKind.isDebugOnly() && !authority.debugAuthorized()) {
            return FacilityCommand.Rejection.DEBUG_AUTHORIZATION_REQUIRED;
        }
        if (!buildKind.isAllowedOn(kind)) return FacilityCommand.Rejection.MODULE_KIND_NOT_ALLOWED;
        if (!buildKind.allowedTiers()
            .contains(physicalSpec.tier()) || buildShape != buildKind.defaultShape()) {
            return FacilityCommand.Rejection.INVALID_MODULE_SPEC;
        }
        return null;
    }

    private boolean validInitialSettingsGroup(FacilityModuleKind buildKind, @Nullable SettingsGroup.ID settingsGroupId,
        @Nullable ModuleInstance copySource) {
        if (copySource != null || settingsGroupId == null) return true;
        if (!FacilityModuleRegistry.get(buildKind)
            .settingsGroups()) return false;
        return moduleSettings.canJoin(buildKind, settingsGroupId);
    }

    private @Nullable ModuleInstance.SettingsBinding prepareModuleSettings(ModuleInstance target,
        @Nullable ModuleInstance copySource, @Nullable SettingsGroup.ID settingsGroupId) {
        try {
            return moduleSettings.prepareAttachment(target, copySource, settingsGroupId);
        } catch (IllegalArgumentException | IllegalStateException invalidSettings) {
            return null;
        }
    }

    private void attachModuleWithoutRevision(ModuleInstance module,
        @Nullable ModuleInstance.SettingsBinding settingsPlan) {
        modules.add(module);
        if (module.recipe() != null) {
            recipeScheduleStates.put(module.id, RecipeBook.ScheduleState.RESET);
        }
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) return;
        if (settingsPlan == null) throw new IllegalStateException("Missing settings attachment for " + module.id);
        moduleSettings.attach(module, settingsPlan);
    }

    public List<ModulePlacement> buildablePlacements(@Nullable FacilityModuleKind moduleKind,
        @Nullable ModuleShape shape, @Nullable ModuleTier tier, @Nullable List<ModulePlacement> candidates) {
        if (moduleKind == null || shape == null
            || tier == null
            || candidates == null
            || candidates.isEmpty()
            || layout == null
            || !moduleKind.isAllowedOn(kind)
            || !moduleKind.allowedTiers()
                .contains(tier)
            || shape != moduleKind.defaultShape()) {
            return List.of();
        }
        List<ModulePlacement> placements = new ArrayList<>(Math.min(candidates.size(), MAX_BUILD_TARGETS));
        Set<StationTileCoord> plannedTiles = new HashSet<>();
        Set<StationTileCoord> originalTiles = layout.snapshot()
            .keySet();
        PlanetaryFeatureKey requiredFeature = moduleKind.requiredAnchorFeature();
        for (ModulePlacement candidate : candidates) {
            if (placements.size() == MAX_BUILD_TARGETS) break;
            StationTileCoord[] footprint = validPlacementFootprint(
                shape,
                candidate,
                requiredFeature,
                originalTiles,
                plannedTiles);
            if (footprint == null) continue;
            placements.add(candidate);
            Collections.addAll(plannedTiles, footprint);
        }
        return List.copyOf(placements);
    }

    private @Nullable StationTileCoord[] validPlacementFootprint(ModuleShape shape, @Nullable ModulePlacement placement,
        @Nullable PlanetaryFeatureKey requiredFeature, Set<StationTileCoord> originalTiles,
        Set<StationTileCoord> plannedTiles) {
        if (placement == null || placement.anchor() == null) return null;
        StationTileCoord anchor = placement.anchor();
        if (!shape.fitsAt(anchor, placement.rotation())) return null;
        if (requiredFeature != null && !planetaryFeaturesAt(anchor).contains(requiredFeature)) return null;
        StationTileCoord[] footprint = shape.tiles(anchor, placement.rotation());
        boolean adjacent = false;
        for (StationTileCoord coord : footprint) {
            if (originalTiles.contains(coord) || plannedTiles.contains(coord)) return null;
            if (hasKnownOccupiedNeighbour(originalTiles, plannedTiles, coord)) adjacent = true;
        }
        return adjacent ? footprint : null;
    }

    private static boolean hasKnownOccupiedNeighbour(Set<StationTileCoord> originalTiles,
        Set<StationTileCoord> plannedTiles, StationTileCoord coord) {
        return containsKnown(originalTiles, plannedTiles, coord.dx() - 1, coord.dy())
            || containsKnown(originalTiles, plannedTiles, coord.dx() + 1, coord.dy())
            || containsKnown(originalTiles, plannedTiles, coord.dx(), coord.dy() - 1)
            || containsKnown(originalTiles, plannedTiles, coord.dx(), coord.dy() + 1);
    }

    private static boolean containsKnown(Set<StationTileCoord> originalTiles, Set<StationTileCoord> plannedTiles,
        int dx, int dy) {
        if (dx < StationTileCoord.MIN || dx > StationTileCoord.MAX
            || dy < StationTileCoord.MIN
            || dy > StationTileCoord.MAX) {
            return false;
        }
        StationTileCoord coord = StationTileCoord.of(dx, dy);
        return originalTiles.contains(coord) || plannedTiles.contains(coord);
    }

    private FacilityCommand.Result applyRequestModuleDeconstruction(
        FacilityCommand.RequestModuleDeconstruction command) {
        return switch (requestModuleDeconstruction(command.moduleId())) {
            case ACCEPTED -> FacilityCommand.Result.CHANGED;
            case NOT_FOUND -> FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
            case ACTIVE_OPERATION -> FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_OPERATION_ACTIVE);
            case INVALID_REFUND -> FacilityCommand.Result
                .rejected(FacilityCommand.Rejection.INVALID_DECONSTRUCTION_REFUND);
            case CAPACITY_EXCEEDED -> FacilityCommand.Result.rejected(FacilityCommand.Rejection.CAPACITY_EXCEEDED);
        };
    }

    private FacilityCommand.Result applyCancelModuleOperation(FacilityCommand.CancelModuleOperation command) {
        ModuleInstance module = moduleById(command.moduleId());
        if (module == null) return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_NOT_FOUND);
        ModuleOperationState operation = module.operationOrNull();
        if (operation == null) return FacilityCommand.Result.UNCHANGED;
        if (operation.phase() != ModuleOperationPhase.WAITING_FOR_MATERIALS
            && operation.phase() != ModuleOperationPhase.BUILDING) {
            return FacilityCommand.Result.rejected(FacilityCommand.Rejection.MODULE_OPERATION_NOT_CANCELLABLE);
        }
        operation.cancel();
        markDirty();
        return FacilityCommand.Result.CHANGED;
    }

    public @Nullable ModuleInstance moduleById(@Nullable ModuleInstance.ID moduleId) {
        if (moduleId == null) return null;
        for (ModuleInstance module : modules) {
            if (moduleId.equals(module.id)) return module;
        }
        return null;
    }

    private static FacilityCommand.Rejection boundResourceRejection(BoundKind kind, InventoryKey resource) {
        if (kind == null || resource == null) return FacilityCommand.Rejection.INVALID_RESOURCE;
        boolean itemBound = kind == BoundKind.ITEM_LOWER || kind == BoundKind.ITEM_UPPER;
        return itemBound == (resource instanceof ItemStackWrapper) ? null
            : FacilityCommand.Rejection.INVALID_RESOURCE_KIND;
    }

    private static boolean isSameBound(InventoryBounds current, long amount, boolean lower) {
        return lower ? current.hasLow() && current.low() == amount : current.hasUpper() && current.upper() == amount;
    }

    private static boolean isLowerBound(BoundKind kind) {
        return kind == BoundKind.ITEM_LOWER || kind == BoundKind.FLUID_LOWER;
    }

    public UpkeepSettlement.Credits upkeepCredits() {
        return upkeepCredits;
    }

    public void loadUpkeepCredits(UpkeepSettlement.Credits upkeepCredits) {
        this.upkeepCredits = upkeepCredits == null ? UpkeepSettlement.Credits.empty() : upkeepCredits;
    }

    public UpkeepSettlement.Result settleUpkeep() {
        UpkeepLedger.UpkeepSummary summary = upkeepSummary();
        UpkeepSettlement.Credits creditsBefore = upkeepCredits;
        upkeepInventoryChanged = false;
        settlingUpkeep = true;
        UpkeepSettlement.Result result;
        boolean settlementCalculated = false;
        try {
            result = UpkeepSettlement.settle(summary.moduleDemands(), creditsBefore, this);
            settlementCalculated = true;
        } finally {
            settlingUpkeep = false;
            if (!settlementCalculated && upkeepInventoryChanged) markDirty();
        }
        upkeepCredits = result.credits();

        Set<ModuleInstance.ID> demanded = new HashSet<>();
        for (UpkeepLedger.ModuleDemand demand : summary.moduleDemands()) {
            demanded.add(demand.moduleId());
        }
        Set<ModuleInstance.ID> paid = result.paidModuleIds();
        Set<ModuleInstance.ID> unpaid = new HashSet<>(result.unpaidModuleIds());
        boolean moduleChanged = false;
        for (ModuleInstance module : modules) {
            if (unpaid.contains(module.id)) {
                moduleChanged |= setModuleUpkeepBlocked(module);
            } else if (paid.contains(module.id) || !demanded.contains(module.id)) {
                moduleChanged |= clearModuleUpkeepBlocked(module);
            }
        }
        if (upkeepInventoryChanged || !creditsBefore.equals(upkeepCredits) || moduleChanged) {
            markDirty();
        }
        return result;
    }

    private static boolean setModuleUpkeepBlocked(ModuleInstance module) {
        if (module.blocking() == BlockingReason.UPKEEP_SHORTAGE && module.state() == ModuleState.BLOCKED) return false;
        module.setBlocking(BlockingReason.UPKEEP_SHORTAGE);
        module.setState(ModuleState.BLOCKED);
        return true;
    }

    private static boolean clearModuleUpkeepBlocked(ModuleInstance module) {
        if (module.blocking() != BlockingReason.UPKEEP_SHORTAGE) return false;
        module.setBlocking(BlockingReason.NONE);
        if (module.state() == ModuleState.BLOCKED) {
            module.setState(ModuleState.IDLE);
        }
        return true;
    }

    public List<ModuleInstance> modules() {
        return Collections.unmodifiableList(modules);
    }

    public void addModule(ModuleInstance module) {
        if (modules.contains(module)) {
            LOG.warn(
                "[PERSIST] addModule: duplicate module {} kind={} id={} (already present)",
                module.kind(),
                module.id,
                System.identityHashCode(module));
            return;
        }
        modules.add(module);
        if (moduleSettings.supports(module)) moduleSettings.attach(module, null);
        if (module.recipe() != null) {
            recipeScheduleStates.put(module.id, RecipeBook.ScheduleState.RESET);
        }
        LOG.debug(
            "[PERSIST] addModule: added {} id={} anchor=({},{}) shape={} status={} (total={})",
            module.kind(),
            module.id,
            (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                .dx() : ModuleInstance.NULL_ANCHOR_LOG_VALUE),
            (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                .dy() : ModuleInstance.NULL_ANCHOR_LOG_VALUE),
            module.shape(),
            module.status(),
            modules.size());

        markDirty();
        SatelliteNetworkService.refreshFacilityEndpoints(this);
    }

    public DeconstructionResult requestModuleDeconstruction(ModuleInstance.ID moduleId) {
        ModuleInstance module = moduleById(moduleId);
        if (module == null) return DeconstructionResult.NOT_FOUND;
        if (module.operationOrNull() != null) return DeconstructionResult.ACTIVE_OPERATION;

        DeconstructionRefund refund = deconstructionRefund(module);
        if (refund == null) return DeconstructionResult.INVALID_REFUND;
        if (module.kind() == FacilityModuleKind.STORAGE) {
            long projectedCapacity = projectedItemCapacityAfterRemoving(module.id);
            try {
                if (Math.addExact(storedItemAmount(), refund.total()) > projectedCapacity) {
                    return DeconstructionResult.CAPACITY_EXCEEDED;
                }
            } catch (ArithmeticException overflow) {
                return DeconstructionResult.INVALID_REFUND;
            }
        }
        if (refund.byKey()
            .isEmpty()) {
            finalizeModuleRemoval(module);
            return DeconstructionResult.ACCEPTED;
        }

        module.updateStatus(Status.DECONSTRUCTION);
        module.setOperation(ModuleOperationState.deconstructing(refund.byKey()));
        FacilityInventory.ReturnItemsResult returned = inventory.returnItems(refund.byItem(), itemCapacity());
        if (returned.completed()) {
            finalizeModuleRemoval(module);
        } else {
            module.setOperation(
                module.operationOrNull()
                    .withRefundBuffer(toItemKeys(returned.remaining())));
            markDirty();
            SatelliteNetworkService.refreshFacilityEndpoints(this);
        }
        return DeconstructionResult.ACCEPTED;
    }

    private void finalizeModuleRemoval(ModuleInstance module) {
        if (!modules.contains(module)) return;
        moduleSettings.remove(module.id);
        modules.remove(module);
        recipeScheduleStates.remove(module.id);
        if (layout != null) layout.removeTileForModule(module.id);
        layoutCache.applyMutation(MutationKind.DECONSTRUCT, module.kind(), module);
        markDirty();
        SatelliteNetworkService.refreshFacilityEndpoints(this);
    }

    public void clearModules() {
        modules.clear();
        moduleSettings.restore(List.of());
        recipeScheduleStates.clear();
        markDirty();
    }

    public void restoreModulesAndSettings(List<ModuleInstance> restoredModules,
        Collection<SettingsGroup> restoredGroups) {
        if (!modules.isEmpty()) throw new IllegalStateException("Facility modules must be empty before restore");
        List<ModuleInstance> restored = List.copyOf(restoredModules);
        modules.addAll(restored);
        try {
            moduleSettings.restore(restoredGroups);
        } catch (RuntimeException invalid) {
            modules.clear();
            throw invalid;
        }
        for (ModuleInstance module : restored) {
            if (module.recipe() != null) {
                recipeScheduleStates.put(module.id, RecipeBook.ScheduleState.RESET);
            }
        }
    }

    public Stream<ModuleInstance> forEachModule() {
        return modules.stream();
    }

    public List<ModuleInstance> modulesInternal() {
        return modules;
    }

    public MinerSettings minerSettings(ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("Miner settings requested for non-miner module " + module.id);
        }
        ModuleSettings effective = moduleSettings.effectiveSettings(module.id);
        if (!(effective instanceof MinerSettings settings)) {
            throw new IllegalStateException("Miner module " + module.id + " has non-miner settings");
        }
        return settings;
    }

    public boolean isMinerOreBlacklisted(ModuleInstance module, String oreKey) {
        return minerSettings(module).isOreBlacklisted(oreKey);
    }

    public RecipeBook recipeBook(ModuleInstance module) {
        requireRecipeModule(module, "Recipe book requested");
        return moduleSettings.recipeBook(module.id);
    }

    public RecipeBook.ScheduleState recipeScheduleState(ModuleInstance module) {
        requireRecipeModule(module, "Recipe schedule requested");
        RecipeBook.ScheduleState state = recipeScheduleStates.get(module.id);
        if (state == null) throw new IllegalStateException("Recipe module missing schedule state " + module.id);
        return state;
    }

    public void restoreRecipeScheduleState(ModuleInstance module, RecipeBook.ScheduleState scheduleState) {
        requireRecipeModule(module, "Recipe schedule restore requested");
        if (scheduleState == null) throw new IllegalStateException("Recipe schedule state must not be null");
        recipeScheduleStates.put(module.id, scheduleState);
    }

    public void installRecipeScheduleState(ModuleInstance module, RecipeBook.ScheduleState scheduleState) {
        restoreRecipeScheduleState(module, scheduleState);
    }

    public Map<ModuleInstance.ID, RecipeBook.ScheduleState> recipeScheduleStates() {
        Map<ModuleInstance.ID, RecipeBook.ScheduleState> scheduleStates = new LinkedHashMap<>();
        for (ModuleInstance module : modules) {
            if (module.recipe() == null) continue;
            scheduleStates.put(module.id, recipeScheduleState(module));
        }
        return Collections.unmodifiableMap(scheduleStates);
    }

    public void restoreRecipeScheduleStates(Map<ModuleInstance.ID, RecipeBook.ScheduleState> scheduleStates) {
        if (scheduleStates == null) throw new IllegalStateException("Missing facility recipe schedule states");
        Map<ModuleInstance.ID, RecipeBook.ScheduleState> remaining = new LinkedHashMap<>(scheduleStates);
        for (ModuleInstance module : modules) {
            if (module.recipe() != null) {
                restoreRecipeScheduleState(module, remaining.remove(module.id));
            }
        }
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("Recipe schedule state references missing module " + remaining.keySet());
        }
    }

    private void requireRecipeModule(ModuleInstance module, String action) {
        if (module == null || module.recipe() == null || moduleById(module.id) == null) {
            throw new IllegalStateException(action + " for non-recipe module " + (module == null ? "null" : module.id));
        }
    }

    private void resetRecipeSchedules(Set<ModuleInstance.ID> moduleIds) {
        for (ModuleInstance.ID moduleId : moduleIds) {
            if (recipeScheduleStates.containsKey(moduleId)) {
                recipeScheduleStates.put(moduleId, RecipeBook.ScheduleState.RESET);
            }
        }
    }

    public boolean canCopyModuleRuntimeSettings(ModuleInstance source, ModuleInstance target) {
        return moduleSettings.canCopySettings(source, target);
    }

    public boolean tryReserveOperationMaterials(ModuleInstance module, Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        if (requested.isEmpty()) return true;
        if (inventory.tryExchange(requested, Map.of(), itemCapacity()) == FacilityInventory.ExchangeResult.REJECTED) {
            return false;
        }
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            deposited.merge(
                material.getKey()
                    .toKey(),
                material.getValue(),
                Long::sum);
        }
        module.setOperation(operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
        markDirty();
        return true;
    }

    public boolean tryConsumeInventory(ItemStackWrapper item, long amount) {
        if (item == null) return false;
        if (amount <= 0L) return true;
        if (itemAmount(item) < amount) return false;
        return extract(item, amount) == amount;
    }

    public boolean tryConsumeFluid(FluidKey key, long amount) {
        if (key == null) return false;
        if (amount <= 0L) return true;
        if (fluidAmount(key) < amount) return false;
        return extract(key, amount) == amount;
    }

    public boolean tryReserveAvailableOperationMaterials(ModuleInstance module,
        Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        Map<ItemStackWrapper, Long> reservedItems = new java.util.LinkedHashMap<>();
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            String itemKey = material.getKey()
                .toKey();
            long alreadyDeposited = operation.depositedResources()
                .getOrDefault(itemKey, 0L);
            long remaining = material.getValue() - alreadyDeposited;
            if (remaining <= 0L) continue;
            long available = itemAmount(material.getKey());
            long reserved = Math.min(available, remaining);
            if (reserved <= 0L) continue;
            reservedItems.put(material.getKey(), reserved);
            deposited.merge(itemKey, reserved, Long::sum);
        }
        if (!reservedItems.isEmpty()) {
            if (inventory.tryExchange(reservedItems, Map.of(), itemCapacity())
                == FacilityInventory.ExchangeResult.REJECTED) {
                throw new IllegalStateException(
                    "Operation partial reservation became inconsistent for module " + module.id);
            }
            module.setOperation(
                operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
            markDirty();
        }
        return operationHasFullDeposit(requireOperation(module), requested);
    }

    public boolean flushModuleOperationRefund(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        if (operation.phase() != ModuleOperationPhase.REFUNDING) return false;
        if (operation.plan()
            .spec() == IModuleOperation.DECONSTRUCTION) {
            return flushDeconstructionRefund(module, operation);
        }
        Map<String, Long> remaining = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Long> entry : operation.refundBuffer()
            .entrySet()) {
            ItemStackWrapper item = requireItemKey(entry.getKey(), module);
            long accepted = insert(item, entry.getValue());
            if (accepted > 0L) changed = true;
            long leftover = entry.getValue() - accepted;
            if (leftover > 0L) remaining.put(entry.getKey(), leftover);
        }
        if (!changed) return false;
        if (!remaining.isEmpty()) {
            module.setOperation(operation.withRefundBuffer(remaining));
        } else if (isCompletionRefund(operation)) {
            module.clearOperation();
        } else {
            module.setOperation(operation.finishRefunding());
        }
        markDirty();
        return true;
    }

    private boolean flushDeconstructionRefund(ModuleInstance module, ModuleOperationState operation) {
        Map<ItemStackWrapper, Long> requested = resolveRefundItems(module, operation.refundBuffer());
        FacilityInventory.ReturnItemsResult returned = inventory.returnItems(requested, itemCapacity());
        if (!returned.changed()) return false;
        if (returned.completed()) {
            finalizeModuleRemoval(module);
        } else {
            module.setOperation(operation.withRefundBuffer(toItemKeys(returned.remaining())));
            markDirty();
        }
        return true;
    }

    private ModuleOperationState requireWaitingOperation(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        if (operation.phase() != ModuleOperationPhase.WAITING_FOR_MATERIALS) {
            throw new IllegalStateException(
                "Module " + module.id + " operation must be WAITING_FOR_MATERIALS, got " + operation.phase());
        }
        return operation;
    }

    private ModuleOperationState requireOperation(ModuleInstance module) {
        if (module == null) {
            throw new IllegalArgumentException("Module operation requested for null module");
        }
        ModuleOperationState operation = module.operationOrNull();
        if (operation == null) {
            throw new IllegalStateException("Module " + module.id + " has no active operation");
        }
        return operation;
    }

    private Map<ItemStackWrapper, Long> requireMaterialCost(Map<ItemStackWrapper, Long> materialCost) {
        if (materialCost == null) {
            throw new IllegalArgumentException("Operation material cost must not be null");
        }
        for (Map.Entry<ItemStackWrapper, Long> entry : materialCost.entrySet()) {
            ItemStackWrapper item = entry.getKey();
            Long amount = entry.getValue();
            if (item == null) {
                throw new IllegalArgumentException("Operation material cost contains null item");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "Operation material cost amount must be > 0 for " + item.toKey() + ", got " + amount);
            }
        }
        return materialCost;
    }

    private ItemStackWrapper requireItemKey(String itemKey, ModuleInstance module) {
        ItemStackWrapper item = ItemStackWrapper.fromKey(itemKey);
        if (item == null) {
            throw new IllegalStateException("Module " + module.id + " operation has unresolvable item key " + itemKey);
        }
        return item;
    }

    private static Map<String, Long> mergeAmounts(Map<String, Long> base, Map<String, Long> added) {
        Map<String, Long> merged = new java.util.LinkedHashMap<>(base);
        for (Map.Entry<String, Long> entry : added.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        return merged;
    }

    private void markInventoryDelta(InventoryKey item, long delta) {
        if (item == null || delta == 0L) return;
        if (settlingUpkeep) {
            upkeepInventoryChanged = true;
            return;
        }
        markDirty();
    }

    public long getEnergyStored() {
        return energyStored;
    }

    public void setEnergyStored(long energyStored) {
        long clamped = Math.clamp(energyStored, 0, energyCapacity());
        if (this.energyStored == clamped) return;
        this.energyStored = clamped;
        markDirty();
    }

    public long energyCapacity() {
        long capacity = BASE_ENERGY_CAPACITY;
        for (CapacityCluster cluster : layoutCache.getCapacityClusters(FacilityModuleKind.BATTERY)) {
            capacity += cluster.effectiveCapacity();
        }
        return capacity;
    }

    public void addEnergy(long delta) {
        setEnergyStored(energyStored + delta);
    }

    public boolean tryConsumeEnergy(long amount) {
        if (energyStored < amount) return false;
        setEnergyStored(energyStored - amount);
        return true;
    }

    @Override
    public boolean hasMiningCapability() {
        for (ModuleInstance m : modules) {
            if (m.kind() == FacilityModuleKind.MINER && m.isOperational()) return true;
        }
        return false;
    }

    @Override
    public boolean hasProductionCapability() {
        for (ModuleInstance m : modules) {
            FacilityModuleKind k = m.kind();
            if (k == FacilityModuleKind.HAMMER && m.isOperational()) return true;
        }
        return false;
    }

    @Override
    public WarningPriority warningPriority() {
        if (!isOperational()) return WarningPriority.NONE;
        if (energyStored <= 0L) return WarningPriority.NO_POWER;
        for (ModuleInstance m : modules) {
            if (m.isOperational()) return WarningPriority.NONE;
        }
        return WarningPriority.IDLE;
    }

    public void tick() {
        ticks++;
        if (ticks % UPKEEP_INTERVAL_TICKS == 0L) {
            settleUpkeep();
        }
        for (int i = 0; i < modules.size();) {
            ModuleInstance module = modules.get(i);
            boolean moduleTickBlocked = tickModuleOperation(module);
            if (i < modules.size() && modules.get(i) == module
                && !moduleTickBlocked
                && module.blocking() != BlockingReason.UPKEEP_SHORTAGE) {
                module.tick(this);
            }
            if (i < modules.size() && modules.get(i) == module) i++;
        }
    }

    private boolean tickModuleOperation(ModuleInstance module) {
        ModuleOperationState operation = module.operationOrNull();
        if (operation == null) return false;
        return switch (operation.phase()) {
            case WAITING_FOR_MATERIALS -> tryBeginModuleOperation(module, operation);
            case BUILDING -> {
                tickBuildingOperation(module, operation);
                yield true;
            }
            case REFUNDING -> {
                flushModuleOperationRefund(module);
                yield true;
            }
            case COMPLETE -> {
                applyCompletedModuleOperation(module, operation);
                yield false;
            }
            case CANCELLED -> {
                module.clearOperation();
                markDirty();
                yield false;
            }
        };
    }

    private boolean tryBeginModuleOperation(ModuleInstance module, ModuleOperationState operation) {
        Map<ItemStackWrapper, Long> materialCost = operation.plan()
            .materialCost();
        boolean hasFullCost = operation.reserveItems() ? tryReserveAvailableOperationMaterials(module, materialCost)
            : tryReserveOperationMaterials(module, materialCost);
        if (!hasFullCost) {
            return false;
        }
        module.setOperation(
            module.operationOrNull()
                .beginBuilding());
        markDirty();
        return true;
    }

    private void tickBuildingOperation(ModuleInstance module, ModuleOperationState operation) {
        int progressTicks = featureModifiedBuildProgressTicks(module);
        ModuleOperationState next = operation;
        for (int i = 0; i < progressTicks && next.phase() == ModuleOperationPhase.BUILDING; i++) {
            next = next.tickBuilding();
        }
        module.setOperation(next);
        markDirty();
        if (next.phase() == ModuleOperationPhase.COMPLETE) {
            applyCompletedModuleOperation(module, next);
        }
    }

    private int featureModifiedBuildProgressTicks(ModuleInstance module) {
        int modifierPercent = buildSpeedModifierPercent(module);
        if (modifierPercent == 0) return 1;

        if (modifierPercent > 0) {
            int extraProgressPercent = Math.min(100, modifierPercent);
            return shouldApplyPercent(extraProgressPercent) ? 2 : 1;
        }

        int progressPercent = Math.max(20, 100 + modifierPercent);
        return shouldApplyPercentFromCurrentTick(progressPercent) ? 1 : 0;
    }

    private boolean shouldApplyPercent(int percent) {
        return Math.floorMod(ticks * percent, 100) < percent;
    }

    private boolean shouldApplyPercentFromCurrentTick(int percent) {
        return Math.floorMod((ticks - 1) * percent, 100) < percent;
    }

    public int buildSpeedModifierPercent(ModuleInstance module) {
        return featureModifiers(module).buildSpeedModifierPercent();
    }

    public int upkeepReductionPercent(ModuleInstance module) {
        return 100 - upkeepMultiplierPercent(module);
    }

    public int upkeepMultiplierPercent(ModuleInstance module) {
        return featureModifiers(module).upkeepMultiplierPercent();
    }

    public UpkeepDemand effectiveUpkeepDemand(ModuleInstance module, UpkeepDemand baseDemand) {
        if (baseDemand == null || baseDemand.isEmpty()) return UpkeepDemand.EMPTY;
        return baseDemand.multiplyPercent(upkeepMultiplierPercent(module));
    }

    public long effectivePowerDrawEuPerTick(ModuleInstance module) {
        long powerDraw = module.powerDrawEuPerTick();
        if (powerDraw <= 0L) {
            return powerDraw;
        }
        int multiplier = featureModifiers(module).powerDrawMultiplierPercent();
        return (powerDraw * multiplier + 99L) / 100L;
    }

    public ModuleFeatureModifiers featureModifiers(ModuleInstance module) {
        if (module == null || module.anchorOrNull() == null) return ModuleFeatureModifiers.EMPTY;
        refreshFeatureModifierCache();
        return featureModifiersByModule.computeIfAbsent(module.id, ignored -> computeFeatureModifiers(module));
    }

    private void refreshFeatureModifierCache() {
        long layoutVersion = layout != null ? layout.version() : Long.MIN_VALUE;
        if (featureModifiersLayoutVersion == layoutVersion
            && featureModifiersStationFeatureSalt == stationFeatureSalt) {
            return;
        }
        featureModifiersByModule.clear();
        featureModifiersLayoutVersion = layoutVersion;
        featureModifiersStationFeatureSalt = stationFeatureSalt;
    }

    private ModuleFeatureModifiers computeFeatureModifiers(ModuleInstance module) {
        Map<PlanetaryFeatureKey, Integer> counts = new LinkedHashMap<>();
        StationTileCoord[] tiles = module.tiles();
        for (StationTileCoord tile : tiles) {
            for (PlanetaryFeatureKey feature : planetaryFeaturesAt(tile)) {
                counts.merge(feature, 1, Integer::sum);
            }
        }
        ModuleFeatureModifierBuilder builder = new ModuleFeatureModifierBuilder();
        for (Map.Entry<PlanetaryFeatureKey, Integer> entry : counts.entrySet()) {
            PlanetaryFeature feature = PlanetaryFeatureRegistry.feature(entry.getKey());
            if (feature == null) continue;
            feature.applyModuleModifiers(
                new FeatureModuleContext(module, entry.getKey(), entry.getValue(), tiles.length),
                builder);
        }
        for (ModuleInstance source : modules) {
            source.areaEffects()
                .forEach(effect -> effect.apply(source, module, builder));
        }
        return builder.build(counts);
    }

    private void applyCompletedModuleOperation(ModuleInstance module, ModuleOperationState operation) {
        ModuleOperationPlan plan = operation.plan();
        applyOperationTarget(module, plan);
        Map<String, Long> completionRefund = completionRefund(module, operation);
        if (completionRefund.isEmpty()) {
            module.clearOperation();
        } else {
            module.setOperation(operation.refundAfterCompletion(completionRefund));
        }
        markDirty();
    }

    private void applyOperationTarget(ModuleInstance module, ModuleOperationPlan plan) {
        ModuleTier oldTier = module.tier();
        module.component()
            .applyOperationTarget(plan.spec(), module);
        if (module.tier() != oldTier) {
            layoutCache.applyMutation(MutationKind.SET_TIER, module.kind(), module);
        }
    }

    private Map<String, Long> completionRefund(ModuleInstance module, ModuleOperationState operation) {
        if (operation.plan()
            .voidCompletionRefund()) {
            return Map.of();
        }
        int refundPercent = operation.plan()
            .completionRefundPercent();
        if (refundPercent <= 0) return Map.of();
        Map<String, Long> refund = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : operation.plan()
            .completionRefundCost()
            .entrySet()) {
            long amount = entry.getValue() * refundPercent / 100L;
            if (amount <= 0L) continue;
            refund.merge(
                entry.getKey()
                    .toKey(),
                amount,
                Long::sum);
        }
        return refund;
    }

    private static boolean operationHasFullDeposit(ModuleOperationState operation,
        Map<ItemStackWrapper, Long> requested) {
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            if (operation.depositedResources()
                .getOrDefault(
                    material.getKey()
                        .toKey(),
                    0L)
                < material.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompletionRefund(ModuleOperationState operation) {
        return operation.elapsedBuildTicks() >= operation.plan()
            .buildTicks();
    }

    public long itemAmount(ItemStackWrapper item) {
        return inventory.amount(item);
    }

    public long fluidAmount(FluidKey fluid) {
        return inventory.amount(fluid);
    }

    public long amount(InventoryKey resource) {
        return inventory.amount(resource);
    }

    public long storedItemAmount() {
        return inventory.totalItems();
    }

    public Map<FluidKey, Long> fluidAmounts() {
        return inventory.fluidAmountsSnapshot();
    }

    public boolean allowsInsertion(InventoryKey resource) {
        return inventory.allowsInsertion(resource);
    }

    public long insert(InventoryKey resource, long requested) {
        long applied = inventory.insert(resource, requested, itemCapacity());
        if (applied != 0L) markInventoryDelta(resource, applied);
        return applied;
    }

    public long extract(InventoryKey resource, long requested) {
        long applied = inventory.extract(resource, requested);
        if (applied != 0L) markInventoryDelta(resource, -applied);
        return applied;
    }

    public boolean tryExchange(Map<? extends InventoryKey, Long> inputs, Map<? extends InventoryKey, Long> outputs) {
        FacilityInventory.ExchangeResult result = inventory.tryExchange(inputs, outputs, itemCapacity());
        if (result == FacilityInventory.ExchangeResult.REJECTED) return false;
        if (result == FacilityInventory.ExchangeResult.CHANGED) markDirty();
        return true;
    }

    public long remainingItemCapacity() {
        return Math.max(0L, itemCapacity() - storedItemAmount());
    }

    public boolean isItemInventoryFull() {
        return remainingItemCapacity() <= 0L;
    }

    public long itemCapacity() {
        long capacity = BASE_ITEM_CAPACITY;
        for (CapacityCluster cluster : layoutCache.getCapacityClusters(FacilityModuleKind.STORAGE)) {
            capacity += cluster.effectiveCapacity();
        }
        return capacity;
    }

    private long projectedItemCapacityAfterRemoving(ModuleInstance.ID moduleId) {
        long capacity = BASE_ITEM_CAPACITY;
        for (CapacityCluster cluster : layoutCache.getCapacityClustersExcluding(FacilityModuleKind.STORAGE, moduleId)) {
            capacity = Math.addExact(capacity, cluster.effectiveCapacity());
        }
        return capacity;
    }

    private DeconstructionRefund deconstructionRefund(ModuleInstance module) {
        Map<ItemStackWrapper, Long> byItem = new LinkedHashMap<>();
        Map<String, Long> byKey = new LinkedHashMap<>();
        long total = 0L;
        try {
            for (Map.Entry<ItemStack, Long> entry : module.getConstructionCost()
                .entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0L) return null;
                if (entry.getValue() == 0L) continue;
                ItemStackWrapper item = ItemStackWrapper.of(entry.getKey());
                if (item == null) return null;
                byItem.merge(item, entry.getValue(), Math::addExact);
                byKey.merge(item.toKey(), entry.getValue(), Math::addExact);
                total = Math.addExact(total, entry.getValue());
            }
        } catch (RuntimeException invalid) {
            return null;
        }
        return new DeconstructionRefund(
            Collections.unmodifiableMap(new LinkedHashMap<>(byItem)),
            Collections.unmodifiableMap(new LinkedHashMap<>(byKey)),
            total);
    }

    private Map<ItemStackWrapper, Long> resolveRefundItems(ModuleInstance module, Map<String, Long> byKey) {
        Map<ItemStackWrapper, Long> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : byKey.entrySet()) {
            resolved.put(requireItemKey(entry.getKey(), module), entry.getValue());
        }
        return resolved;
    }

    private static Map<String, Long> toItemKeys(Map<ItemStackWrapper, Long> byItem) {
        Map<String, Long> byKey = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : byItem.entrySet()) {
            byKey.put(
                entry.getKey()
                    .toKey(),
                entry.getValue());
        }
        return byKey;
    }

    private record DeconstructionRefund(Map<ItemStackWrapper, Long> byItem, Map<String, Long> byKey, long total) {}

    /// ----------------------------------------------------------------------------------
    /// Persistence helpers
    /// ----------------------------------------------------------------------------------

    public Map<ItemStackWrapper, Long> itemSnapshot() {
        return inventory.itemSnapshot();
    }

    public Map<InventoryKey, Long> inventorySnapshot() {
        return inventory.amountsSnapshot();
    }

    public Map<InventoryKey, InventoryBounds> boundsSnapshot() {
        return inventory.boundsSnapshot();
    }

    public void restoreInventory(Map<? extends InventoryKey, Long> resources) {
        inventory.restoreSnapshot(resources);
    }

    public void restoreBounds(Map<? extends InventoryKey, InventoryBounds> bounds) {
        inventory.restoreBounds(bounds);
    }

    public void clear() {
        inventory.clear();
    }

    public void addFilter(String key, boolean item) {
        if (inventory.addFilter(key, item)) markDirty();
    }

    public void removeFilter(String key, boolean item) {
        if (inventory.removeFilter(key, item)) markDirty();
    }

    public Map<Boolean, List<String>> filtersSnapshot() {
        return inventory.filtersSnapshot();
    }

    public void setFilters(List<String> filters, boolean item) {
        if (inventory.setFilters(filters, item)) markDirty();
    }

    public void clearFilters(boolean item) {
        if (inventory.clearFilters(item)) markDirty();
    }
}
