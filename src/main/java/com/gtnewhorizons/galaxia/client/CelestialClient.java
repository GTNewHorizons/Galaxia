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
import com.gtnewhorizons.galaxia.core.network.AssetFilterUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket.ConfigAction;
import com.gtnewhorizons.galaxia.core.network.ClientStateLifecycle;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.core.profiling.HammerTrajectoryLoadSample;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldClientKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNodeCatalog;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjectionBuilder;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

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

    public static List<CelestialAsset> getState(CelestialObjectId celestialObjectId) {
        return getState(CelestialObjectKey.registered(celestialObjectId));
    }

    public static List<CelestialAsset> getState(CelestialObjectKey celestialObjectId) {
        List<CelestialAsset> result = new ArrayList<>();
        for (CelestialAsset asset : CelestialAssetStore.CLIENT.allAssetsInternal()) {
            if (asset.celestialObjectId.equals(celestialObjectId)) {
                result.add(asset);
            }
        }
        return result;
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
    private static HammerTrajectoryLoadSample hammerTrajectoryLoadSample = new HammerTrajectoryLoadSample(0.0, 0.0);
    // Client-only debug toggle. Server knowledge still controls which asteroids
    // are actually known; this only asks the renderer to include hidden nodes.
    private static boolean showHiddenAsteroidObjects = false;
    private static final Map<CelestialObjectKey, CachedAsteroidProjections> asteroidProjectionCache = new LinkedHashMap<>();

    private static final Map<CelestialObjectKey, Map<String, Long>> systemSignals = new LinkedHashMap<>();
    private static final Map<CelestialObjectKey, Map<String, Long>> planetSignals = new LinkedHashMap<>();

    private CelestialClient() {}

    private record CachedAsteroidProjections(List<AsteroidFieldKnowledgeSnapshot> snapshots,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots, boolean includeHidden,
        List<AsteroidStarmapProjection> projections, Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId) {

        boolean matches(List<AsteroidFieldKnowledgeSnapshot> currentSnapshots,
            List<CelestialDiscoveryScanSnapshot> currentScanSnapshots, boolean currentIncludeHidden) {
            return snapshots == currentSnapshots && scanSnapshots == currentScanSnapshots
                && includeHidden == currentIncludeHidden;
        }
    }

    public static boolean registerAsset(CelestialObjectId celestialObjectId, CelestialAsset asset) {
        return registerAsset(CelestialObjectKey.registered(celestialObjectId), asset);
    }

    public static boolean registerAsset(CelestialObjectKey celestialObjectId, CelestialAsset asset) {
        return StarmapActionSyncHandler.sendRegisterAsset(celestialObjectId, asset);
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
        signalRevision = 0;
        showHiddenAsteroidObjects = false;
        asteroidProjectionCache.clear();
        hammerTrajectoryLoadSample = new HammerTrajectoryLoadSample(0.0, 0.0);
    }

    public static void createModule(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled) {
        createModules(assetId, kind, creativeBuildModeEnabled, null);
    }

    public static void createModule(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled,
        @Nullable StationTileCoord tileCoord) {
        createModules(assetId, kind, creativeBuildModeEnabled, tileCoord == null ? null : List.of(tileCoord));
    }

    public static void createModules(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled,
        List<StationTileCoord> tileCoords) {
        createModules(
            assetId,
            kind,
            kind.defaultShape(),
            kind.defaultTier(),
            null,
            MinerFocusTier.NONE,
            (short) 0,
            creativeBuildModeEnabled,
            tileCoords);
    }

    public static boolean createModules(ID assetId, FacilityModuleKind kind, ModuleShape shape, ModuleTier tier,
        @Nullable HammerVariant hammerVariant, MinerFocusTier minerFocusTier, short settingsGroupId,
        boolean creativeBuildModeEnabled, List<StationTileCoord> tileCoords) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return false;
        if (!kind.isAllowedOn(state.kind)) return false;
        return StarmapActionSyncHandler.sendBuildModules(
            assetId,
            kind,
            shape,
            tier,
            hammerVariant,
            minerFocusTier,
            settingsGroupId,
            creativeBuildModeEnabled,
            tileCoords);
    }

    public static boolean copyModule(ID assetId, int sourceModuleIndex, ModuleInstance.ID sourceModuleId,
        boolean creativeBuildModeEnabled, List<StationTileCoord> tileCoords) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null || sourceModuleIndex < 0
            || sourceModuleIndex >= state.modules()
                .size()) {
            return false;
        }
        return StarmapActionSyncHandler
            .sendCopyModule(assetId, sourceModuleIndex, sourceModuleId, creativeBuildModeEnabled, tileCoords);
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

    public static void updateModuleAction(ID assetId, int moduleIndex, AssetModuleUpdatePacket.Action action) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.action(assetId, moduleIndex, module.id, action));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, String payload) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, boolean payload) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, double payload) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static <T extends Enum<T>> void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction,
        T payload) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static void updateModuleRecipeSlot(ID assetId, int moduleIndex, ConfigAction configAction, byte slotIndex,
        SavedRecipe slot) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .recipeSlotPayload(assetId, moduleIndex, module.id, configAction, slotIndex, slot));
    }

    public static void updateInventoryBound(ID assetId, int moduleIndex, ConfigAction configAction, BoundKind kind,
        InventoryKey resource, long amount) {
        updateInventoryBound(assetId, configAction, kind, resource, amount);
    }

    public static void updateInventoryBound(ID assetId, ConfigAction configAction, BoundKind kind,
        InventoryKey resource, long amount) {
        AssetInventoryUpdatePacket packet = configAction == ConfigAction.CLEAR_INVENTORY_BOUND
            ? AssetInventoryUpdatePacket.clearBound(assetId, kind, resource)
            : AssetInventoryUpdatePacket.setBound(assetId, kind, resource, amount);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void updateMinerOreBlacklisted(ID assetId, int moduleIndex, String oreKey, boolean blacklisted) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .minerOreBlacklisted(assetId, moduleIndex, module.id, oreKey, blacklisted));
    }

    public static void updateModuleSettingsGroup(ID assetId, int moduleIndex, short groupId) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.moduleSettingsGroup(assetId, moduleIndex, module.id, groupId));
    }

    public static void createModuleSettingsGroup(ID assetId, int moduleIndex) {
        createModuleSettingsGroup(assetId, moduleIndex, "");
    }

    public static void createModuleSettingsGroup(ID assetId, int moduleIndex, String displayName) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.createModuleSettingsGroup(assetId, moduleIndex, module.id, displayName));
    }

    public static void renameModuleSettingsGroup(ID assetId, int moduleIndex, short groupId, String displayName) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .renameModuleSettingsGroup(assetId, moduleIndex, module.id, groupId, displayName));
    }

    public static void cancelModuleOperation(ID assetId, int moduleIndex) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.cancelModuleOperation(assetId, moduleIndex, module.id));
    }

    public static void planHammerUpgrade(ID assetId, int moduleIndex, HammerVariant variant, ModuleTier tier,
        boolean reserveItems, boolean voidCompletionRefund) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .hammerUpgradePlan(assetId, moduleIndex, module.id, variant, tier, reserveItems, voidCompletionRefund));
    }

    public static void planModuleUpgradeTargets(ID assetId, int moduleIndex, ModuleTier tier,
        @Nullable HammerVariant variant, boolean reserveItems, boolean voidCompletionRefund,
        List<StationTileCoord> targetCoords) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.moduleUpgradeTargets(
                assetId,
                moduleIndex,
                module.id,
                tier,
                variant,
                reserveItems,
                voidCompletionRefund,
                targetCoords));
    }

    public static void planMinerFocusTier(ID assetId, int moduleIndex, MinerFocusTier focusTier) {
        planMinerFocusTier(assetId, moduleIndex, ModuleTier.NONE, focusTier);
    }

    public static void planMinerFocusTier(ID assetId, int moduleIndex, ModuleTier targetTier,
        MinerFocusTier focusTier) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .minerFocusTierPlan(assetId, moduleIndex, module.id, targetTier, focusTier));
    }

    public static void setMinerFocusOre(ID assetId, int moduleIndex, String oreKey) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.minerFocusOre(assetId, moduleIndex, module.id, oreKey));
    }

    public static void copyModuleSettings(ID assetId, int moduleIndex, List<StationTileCoord> targetCoords) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.copyModuleSettings(assetId, moduleIndex, module.id, targetCoords));
    }

    public static void updateDebugDataGeneratorConfig(ID assetId, int moduleIndex,
        ModuleDebugDataGenerator.Config config) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.debugDataGeneratorConfig(assetId, moduleIndex, module.id, config));
    }

    private static void sendModuleUpdate(ID assetId, int moduleIndex,
        Function<ModuleInstance, AssetModuleUpdatePacket> packetFactory) {
        ModuleInstance module = resolveModule(assetId, moduleIndex);
        if (module == null) return;
        AssetModuleUpdatePacket packet = packetFactory.apply(module);
        if (packet == null) return;
        StarmapActionSyncHandler.sendModuleUpdate(packet);
    }

    private static @Nullable ModuleInstance resolveModule(ID assetId, int moduleIndex) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return null;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return null;
        return modules.get(moduleIndex);
    }

    public static void addInventory(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.add(assetId, resource, amount);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void removeInventory(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.remove(assetId, resource);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void removeInventoryAmount(CelestialAsset.ID assetId, ItemStackWrapper resource, long amount) {
        AssetInventoryUpdatePacket packet = AssetInventoryUpdatePacket.removeAmount(assetId, resource, amount);
        StarmapActionSyncHandler.sendInventoryUpdate(packet);
    }

    public static void updateLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource,
        LogisticsResourceConfig config) {
        updateLogisticsConfig(assetId, resource, config, LogisticsConfigAccessMode.FULL);
    }

    public static void updateLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource,
        LogisticsResourceConfig config, LogisticsConfigAccessMode accessMode) {
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(assetId, resource, config, accessMode);
        StarmapActionSyncHandler.sendLogisticsConfig(packet);
    }

    public static void removeLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        LogisticsConfigUpdatePacket packet = LogisticsConfigUpdatePacket.remove(assetId, resource);
        StarmapActionSyncHandler.sendLogisticsConfig(packet);
    }

    // ── Filter actions ──

    public static void addFilter(CelestialAsset.ID assetId, boolean isItem, String filterKey) {
        AssetFilterUpdatePacket packet = AssetFilterUpdatePacket.addFilter(assetId, isItem, filterKey);
        StarmapActionSyncHandler.sendFilterUpdate(packet);
    }

    public static void removeFilter(CelestialAsset.ID assetId, boolean isItem, String filterKey) {
        AssetFilterUpdatePacket packet = AssetFilterUpdatePacket.removeFilter(assetId, isItem, filterKey);
        StarmapActionSyncHandler.sendFilterUpdate(packet);
    }

    public static void clearFilters(CelestialAsset.ID assetId, boolean isItem) {
        AssetFilterUpdatePacket packet = AssetFilterUpdatePacket.clearFilters(assetId, isItem);
        StarmapActionSyncHandler.sendFilterUpdate(packet);
    }

    public static void setFilters(CelestialAsset.ID assetId, boolean isItem, List<String> filterKeys) {
        AssetFilterUpdatePacket packet = AssetFilterUpdatePacket.setFilters(assetId, isItem, filterKeys);
        StarmapActionSyncHandler.sendFilterUpdate(packet);
    }

    // ── Signal mirror ──

    public static void updateClientSignals(Map<CelestialObjectKey, Map<String, Long>> bySystem,
        Map<CelestialObjectKey, Map<String, Long>> byPlanet) {
        systemSignals.clear();
        systemSignals.putAll(bySystem);
        planetSignals.clear();
        planetSignals.putAll(byPlanet);
        signalRevision++;
    }

    public static Map<String, Long> clientSignalsForSystem(CelestialObjectKey systemId) {
        Map<String, Long> result = systemSignals.get(systemId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    public static Map<String, Long> clientSignalsForPlanet(CelestialObjectKey anchorBodyId) {
        Map<String, Long> result = planetSignals.get(anchorBodyId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
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

    public static List<CelestialAsset> listAssetsInSystem(CelestialObjectId systemId) {
        return listAssetsInSystem(CelestialObjectKey.registered(systemId));
    }

    public static List<CelestialAsset> listAssetsInSystem(CelestialObjectKey systemId) {
        return CelestialAssetStore.CLIENT.listAssetsInSystemInternal(systemId, GTTeamsCompat.getTeam());
    }

    public static List<CelestialObject> getChildren(CelestialObject parent) {
        return parent == null ? List.of() : getChildren(parent.id());
    }

    public static List<CelestialObject> getChildren(CelestialObjectKey parentId) {
        List<CelestialObject> registeredChildren = GalaxiaCelestialAPI.getChildren(parentId);
        List<CelestialObject> asteroidChildren = clientAsteroidChildren(parentId);
        if (asteroidChildren.isEmpty()) return registeredChildren;
        List<CelestialObject> children = new ArrayList<>(registeredChildren.size() + asteroidChildren.size());
        children.addAll(registeredChildren);
        children.addAll(asteroidChildren);
        return List.copyOf(children);
    }

    public static List<AsteroidStarmapProjection> getChildAsteroidProjections(CelestialObject parent) {
        if (parent == null || parent.properties()
            .asteroidFieldProfile() == null) return List.of();
        return asteroidProjectionSet(parent).projections();
    }

    public static Optional<AsteroidStarmapProjection> asteroidProjection(CelestialObject body) {
        if (body == null || !body.id()
            .isMinorBody()) return Optional.empty();
        return GalaxiaCelestialAPI.get(
            body.id()
                .minorBodyId()
                .parentBodyId())
            .flatMap(
                belt -> Optional.ofNullable(
                    asteroidProjectionSet(belt).byBodyId()
                        .get(body.id())));
    }

    public static boolean showHiddenAsteroidObjects() {
        return showHiddenAsteroidObjects;
    }

    public static void toggleShowHiddenAsteroidObjects() {
        showHiddenAsteroidObjects = !showHiddenAsteroidObjects;
        asteroidProjectionCache.clear();
    }

    public static void setShowHiddenAsteroidObjects(boolean value) {
        showHiddenAsteroidObjects = value;
        asteroidProjectionCache.clear();
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
        return CelestialDiscoveryClientState.scanTarget(body.id(), SatelliteKind.PROSPECTING);
    }

    // ── Helpers ──

    private static List<CelestialObject> clientAsteroidChildren(CelestialObjectKey parentId) {
        if (parentId == null || !parentId.isRegistered()) return List.of();
        return GalaxiaCelestialAPI.get(parentId.registeredBodyId())
            .filter(
                body -> body.properties()
                    .asteroidFieldProfile() != null)
            .map(
                body -> asteroidProjectionSet(body).projections()
                    .stream()
                    .map(AsteroidStarmapProjection::body)
                    .toList())
            .orElse(List.of());
    }

    private static CachedAsteroidProjections asteroidProjectionSet(CelestialObject belt) {
        List<AsteroidFieldKnowledgeSnapshot> snapshots = AsteroidFieldClientKnowledgeState.snapshots();
        List<CelestialDiscoveryScanSnapshot> scanSnapshots = CelestialDiscoveryClientState.snapshots();
        CachedAsteroidProjections cached = asteroidProjectionCache.get(belt.id());
        if (cached != null && cached.matches(snapshots, scanSnapshots, showHiddenAsteroidObjects)) return cached;

        Set<MinorCelestialBodyId> scanTargets = scanTargetsForBelt(belt.id(), scanSnapshots);
        Set<MinorCelestialBodyId> sensorRevealTargets = sensorRevealTargetsForBelt(belt, snapshots, scanSnapshots);
        List<AsteroidStarmapProjection> projections = AsteroidStarmapProjectionBuilder
            .forBelt(belt, snapshots, showHiddenAsteroidObjects, scanTargets, sensorRevealTargets);
        Map<CelestialObjectKey, AsteroidStarmapProjection> byBodyId = projections.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    projection -> projection.body()
                        .id(),
                    Function.identity()));
        CachedAsteroidProjections rebuilt = new CachedAsteroidProjections(
            snapshots,
            scanSnapshots,
            showHiddenAsteroidObjects,
            projections,
            byBodyId);
        asteroidProjectionCache.put(belt.id(), rebuilt);
        return rebuilt;
    }

    private static Set<MinorCelestialBodyId> scanTargetsForBelt(CelestialObjectKey beltId,
        List<CelestialDiscoveryScanSnapshot> scanSnapshots) {

        if (beltId == null || !beltId.isRegistered()) return Set.of();
        CelestialObjectId registeredBeltId = beltId.registeredBodyId();
        Set<MinorCelestialBodyId> targets = new LinkedHashSet<>();
        for (CelestialDiscoveryScanSnapshot snapshot : scanSnapshots) {
            if (snapshot.targetKey() != null && snapshot.targetKey()
                .isMinorBody()
                && snapshot.targetKey()
                    .minorBodyId()
                    .parentBodyId() == registeredBeltId) {
                targets.add(
                    snapshot.targetKey()
                        .minorBodyId());
            }
        }
        return Set.copyOf(targets);
    }

    private static Set<MinorCelestialBodyId> sensorRevealTargetsForBelt(CelestialObject belt,
        List<AsteroidFieldKnowledgeSnapshot> knowledgeSnapshots, List<CelestialDiscoveryScanSnapshot> scanSnapshots) {

        if (belt == null || !belt.id()
            .isRegistered()) return Set.of();
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) return Set.of();

        CelestialObjectId beltId = belt.id()
            .registeredBodyId();
        Optional<AsteroidFieldKnowledgeSnapshot> snapshot = knowledgeSnapshots == null ? Optional.empty()
            : knowledgeSnapshots.stream()
                .filter(candidate -> candidate.beltId() == beltId)
                .findFirst();
        AsteroidFieldNodeCatalog catalog = snapshot
            .map(value -> AsteroidFieldNodeCatalog.fromSnapshots(beltId, profile, value.nodeSnapshots()))
            .orElseGet(
                () -> AsteroidFieldNodeCatalog.restored(beltId)
                    .orElseGet(() -> AsteroidFieldNodeCatalog.fromGenerated(beltId, profile)));
        Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex = new LinkedHashMap<>();
        snapshot.ifPresent(
            value -> value.entries()
                .forEach(entry -> entriesByIndex.put(entry.index(), entry)));

        double revealRadius = profile.satelliteScanRadius() * 2.0;
        Set<MinorCelestialBodyId> targets = new LinkedHashSet<>();
        for (CelestialDiscoveryScanSnapshot scan : scanSnapshots) {
            if (!scan.anchorKey()
                .isMinorBody()
                || scan.anchorKey()
                    .minorBodyId()
                    .parentBodyId() != beltId)
                continue;
            Optional<AsteroidFieldNode> anchorNode = catalog.resolve(
                scan.anchorKey()
                    .minorBodyId());
            if (anchorNode.isEmpty()) continue;
            for (AsteroidFieldNode candidate : catalog.nodes()) {
                if (!isHiddenAsteroidNode(candidate, entriesByIndex)) continue;
                if (distance(profile, anchorNode.get(), candidate) <= revealRadius) {
                    targets.add(candidate.id());
                }
            }
        }
        return Set.copyOf(targets);
    }

    private static boolean isHiddenAsteroidNode(AsteroidFieldNode node,
        Map<Integer, AsteroidFieldKnowledgeSnapshot.Entry> entriesByIndex) {

        AsteroidFieldKnowledgeSnapshot.Entry entry = entriesByIndex.get(node.index());
        DiscoveryState detectionState = entry == null ? node.initialDetectionState() : entry.detectionState();
        return detectionState == DiscoveryState.HIDDEN;
    }

    private static double distance(AsteroidFieldProfile profile, AsteroidFieldNode first, AsteroidFieldNode second) {
        double firstRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, first);
        double firstAngle = Math.toRadians(first.angleOffsetDeg());
        double secondRadius = AsteroidFieldOrbitResolver.resolveRadius(profile, second);
        double secondAngle = Math.toRadians(second.angleOffsetDeg());
        double dx = Math.cos(firstAngle) * firstRadius - Math.cos(secondAngle) * secondRadius;
        double dy = Math.sin(firstAngle) * firstRadius - Math.sin(secondAngle) * secondRadius;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static void collectTransferTargets(CelestialObject current, List<TransferTarget> targets) {
        List<CelestialAsset> state = getState(current.id());
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
