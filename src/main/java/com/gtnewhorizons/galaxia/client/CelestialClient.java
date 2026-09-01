package com.gtnewhorizons.galaxia.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.ClientStateLifecycle;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.core.profiling.HammerTrajectoryLoadSample;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidClientProjectionService;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
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
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side API. Asset storage delegates to {@link CelestialAssetStore#CLIENT},
 * keeping client and server state isolated in single-player.
 * Client-side prediction is deferred — see Architecture §15.
 */
@SideOnly(Side.CLIENT)
public final class CelestialClient {

    @Deprecated
    public record TransferTarget(CelestialAsset.ID assetId, String displayName, CelestialObject hostBody) {}

    // ── Client-side asset mirror (via CLIENT store) ──

    public static CelestialAsset getByAssetId(CelestialAsset.ID assetId) {
        return CelestialAssetStore.CLIENT.findAssetInternal(assetId);
    }

    public static List<CelestialAsset> getState(CelestialObjectKey celestialObjectKey) {
        return CelestialAssetStore.CLIENT.getStateInternal(GTTeamsCompat.getTeam(), celestialObjectKey);
    }

    public static List<CelestialAsset> allAssets() {
        return CelestialAssetStore.CLIENT.allAssetsInternal();
    }

    public static List<AutomatedFacility> allOutposts() {
        List<AutomatedFacility> result = new ArrayList<>();
        for (CelestialAsset asset : CelestialAssetStore.CLIENT.allAssetsInternal()) {
            if (asset instanceof AutomatedFacility af) {
                result.add(af);
            }
        }
        return result;
    }

    // ── Logistics mirror ──

    private static final List<LogisticsDelivery> deliveries = new ArrayList<>();
    private static int deliveryRevision = 0;
    private static int signalRevision = 0;
    private static List<LogisticSignal> signals = List.of();
    private static HammerTrajectoryLoadSample hammerTrajectoryLoadSample = new HammerTrajectoryLoadSample(0.0, 0.0);
    private static final AsteroidClientProjectionService asteroidProjections = new AsteroidClientProjectionService();

    private static final Map<CelestialObjectKey, CachedChildren> childrenCache = new LinkedHashMap<>();

    private CelestialClient() {}

    public static boolean registerAsset(CelestialObjectKey celestialObjectKey, CelestialAsset asset) {
        return StarmapActionSyncHandler.sendRegisterAsset(celestialObjectKey, asset);
    }

    public static void add(CelestialAsset state) {
        CelestialAssetStore.CLIENT.registerAssetInternal(GTTeamsCompat.getTeam(), state);
    }

    public static void clear() {
        ClientStateLifecycle.clearAll();
    }

    public static void clearLocalState() {
        deliveries.clear();
        deliveryRevision = 0;
        signals = List.of();
        signalRevision = 0;
        asteroidProjections.clear();
        childrenCache.clear();
        hammerTrajectoryLoadSample = new HammerTrajectoryLoadSample(0.0, 0.0);
    }

    public static void createModules(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled,
        List<ModulePlacement> placements) {
        createModules(
            assetId,
            kind,
            kind.defaultShape(),
            kind.defaultTier(),
            null,
            MinerFocusTier.NONE,
            null,
            creativeBuildModeEnabled,
            placements);
    }

    public static boolean createModules(ID assetId, FacilityModuleKind kind, ModuleShape shape, ModuleTier tier,
        @Nullable HammerVariant hammerVariant, MinerFocusTier minerFocusTier,
        @Nullable SettingsGroup.ID settingsGroupId, boolean creativeBuildModeEnabled,
        List<ModulePlacement> placements) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return false;
        if (!kind.isAllowedOn(state.kind)) return false;
        return StarmapActionSyncHandler.sendFacilityCommand(
            new FacilityCommand.BuildModules(
                assetId,
                kind,
                shape,
                buildPhysicalSpec(kind, tier, hammerVariant, minerFocusTier),
                settingsGroupId,
                creativeBuildModeEnabled,
                placements));
    }

    private static IModuleComponent.BuildPhysicalSpec buildPhysicalSpec(FacilityModuleKind kind, ModuleTier tier,
        @Nullable HammerVariant hammerVariant, MinerFocusTier minerFocusTier) {
        if (kind == FacilityModuleKind.HAMMER) {
            return new IModuleComponent.BuildPhysicalSpec.Hammer(
                tier,
                hammerVariant == null ? HammerVariant.BASE : hammerVariant);
        }
        if (kind == FacilityModuleKind.MINER) {
            return new IModuleComponent.BuildPhysicalSpec.Miner(tier, minerFocusTier);
        }
        return new IModuleComponent.BuildPhysicalSpec.Tier(tier);
    }

    public static boolean copyModule(ID assetId, ModuleInstance.ID sourceModuleId, boolean creativeBuildModeEnabled,
        List<ModulePlacement> placements) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        ModuleInstance sourceModule = state == null ? null : state.moduleById(sourceModuleId);
        if (sourceModule == null) return false;
        return StarmapActionSyncHandler.sendFacilityCommand(
            new FacilityCommand.CopyBuildModules(assetId, sourceModule.id, creativeBuildModeEnabled, placements));
    }

    public static boolean destroyAsset(ID assetId) {
        return StarmapActionSyncHandler.sendDestroyAsset(assetId);
    }

    public static boolean cancelConstruction(ID assetId) {
        return StarmapActionSyncHandler.sendCancelConstruction(assetId);
    }

    public static boolean startDeconstruction(ID assetId) {
        return StarmapActionSyncHandler.sendStartDeconstruction(assetId);
    }

    public static boolean renameAsset(ID assetId, String displayName) {
        return StarmapActionSyncHandler.sendRenameAsset(assetId, displayName);
    }

    public static void requestFullSync(ID assetId) {
        StarmapActionSyncHandler.sendRequestFullSync(assetId);
    }

    public static List<TransferTarget> getTransferTargetsInSystem(CelestialObject root, CelestialObject body) {
        List<TransferTarget> targets = new ArrayList<>();
        if (body == null) return targets;
        CelestialObject hostStar = GalaxiaCelestialAPI.findStar(root, body);
        if (hostStar == null) return targets;
        collectTransferTargets(hostStar, targets);
        return targets;
    }

    public static void requestModuleDeconstruction(ID assetId, ModuleInstance.ID moduleId) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.RequestModuleDeconstruction(assetId, module.id));
    }

    public static void configureHammer(ID assetId, ModuleInstance.ID moduleId, AllowShootingConfig config,
        OrbitalTransferPlanner.RoutePriority priority) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.ConfigureHammer(assetId, module.id, config, priority));
    }

    public static void planModuleTierUpgrade(ID assetId, ModuleInstance.ID moduleId, ModuleTier targetTier,
        boolean reserveItems) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.PlanTierUpgrade(assetId, List.of(module.id), targetTier, reserveItems));
    }

    public static void replaceRecipeBook(ID assetId, RecipeBookOwner owner, RecipeBook replacement) {
        if (!(getByAssetId(assetId) instanceof AutomatedFacility)) return;
        StarmapActionSyncHandler
            .sendFacilityCommand(new FacilityCommand.ReplaceRecipeBook(assetId, owner, replacement));
    }

    public static void setInventoryBound(ID assetId, BoundKind kind, InventoryKey resource, long amount) {
        if (!(getByAssetId(assetId) instanceof AutomatedFacility)) return;
        StarmapActionSyncHandler
            .sendFacilityCommand(new FacilityCommand.SetInventoryBound(assetId, kind, resource, amount));
    }

    public static void clearInventoryBound(ID assetId, BoundKind kind, InventoryKey resource) {
        if (!(getByAssetId(assetId) instanceof AutomatedFacility)) return;
        StarmapActionSyncHandler.sendFacilityCommand(new FacilityCommand.ClearInventoryBound(assetId, kind, resource));
    }

    public static void replaceMinerSettings(ID assetId, ModuleInstance.ID moduleId, MinerSettings replacement) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.ReplaceMinerSettings(assetId, module.id, replacement));
    }

    public static void updateModuleSettingsGroup(ID assetId, ModuleInstance.ID moduleId,
        @Nullable SettingsGroup.ID groupId) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.SetSettingsGroup(assetId, module.id, groupId));
    }

    public static void createModuleSettingsGroup(ID assetId, ModuleInstance.ID moduleId) {
        createModuleSettingsGroup(assetId, moduleId, "");
    }

    public static void createModuleSettingsGroup(ID assetId, ModuleInstance.ID moduleId, String displayName) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.CreateSettingsGroup(assetId, module.id, displayName));
    }

    public static void renameModuleSettingsGroup(ID assetId, SettingsGroup.ID groupId, String displayName) {
        if (!(getByAssetId(assetId) instanceof AutomatedFacility)) return;
        StarmapActionSyncHandler
            .sendFacilityCommand(new FacilityCommand.RenameSettingsGroup(assetId, groupId, displayName));
    }

    public static void cancelModuleOperation(ID assetId, ModuleInstance.ID moduleId) {
        sendModuleCommand(assetId, moduleId, module -> new FacilityCommand.CancelModuleOperation(assetId, module.id));
    }

    public static void planHammerUpgrade(ID assetId, ModuleInstance.ID moduleId, HammerVariant variant, ModuleTier tier,
        boolean reserveItems, boolean voidCompletionRefund) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.PlanHammerUpgrade(
                assetId,
                List.of(module.id),
                variant,
                tier,
                reserveItems,
                voidCompletionRefund));
    }

    public static void planModuleUpgradeTargets(ID assetId, ModuleInstance.ID sourceModuleId, ModuleTier tier,
        @Nullable HammerVariant variant, boolean reserveItems, boolean voidCompletionRefund,
        List<StationTileCoord> targetCoords) {
        AutomatedFacility facility = getByAssetId(assetId) instanceof AutomatedFacility af ? af : null;
        if (facility == null || facility.moduleById(sourceModuleId) == null) return;
        List<ModuleInstance.ID> targetIds = resolveTargetModuleIds(facility, targetCoords);
        if (targetIds == null) return;
        FacilityCommand command = variant != null
            ? new FacilityCommand.PlanHammerUpgrade(
                assetId,
                targetIds,
                variant,
                tier,
                reserveItems,
                voidCompletionRefund)
            : new FacilityCommand.PlanTierUpgrade(assetId, targetIds, tier, reserveItems);
        StarmapActionSyncHandler.sendFacilityCommand(command);
    }

    public static void planMinerFocusTier(ID assetId, ModuleInstance.ID moduleId, MinerFocusTier focusTier) {
        planMinerFocusTier(assetId, moduleId, ModuleTier.NONE, focusTier);
    }

    public static void planMinerFocusTier(ID assetId, ModuleInstance.ID moduleId, ModuleTier targetTier,
        MinerFocusTier focusTier) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.PlanMinerFocusUpgrade(
                assetId,
                module.id,
                targetTier == ModuleTier.NONE ? module.tier() : targetTier,
                focusTier));
    }

    public static void setMinerFocusOre(ID assetId, ModuleInstance.ID moduleId, String oreKey) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.SetMinerFocusOre(assetId, module.id, oreKey));
    }

    public static void copyModuleSettings(ID assetId, ModuleInstance.ID sourceModuleId,
        List<StationTileCoord> targetCoords) {
        AutomatedFacility facility = getByAssetId(assetId) instanceof AutomatedFacility af ? af : null;
        ModuleInstance source = resolveModule(assetId, sourceModuleId);
        if (facility == null || source == null) return;
        List<ModuleInstance.ID> targetIds = resolveTargetModuleIds(facility, targetCoords);
        if (targetIds == null) return;
        StarmapActionSyncHandler
            .sendFacilityCommand(new FacilityCommand.CopyModuleSettings(assetId, source.id, targetIds));
    }

    public static void updateDebugDataGeneratorConfig(ID assetId, ModuleInstance.ID moduleId,
        ModuleDebugDataGenerator.Config config) {
        sendModuleCommand(
            assetId,
            moduleId,
            module -> new FacilityCommand.ConfigureDebugDataGenerator(assetId, module.id, config));
    }

    private static void sendModuleCommand(ID assetId, ModuleInstance.ID moduleId,
        Function<ModuleInstance, FacilityCommand> commandFactory) {
        ModuleInstance module = resolveModule(assetId, moduleId);
        if (module == null) return;
        FacilityCommand command = commandFactory.apply(module);
        if (command != null) StarmapActionSyncHandler.sendFacilityCommand(command);
    }

    private static @Nullable ModuleInstance resolveModule(ID assetId, ModuleInstance.ID moduleId) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        return state == null ? null : state.moduleById(moduleId);
    }

    private static @Nullable List<ModuleInstance.ID> resolveTargetModuleIds(AutomatedFacility facility,
        List<StationTileCoord> targetCoords) {
        if (facility.stationLayout() == null || targetCoords == null) return null;
        Set<ModuleInstance.ID> targetIds = new LinkedHashSet<>();
        for (StationTileCoord targetCoord : targetCoords) {
            if (targetCoord == null) return null;
            ModuleInstance target = facility.stationLayout()
                .moduleAt(targetCoord);
            if (target == null) return null;
            targetIds.add(target.id);
        }
        return targetIds.isEmpty() ? null : List.copyOf(targetIds);
    }

    public static void addInventory(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        CelestialAsset asset = getByAssetId(assetId);
        if (asset instanceof AutomatedFacility) {
            StarmapActionSyncHandler.sendFacilityCommand(
                new FacilityCommand.AdjustInventory(
                    assetId,
                    resource,
                    FacilityCommand.InventoryAdjustment.INSERT,
                    amount));
            return;
        }
        if (!(asset instanceof IDistributedInventory)) return;
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.add(assetId, resource, amount);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void removeInventory(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        CelestialAsset asset = getByAssetId(assetId);
        if (asset instanceof AutomatedFacility) {
            StarmapActionSyncHandler.sendFacilityCommand(new FacilityCommand.ClearInventoryResource(assetId, resource));
            return;
        }
        if (!(asset instanceof IDistributedInventory)) return;
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.remove(assetId, resource);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void removeInventoryAmount(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        CelestialAsset asset = getByAssetId(assetId);
        if (asset instanceof AutomatedFacility) {
            StarmapActionSyncHandler.sendFacilityCommand(
                new FacilityCommand.AdjustInventory(
                    assetId,
                    resource,
                    FacilityCommand.InventoryAdjustment.EXTRACT,
                    amount));
            return;
        }
        if (!(asset instanceof IDistributedInventory)) return;
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.removeAmount(assetId, resource, amount);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void updateLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource,
        LogisticsResourceConfig config) {
        updateLogisticsConfig(assetId, resource, config, LogisticsConfigAccessMode.FULL);
    }

    public static void updateLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource,
        LogisticsResourceConfig config, LogisticsConfigAccessMode accessMode) {
        if (getByAssetId(assetId) instanceof AutomatedFacility) {
            StarmapActionSyncHandler
                .sendFacilityCommand(new FacilityCommand.PutLogisticsConfig(assetId, resource, config, accessMode));
            return;
        }
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(assetId, resource, config, accessMode);
        StarmapActionSyncHandler.sendLogisticsConfig(packet);
    }

    public static void removeLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        if (getByAssetId(assetId) instanceof AutomatedFacility) {
            StarmapActionSyncHandler.sendFacilityCommand(new FacilityCommand.RemoveLogisticsConfig(assetId, resource));
            return;
        }
        LogisticsConfigUpdatePacket packet = LogisticsConfigUpdatePacket.remove(assetId, resource);
        StarmapActionSyncHandler.sendLogisticsConfig(packet);
    }

    // ── Filter actions ──

    public static void addFilter(CelestialAsset.ID assetId, boolean isItem, String filterKey) {
        AutomatedFacility facility = getByAssetId(assetId) instanceof AutomatedFacility af ? af : null;
        if (facility == null) return;
        List<String> filters = currentFilters(facility, isItem);
        if (!filters.contains(filterKey)) filters.add(filterKey);
        sendFilters(assetId, isItem, filters);
    }

    public static void removeFilter(CelestialAsset.ID assetId, boolean isItem, String filterKey) {
        AutomatedFacility facility = getByAssetId(assetId) instanceof AutomatedFacility af ? af : null;
        if (facility == null) return;
        List<String> filters = currentFilters(facility, isItem);
        filters.remove(filterKey);
        sendFilters(assetId, isItem, filters);
    }

    public static void clearFilters(CelestialAsset.ID assetId, boolean isItem) {
        if (!(getByAssetId(assetId) instanceof AutomatedFacility)) return;
        sendFilters(assetId, isItem, List.of());
    }

    public static void setFilters(CelestialAsset.ID assetId, boolean isItem, List<String> filterKeys) {
        if (!(getByAssetId(assetId) instanceof AutomatedFacility)) return;
        sendFilters(assetId, isItem, filterKeys);
    }

    private static List<String> currentFilters(AutomatedFacility facility, boolean isItem) {
        return new ArrayList<>(
            facility.filtersSnapshot()
                .getOrDefault(isItem, List.of()));
    }

    private static void sendFilters(CelestialAsset.ID assetId, boolean isItem, List<String> filterKeys) {
        FacilityCommand.FilterKind kind = isItem ? FacilityCommand.FilterKind.ITEM : FacilityCommand.FilterKind.FLUID;
        StarmapActionSyncHandler.sendFacilityCommand(new FacilityCommand.ReplaceFilters(assetId, kind, filterKeys));
    }

    // ── Signal mirror ──

    public static void updateClientSignals(List<LogisticSignal> newSignals) {
        signals = List.copyOf(newSignals);
        signalRevision++;
    }

    public static Map<String, Long> clientSignalsForSystem(CelestialObjectKey systemKey) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (LogisticSignal signal : signals) {
            if (signal.scope() != LogisticSignal.Scope.SYSTEM || !systemKey.equals(signal.systemKey())) continue;
            result.merge(
                signal.resourceId()
                    .toKey(),
                signal.amount(),
                Long::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Long> clientSignalsForPlanet(CelestialObjectKey anchorBodyKey) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (LogisticSignal signal : signals) {
            if (signal.scope() != LogisticSignal.Scope.SYSTEM || !anchorBodyKey.equals(signal.planetaryAnchorBodyKey()))
                continue;
            result.merge(
                signal.resourceId()
                    .toKey(),
                signal.amount(),
                Long::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    public static int clientSignalRevision() {
        return signalRevision;
    }

    // ── Delivery mirror ──

    public static void updateClientDeliveries(List<LogisticsDelivery> newDeliveries) {
        deliveries.clear();
        newDeliveries.stream()
            .filter(t -> t.data.resourceId() != null)
            .forEach(deliveries::add);
        deliveryRevision++;
    }

    public static List<LogisticsDelivery> clientDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    public static int clientDeliveryRevision() {
        return deliveryRevision;
    }

    public static void updateHammerTrajectoryLoad(HammerTrajectoryLoadSample sample) {
        hammerTrajectoryLoadSample = sample == null ? new HammerTrajectoryLoadSample(0.0, 0.0) : sample;
    }

    public static HammerTrajectoryLoadSample hammerTrajectoryLoadSample() {
        return hammerTrajectoryLoadSample;
    }

    public static List<CelestialAsset> listAssetsInSystem(CelestialObjectKey systemKey) {
        return CelestialAssetStore.CLIENT.listAssetsInSystemInternal(systemKey, GTTeamsCompat.getTeam());
    }

    /**
     * Thin client adapter over {@link CelestialRegistry#children}: supplies the synced
     * discovery view plus temporary scan/sensor visibility and the debug include-hidden flag.
     * Does not rebuild or materialize a second child list.
     */
    public static List<CelestialObject> getChildren(CelestialObject parent) {
        return parent == null ? List.of() : getChildren(parent.key());
    }

    public static List<CelestialObject> getChildren(CelestialObjectKey parentKey) {
        // Starmap rendering asks for the same children several times per body per frame, and each miss
        // materializes every child. The result only changes when synced knowledge, synced scans or the
        // debug include-hidden flag change, so key the cache on those.
        boolean includeHidden = asteroidProjections.includeHidden();
        CachedChildren cached = childrenCache.get(parentKey);
        if (cached != null && cached.matches(
            CelestialKnowledgeClientState.revision(),
            CelestialDiscoveryClientState.revision(),
            includeHidden)) {
            return cached.children();
        }
        List<CelestialObject> children = CelestialRegistry.children(
            parentKey,
            asteroidProjections.discoveryView(
                parentKey,
                CelestialDiscoveryClientState.snapshots(),
                CelestialKnowledgeClientState.discoveryView()),
            includeHidden);
        childrenCache.put(
            parentKey,
            new CachedChildren(
                CelestialKnowledgeClientState.revision(),
                CelestialDiscoveryClientState.revision(),
                includeHidden,
                children));
        return children;
    }

    private record CachedChildren(int knowledgeRevision, int discoveryRevision, boolean includeHidden,
        List<CelestialObject> children) {

        boolean matches(int currentKnowledge, int currentDiscovery, boolean currentIncludeHidden) {
            return knowledgeRevision == currentKnowledge && discoveryRevision == currentDiscovery
                && includeHidden == currentIncludeHidden;
        }
    }

    public static Optional<AsteroidStarmapProjection> asteroidProjection(CelestialObject body) {
        if (body == null || body.parentKey() == null) return Optional.empty();
        // Resolving siblings rebuilds the belt catalog; skip it for bodies that can never have a projection.
        if (!body.key()
            .isMinorBody()) return Optional.empty();
        List<CelestialObject> siblings = getChildren(body.parentKey());
        return asteroidProjections.projectionFor(body, siblings, CelestialDiscoveryClientState.snapshots());
    }

    public static boolean showHiddenAsteroidObjects() {
        return asteroidProjections.includeHidden();
    }

    public static void toggleShowHiddenAsteroidObjects() {
        asteroidProjections.toggleIncludeHidden();
    }

    public static void setShowHiddenAsteroidObjects(boolean value) {
        asteroidProjections.setIncludeHidden(value);
    }

    public static boolean isDebugHiddenAsteroid(CelestialObject body) {
        return asteroidProjection(body).map(AsteroidStarmapProjection::debugHidden)
            .orElse(false);
    }

    public static boolean isAsteroidScanInProgress(CelestialObject body) {
        return asteroidProjection(body).map(AsteroidStarmapProjection::scanInProgress)
            .orElse(false);
    }

    public static boolean isSensorRevealedAsteroid(CelestialObject body) {
        return asteroidProjection(body).map(AsteroidStarmapProjection::sensorRevealed)
            .orElse(false);
    }

    public static Optional<CelestialDiscoveryScanSnapshot> asteroidScanSnapshotByTarget(CelestialObject body) {
        if (body == null) return Optional.empty();
        return CelestialDiscoveryClientState.scanTarget(body.key(), CelestialDiscoveryCapability.PROSPECTING);
    }

    // ── Helpers ──

    private static void collectTransferTargets(CelestialObject current, List<TransferTarget> targets) {
        List<CelestialAsset> state = getState(current.key());
        for (CelestialAsset asset : state) {
            if (asset.isManageable()) {
                targets.add(new TransferTarget(asset.assetId, asset.displayName(), current));
            }
        }
        for (CelestialObject child : getChildren(current)) {
            collectTransferTargets(child, targets);
        }
    }

}
