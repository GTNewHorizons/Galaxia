package com.gtnewhorizons.galaxia.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket.ConfigAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side mirror of server state. Owns its own asset storage — never touches
 * {@link CelestialAssetStore} directly except for server-side factory methods.
 */
@SideOnly(Side.CLIENT)
public final class CelestialClient {

    @Deprecated
    public record TransferTarget(CelestialAsset.ID assetId, String displayName, CelestialObject hostBody) {}

    // ── Client-side asset mirror ──

    private static final Map<CelestialAsset.ID, CelestialAsset> clientAssets = new LinkedHashMap<>();

    public static CelestialAsset getByAssetId(CelestialAsset.ID assetId) {
        return clientAssets.get(assetId);
    }

    public static void add(AutomatedFacility state) {
        clientAssets.put(state.assetId, state);
    }

    public static List<CelestialAsset> getState(CelestialObjectId celestialObjectId) {
        return clientAssets.values()
            .stream()
            .filter(a -> a.celestialObjectId == celestialObjectId)
            .collect(Collectors.toList());
    }

    public static List<AutomatedFacility> allOutposts() {
        return clientAssets.values()
            .stream()
            .filter(a -> a instanceof AutomatedFacility)
            .map(a -> (AutomatedFacility) a)
            .collect(Collectors.toList());
    }

    // ── Server-side asset creation (delegates to the shared store) ──

    public static CelestialAsset createAssetInConstruction(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind) {
        return CelestialAssetStore
            .createAssetInConstruction(TempTeamCompat.getTeam(), celestialObjectId, displayName, kind);
    }

    public static CelestialAsset createOperationalAsset(CelestialObjectId celestialObjectId, String displayName,
        CelestialAsset.Kind kind) {
        return CelestialAssetStore
            .createOperationalAsset(TempTeamCompat.getTeam(), celestialObjectId, displayName, kind);
    }

    // ── Logistics mirror ──

    private static final List<LogisticsDelivery> deliveries = new ArrayList<>();
    private static int deliveryRevision = 0;
    private static int signalRevision = 0;

    private static final Map<CelestialObjectId, Map<String, Long>> systemSignals = new LinkedHashMap<>();
    private static final Map<CelestialObjectId, Map<String, Long>> planetSignals = new LinkedHashMap<>();

    private CelestialClient() {}

    public static void clear() {
        clientAssets.clear();
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
        StationTileCoord anchor = tileCoord != null ? tileCoord : StationTileCoord.CORE;
        ModuleInstance module = kind.create(anchor, ModuleShape.SINGLE, kind.defaultTier());
        boolean creativePlayer = Minecraft.getMinecraft().thePlayer != null
            && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode;
        if (creativeBuildModeEnabled && creativePlayer) {
            module.completeConstruction();
        }
        state.addModule(module);
        StationLayout layout = state.stationLayout();
        if (layout != null && module.anchor() != null) {
            layout.place(module);
        }
        Galaxia.GALAXIA_NETWORK.sendToServer(
            new AssetBuildModulePacket(
                assetId,
                kind,
                ModuleShape.SINGLE,
                kind.defaultTier(),
                creativeBuildModeEnabled,
                tileCoord));
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
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return;
        ModuleInstance module = modules.get(moduleIndex);
        switch (action) {
            case ENABLE -> module.updateStatus(Buildable.Status.OPERATIONAL);
            case DISABLE -> module.updateStatus(Buildable.Status.DISABLED);
            case DESTROY -> state.removeModule(module.id);
        }
        Galaxia.GALAXIA_NETWORK.sendToServer(AssetModuleUpdatePacket.action(assetId, moduleIndex, module.id, action));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, String payload) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return;
        ModuleInstance module = modules.get(moduleIndex);
        if (!(module.component() instanceof ModuleMiner miner)) return;
        switch (configAction) {
            case ADD_MINER_BLACKLIST -> miner.addToBlacklist(payload);
            case REMOVE_MINER_BLACKLIST -> miner.removeFromBlacklist(payload);
        }
        Galaxia.GALAXIA_NETWORK
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, boolean payload) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return;
        ModuleInstance module = modules.get(moduleIndex);
        switch (configAction) {
            case SET_MINER_COPY_SETTINGS -> {
                if (module.component() instanceof ModuleMiner miner) {
                    miner.setCopySettingToOtherMiners(payload);
                }
            }
            case SET_PLANETARY_HANDLING -> {
                if (module.component() instanceof ModuleHammer hammer) {
                    hammer.setPlanetaryHandling(payload);
                }
            }
            default -> {}
        }
        Galaxia.GALAXIA_NETWORK
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, double payload) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return;
        ModuleInstance module = modules.get(moduleIndex);
        switch (configAction) {
            case SET_ALLOW_SHOOTING_THRESHOLD -> {
                if (module.component() instanceof ModuleHammer hammer) {
                    hammer.setConfig(
                        new AllowShootingConfig(
                            hammer.config()
                                .mode(),
                            payload));
                }
            }
            default -> {}
        }
        Galaxia.GALAXIA_NETWORK
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
    }

    public static <T extends Enum<T>> void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction,
        T payload) {
        AutomatedFacility state = getByAssetId(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return;
        ModuleInstance module = modules.get(moduleIndex);
        if (!(module.component() instanceof ModuleHammer hammer)) return;

        switch (configAction) {
            case SET_ALLOW_SHOOTING_MODE -> {
                AllowShootingConfig.Mode mode = (AllowShootingConfig.Mode) payload;
                hammer.setConfig(
                    new AllowShootingConfig(
                        mode,
                        hammer.config()
                            .threshold()));
            }
            case SET_ROUTE_PRIORITY -> {
                OrbitalTransferPlanner.RoutePriority priority = (OrbitalTransferPlanner.RoutePriority) payload;
                hammer.setRoutePriority(priority);
            }
            default -> {}
        }
        Galaxia.GALAXIA_NETWORK
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, module.id, configAction, payload));
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
