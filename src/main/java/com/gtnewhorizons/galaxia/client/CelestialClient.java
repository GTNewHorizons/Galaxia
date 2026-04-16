package com.gtnewhorizons.galaxia.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.network.LogisticsSyncPacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/*
 * This class abstracts CelestialAssetStore, LogisticSignalStore and stores all client state. Its purpose is to be used
 * as an API by the client so that it will never call the underlying stores to avoid server side logic
 */
@SideOnly(Side.CLIENT)
public final class CelestialClient {

    public static List<CelestialAsset> getState(CelestialObjectId celestialObjectId) {
        // TODO: Is it safe to use the store here? (Store = server state)
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

    public static void add(AutomatedOutpost state) {
        CelestialAssetStore.add(TempTeamCompat.getTeam(), state);
    }

    public static List<AutomatedOutpost> allOutposts() {
        return CelestialAssetStore.allAssets()
            .stream()
            .filter(a -> a instanceof AutomatedOutpost)
            .map(a -> (AutomatedOutpost) a)
            .collect(Collectors.toList());
    }

    public static void clear() {
        deliveries.clear();
        deliveryRevision = 0;
        signalRevision = 0;
    }

    /// This is just used in the UI, I mark it as deprecated since it's just duplicate copied stuff, but I can't be
    /// bothered to fix the UI
    @Deprecated

    public record TransferTarget(CelestialAsset.ID assetId, String displayName, CelestialObject hostBody) {}

    public static List<TransferTarget> getTransferTargetsInSystem(CelestialObject root, CelestialObject body) {
        List<TransferTarget> targets = new ArrayList<>();
        if (body == null) return targets;
        CelestialObject hostStar = GalaxiaCelestialAPI.findStar(root, body);
        if (hostStar == null) return targets;
        collectTransferTargets(hostStar, targets);
        return targets;
    }

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
