package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

public final class CoreDispatchPlanner {

    private CoreDispatchPlanner() {}

    public static boolean tryDispatch(CelestialAsset supplier, CelestialAsset requester, ItemStackWrapper resource,
        double orbitalTime) {
        if (supplier == null || requester == null || resource == null) return false;
        if (!(requester instanceof AutomatedFacility requestingFacility)) return false;
        if (!requestingFacility.isUpkeepAutoOrderEnabled(resource)) return false;
        if (supplier.assetId.equals(requester.assetId) || !Objects.equals(supplier.systemId, requester.systemId)) {
            return false;
        }

        LogisticsResourceConfig supplierConfig = supplier.logisticsConfig.get(resource);
        if (supplierConfig == null || !supplierConfig.isSupplyEnabled()) return false;

        long supplyReserve = supplierConfig.minReserve();
        if (supplier instanceof AutomatedFacility supplyingFacility) {
            supplyReserve = Math.max(supplyReserve, supplyingFacility.effectiveLowerBound(resource));
        }

        long availableSurplus = CelestialAsset.getItemAmount(supplier, resource) - supplyReserve;
        if (availableSurplus <= 0L) return false;

        long requesterStock = CelestialAsset.getItemAmount(requester, resource);
        long inboundInTransit = LogisticStore.inboundInTransitAmount(requester.assetId, resource);
        long requestedAmount = Math
            .max(0L, requestingFacility.effectiveLowerBound(resource) - requesterStock - inboundInTransit);
        if (requestedAmount <= 0L) return false;

        long sendAmount = Math.min(availableSurplus, requestedAmount);
        long removed = supplier.updateContents(resource, -(int) Math.min(sendAmount, Integer.MAX_VALUE), true);
        if (removed <= 0L) return false;

        boolean sameBody = supplier.celestialObjectId.equals(requester.celestialObjectId);
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                supplier.assetId,
                requester.assetId,
                resource,
                removed,
                sameBody ? 1 : 20,
                sameBody ? LogisticSignal.Scope.PLANETARY : LogisticSignal.Scope.SYSTEM,
                supplier.celestialObjectId,
                requester.celestialObjectId,
                orbitalTime,
                0.0D,
                null));
        return true;
    }
}
