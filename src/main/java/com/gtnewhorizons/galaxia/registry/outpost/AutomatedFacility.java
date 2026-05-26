package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
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

public final class AutomatedFacility extends CelestialAsset {

    private static final Logger LOG = LogManager.getLogger(AutomatedFacility.class);

    private final Map<ItemStackWrapper, Long> amounts = new LinkedHashMap<>();
    private final Map<FluidKey, Long> fluidAmounts = new LinkedHashMap<>();

    private final List<ModuleInstance> modules;
    private final StationLayout layout;
    private final LayoutCacheBundle layoutCache;
    private final SettingsGroupRegistry settingsGroups;

    private final UpkeepLedger upkeepLedger;
    private UpkeepSettlement.Credits upkeepCredits = UpkeepSettlement.Credits.empty();

    private long stationFeatureSalt;
    private final Map<ModuleInstance.ID, ModuleFeatureModifiers> featureModifiersByModule = new LinkedHashMap<>();
    private long featureModifiersLayoutVersion = Long.MIN_VALUE;
    private long featureModifiersStationFeatureSalt = Long.MIN_VALUE;

    private long energyStored;
    private final Set<ModuleInstance.ID> dirtyModuleIds = new HashSet<>();
    private final Set<ModuleInstance.ID> dirtyRemovedIds = new HashSet<>();
    private final Map<InventoryKey, Long> dirtyInventoryDeltas = new LinkedHashMap<>();
    private final Set<UUID> syncedPlayerIds = new HashSet<>();
    private final Set<String> dirtyMinerVoidChanceOreKeys = new HashSet<>();
    private long ticks;

    private final ResourceFilter<ItemStackWrapper> itemFilter = ResourceFilter.forItems();
    private final ResourceFilter<FluidKey> fluidFilter = ResourceFilter.forFluids();

    public static final long MAX_ENERGY = 8_000_000L;
    public static final long BASE_ITEM_CAPACITY = 1000L;
    public static final int UPKEEP_INTERVAL_TICKS = 20 * 60;

    public AutomatedFacility(CelestialAsset.ID assetId, CelestialObjectId celestialBodyId, Kind kind, Status status) {
        super(assetId, celestialBodyId, kind, status, null);
        if (kind != Kind.AUTOMATED_OUTPOST && kind != Kind.AUTOMATED_STATION) {
            throw new IllegalArgumentException(
                "AutomatedFacility kind must be AUTOMATED_OUTPOST or AUTOMATED_STATION, got: " + kind);
        }
        this.modules = new ArrayList<>();
        this.layout = ownsStationLayout(kind) ? new StationLayout() : null;
        this.layoutCache = new LayoutCacheBundle(layout);
        this.settingsGroups = new SettingsGroupRegistry();
        this.upkeepLedger = new UpkeepLedger();
        this.stationFeatureSalt = createStationFeatureSalt(assetId, celestialBodyId);
        this.energyStored = 0;
        this.ticks = 0;
    }

    private static long createStationFeatureSalt(CelestialAsset.ID assetId, CelestialObjectId bodyId) {
        long value = assetId == null || assetId.id() == null ? 0L
            : assetId.id()
                .getMostSignificantBits()
                ^ assetId.id()
                    .getLeastSignificantBits();
        value ^= bodyId == null ? 0L : ((long) bodyId.ordinal() << 32);
        value ^= 0xD1B54A32D192ED03L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value;
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
        return settingsGroups;
    }

    public long stationFeatureSalt() {
        return stationFeatureSalt;
    }

    public void setStationFeatureSalt(long stationFeatureSalt) {
        this.stationFeatureSalt = stationFeatureSalt;
        featureModifiersByModule.clear();
        featureModifiersStationFeatureSalt = Long.MIN_VALUE;
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
        return GalaxiaCelestialAPI.get(planetaryAnchorBodyId)
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
        for (SettingsGroup group : settingsGroups.groups()
            .values()) {
            applyRecipeSettingsToGroup(group);
        }
    }

    public void syncRecipeSettingsGroupsFromModules() {
        for (SettingsGroup group : settingsGroups.groups()
            .values()) {
            if (!(group.settings() instanceof RecipeModuleSettings recipeSettings)) continue;
            for (StationTileCoord coord : group.members()) {
                ModuleInstance module = moduleAtAnchor(coord);
                if (module != null && module.component() instanceof IRecipeModule recipeModule) {
                    recipeSettings.setConfig(recipeModule.getRecipeConfig());
                    break;
                }
            }
        }
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
        logisticsConfig.set(item, current.withMinReserve((int) Math.min(Integer.MAX_VALUE, amount)));
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
        if (enabled) {
            long reserve = upkeepReserve(item);
            int minReserve = (int) Math.min(Integer.MAX_VALUE, reserve);
            int orderSize = current == LogisticsResourceConfig.DEFAULT ? 64 : current.orderSize();
            logisticsConfig.set(item, new LogisticsResourceConfig(minReserve, orderSize, true, false));
        } else {
            logisticsConfig.set(
                item,
                current.withImportEnabled(false)
                    .withSupplyEnabled(false));
        }
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

    @Override
    public boolean isAboveLow(InventoryKey key, long amount) {
        return (resourceAmount(key) - amount) >= effectiveLowerBound(key);
    }

    private long resourceAmount(InventoryKey key) {
        if (key instanceof ItemStackWrapper item) return getItemAmount(item);
        if (key instanceof FluidKey fluid) return getFluidAmount(fluid);
        return 0L;
    }

    public UpkeepSettlement.Credits upkeepCredits() {
        return upkeepCredits;
    }

    public void loadUpkeepCredits(UpkeepSettlement.Credits upkeepCredits) {
        this.upkeepCredits = upkeepCredits == null ? UpkeepSettlement.Credits.empty() : upkeepCredits;
    }

    public UpkeepSettlement.Result settleUpkeep() {
        UpkeepLedger.UpkeepSummary summary = upkeepSummary();
        UpkeepSettlement.Result result = UpkeepSettlement.settle(summary.moduleDemands(), upkeepCredits, this);
        upkeepCredits = result.credits();
        Set<ModuleInstance.ID> demanded = new HashSet<>();
        for (UpkeepLedger.ModuleDemand demand : summary.moduleDemands()) {
            demanded.add(demand.moduleId());
        }
        Set<ModuleInstance.ID> paid = result.paidModuleIds();
        Set<ModuleInstance.ID> unpaid = new HashSet<>(result.unpaidModuleIds());
        for (ModuleInstance module : modules) {
            if (unpaid.contains(module.id)) {
                setModuleUpkeepBlocked(module);
            } else if (paid.contains(module.id) || !demanded.contains(module.id)) {
                clearModuleUpkeepBlocked(module);
            }
        }
        return result;
    }

    private void setModuleUpkeepBlocked(ModuleInstance module) {
        if (module.blocking() == BlockingReason.UPKEEP_SHORTAGE && module.state() == ModuleState.BLOCKED) return;
        module.setBlocking(BlockingReason.UPKEEP_SHORTAGE);
        module.setState(ModuleState.BLOCKED);
        markModuleDirty(module.id);
    }

    private void clearModuleUpkeepBlocked(ModuleInstance module) {
        if (module.blocking() != BlockingReason.UPKEEP_SHORTAGE) return;
        module.setBlocking(BlockingReason.NONE);
        if (module.state() == ModuleState.BLOCKED) {
            module.setState(ModuleState.IDLE);
        }
        markModuleDirty(module.id);
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
        if (FacilityModuleRegistry.get(module.kind())
            .settingsGroups() && module.groupId() == 0) {
            attachToSettingsGroup(module, settingsGroups.create(module.kind(), privateSettingsFor(module)));
        }
        dirtyModuleIds.add(module.id);
        bumpSyncRevision();
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
    }

    public void removeModule(int index) {
        ModuleInstance removed = modules.remove(index);
        if (removed != null) {
            detachFromSettingsGroup(removed);
            dirtyRemovedIds.add(removed.id);
            dirtyModuleIds.remove(removed.id);
            bumpSyncRevision();
            if (layout != null) layout.removeTileForModule(removed.id);
            layoutCache.applyMutation(MutationKind.DECONSTRUCT, removed.kind(), removed);
            markDirty();
        }
    }

    public boolean removeModule(ModuleInstance.ID moduleId) {
        int index = moduleIndex(moduleId);
        if (index < 0) return false;
        removeModule(index);
        return true;
    }

    public int moduleIndex(ModuleInstance.ID moduleId) {
        if (moduleId == null) return -1;
        for (int i = 0; i < modules.size(); i++) {
            if (moduleId.equals(modules.get(i).id)) return i;
        }
        return -1;
    }

    private @Nullable ModuleInstance moduleAtAnchor(StationTileCoord coord) {
        if (coord == null) return null;
        for (ModuleInstance module : modules) {
            if (coord.equals(module.anchorOrNull())) return module;
        }
        return null;
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
        SettingsGroup group = settingsGroups.require(module.groupId(), FacilityModuleKind.MINER);
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
            markSettingsGroupMembersDirty(settingsGroups.require(module.groupId(), FacilityModuleKind.MINER));
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
        SettingsGroup group = settingsGroups.require(module.groupId(), module.kind());
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
            attachToSettingsGroup(module, settingsGroups.create(module.kind(), new RecipeModuleSettings(normalized)));
        }
        SettingsGroup group = settingsGroups.require(module.groupId(), module.kind());
        if (!(group.settings() instanceof RecipeModuleSettings settings)) {
            throw new IllegalStateException(
                "Recipe settings group " + module.groupId() + " has non-recipe settings for module " + module.id);
        }
        settings.setConfig(normalized);
        applyRecipeSettingsToGroup(group);
        markSettingsGroupMembersDirty(group);
    }

    public void copyModuleRuntimeSettings(ModuleInstance source, ModuleInstance target) {
        requireSettingsGroupsSupported(source);
        requireSettingsGroupsSupported(target);
        if (source.kind() != target.kind()) {
            throw new IllegalStateException(
                "Module settings copy target kind mismatch: " + source.kind() + " -> " + target.kind());
        }
        if (source.id.equals(target.id)) {
            throw new IllegalStateException("Module settings copy target must be different from source: " + source.id);
        }
        SettingsGroup sourceGroup = settingsGroups.require(source.groupId(), source.kind());
        source.component()
            .validateSettingsCopyTarget(source, target);
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

    public boolean tryReserveOperationMaterials(ModuleInstance module, Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            if (getItemAmount(material.getKey()) < material.getValue()) return false;
        }
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            long reserved = updateContents(material.getKey(), -material.getValue(), true);
            if (reserved != material.getValue()) {
                throw new IllegalStateException(
                    "Operation material reservation became inconsistent for module " + module.id
                        + ", item="
                        + material.getKey()
                            .toKey());
            }
            deposited.merge(
                material.getKey()
                    .toKey(),
                reserved,
                Long::sum);
        }
        module.setOperation(operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
        markModuleDirty(module.id);
        return true;
    }

    public boolean tryConsumeInventory(ItemStackWrapper item, long amount) {
        if (item == null) return false;
        if (amount <= 0L) return true;
        if (getItemAmount(item) < amount) return false;
        return updateContents(item, -amount, true) == amount;
    }

    public boolean tryConsumeFluid(FluidKey key, long amount) {
        if (key == null) return false;
        if (amount <= 0L) return true;
        if (getFluidAmount(key) < amount) return false;
        return updateContents(key, -amount, true) == amount;
    }

    public boolean tryReserveAvailableOperationMaterials(ModuleInstance module,
        Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            String itemKey = material.getKey()
                .toKey();
            long alreadyDeposited = operation.depositedResources()
                .getOrDefault(itemKey, 0L);
            long remaining = material.getValue() - alreadyDeposited;
            if (remaining <= 0L) continue;
            long available = getItemAmount(material.getKey());
            long reserved = Math.min(available, remaining);
            if (reserved <= 0L) continue;
            long applied = updateContents(material.getKey(), -reserved, true);
            if (applied <= 0L) {
                throw new IllegalStateException(
                    "Operation partial reservation became inconsistent for module " + module.id + ", item=" + itemKey);
            }
            deposited.merge(itemKey, applied, Long::sum);
            changed = true;
        }
        if (changed) {
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
        Map<String, Long> remaining = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Long> entry : operation.refundBuffer()
            .entrySet()) {
            ItemStackWrapper item = requireItemKey(entry.getKey(), module);
            long accepted = updateContents(item, entry.getValue(), true);
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

    public SettingsGroup createSettingsGroupForModule(ModuleInstance module, String displayName) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroups.require(module.groupId(), module.kind());
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
        ModuleSettings settings = copySettings(module);
        detachFromSettingsGroup(module);
        SettingsGroup group = settingsGroups.create(module.kind(), displayName, true, settings);
        attachToSettingsGroup(module, group);
        return group;
    }

    public void renameSettingsGroupForModule(ModuleInstance module, short groupId, String displayName) {
        if (module == null) {
            throw new IllegalArgumentException("renameSettingsGroupForModule: module must not be null");
        }
        requireSettingsGroupsSupported(module);
        SettingsGroup group = settingsGroups.require(groupId, module.kind());
        if (!group.isJoinable()) {
            throw new IllegalStateException("Settings group " + groupId + " is private and cannot be renamed");
        }
        group.setDisplayName(displayName);
        markSettingsGroupMembersDirty(group);
        if (group.members()
            .isEmpty()) {
            bumpSyncRevision();
            markDirty();
        }
    }

    public void assignSettingsGroup(ModuleInstance module, short groupId) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() == groupId) return;
        if (groupId == 0) {
            leaveSettingsGroup(module);
            return;
        }
        SettingsGroup group = settingsGroups.require(groupId, module.kind());
        if (!group.isJoinable()) {
            throw new IllegalStateException("Settings group " + groupId + " is private and cannot be joined");
        }
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, group);
    }

    public void leaveSettingsGroup(ModuleInstance module) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroups.require(module.groupId(), module.kind());
            if (current.members()
                .size() == 1) {
                current.setJoinable(false);
                markModuleDirty(module.id);
                return;
            }
        }
        ModuleSettings settings = copySettings(module);
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, settingsGroups.create(module.kind(), settings));
        markModuleDirty(module.id);
    }

    private void setPrivateMinerSettings(ModuleInstance module, MinerSettings settings) {
        setPrivateModuleSettings(module, settings);
    }

    private void setPrivateModuleSettings(ModuleInstance module, ModuleSettings settings) {
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroups.require(module.groupId(), module.kind());
            if (!current.isJoinable() && current.members()
                .size() == 1) {
                current.setSettings(settings);
                applySettingsToModule(settings, module);
                markModuleDirty(module.id);
                return;
            }
        }
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, settingsGroups.create(module.kind(), settings));
    }

    private ModuleSettings copySettings(ModuleInstance module) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup group = settingsGroups.require(module.groupId(), module.kind());
            return module.component()
                .copySettings(module, group.settings());
        }
        return module.component()
            .createPrivateSettings(module);
    }

    private ModuleSettings privateSettingsFor(ModuleInstance module) {
        requireSettingsGroupsSupported(module);
        return module.component()
            .createPrivateSettings(module);
    }

    private static void requireSettingsGroupsSupported(ModuleInstance module) {
        if (module == null) {
            throw new IllegalArgumentException("Settings group module must not be null");
        }
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            throw new IllegalStateException("Settings groups are not supported for module kind " + module.kind());
        }
    }

    private void attachToSettingsGroup(ModuleInstance module, SettingsGroup group) {
        settingsGroups.require(group.id(), module.kind());
        settingsGroups.addMember(group.id(), module.anchor());
        module.setGroupId(group.id());
        applySettingsToModule(group.settings(), module);
        markModuleDirty(module.id);
    }

    private void detachFromSettingsGroup(ModuleInstance module) {
        if (module.groupId() == 0) return;
        short oldGroupId = module.groupId();
        settingsGroups.removeMember(oldGroupId, module.anchor());
        module.setGroupId((short) 0);
    }

    private void markSettingsGroupMembersDirty(SettingsGroup group) {
        for (StationTileCoord coord : group.members()) {
            for (ModuleInstance module : modules) {
                if (coord.equals(module.anchorOrNull())) {
                    markModuleDirty(module.id);
                }
            }
        }
    }

    private void applyRecipeSettingsToGroup(SettingsGroup group) {
        for (StationTileCoord coord : group.members()) {
            for (ModuleInstance module : modules) {
                if (coord.equals(module.anchorOrNull())) {
                    applySettingsToModule(group.settings(), module);
                }
            }
        }
    }

    private static void applySettingsToModule(ModuleSettings settings, ModuleInstance module) {
        module.component()
            .applySettings(module, settings);
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
        dirtyModuleIds.add(id);
        bumpSyncRevision();
        markDirty();
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || !dirtyModuleIds.isEmpty()
            || !dirtyRemovedIds.isEmpty()
            || !dirtyInventoryDeltas.isEmpty();
    }

    @Override
    public boolean needsFullSyncFor(UUID playerId) {
        return !syncedPlayerIds.contains(playerId);
    }

    @Override
    public void markSyncedFor(UUID playerId) {
        syncedPlayerIds.add(playerId);
    }

    public List<ModuleInstance> drainDirtyModules() {
        List<ModuleInstance> result = new ArrayList<>(dirtyModuleIds.size());
        for (ModuleInstance.ID id : dirtyModuleIds) {
            int idx = moduleIndex(id);
            if (idx >= 0) result.add(modules.get(idx));
        }
        dirtyModuleIds.clear();
        return result;
    }

    public Map<InventoryKey, Long> drainDirtyInventoryDeltas() {
        Map<InventoryKey, Long> result = new LinkedHashMap<>(dirtyInventoryDeltas);
        dirtyInventoryDeltas.clear();
        return result;
    }

    public List<ModuleInstance.ID> drainRemovedIds() {
        List<ModuleInstance.ID> result = new ArrayList<>(dirtyRemovedIds);
        dirtyRemovedIds.clear();
        return result;
    }

    private void markInventoryDelta(InventoryKey item, long delta) {
        if (item == null || delta == 0L) return;
        dirtyInventoryDeltas.merge(item, delta, Long::sum);
        if (dirtyInventoryDeltas.getOrDefault(item, 0L) == 0L) {
            dirtyInventoryDeltas.remove(item);
        }
        bumpSyncRevision();
    }

    public long getEnergyStored() {
        return energyStored;
    }

    public void setEnergyStored(long energyStored) {
        this.energyStored = Math.clamp(energyStored, 0, MAX_ENERGY);
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
        for (ModuleInstance module : modules) {
            boolean moduleTickBlocked = tickModuleOperation(module);
            if (!moduleTickBlocked && module.blocking() != BlockingReason.UPKEEP_SHORTAGE) {
                module.tick(this);
            }
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
        StationTileCoord[] tiles = module.shape()
            .tiles(module.anchor());
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

    @Override
    public Map<ItemStackWrapper, Long> getItemAmounts() {
        return amounts;
    }

    @Override
    public Map<FluidKey, Long> getFluidAmounts() {
        return fluidAmounts;
    }

    @Override
    public long getFreeItemSpace(ItemStackWrapper item) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getFreeFluidSpace(FluidKey fluid) {
        return Long.MAX_VALUE;
    }

    @Override
    public ResourceFilter<ItemStackWrapper> getItemFilter() {
        return itemFilter;
    }

    @Override
    public ResourceFilter<FluidKey> getFluidFilter() {
        return fluidFilter;
    }

    public long updateContents(InventoryKey item, long delta, boolean sync) {
        final long actual = item instanceof ItemStackWrapper ? updateItems((ItemStackWrapper) item, delta)
            : updateFluids((FluidKey) item, delta);
        if (actual != 0L && sync) markInventoryDelta(item, delta > 0 ? actual : -actual);
        return actual;
    }

    public long usedItemInventoryCapacity() {
        return totalItemsStored();
    }

    public long remainingItemInventoryCapacity() {
        return Math.max(0L, totalItemCapacity() - usedItemInventoryCapacity());
    }

    public boolean isItemInventoryFull() {
        return remainingItemInventoryCapacity() <= 0L;
    }

    @Override
    public long totalItemCapacity() {
        long capacity = BASE_ITEM_CAPACITY;
        for (CapacityCluster cluster : layoutCache.getCapacityClusters(FacilityModuleKind.STORAGE)) {
            capacity += cluster.effectiveCapacity();
        }
        return capacity;
    }

    public long usedFluidInventoryCapacity() {
        return totalFluidStored();
    }

    public long remainingFluidInventoryCapacity() {
        return Math.max(0L, totalFluidCapacity() - usedFluidInventoryCapacity());
    }

    public boolean isFluidInventoryFull() {
        return remainingFluidInventoryCapacity() <= 0L;
    }

    @Override
    public long totalFluidCapacity() {
        // TODO
        return Long.MAX_VALUE;
    }

    /// ----------------------------------------------------------------------------------
    /// Persistence helpers
    /// ----------------------------------------------------------------------------------

    public Map<ItemStackWrapper, Long> itemSnapshot() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : amounts.entrySet()) {
            result.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, Long> fluidSnapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> e : fluidAmounts.entrySet()) {
            result.put(
                e.getKey()
                    .fluid()
                    .getName(),
                e.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    public void loadFromSnapshot(Map<ItemStackWrapper, Long> snapshot) {
        amounts.clear();
        for (Map.Entry<ItemStackWrapper, Long> e : snapshot.entrySet()) {
            if (e.getValue() > 0) {
                amounts.put(e.getKey(), e.getValue());
            }
        }
    }

    public void loadFluidSnapshot(Map<String, Long> snapshot) {
        fluidAmounts.clear();
        for (Map.Entry<String, Long> e : snapshot.entrySet()) {
            if (e.getKey() == null || e.getKey()
                .isEmpty() || e.getValue() <= 0) continue;
            FluidKey key = FluidKey.fromName(e.getKey());
            if (key != null) fluidAmounts.put(key, e.getValue());
        }
    }

    @Override
    public void clear() {
        super.clear();
        amounts.clear();
        fluidAmounts.clear();
    }

    public void addFilter(String key, boolean item) {
        if (key == null) return;
        if (item) itemFilter.add(key);
        else fluidFilter.add(key);
        markDirty();
    }

    public void removeFilter(String key, boolean item) {
        if (key == null) return;
        if (item) itemFilter.remove(key);
        else fluidFilter.remove(key);
        markDirty();
    }

    public Map<Boolean, List<String>> filtersSnapshot() {
        Map<Boolean, List<String>> result = new LinkedHashMap<>();
        List<String> itemSerialized = itemFilter.serialize();
        if (!itemSerialized.isEmpty()) result.put(true, itemSerialized);
        List<String> fluidSerialized = fluidFilter.serialize();
        if (!fluidSerialized.isEmpty()) result.put(false, fluidSerialized);
        return result;
    }

    public void setFilters(List<String> filters, boolean item) {
        if (filters == null) return;
        if (item) itemFilter.load(filters);
        else fluidFilter.load(filters);
        markDirty();
    }

    public void clearFilters(boolean item) {
        if (item) itemFilter.clear();
        else fluidFilter.clear();
        markDirty();
    }
}
