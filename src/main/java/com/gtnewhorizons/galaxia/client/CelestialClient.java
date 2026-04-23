package com.gtnewhorizons.galaxia.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetBuildModulePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket.ConfigAction;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
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

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/*
 * This class abstracts CelestialAssetStore, LogisticSignalStore and stores all client state. Its purpose is to be used
 * as an API by the client so that it will never call the underlying stores to avoid server side logic
 */
@SideOnly(Side.CLIENT)
public final class CelestialClient {

    /// This is just used in the UI, I mark it as deprecated since it's just duplicate copied stuff, but I can't be
    /// bothered to fix the UI
    @Deprecated

    public record TransferTarget(CelestialAsset.ID assetId, String displayName, CelestialObject hostBody) {}

    /**
     * Client-side snapshot of in-flight logistics tasks. Updated by
     * {@link LogisticsSyncPacket}.
     * Always empty on the server; never null.
     */
    private static final List<LogisticsDelivery> deliveries = new ArrayList<>();
    private static int deliveryRevision = 0;
    private static int signalRevision = 0;

    /**
     * Client-side snapshot of aggregated logistics signals, indexed by system id.
     * Updated by {@link LogisticsSyncPacket}.
     * Always empty on the server; never null.
     * <p>
     * Inner map: resourceKey → net signed amount (positive = surplus, negative = deficit).
     */
    private static final Map<CelestialObjectId, Map<String, Long>> systemSignals = new LinkedHashMap<>();

    /**
     * Client-side snapshot of aggregated logistics signals, indexed by planetary anchor body id.
     * Updated alongside {@link #systemSignals}.
     */
    private static final Map<CelestialObjectId, Map<String, Long>> planetSignals = new LinkedHashMap<>();

    private CelestialClient() {}

    public static List<CelestialAsset> getState(CelestialObjectId celestialObjectId) {
        return CelestialAssetStore.getState(TempTeamCompat.getTeam(), celestialObjectId);
    }

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

    public static CelestialAsset getByAssetId(CelestialAsset.ID assetId) {
        return CelestialAssetStore.findAsset(assetId);
    }

    public static void add(AutomatedFacility state) {
        CelestialAssetStore.add(TempTeamCompat.getTeam(), state);
    }

    public static List<AutomatedFacility> allOutposts() {
        return CelestialAssetStore.allAssets()
            .stream()
            .filter(a -> a instanceof AutomatedFacility)
            .map(a -> (AutomatedFacility) a)
            .collect(Collectors.toList());
    }

    public static void clear() {
        deliveries.clear();
        deliveryRevision = 0;
        signalRevision = 0;
    }

    public static void createModule(ID assetId, FacilityModuleKind kind, boolean creativeBuildModeEnabled) {
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        ModuleInstance module = kind.createInstance();
        if (creativeBuildModeEnabled && Minecraft.getMinecraft().thePlayer.capabilities.isCreativeMode) {
            module.completeConstruction();
        }
        state.addModule(module);

        Galaxia.GALAXIA_NETWORK
            .sendToServer(new AssetBuildModulePacket(assetId, kind, module.id, creativeBuildModeEnabled));
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
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
        if (state == null) return;
        var modules = state.modules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return;
        ModuleInstance module = modules.get(moduleIndex);
        switch (action) {
            case ENABLE -> module.updateStatus(Buildable.Status.OPERATIONAL);
            case DISABLE -> module.updateStatus(Buildable.Status.DISABLED);
            case DESTROY -> state.removeModule(moduleIndex);
        }
        Galaxia.GALAXIA_NETWORK.sendToServer(AssetModuleUpdatePacket.action(assetId, moduleIndex, action));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, String payload) {
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
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
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, configAction, payload));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, boolean payload) {
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
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
                if (module.kind() == FacilityModuleKind.BIG_HAMMER
                    && module.component() instanceof ModuleHammer hammer) {
                    hammer.setPlanetaryHandling(payload);
                }
            }
            default -> {}
        }
        Galaxia.GALAXIA_NETWORK
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, configAction, payload));
    }

    public static void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction, double payload) {
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
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
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, configAction, payload));
    }

    public static <T extends Enum<T>> void updateModuleConfig(ID assetId, int moduleIndex, ConfigAction configAction,
        T payload) {
        AutomatedFacility state = CelestialAssetStore.findAsset(assetId) instanceof AutomatedFacility o ? o : null;
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
            .sendToServer(AssetModuleUpdatePacket.config(assetId, moduleIndex, configAction, payload));
    }

    /**
     * Replaces the client signal maps and bumps the signal revision counter.
     * Client-side only.
     */
    public static void updateClientSignals(Map<CelestialObjectId, Map<String, Long>> bySystem,
        Map<CelestialObjectId, Map<String, Long>> byPlanet) {
        systemSignals.clear();
        systemSignals.putAll(bySystem);
        planetSignals.clear();
        planetSignals.putAll(byPlanet);
        signalRevision++;
    }

    /**
     * Returns the aggregated net amounts (resourceKey → netAmount) for the given
     * star system, or an empty map if none are available.
     */
    public static Map<String, Long> clientSignalsForSystem(CelestialObjectId systemId) {
        Map<String, Long> result = systemSignals.get(systemId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /**
     * Returns the aggregated net amounts (resourceKey → netAmount) for the given
     * planetary anchor body, or an empty map if none are available.
     */
    public static Map<String, Long> clientSignalsForPlanet(CelestialObjectId anchorBodyId) {
        Map<String, Long> result = planetSignals.get(anchorBodyId);
        return result != null ? Collections.unmodifiableMap(result) : Collections.emptyMap();
    }

    /** Monotonically incrementing counter; bumped each time signal data is replaced. */
    public static int clientSignalRevision() {
        return signalRevision;
    }

    // -------------------------------------------------------------------------
    // Client-side task snapshot (populated by LogisticsTasksSyncPacket)
    // -------------------------------------------------------------------------

    /** Replaces the client delivery list and bumps the revision counter. Client-side only. */
    public static void updateClientDeliveries(List<LogisticsDelivery> newDeliveries) {
        deliveries.clear();
        newDeliveries.stream()
            .filter(t -> t.data.resourceId() != null)
            .forEach(deliveries::add);
        deliveryRevision++;
    }

    /** Returns an unmodifiable view of the latest client delivery snapshot. */
    public static List<LogisticsDelivery> clientDeliveries() {
        return Collections.unmodifiableList(deliveries);
    }

    /** Monotonically incrementing counter; bumped each time deliveries are replaced. */
    public static int clientDeliveryRevision() {
        return deliveryRevision;
    }

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
