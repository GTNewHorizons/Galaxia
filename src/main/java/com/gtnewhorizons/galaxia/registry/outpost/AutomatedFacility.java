package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
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
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifiers;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleState;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleDeconstructionOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.LayoutCacheBundle;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroupRegistry;
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
    private final Map<ItemStackWrapper, InventoryBounds> itemBounds = new LinkedHashMap<>();
    private final Map<FluidKey, InventoryBounds> fluidBounds = new LinkedHashMap<>();

    private final List<ModuleInstance> modules;
    private final StationLayout layout;
    private final LayoutCacheBundle layoutCache;
    private final FacilitySettingsGroupState settingsGroupState;

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

    public static final long MAX_ENERGY = 8_000_000L;
    public static final long BASE_ITEM_CAPACITY = 1000L;
    public static final int UPKEEP_INTERVAL_TICKS = 20 * 60;

    public AutomatedFacility(CelestialAsset.ID assetId, CelestialObjectKey celestialBodyKey, Kind kind, Status status) {
        super(assetId, celestialBodyKey, kind, status, null);
        if (kind != Kind.AUTOMATED_OUTPOST && kind != Kind.AUTOMATED_STATION) {
            throw new IllegalArgumentException(
                "AutomatedFacility kind must be AUTOMATED_OUTPOST or AUTOMATED_STATION, got: " + kind);
        }
        this.modules = new ArrayList<>();
        this.layout = ownsStationLayout(kind) ? new StationLayout() : null;
        this.layoutCache = new LayoutCacheBundle(layout);
        this.settingsGroupState = new FacilitySettingsGroupState();
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

    public SettingsGroupRegistry settingsGroups() {
        return settingsGroupState.registry();
    }

    public long stationFeatureSalt() {
        return stationFeatureSalt;
    }

    public void setStationFeatureSalt(long stationFeatureSalt) {
        if (this.stationFeatureSalt == stationFeatureSalt) return;
        this.stationFeatureSalt = stationFeatureSalt;
        featureModifiersByModule.clear();
        featureModifiersStationFeatureSalt = Long.MIN_VALUE;
        markStateChanged();
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

    public void applySettingsGroupsToModules() {
        settingsGroupState.applyGroupsToModules(modules);
    }

    public void syncRecipeSettingsGroupsFromModules() {
        settingsGroupState.syncRecipeGroupsFromModules(modules);
    }

    public LayoutCacheBundle layoutCache() {
        return layoutCache;
    }

    public UpkeepLedger.UpkeepSummary upkeepSummary() {
        return upkeepLedger.summary(this);
    }

    public void setUpkeepReserve(ItemStackWrapper item, long amount) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        if (amount < 0L) {
            throw new IllegalArgumentException("upkeep reserve must be >= 0");
        }
        LogisticsResourceConfig current = logisticsConfig.get(item);
        LogisticsResourceConfig updated = current.withMinReserve((int) Math.min(Integer.MAX_VALUE, amount));
        if (updated.equals(current)) return;
        logisticsConfig.set(item, updated);
        markStateChanged();
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

    public void setUpkeepAutoOrder(ItemStackWrapper item, boolean enabled) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }
        LogisticsResourceConfig current = logisticsConfig.get(item);
        LogisticsResourceConfig updated;
        if (enabled) {
            long reserve = upkeepReserve(item);
            int minReserve = (int) Math.min(Integer.MAX_VALUE, reserve);
            int orderSize = current == LogisticsResourceConfig.DEFAULT ? 64 : current.orderSize();
            updated = new LogisticsResourceConfig(minReserve, orderSize, true, false);
        } else {
            updated = current.withImportEnabled(false)
                .withSupplyEnabled(false);
        }
        if (updated.equals(current)) return;
        logisticsConfig.set(item, updated);
        markStateChanged();
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
        return (resourceAmount(key) - amount) >= effectiveLowerBound(key);
    }

    public boolean isBelowUpper(InventoryKey key) {
        return resourceAmount(key) < getBound(key).upperOrDefault();
    }

    private long resourceAmount(InventoryKey key) {
        return inventory.amount(key);
    }

    private <T extends InventoryKey> Map<T, InventoryBounds> boundsFor(T key) {
        return key instanceof ItemStackWrapper ? (Map<T, InventoryBounds>) itemBounds
            : (Map<T, InventoryBounds>) fluidBounds;
    }

    public boolean hasLowerBound(InventoryKey key) {
        if (key == null) return false;
        return getBound(key).hasLow();
    }

    public boolean hasUpperBound(InventoryKey key) {
        if (key == null) return false;
        return getBound(key).hasUpper();
    }

    public InventoryBounds getBound(InventoryKey key) {
        if (key == null) return InventoryBounds.invalid();
        return boundsFor(key).getOrDefault(key, InventoryBounds.invalid());
    }

    public void setBound(InventoryKey key, long low, long upper) {
        if (key == null) return;
        boundsFor(key).put(key, new InventoryBounds(low, upper));
    }

    public void setBound(InventoryKey key, long amount, boolean low) {
        if (key == null) return;
        InventoryBounds current = getBound(key);
        InventoryBounds updated = low ? new InventoryBounds(amount, current.upper())
            : new InventoryBounds(current.low(), amount);
        boundsFor(key).put(key, updated);
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
        boundsFor(key).put(key, updated);
        return true;
    }

    public boolean clearBound(InventoryKey key) {
        if (key == null) return false;
        return boundsFor(key).remove(key) != null;
    }

    public boolean clearBound(InventoryKey key, boolean low) {
        if (key == null) return false;
        Map<InventoryKey, InventoryBounds> bounds = boundsFor(key);
        InventoryBounds current = bounds.get(key);
        if (current == null || (low ? !current.hasLow() : !current.hasUpper())) return false;
        if (low && current.hasUpper()) {
            bounds.put(key, InventoryBounds.upperBound(current.upper()));
        } else if (!low && current.hasLow()) {
            bounds.put(key, InventoryBounds.lowBound(current.low()));
        } else {
            bounds.remove(key);
        }
        return true;
    }

    public <T extends InventoryKey> Map<T, InventoryBounds> getBounds(boolean items) {
        Map<T, InventoryBounds> bounds = items ? (Map<T, InventoryBounds>) itemBounds
            : (Map<T, InventoryBounds>) fluidBounds;
        return Collections.unmodifiableMap(bounds);
    }

    public void markInventoryBoundDelta(BoundKind kind, InventoryKey resource, boolean present, long amount) {
        if (kind == null || resource == null) return;
        markStateChanged();
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
            if (!settlementCalculated && upkeepInventoryChanged) markStateChanged();
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
            markStateChanged();
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
        settingsGroupState.attachPrivateGroupIfSupported(module, this::markModuleDirty);
        bumpStateRevision();
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
        int index = moduleIndex(moduleId);
        if (index < 0) return DeconstructionResult.NOT_FOUND;
        ModuleInstance module = modules.get(index);
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
            markModuleDirty(module.id);
            SatelliteNetworkService.refreshFacilityEndpoints(this);
        }
        return DeconstructionResult.ACCEPTED;
    }

    private void finalizeModuleRemoval(ModuleInstance module) {
        if (!modules.remove(module)) return;
        settingsGroupState.detach(module);
        if (layout != null) layout.removeTileForModule(module.id);
        layoutCache.applyMutation(MutationKind.DECONSTRUCT, module.kind(), module);
        bumpStateRevision();
        markDirty();
        SatelliteNetworkService.refreshFacilityEndpoints(this);
    }

    public int moduleIndex(ModuleInstance.ID moduleId) {
        if (moduleId == null) return -1;
        for (int i = 0; i < modules.size(); i++) {
            if (moduleId.equals(modules.get(i).id)) return i;
        }
        return -1;
    }

    public void clearModules() {
        modules.clear();
        markDirty();
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
        if (module.groupId() == 0) {
            throw new IllegalStateException("Miner module " + module.id + " has no settings group");
        }
        SettingsGroup group = settingsGroupState.registry()
            .require(module.groupId(), FacilityModuleKind.MINER);
        if (!(group.settings() instanceof MinerSettings settings)) {
            throw new IllegalStateException(
                "Miner settings group " + module.groupId() + " has non-miner settings for module " + module.id);
        }
        return settings;
    }

    public boolean isMinerOreBlacklisted(ModuleInstance module, String oreKey) {
        return minerSettings(module).isOreBlacklisted(oreKey);
    }

    public void setMinerOreBlacklisted(ModuleInstance module, String oreKey, boolean blacklisted) {
        if (minerSettings(module).setOreBlacklisted(oreKey, blacklisted)) {
            settingsGroupState.markMembersDirty(
                settingsGroupState.registry()
                    .require(module.groupId(), FacilityModuleKind.MINER),
                modules,
                this::markModuleDirty);
        }
    }

    public RecipeConfig recipeConfig(ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) {
            throw new IllegalStateException("Recipe config requested for non-recipe module " + module.id);
        }
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            RecipeConfig config = recipeModule.getRecipeConfig();
            return config != null ? config : RecipeConfig.empty();
        }
        if (module.groupId() == 0) {
            throw new IllegalStateException("Recipe module " + module.id + " has no settings group");
        }
        SettingsGroup group = settingsGroupState.registry()
            .require(module.groupId(), module.kind());
        if (!(group.settings() instanceof RecipeModuleSettings settings)) {
            throw new IllegalStateException(
                "Recipe settings group " + module.groupId() + " has non-recipe settings for module " + module.id);
        }
        RecipeConfig config = settings.config();
        return config != null ? config : RecipeConfig.empty();
    }

    public void setRecipeConfig(ModuleInstance module, RecipeConfig config) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) {
            throw new IllegalStateException("Recipe config update requested for non-recipe module " + module.id);
        }
        RecipeConfig normalized = RecipeModuleSettings.copyConfig(config);
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            recipeModule.setRecipeConfig(normalized);
            markModuleDirty(module.id);
            return;
        }
        if (module.groupId() == 0) {
            settingsGroupState.attach(
                module,
                settingsGroupState.registry()
                    .create(module.kind(), new RecipeModuleSettings(normalized)),
                this::markModuleDirty);
        }
        SettingsGroup group = settingsGroupState.registry()
            .require(module.groupId(), module.kind());
        if (!(group.settings() instanceof RecipeModuleSettings settings)) {
            throw new IllegalStateException(
                "Recipe settings group " + module.groupId() + " has non-recipe settings for module " + module.id);
        }
        settings.setConfig(normalized);
        settingsGroupState.applySettingsToGroup(group, modules);
        settingsGroupState.markMembersDirty(group, modules, this::markModuleDirty);
    }

    public boolean canCopyModuleRuntimeSettings(ModuleInstance source, ModuleInstance target) {
        try {
            validateModuleRuntimeSettingsCopy(source, target);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void copyModuleRuntimeSettings(ModuleInstance source, ModuleInstance target) {
        SettingsGroup sourceGroup = validateModuleRuntimeSettingsCopy(source, target);
        if (sourceGroup.isJoinable()) {
            assignSettingsGroup(target, sourceGroup.id());
        } else {
            setPrivateModuleSettings(
                target,
                source.component()
                    .copySettings(source, sourceGroup.settings()));
        }
        source.component()
            .afterSettingsCopied(source, target);
        markModuleDirty(target.id);
    }

    private SettingsGroup validateModuleRuntimeSettingsCopy(ModuleInstance source, ModuleInstance target) {
        FacilitySettingsGroupState.requireSupported(source);
        FacilitySettingsGroupState.requireSupported(target);
        if (source.kind() != target.kind()) {
            throw new IllegalStateException(
                "Module settings copy target kind mismatch: " + source.kind() + " -> " + target.kind());
        }
        if (source.id.equals(target.id)) {
            throw new IllegalStateException("Module settings copy target must be different from source: " + source.id);
        }
        SettingsGroup sourceGroup = settingsGroupState.registry()
            .require(source.groupId(), source.kind());
        source.component()
            .validateSettingsCopyTarget(source, target);
        return sourceGroup;
    }

    public boolean tryReserveOperationMaterials(ModuleInstance module, Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        if (requested.isEmpty()) return true;
        if (inventory.tryExchange(new InventoryExchange(requested, Map.of(), Map.of(), Map.of()), itemCapacity())
            == FacilityInventory.ExchangeResult.REJECTED) {
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
        markModuleDirty(module.id);
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
            if (inventory
                .tryExchange(new InventoryExchange(reservedItems, Map.of(), Map.of(), Map.of()), itemCapacity())
                == FacilityInventory.ExchangeResult.REJECTED) {
                throw new IllegalStateException(
                    "Operation partial reservation became inconsistent for module " + module.id);
            }
            module.setOperation(
                operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
            markModuleDirty(module.id);
        }
        return operationHasFullDeposit(requireOperation(module), requested);
    }

    public void cancelModuleOperation(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        module.setOperation(operation.cancel());
        markModuleDirty(module.id);
    }

    public void applyCreativeModuleOperation(ModuleInstance module, ModuleOperationPlan plan) {
        if (module == null) {
            throw new IllegalArgumentException("applyCreativeModuleOperation: module must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("applyCreativeModuleOperation: plan must not be null for " + module.id);
        }
        ModuleOperationState existingOperation = module.operationOrNull();
        if (existingOperation != null && !existingOperation.phase()
            .isTerminal()) {
            if (!existingOperation.depositedResources()
                .isEmpty()
                || !existingOperation.refundBuffer()
                    .isEmpty()) {
                throw new IllegalStateException(
                    "Creative operation cannot replace active operation with stored items for module " + module.id);
            }
        }
        applyOperationTarget(module, plan);
        module.clearOperation();
        markModuleDirty(module.id);
    }

    public boolean flushModuleOperationRefund(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        if (operation.phase() != ModuleOperationPhase.REFUNDING) return false;
        if (operation.plan()
            .spec() instanceof ModuleDeconstructionOperation) {
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
        markModuleDirty(module.id);
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
            markModuleDirty(module.id);
        }
        return true;
    }

    public SettingsGroup createSettingsGroupForModule(ModuleInstance module, String displayName) {
        FacilitySettingsGroupState.requireSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroupState.registry()
                .require(module.groupId(), module.kind());
            if (current.members()
                .size() == 1) {
                if (displayName != null) {
                    current.setDisplayName(displayName);
                } else if (current.hasDefaultPrivateDisplayName()) {
                    current.setDisplayName(current.defaultJoinableDisplayName());
                }
                current.setJoinable(true);
                markModuleDirty(module.id);
                return current;
            }
        }
        ModuleSettings settings = settingsGroupState.copySettings(module);
        settingsGroupState.detach(module);
        SettingsGroup group = settingsGroupState.registry()
            .create(module.kind(), displayName, true, settings);
        settingsGroupState.attach(module, group, this::markModuleDirty);
        return group;
    }

    public void renameSettingsGroupForModule(ModuleInstance module, short groupId, String displayName) {
        if (module == null) {
            throw new IllegalArgumentException("renameSettingsGroupForModule: module must not be null");
        }
        FacilitySettingsGroupState.requireSupported(module);
        SettingsGroup group = settingsGroupState.registry()
            .require(groupId, module.kind());
        if (!group.isJoinable()) {
            throw new IllegalStateException("Settings group " + groupId + " is private and cannot be renamed");
        }
        group.setDisplayName(displayName);
        settingsGroupState.markMembersDirty(group, modules, this::markModuleDirty);
        if (group.members()
            .isEmpty()) {
            bumpStateRevision();
            markDirty();
        }
    }

    public void assignSettingsGroup(ModuleInstance module, short groupId) {
        FacilitySettingsGroupState.requireSupported(module);
        if (module.groupId() == groupId) return;
        if (groupId == 0) {
            leaveSettingsGroup(module);
            return;
        }
        SettingsGroup group = settingsGroupState.registry()
            .require(groupId, module.kind());
        if (!group.isJoinable()) {
            throw new IllegalStateException("Settings group " + groupId + " is private and cannot be joined");
        }
        settingsGroupState.detach(module);
        settingsGroupState.attach(module, group, this::markModuleDirty);
    }

    public boolean canJoinSettingsGroup(FacilityModuleKind kind, short groupId) {
        if (kind == null || groupId <= 0) return false;
        SettingsGroup group = settingsGroupState.registry()
            .get(groupId);
        return group != null && group.kind() == kind && group.isJoinable();
    }

    public void leaveSettingsGroup(ModuleInstance module) {
        FacilitySettingsGroupState.requireSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroupState.registry()
                .require(module.groupId(), module.kind());
            if (current.members()
                .size() == 1) {
                current.setJoinable(false);
                markModuleDirty(module.id);
                return;
            }
        }
        ModuleSettings settings = settingsGroupState.copySettings(module);
        settingsGroupState.detach(module);
        settingsGroupState.attach(
            module,
            settingsGroupState.registry()
                .create(module.kind(), settings),
            this::markModuleDirty);
        markModuleDirty(module.id);
    }

    private void setPrivateModuleSettings(ModuleInstance module, ModuleSettings settings) {
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroupState.registry()
                .require(module.groupId(), module.kind());
            if (!current.isJoinable() && current.members()
                .size() == 1) {
                current.setSettings(settings);
                FacilitySettingsGroupState.applySettingsToModule(settings, module);
                markModuleDirty(module.id);
                return;
            }
        }
        settingsGroupState.detach(module);
        settingsGroupState.attach(
            module,
            settingsGroupState.registry()
                .create(module.kind(), settings),
            this::markModuleDirty);
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

    public void markModuleDirty(ModuleInstance.ID id) {
        bumpStateRevision();
        markDirty();
    }

    private void markInventoryDelta(InventoryKey item, long delta) {
        if (item == null || delta == 0L) return;
        if (settlingUpkeep) {
            upkeepInventoryChanged = true;
            return;
        }
        bumpStateRevision();
        markDirty();
    }

    public long getEnergyStored() {
        return energyStored;
    }

    public void setEnergyStored(long energyStored) {
        long clamped = Math.clamp(energyStored, 0, MAX_ENERGY);
        if (this.energyStored == clamped) return;
        this.energyStored = clamped;
        markStateChanged();
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

        LogisticStore.updateSignalsForFacility(this);
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
                markModuleDirty(module.id);
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
        markModuleDirty(module.id);
        return true;
    }

    private void tickBuildingOperation(ModuleInstance module, ModuleOperationState operation) {
        int progressTicks = featureModifiedBuildProgressTicks(module);
        ModuleOperationState next = operation;
        for (int i = 0; i < progressTicks && next.phase() == ModuleOperationPhase.BUILDING; i++) {
            next = next.tickBuilding();
        }
        module.setOperation(next);
        markModuleDirty(module.id);
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
        markModuleDirty(module.id);
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

    public boolean tryExchange(InventoryExchange exchange) {
        FacilityInventory.ExchangeResult result = inventory.tryExchange(exchange, itemCapacity());
        if (result == FacilityInventory.ExchangeResult.REJECTED) return false;
        if (result == FacilityInventory.ExchangeResult.CHANGED) markStateChanged();
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

    public Map<String, Long> fluidSnapshot() {
        return inventory.fluidSnapshot();
    }

    public void loadFromSnapshot(Map<ItemStackWrapper, Long> snapshot) {
        inventory.loadItemSnapshot(snapshot);
    }

    public void loadFluidSnapshot(Map<String, Long> snapshot) {
        inventory.loadFluidSnapshot(snapshot);
    }

    public void clear() {
        itemBounds.clear();
        fluidBounds.clear();
        inventory.clear();
    }

    public void addFilter(String key, boolean item) {
        if (inventory.addFilter(key, item)) markStateChanged();
    }

    public void removeFilter(String key, boolean item) {
        if (inventory.removeFilter(key, item)) markStateChanged();
    }

    public Map<Boolean, List<String>> filtersSnapshot() {
        return inventory.filtersSnapshot();
    }

    public void setFilters(List<String> filters, boolean item) {
        if (inventory.setFilters(filters, item)) markStateChanged();
    }

    public void clearFilters(boolean item) {
        if (inventory.clearFilters(item)) markStateChanged();
    }
}
