package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

// TODO: Make store work with fluids as well, there is already a half implementation throughout the codebase. Use
// InventoryKey for the refactor
public final class LogisticStore {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private static final List<LogisticsDelivery> activeDeliveries = new ArrayList<>();
    private static final List<LogisticsDelivery> deliveryView = Collections.unmodifiableList(activeDeliveries);
    private static final Map<CelestialAsset.ID, Map<ItemStackWrapper, InboundAmounts>> inbound = new LinkedHashMap<>();

    private LogisticStore() {}

    public static List<LogisticsDelivery> activeDeliveries() {
        return deliveryView;
    }

    public static void addDelivery(LogisticsDelivery delivery) {
        activeDeliveries.add(delivery);
        adjustInbound(delivery, delivery.data.amount(), delivery.isArrived() ? delivery.data.amount() : 0L);
    }

    public static void clearDeliveries() {
        activeDeliveries.clear();
        inbound.clear();
    }

    /** Pending cargo includes arrived cargo still waiting for inventory space. */
    public record InboundAmounts(long allPending, long arrived) {

        private static final InboundAmounts EMPTY = new InboundAmounts(0L, 0L);
    }

    public static InboundAmounts inboundAmounts(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        return inbound.getOrDefault(toAssetId, Map.of())
            .getOrDefault(resource, InboundAmounts.EMPTY);
    }

    public static long inboundInTransitAmount(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        return inboundAmounts(toAssetId, resource).allPending();
    }

    public static long arrivedInboundAmount(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        return inboundAmounts(toAssetId, resource).arrived();
    }

    private static void adjustInbound(LogisticsDelivery delivery, long pendingDelta, long arrivedDelta) {
        if (pendingDelta == 0L && arrivedDelta == 0L) return;
        CelestialAsset.ID destination = delivery.data.toAssetId();
        ItemStackWrapper resource = delivery.data.resourceId();
        Map<ItemStackWrapper, InboundAmounts> resources = inbound
            .computeIfAbsent(destination, ignored -> new LinkedHashMap<>());
        InboundAmounts previous = resources.getOrDefault(resource, InboundAmounts.EMPTY);
        InboundAmounts next = new InboundAmounts(
            previous.allPending() + pendingDelta,
            previous.arrived() + arrivedDelta);
        if (next.allPending() == 0L && next.arrived() == 0L) {
            resources.remove(resource);
            if (resources.isEmpty()) inbound.remove(destination);
        } else {
            resources.put(resource, next);
        }
    }

    private static void removeDelivery(int index, LogisticsDelivery delivery) {
        adjustInbound(delivery, -delivery.data.amount(), delivery.isArrived() ? -delivery.data.amount() : 0L);
        activeDeliveries.remove(index);
    }

    public static void tickDeliveries() {
        for (int i = activeDeliveries.size() - 1; i >= 0; i--) {
            LogisticsDelivery current = activeDeliveries.get(i);
            boolean wasArrived = current.isArrived();
            LogisticsDelivery ticked = current.tick();
            if (wasArrived != ticked.isArrived()) {
                adjustInbound(ticked, 0L, ticked.isArrived() ? ticked.data.amount() : -ticked.data.amount());
            }
            if (!ticked.isArrived()) continue;
            CelestialAsset destination = CelestialAssetStore.findAsset(ticked.data.toAssetId());
            if (canReceiveCargo(destination)) deliverOrRetain(i, ticked, destination);
            else loseDelivery(i, ticked);
        }
    }

    private static boolean canReceiveCargo(CelestialAsset asset) {
        return asset instanceof AutomatedFacility || asset instanceof IDistributedInventory;
    }

    private static void loseDelivery(int index, LogisticsDelivery delivery) {
        LOG.warn(
            "[Logistics] Lost delivery {} from {} to {} containing {} x {} because the destination cannot receive cargo",
            delivery.deliveryId,
            delivery.data.fromAssetId(),
            delivery.data.toAssetId(),
            delivery.data.amount(),
            delivery.data.resourceId());
        removeDelivery(index, delivery);
    }

    private static void deliverOrRetain(int index, LogisticsDelivery delivery, CelestialAsset recipient) {
        long accepted = insertCargo(recipient, delivery.data.resourceId(), delivery.data.amount());
        long remaining = delivery.data.amount() - accepted;
        if (remaining > 0L) {
            adjustInbound(delivery, -accepted, delivery.isArrived() ? -accepted : 0L);
            delivery.setAmount(remaining);
        } else removeDelivery(index, delivery);
        LOG.debug(
            "[Logistics] Task {} delivered {} x {} to {}",
            delivery.deliveryId,
            accepted,
            delivery.data.resourceId(),
            recipient.assetId);
    }

    private static long insertCargo(CelestialAsset recipient, ItemStackWrapper resource, long amount) {
        if (recipient instanceof AutomatedFacility facility) return facility.insert(resource, amount);
        if (recipient instanceof IDistributedInventory physicalInventory) {
            return physicalInventory.updateContents(resource, amount);
        }
        return 0L;
    }

    public static List<LogisticSignal> collectSignals(Iterable<? extends CelestialAsset> assets) {
        List<LogisticSignal> signals = new ArrayList<>();
        for (CelestialAsset asset : assets) {
            if (asset instanceof Station station && station.getTileController() == null) continue;
            collectSignals(asset, signals);
        }
        return List.copyOf(signals);
    }

    private static void collectSignals(CelestialAsset asset, List<LogisticSignal> signals) {
        Map<ItemStackWrapper, Long> snapshot = itemSnapshot(asset);
        Set<ItemStackWrapper> allResources = new LinkedHashSet<>();
        for (InventoryKey key : asset.logisticsConfig.snapshot()
            .keySet()) {
            if (key instanceof ItemStackWrapper item) {
                allResources.add(item);
            }
        }
        allResources.addAll(snapshot.keySet());

        for (ItemStackWrapper resource : allResources) {
            LogisticsResourceConfig config = asset.logisticsConfig.get(resource);
            long importStock = snapshot.getOrDefault(resource, 0L);
            long amount = signalAmount(asset, resource, config, importStock);
            if (amount == 0L) continue;
            signals.add(
                new LogisticSignal(
                    asset.assetId,
                    asset.systemKey,
                    resource,
                    amount,
                    LogisticSignal.Scope.SYSTEM,
                    asset.celestialObjectKey,
                    asset.planetaryAnchorBodyKey));
        }
    }

    private static Map<ItemStackWrapper, Long> itemSnapshot(CelestialAsset asset) {
        if (asset instanceof AutomatedFacility facility) return facility.itemSnapshot();
        if (asset instanceof IDistributedInventory physicalInventory) return physicalInventory.aggregatedItems();
        return Map.of();
    }

    private static long signalAmount(CelestialAsset asset, ItemStackWrapper resource, LogisticsResourceConfig config,
        long importStock) {
        long requested = requestedAmount(asset, resource, config, importStock);
        if (requested > 0L) return -requested;
        if (!config.isSupplyEnabled()) return 0L;
        long supply = asset instanceof Station station ? station.getCannonSupplyAmount(resource, config.minReserve())
            : importStock - reserveFor(asset, resource, config);
        return Math.max(supply, 0L);
    }

    static long reserveFor(CelestialAsset asset, ItemStackWrapper resource, LogisticsResourceConfig config) {
        return asset instanceof AutomatedFacility facility ? Math.addExact(
            Math.max(config.minReserve(), facility.effectiveLowerBound(resource)),
            facility.operationMaterialNeed(resource)) : config.minReserve();
    }

    static long requestedAmount(CelestialAsset asset, ItemStackWrapper resource, LogisticsResourceConfig config,
        long stock) {
        if (!config.isImportEnabled()) return 0L;
        long missing = Math.max(0L, reserveFor(asset, resource, config) - stock);
        return Math.max(0L, missing - inboundAmounts(asset.assetId, resource).allPending());
    }

    public static Map<CelestialObjectKey, List<LogisticSignal>> groupSignals(List<LogisticSignal> signals,
        LogisticSignal.Scope scope) {
        Map<CelestialObjectKey, List<LogisticSignal>> result = new LinkedHashMap<>();
        for (LogisticSignal signal : signals) {
            if (signal.scope() != scope) continue;
            result.computeIfAbsent(scopeKeyFor(signal), ignored -> new ArrayList<>())
                .add(signal);
        }
        for (Map.Entry<CelestialObjectKey, List<LogisticSignal>> entry : result.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    public static List<LogisticSignal> signalsOwnedBy(UUID teamId, List<LogisticSignal> signals) {
        List<LogisticSignal> owned = new ArrayList<>();
        for (LogisticSignal signal : signals) {
            if (CelestialAssetStore.isOwnedBy(teamId, signal.outpostAssetId())) owned.add(signal);
        }
        return List.copyOf(owned);
    }

    private static CelestialObjectKey scopeKeyFor(LogisticSignal signal) {
        return switch (signal.scope()) {
            case PLANETARY -> signal.planetaryAnchorBodyKey();
            case SYSTEM -> signal.systemKey();
            case GALACTIC -> signal.systemKey();
        };
    }
}
