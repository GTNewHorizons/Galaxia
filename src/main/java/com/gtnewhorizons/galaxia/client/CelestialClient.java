package com.gtnewhorizons.galaxia.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.core.network.AssetInventoryUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket.ConfigAction;
import com.gtnewhorizons.galaxia.core.network.LogisticsConfigUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
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
        List<CelestialAsset> result = new ArrayList<>();
        for (CelestialAsset asset : CelestialAssetStore.CLIENT.allAssetsInternal()) {
            if (asset.celestialObjectId == celestialObjectId) {
                result.add(asset);
            }
        }
        return result;
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

    // ── Client-side asset creation (delegates to CLIENT store) ──

    public static CelestialAsset createAssetInConstruction(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind) {
        StarmapActionSyncHandler
            .sendCreateAsset(celestialObjectId, displayName, kind, Buildable.Status.CONSTRUCTION_SITE);
        return null;
    }

    public static CelestialAsset createOperationalAsset(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind) {
        StarmapActionSyncHandler.sendCreateAsset(celestialObjectId, displayName, kind, Buildable.Status.OPERATIONAL);
        return null;
    }

    // ── Logistics mirror ──

    private static final List<LogisticsDelivery> deliveries = new ArrayList<>();
    private static int deliveryRevision = 0;
    private static int signalRevision = 0;

    private static final Map<CelestialObjectId, Map<String, Long>> systemSignals = new LinkedHashMap<>();
    private static final Map<CelestialObjectId, Map<String, Long>> planetSignals = new LinkedHashMap<>();

    private CelestialClient() {}

    public static void clear() {
        CelestialAssetStore.CLIENT.clearInternal();
        deliveries.clear();
        deliveryRevision = 0;
        signalRevision = 0;
    }

    public static void createModule(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled) {
        createModule(assetId, kind, creativeBuildModeEnabled, null);
    }

    public static void createModule(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled,
        @Nullable StationTileCoord tileCoord) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        if (!kind.isAllowedOn(state.kind)) return;
        StarmapActionSyncHandler.sendBuildModule(
            assetId,
            kind,
            ModuleShape.SINGLE,
            kind.defaultTier(),
            creativeBuildModeEnabled,
            tileCoord);
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
        RecipeSlot slot) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .recipeSlotPayload(assetId, moduleIndex, module.id, configAction, slotIndex, slot));
    }

    public static void updateMinerOreBlacklisted(ID assetId, int moduleIndex, String oreKey, boolean blacklisted) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket
                .minerOreBlacklisted(assetId, moduleIndex, module.id, oreKey, blacklisted));
    }

    public static void updateMinerSettingsGroup(ID assetId, int moduleIndex, short groupId) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.minerSettingsGroup(assetId, moduleIndex, module.id, groupId));
    }

    public static void createMinerSettingsGroup(ID assetId, int moduleIndex) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.createMinerSettingsGroup(assetId, moduleIndex, module.id));
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

    public static void planMinerFocus(ID assetId, int moduleIndex, MinerFocusTier focusTier, String oreKey) {
        sendModuleUpdate(
            assetId,
            moduleIndex,
            module -> AssetModuleUpdatePacket.minerFocusPlan(assetId, moduleIndex, module.id, focusTier, oreKey));
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

    public static void updateLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource,
        LogisticsResourceConfig config) {
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(assetId, resource, config);
        StarmapActionSyncHandler.sendLogisticsConfig(packet);
    }

    public static void removeLogisticsConfig(CelestialAsset.ID assetId, ItemStackWrapper resource) {
        LogisticsConfigUpdatePacket packet = LogisticsConfigUpdatePacket.remove(assetId, resource);
        StarmapActionSyncHandler.sendLogisticsConfig(packet);
    }

    // ── Signal mirror ──

    public static void updateClientSignals(Map<CelestialObjectId, Map<String, Long>> bySystem,
        Map<CelestialObjectId, Map<String, Long>> byPlanet) {
        systemSignals.clear();
        systemSignals.putAll(bySystem);
        planetSignals.clear();
        planetSignals.putAll(byPlanet);
        signalRevision++;
    }

    public static Map<String, Long> clientSignalsForSystem(CelestialObjectId systemId) {
        Map<String, Long> result = systemSignals.get(systemId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    public static Map<String, Long> clientSignalsForPlanet(CelestialObjectId anchorBodyId) {
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

    // ── Helpers ──

    private static void collectTransferTargets(CelestialObject current, List<TransferTarget> targets) {
        List<CelestialAsset> state = getState(current.id());
        for (CelestialAsset asset : state) {
            if (asset.isManageable()) {
                targets.add(new TransferTarget(asset.assetId, asset.displayName(), current));
            }
        }
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            collectTransferTargets(child, targets);
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            clear();
        }
    }
}
