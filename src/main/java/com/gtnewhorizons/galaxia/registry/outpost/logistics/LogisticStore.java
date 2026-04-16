package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsConfiguration;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

public final class LogisticStore {

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
                activeDeliveries.remove(i);
                CelestialAsset destination = CelestialAssetStore.findAsset(ticked.data.toAssetId());
                if (destination == null) {
                    Galaxia.LOG.warn(
                        "[Logistics] Task {} arrived but destination outpost {} not found; resources lost.",
                        ticked.deliveryId,
                        ticked.data.toAssetId());
                    return;
                }
                if (destination instanceof AutomatedOutpost outpost) {
                    outpost.inventory.add(ticked.data.resourceId(), ticked.data.amount());
                    Galaxia.LOG.debug(
                        "[Logistics] Task {} delivered {} x {} to {}",
                        ticked.deliveryId,
                        ticked.data.amount(),
                        ticked.data.resourceId(),
                        ticked.data.toAssetId());
                }
            } else {
                activeDeliveries.set(i, ticked);
            }
        }
    }

    public static void updateSignalsForOutpost(AutomatedOutpost outpost) {
        CelestialAsset.ID outpostAssetId = outpost.assetId;
        Map<ItemStackWrapper, Long> snapshot = outpost.inventory.snapshot();
        LogisticsConfiguration config = outpost.logisticsConfig;

        Map<ItemStackWrapper, LogisticSignal> currentSignals = outpostSignals
            .computeIfAbsent(outpostAssetId, k -> new LinkedHashMap<>());

        List<ItemStackWrapper> allResources = new ArrayList<>(
            config.snapshot()
                .keySet());
        for (ItemStackWrapper r : snapshot.keySet()) {
            if (!allResources.contains(r)) allResources.add(r);
        }

        CelestialObjectId bodyId = outpost.celestialObjectId;
        CelestialObjectId systemId = outpost.systemId;
        CelestialObjectId planetaryAnchorBodyId = outpost.planetaryAnchorBodyId;

        for (ItemStackWrapper resource : allResources) {
            long stock = outpost.inventory.getAmount(resource);
            LogisticsResourceConfig cfg = config.get(resource);

            LogisticSignal oldSignal = currentSignals.get(resource);

            long newAmount = 0;
            LogisticSignal.Scope newScope = LogisticSignal.Scope.SYSTEM;

            if (cfg.isImportEnabled() && stock < cfg.minReserve()) {
                newAmount = -(cfg.minReserve() - stock);
            } else if (cfg.isSupplyEnabled() && stock > cfg.minReserve()) {
                newAmount = stock - cfg.minReserve();
            }

            if (newAmount == 0) {
                if (oldSignal != null) {
                    currentSignals.remove(resource);
                }
            } else {
                LogisticSignal newSignal = new LogisticSignal(
                    outpostAssetId,
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
