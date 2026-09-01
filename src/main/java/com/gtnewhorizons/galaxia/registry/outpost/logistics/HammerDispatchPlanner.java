package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

public final class HammerDispatchPlanner {

    private HammerDispatchPlanner() {}

    public record Plan(CelestialAsset supplier, CelestialAsset requester, ItemStackWrapper resource,
        ModuleInstance hammerModule, ModuleHammer hammer, long sendAmount, int orderSize, long requiredEnergy,
        LogisticSignal.Scope deliveryScope, int travelTimeTicks, double departureDv, double shotDv,
        double tofOrbitalOsu, OrbitalTransferPlanner.TransferRoute route) {}

    public record Result(HammerDispatchStatus.Code code, long requiredEnergy, long storedEnergy, long sendAmount,
        int orderSize, Plan plan) {

        public static Result simple(HammerDispatchStatus.Code code, ModuleHammer hammer) {
            return new Result(code, 0L, hammer.energyStored(), 0L, 0, null);
        }

        public HammerDispatchStatus.Status toStatus() {
            return new HammerDispatchStatus.Status(code, requiredEnergy, storedEnergy, sendAmount, orderSize);
        }
    }

    public static Result evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        double orbitalTime) {
        return evaluate(supplier, hammerModule, assets, orbitalTime, null);
    }

    public static Result evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        double orbitalTime, UUID routeProfileTeamId) {
        if (supplier == null || hammerModule == null || !(hammerModule.component() instanceof ModuleHammer hammer)) {
            return new Result(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, 0L, 0L, 0L, 0, null);
        }

        Map<InventoryKey, LogisticsResourceConfig> supplierConfigs = supplier.logisticsConfig.snapshot();
        boolean hasExportConfig = supplierConfigs.values()
            .stream()
            .anyMatch(LogisticsResourceConfig::isSupplyEnabled);
        if (!hasExportConfig) return Result.simple(HammerDispatchStatus.Code.NO_EXPORT_CONFIG, hammer);

        boolean sawSurplusBlocked = false;
        Result bestBlockedStatus = null;

        for (Map.Entry<InventoryKey, LogisticsResourceConfig> supplierEntry : supplierConfigs.entrySet()) {
            LogisticsResourceConfig supplierCfg = supplierEntry.getValue();
            if (!supplierCfg.isSupplyEnabled()) continue;

            if (!(supplierEntry.getKey() instanceof ItemStackWrapper resource)) continue;
            long availableSurplus = itemAmount(supplier, resource) - supplyReserveFor(supplier, resource, supplierCfg);
            if (availableSurplus <= 0L) {
                sawSurplusBlocked = true;
                continue;
            }

            for (Object asset : assets) {
                if (!(asset instanceof CelestialAsset requester)) continue;
                if (supplier.assetId.equals(requester.assetId)) continue;
                if (!Objects.equals(supplier.systemKey, requester.systemKey)) continue;

                LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
                if (requesterCfg == null || !requesterCfg.isImportEnabled()) continue;

                long requesterStock = itemAmount(requester, resource);
                long inboundInTransit = LogisticStore.inboundInTransitAmount(requester.assetId, resource);
                long arrivedInbound = LogisticStore.arrivedInboundAmount(requester.assetId, resource);
                long requestedAmount = Math
                    .max(0L, importTargetFor(requester, resource, requesterCfg) - requesterStock - inboundInTransit);
                if (requestedAmount <= 0L) {
                    if (arrivedInbound > 0L) {
                        bestBlockedStatus = prefer(
                            destinationBlocked(hammer, arrivedInbound, requesterCfg.orderSize()),
                            bestBlockedStatus);
                    }
                    continue;
                }
                if (arrivedInbound > 0L) {
                    bestBlockedStatus = prefer(
                        destinationBlocked(hammer, arrivedInbound, requesterCfg.orderSize()),
                        bestBlockedStatus);
                    continue;
                }
                long sendAmount = dispatchAmount(hammer, availableSurplus, requestedAmount);
                if (sendAmount <= 0L) continue;
                long freeCapacity = destinationFreeItemCapacity(requester);
                if (freeCapacity < sendAmount) {
                    bestBlockedStatus = prefer(
                        destinationLacksPackageSpace(hammer, freeCapacity, requesterCfg.orderSize()),
                        bestBlockedStatus);
                    continue;
                }

                Result result = evaluateCandidateFor(
                    supplier,
                    requester,
                    resource,
                    availableSurplus,
                    requestedAmount,
                    requesterCfg,
                    hammerModule,
                    hammer,
                    orbitalTime,
                    routeProfileTeamId);
                if (result.code() == HammerDispatchStatus.Code.READY) return result;
                bestBlockedStatus = prefer(result, bestBlockedStatus);
            }
        }

        if (bestBlockedStatus != null) return bestBlockedStatus;
        if (sawSurplusBlocked) return Result.simple(HammerDispatchStatus.Code.NO_SURPLUS_AFTER_RESERVE, hammer);
        return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
    }

    public static Result evaluate(CelestialAsset supplier, ModuleInstance hammerModule, CelestialAsset requester,
        ItemStackWrapper resource, double orbitalTime, UUID routeProfileTeamId) {
        if (supplier == null || requester == null
            || resource == null
            || hammerModule == null
            || !(hammerModule.component() instanceof ModuleHammer hammer)) {
            return new Result(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, 0L, 0L, 0L, 0, null);
        }
        if (supplier.assetId.equals(requester.assetId) || !Objects.equals(supplier.systemKey, requester.systemKey)) {
            return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
        }

        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        if (supplierCfg == null || !supplierCfg.isSupplyEnabled()) {
            return Result.simple(HammerDispatchStatus.Code.NO_EXPORT_CONFIG, hammer);
        }

        long supplierStock = supplier instanceof Station station ? station.getCannonChestItems()
            .getOrDefault(resource, 0L) : itemAmount(supplier, resource);
        long availableSurplus = supplierStock - supplyReserveFor(supplier, resource, supplierCfg);
        if (availableSurplus <= 0L) return Result.simple(HammerDispatchStatus.Code.NO_SURPLUS_AFTER_RESERVE, hammer);

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        if (requesterCfg == null || !requesterCfg.isImportEnabled()) {
            return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
        }

        long requesterStock = itemAmount(requester, resource);
        long inboundInTransit = LogisticStore.inboundInTransitAmount(requester.assetId, resource);
        long arrivedInbound = LogisticStore.arrivedInboundAmount(requester.assetId, resource);
        long requestedAmount = Math
            .max(0L, importTargetFor(requester, resource, requesterCfg) - requesterStock - inboundInTransit);
        if (requestedAmount <= 0L) {
            if (arrivedInbound > 0L) return destinationBlocked(hammer, arrivedInbound, requesterCfg.orderSize());
            return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
        }
        if (arrivedInbound > 0L) return destinationBlocked(hammer, arrivedInbound, requesterCfg.orderSize());
        long sendAmount = dispatchAmount(hammer, availableSurplus, requestedAmount);
        if (sendAmount <= 0L) return Result.simple(HammerDispatchStatus.Code.NO_SURPLUS_AFTER_RESERVE, hammer);
        long freeCapacity = destinationFreeItemCapacity(requester);
        if (freeCapacity < sendAmount) {
            return destinationLacksPackageSpace(hammer, freeCapacity, requesterCfg.orderSize());
        }

        return evaluateCandidateFor(
            supplier,
            requester,
            resource,
            availableSurplus,
            requestedAmount,
            requesterCfg,
            hammerModule,
            hammer,
            orbitalTime,
            routeProfileTeamId);
    }

    private static Result evaluateCandidate(CelestialAsset supplier, CelestialAsset requester,
        ItemStackWrapper resource, ModuleInstance hammerModule, ModuleHammer hammer, boolean sameBody,
        boolean shareAnchor, long availableSurplus, long requestedAmount, int orderSize,
        OrbitalTransferPlanner.TransferRoute route) {
        long sendAmount = dispatchAmount(hammer, availableSurplus, requestedAmount);
        if (sendAmount <= 0L) return Result.simple(HammerDispatchStatus.Code.NO_SURPLUS_AFTER_RESERVE, hammer);
        if (!shareAnchor && hammer.variant() != HammerVariant.BIG) {
            return new Result(
                HammerDispatchStatus.Code.NEED_BIG_HAMMER,
                0L,
                hammer.energyStored(),
                sendAmount,
                orderSize,
                null);
        }
        if (!sameBody && route == null) {
            return new Result(
                HammerDispatchStatus.Code.ROUTE_UNAVAILABLE,
                0L,
                hammer.energyStored(),
                sendAmount,
                orderSize,
                null);
        }
        double departureDv = sameBody ? 1.0 : route.departureDv();
        double tofSeconds = sameBody ? 0.0 : route.tofSeconds();
        if (!sameBody && !hammer.config()
            .allows(departureDv, tofSeconds)) {
            HammerDispatchStatus.Code code = hammer.config()
                .mode() == AllowShootingConfig.Mode.WHEN_TOF_UNDER ? HammerDispatchStatus.Code.BLOCKED_BY_TOF_LIMIT
                    : HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT;
            return new Result(code, 0L, hammer.energyStored(), sendAmount, orderSize, null);
        }

        double shotDv = sameBody ? 1.0 : route.totalDv();
        long requiredEnergy = ModuleHammer.shotEnergyCost(shotDv);
        if (!hammer.canSpendShotEnergy(requiredEnergy)) {
            return new Result(
                HammerDispatchStatus.Code.NEED_ENERGY,
                requiredEnergy,
                hammer.energyStored(),
                sendAmount,
                orderSize,
                null);
        }

        Plan plan = new Plan(
            supplier,
            requester,
            resource,
            hammerModule,
            hammer,
            sendAmount,
            orderSize,
            requiredEnergy,
            sameBody ? LogisticSignal.Scope.PLANETARY : LogisticSignal.Scope.SYSTEM,
            sameBody ? 1 : route.tofTicks(),
            departureDv,
            shotDv,
            sameBody ? 0.0 : route.tofOsu(),
            route);
        return new Result(
            HammerDispatchStatus.Code.READY,
            requiredEnergy,
            hammer.energyStored(),
            sendAmount,
            orderSize,
            plan);
    }

    private static long dispatchAmount(ModuleHammer hammer, long availableSurplus, long requestedAmount) {
        return Math.min(Math.min(requestedAmount, availableSurplus), hammer.maxBatchSize());
    }

    private static long supplyReserveFor(CelestialAsset supplier, ItemStackWrapper resource,
        LogisticsResourceConfig supplierCfg) {
        long reserve = supplierCfg.minReserve();
        if (supplier instanceof AutomatedFacility facility) {
            reserve = Math.max(reserve, facility.effectiveLowerBound(resource));
        }
        return reserve;
    }

    private static long importTargetFor(CelestialAsset requester, ItemStackWrapper resource,
        LogisticsResourceConfig requesterCfg) {
        long target = requesterCfg.minReserve();
        if (requester instanceof AutomatedFacility facility) {
            target = Math.max(target, facility.effectiveLowerBound(resource));
        }
        return target;
    }

    private static Result destinationLacksPackageSpace(ModuleHammer hammer, long freeCapacity, int orderSize) {
        return new Result(
            HammerDispatchStatus.Code.DESTINATION_LACKS_PACKAGE_SPACE,
            0L,
            hammer.energyStored(),
            freeCapacity,
            orderSize,
            null);
    }

    private static Result destinationBlocked(ModuleHammer hammer, long arrivedAmount, int orderSize) {
        return new Result(
            HammerDispatchStatus.Code.DESTINATION_CAPACITY_BLOCKED,
            0L,
            hammer.energyStored(),
            arrivedAmount,
            orderSize,
            null);
    }

    private static long destinationFreeItemCapacity(CelestialAsset requester) {
        if (requester instanceof AutomatedFacility facility) return facility.remainingItemCapacity();
        return Long.MAX_VALUE;
    }

    private static long itemAmount(CelestialAsset asset, ItemStackWrapper resource) {
        if (asset instanceof AutomatedFacility facility) return facility.itemAmount(resource);
        if (asset instanceof IDistributedInventory physicalInventory) {
            return physicalInventory.getItemAmount(resource);
        }
        return 0L;
    }

    private static Result evaluateCandidateFor(CelestialAsset supplier, CelestialAsset requester,
        ItemStackWrapper resource, long availableSurplus, long requestedAmount, LogisticsResourceConfig requesterCfg,
        ModuleInstance hammerModule, ModuleHammer hammer, double orbitalTime, UUID routeProfileTeamId) {
        boolean sameBody = supplier.celestialObjectKey.equals(requester.celestialObjectKey);
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        boolean shareAnchor = sameBody || GalaxiaCelestialAPI
            .sharesPlanetaryAnchor(root, supplier.celestialObjectKey, requester.celestialObjectKey);
        OrbitalTransferPlanner.TransferRoute route = sameBody ? null
            : routeBetween(root, supplier, requester, orbitalTime, hammer, routeProfileTeamId);
        return evaluateCandidate(
            supplier,
            requester,
            resource,
            hammerModule,
            hammer,
            sameBody,
            shareAnchor,
            availableSurplus,
            requestedAmount,
            requesterCfg.orderSize(),
            route);
    }

    private static OrbitalTransferPlanner.TransferRoute routeBetween(CelestialObject root, CelestialAsset supplier,
        CelestialAsset requester, double orbitalTime, ModuleHammer hammer, UUID routeProfileTeamId) {
        CelestialObject srcBody = GalaxiaCelestialAPI.findBodyByKey(root, supplier.celestialObjectKey);
        CelestialObject dstBody = GalaxiaCelestialAPI.findBodyByKey(root, requester.celestialObjectKey);
        CelestialObject attractor = srcBody != null ? GalaxiaCelestialAPI.findStar(root, srcBody) : null;
        if (srcBody == null || dstBody == null || attractor == null) return null;

        hammer.markRouteProbeAttempted();
        boolean shouldProfile = routeProfileTeamId != null;
        long routeStartNanos = shouldProfile ? System.nanoTime() : 0L;
        try {
            return OrbitalTransferPlanner
                .computeRoute(root, attractor, srcBody, dstBody, orbitalTime, hammer.routePriority());
        } finally {
            if (shouldProfile) {
                HammerTrajectoryLoadTracker
                    .recordRouteComputation(routeProfileTeamId, System.nanoTime() - routeStartNanos);
            }
        }
    }

    private static Result prefer(Result result, Result current) {
        if (current == null) return result;
        return result.code()
            .priority()
            > current.code()
                .priority() ? result : current;
    }
}
