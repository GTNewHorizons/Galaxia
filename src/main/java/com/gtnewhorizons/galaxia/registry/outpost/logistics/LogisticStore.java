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

    private LogisticStore() {}

    public static List<LogisticsDelivery> activeDeliveries() {
        return activeDeliveries;
    }

    public static void addDelivery(LogisticsDelivery delivery) {
        activeDeliveries.add(delivery);
    }

    public static void clearDeliveries() {
        activeDeliveries.clear();
    }

    public static long inboundInTransitAmount(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        long total = 0L;
        for (LogisticsDelivery task : activeDeliveries) {
            if (!toAssetId.equals(task.data.toAssetId())) continue;
            if (!resource.equals(task.data.resourceId())) continue;
            total += task.data.amount();
        }
        return total;
    }

    public static long arrivedInboundAmount(CelestialAsset.ID toAssetId, ItemStackWrapper resource) {
        long total = 0L;
        for (LogisticsDelivery task : activeDeliveries) {
            if (!toAssetId.equals(task.data.toAssetId())) continue;
            if (!resource.equals(task.data.resourceId())) continue;
            if (!task.isArrived()) continue;
            total += task.data.amount();
        }
        return total;
    }

    public static void tickDeliveries() {
        for (int i = activeDeliveries.size() - 1; i >= 0; i--) {
            LogisticsDelivery current = activeDeliveries.get(i);
            CelestialAsset source = CelestialAssetStore.findAsset(current.data.fromAssetId());
            CelestialAsset destination = CelestialAssetStore.findAsset(current.data.toAssetId());
            if (destination == null) {
                if (canReceiveCargo(source)) {
                    deliverOrRetain(i, current, source, "refunded");
                } else {
                    removeOwnerlessDelivery(i, current);
                }
                continue;
            }
            LogisticsDelivery ticked = current.tick();
            if (ticked.isArrived()) {
                CelestialAsset recipient = canReceiveCargo(destination) ? destination : source;
                if (canReceiveCargo(recipient)) {
                    deliverOrRetain(i, ticked, recipient, recipient == destination ? "delivered" : "refunded");
                } else {
                    removeOwnerlessDelivery(i, ticked);
                }
            }
        }
    }

    private static boolean canReceiveCargo(CelestialAsset asset) {
        return asset instanceof AutomatedFacility || asset instanceof IDistributedInventory;
    }

    private static void removeOwnerlessDelivery(int index, LogisticsDelivery delivery) {
        LOG.warn(
            "[Logistics] Removing ownerless task {} from {} to {} containing {} x {} because neither endpoint can receive it",
            delivery.deliveryId,
            delivery.data.fromAssetId(),
            delivery.data.toAssetId(),
            delivery.data.amount(),
            delivery.data.resourceId());
        activeDeliveries.remove(index);
    }

    private static void deliverOrRetain(int index, LogisticsDelivery delivery, CelestialAsset recipient,
        String outcome) {
        long accepted = insertCargo(recipient, delivery.data.resourceId(), delivery.data.amount());
        long remaining = delivery.data.amount() - accepted;
        if (remaining > 0L) delivery.setAmount(remaining);
        else activeDeliveries.remove(index);
        LOG.debug(
            "[Logistics] Task {} {} {} x {} to {}",
            delivery.deliveryId,
            outcome,
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
        Map<ItemStackWrapper, Long> cannonItems = asset instanceof Station station ? station.getCannonChestItems()
            : Map.of();
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
        long lowerBound = asset instanceof AutomatedFacility facility ? facility.effectiveLowerBound(resource) : 0L;
        long importTarget = config.isImportEnabled() ? Math.max(config.minReserve(), lowerBound) : 0L;
        if (importTarget > 0L && importTarget > importStock) return importStock - importTarget;
        if (!config.isSupplyEnabled()) return 0L;
        long supply = asset instanceof Station station ? station.getCannonSupplyAmount(resource, config.minReserve())
            : importStock - Math.max(config.minReserve(), lowerBound);
        return Math.max(supply, 0L);
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
