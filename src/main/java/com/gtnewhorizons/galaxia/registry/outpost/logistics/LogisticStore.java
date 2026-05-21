package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

// TODO: Make store work with fluids as well, there is already a half implementation throughout the codebase. Use
// InventoryKey for the refactor
public final class LogisticStore {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private static final List<LogisticsDelivery> activeDeliveries = new ArrayList<>();
    private static final Map<CelestialAsset.ID, Map<ItemStackWrapper, LogisticSignal>> outpostSignals = new LinkedHashMap<>();

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

    public static void tickDeliveries() {
        for (int i = activeDeliveries.size() - 1; i >= 0; i--) {
            LogisticsDelivery current = activeDeliveries.get(i);
            if (CelestialAssetStore.findAsset(current.data.fromAssetId()) == null
                || CelestialAssetStore.findAsset(current.data.toAssetId()) == null) {
                activeDeliveries.remove(i);
                continue;
            }
            LogisticsDelivery ticked = current.tick();
            if (ticked.isArrived()) {
                CelestialAsset destination = CelestialAssetStore.findAsset(ticked.data.toAssetId());
                if (destination == null) {
                    activeDeliveries.remove(i);
                    LOG.warn(
                        "[Logistics] Task {} arrived but destination outpost {} not found; resources lost.",
                        ticked.deliveryId,
                        ticked.data.toAssetId());
                    return;
                }
                long accepted = destination.updateContents(
                    ticked.data.resourceId(),
                    (int) Math.min(ticked.data.amount(), Integer.MAX_VALUE),
                    true);
                long remaining = ticked.data.amount() - accepted;
                if (remaining > 0L) {
                    ticked.setAmount(remaining);
                } else {
                    activeDeliveries.remove(i);
                }
                LOG.debug(
                    "[Logistics] Task {} delivered {} x {} to {}",
                    ticked.deliveryId,
                    accepted,
                    ticked.data.resourceId(),
                    ticked.data.toAssetId());
            }
        }
    }

    public static void updateSignalsForFacility(CelestialAsset asset) {
        CelestialAsset.ID assetId = asset.assetId;
        Map<ItemStackWrapper, Long> snapshot = asset.aggregatedItems();
        Map<ItemStackWrapper, Long> cannonItems = null;
        if (asset instanceof Station station) {
            cannonItems = station.getCannonChestItems();
        }

        Map<ItemStackWrapper, LogisticSignal> currentSignals = outpostSignals
            .computeIfAbsent(assetId, k -> new LinkedHashMap<>());

        List<ItemStackWrapper> allResources = new ArrayList<>();
        for (InventoryKey key : asset.logisticsConfig.snapshot()
            .keySet()) {
            if (key instanceof ItemStackWrapper item) {
                allResources.add(item);
            }
        }
        for (ItemStackWrapper r : snapshot.keySet()) {
            if (!allResources.contains(r)) allResources.add(r);
        }

        CelestialObjectId bodyId = asset.celestialObjectId;
        CelestialObjectId systemId = asset.systemId;
        CelestialObjectId planetaryAnchorBodyId = asset.planetaryAnchorBodyId;

        for (ItemStackWrapper resource : allResources) {
            long stock = snapshot.getOrDefault(resource, 0L);
            LogisticsResourceConfig cfg = asset.logisticsConfig.get(resource);

            LogisticSignal oldSignal = currentSignals.get(resource);

            long newAmount = 0;
            LogisticSignal.Scope newScope = LogisticSignal.Scope.SYSTEM;

            if (cfg.isImportEnabled() && stock < cfg.minReserve()) {
                newAmount = -(cfg.minReserve() - stock);
            } else if (cfg.isSupplyEnabled()) {
                long supplyStock = stock;
                if (cannonItems != null) {
                    supplyStock += cannonItems.getOrDefault(resource, 0L);
                }
                if (supplyStock > cfg.minReserve()) {
                    newAmount = supplyStock - cfg.minReserve();
                }
            }

            if (newAmount == 0) {
                if (oldSignal != null) {
                    currentSignals.remove(resource);
                }
            } else {
                LogisticSignal newSignal = new LogisticSignal(
                    assetId,
                    systemId,
                    resource,
                    newAmount,
                    newScope,
                    bodyId,
                    planetaryAnchorBodyId);

                if (!Objects.equals(oldSignal, newSignal)) {
                    currentSignals.put(resource, newSignal);
                }
            }
        }
    }

    public static Map<CelestialObjectId, List<LogisticSignal>> allSignalsForScope(LogisticSignal.Scope scope) {
        Map<CelestialObjectId, List<LogisticSignal>> result = new LinkedHashMap<>();

        for (Map<ItemStackWrapper, LogisticSignal> outpostMap : outpostSignals.values()) {
            for (LogisticSignal signal : outpostMap.values()) {
                if (signal.scope() == scope) {
                    CelestialObjectId scopeKey = scopeKeyFor(signal);
                    result.computeIfAbsent(scopeKey, k -> new ArrayList<>())
                        .add(signal);
                }
            }
        }

        Map<CelestialObjectId, List<LogisticSignal>> safe = new LinkedHashMap<>(result.size());
        for (Map.Entry<CelestialObjectId, List<LogisticSignal>> e : result.entrySet()) {
            safe.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(safe);
    }

    private static CelestialObjectId scopeKeyFor(LogisticSignal signal) {
        return switch (signal.scope()) {
            case PLANETARY -> signal.planetaryAnchorBodyId();
            case SYSTEM -> signal.systemId();
            case GALACTIC -> signal.systemId();
        };
    }
}
