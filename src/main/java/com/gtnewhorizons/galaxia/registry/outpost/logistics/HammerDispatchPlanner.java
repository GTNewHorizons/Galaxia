package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.Map;
import java.util.Objects;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

public final class HammerDispatchPlanner {

    private HammerDispatchPlanner() {}

    public interface RouteObserver {

        RouteObserver NONE = new RouteObserver() {};

        default void beforeRouteComputation(ModuleInstance hammerModule, ModuleHammer hammer) {}

        default void afterRouteComputation(long elapsedNanos) {}
    }

    public record Candidate(boolean sameBody, boolean shareAnchor, boolean routeAvailable, AutomatedFacility requester,
        ItemStackWrapper resource, long availableSurplus, long requestedAmount, int orderSize, double departureDv,
        double totalDv, double tofSeconds, int tofTicks, double tofOsu, OrbitalTransferPlanner.TransferRoute route) {

        public static Candidate fromStatusCandidate(HammerDispatchStatus.Candidate candidate) {
            return new Candidate(
                candidate.sameBody(),
                candidate.shareAnchor(),
                candidate.routeAvailable(),
                null,
                null,
                candidate.availableSurplus(),
                candidate.requestedAmount(),
                candidate.orderSize(),
                candidate.departureDv(),
                candidate.totalDv(),
                candidate.tofSeconds(),
                candidate.sameBody() ? 1 : 0,
                0.0,
                null);
        }
    }

    public record Plan(AutomatedFacility supplier, AutomatedFacility requester, ItemStackWrapper resource,
        ModuleInstance hammerModule, ModuleHammer hammer, long sendAmount, int orderSize, long requiredEnergy,
        LogisticSignal.Scope deliveryScope, int travelTimeTicks, double departureDv, double shotDv,
        double tofOrbitalSeconds, OrbitalTransferPlanner.TransferRoute route) {}

    public record Result(HammerDispatchStatus.Code code, long requiredEnergy, long storedEnergy, long sendAmount,
        int orderSize, Plan plan) {

        public static Result simple(HammerDispatchStatus.Code code, ModuleHammer hammer) {
            return new Result(code, 0L, hammer.energyStored(), 0L, 0, null);
        }

        public HammerDispatchStatus.Status toStatus() {
            return new HammerDispatchStatus.Status(code, requiredEnergy, storedEnergy, sendAmount, orderSize);
        }
    }

    public static Result evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, double orbitalTime) {
        return evaluate(
            supplier,
            hammerModule,
            CelestialAssetStore.allAssets(),
            LogisticStore.activeDeliveries(),
            orbitalTime);
    }

    public static Result evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        Iterable<LogisticsDelivery> deliveries, double orbitalTime) {
        return evaluate(supplier, hammerModule, assets, deliveries, orbitalTime, RouteObserver.NONE);
    }

    public static Result evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        Iterable<LogisticsDelivery> deliveries, double orbitalTime, RouteObserver routeObserver) {
        if (supplier == null || hammerModule == null || !(hammerModule.component() instanceof ModuleHammer hammer)) {
            return new Result(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, 0L, 0L, 0L, 0, null);
        }

        Map<ItemStackWrapper, LogisticsResourceConfig> supplierConfigs = supplier.logisticsConfig.snapshot();
        boolean hasExportConfig = supplierConfigs.values()
            .stream()
            .anyMatch(LogisticsResourceConfig::isSupplyEnabled);
        if (!hasExportConfig) return Result.simple(HammerDispatchStatus.Code.NO_EXPORT_CONFIG, hammer);

        boolean sawSurplusBlocked = false;
        boolean sawAnyRequest = false;
        Result bestBlockedStatus = null;

        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> supplierEntry : supplierConfigs.entrySet()) {
            LogisticsResourceConfig supplierCfg = supplierEntry.getValue();
            if (!supplierCfg.isSupplyEnabled()) continue;

            ItemStackWrapper resource = supplierEntry.getKey();
            long availableSurplus = supplier.inventory.getAmount(resource) - supplierCfg.minReserve();
            if (availableSurplus <= 0L) {
                sawSurplusBlocked = true;
                continue;
            }

            for (Object asset : assets) {
                if (!(asset instanceof AutomatedFacility requester)) continue;
                if (supplier.assetId.equals(requester.assetId)) continue;
                if (!Objects.equals(supplier.systemId, requester.systemId)) continue;

                LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
                if (requesterCfg == null || !requesterCfg.isImportEnabled()) continue;

                long requesterStock = requester.inventory.getAmount(resource);
                long inboundInTransit = inboundInTransitAmount(deliveries, requester.assetId, resource);
                long requestedAmount = Math.max(0L, requesterCfg.minReserve() - requesterStock - inboundInTransit);
                if (requestedAmount <= 0L) continue;

                Candidate candidate = candidateFor(
                    supplier,
                    requester,
                    resource,
                    availableSurplus,
                    requestedAmount,
                    requesterCfg,
                    hammerModule,
                    hammer,
                    orbitalTime,
                    routeObserver);
                sawAnyRequest = true;

                Result result = evaluateCandidate(hammer, candidate, supplier, hammerModule);
                if (result.code() == HammerDispatchStatus.Code.READY) return result;
                bestBlockedStatus = prefer(result, bestBlockedStatus);
            }
        }

        if (bestBlockedStatus != null) return bestBlockedStatus;
        if (sawSurplusBlocked) return Result.simple(HammerDispatchStatus.Code.NO_SURPLUS_AFTER_RESERVE, hammer);
        return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
    }

    public static Result evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, AutomatedFacility requester,
        ItemStackWrapper resource, Iterable<LogisticsDelivery> deliveries, double orbitalTime,
        RouteObserver routeObserver) {
        if (supplier == null || requester == null
            || resource == null
            || hammerModule == null
            || !(hammerModule.component() instanceof ModuleHammer hammer)) {
            return new Result(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, 0L, 0L, 0L, 0, null);
        }
        if (supplier.assetId.equals(requester.assetId) || !Objects.equals(supplier.systemId, requester.systemId)) {
            return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
        }

        LogisticsResourceConfig supplierCfg = supplier.logisticsConfig.get(resource);
        if (supplierCfg == null || !supplierCfg.isSupplyEnabled()) {
            return Result.simple(HammerDispatchStatus.Code.NO_EXPORT_CONFIG, hammer);
        }

        long availableSurplus = supplier.inventory.getAmount(resource) - supplierCfg.minReserve();
        if (availableSurplus <= 0L) return Result.simple(HammerDispatchStatus.Code.NO_SURPLUS_AFTER_RESERVE, hammer);

        LogisticsResourceConfig requesterCfg = requester.logisticsConfig.get(resource);
        if (requesterCfg == null || !requesterCfg.isImportEnabled()) {
            return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);
        }

        long requesterStock = requester.inventory.getAmount(resource);
        long inboundInTransit = inboundInTransitAmount(deliveries, requester.assetId, resource);
        long requestedAmount = Math.max(0L, requesterCfg.minReserve() - requesterStock - inboundInTransit);
        if (requestedAmount <= 0L) return Result.simple(HammerDispatchStatus.Code.WAITING_FOR_REQUEST, hammer);

        Candidate candidate = candidateFor(
            supplier,
            requester,
            resource,
            availableSurplus,
            requestedAmount,
            requesterCfg,
            hammerModule,
            hammer,
            orbitalTime,
            routeObserver);
        return evaluateCandidate(hammer, candidate, supplier, hammerModule);
    }

    public static Result evaluateCandidate(ModuleHammer hammer, Candidate candidate) {
        return evaluateCandidate(hammer, candidate, null, null);
    }

    public static Result evaluateCandidate(ModuleHammer hammer, Candidate candidate, AutomatedFacility supplier,
        ModuleInstance hammerModule) {
        long sendAmount = dispatchAmount(
            hammer,
            candidate.availableSurplus(),
            candidate.requestedAmount(),
            candidate.orderSize());
        if (sendAmount < candidate.orderSize() || sendAmount <= 0L) {
            return new Result(
                HammerDispatchStatus.Code.ORDER_BELOW_PACKAGE_SIZE,
                0L,
                hammer.energyStored(),
                sendAmount,
                candidate.orderSize(),
                null);
        }
        if (!candidate.shareAnchor() && hammer.variant() != HammerVariant.BIG) {
            return new Result(
                HammerDispatchStatus.Code.NEED_BIG_HAMMER,
                0L,
                hammer.energyStored(),
                sendAmount,
                candidate.orderSize(),
                null);
        }
        if (!candidate.sameBody() && !candidate.routeAvailable()) {
            return new Result(
                HammerDispatchStatus.Code.ROUTE_UNAVAILABLE,
                0L,
                hammer.energyStored(),
                sendAmount,
                candidate.orderSize(),
                null);
        }
        if (!candidate.sameBody() && !hammer.config()
            .allows(candidate.departureDv(), candidate.tofSeconds())) {
            HammerDispatchStatus.Code code = hammer.config()
                .mode() == AllowShootingConfig.Mode.WHEN_TOF_UNDER ? HammerDispatchStatus.Code.BLOCKED_BY_TOF_LIMIT
                    : HammerDispatchStatus.Code.BLOCKED_BY_DV_LIMIT;
            return new Result(code, 0L, hammer.energyStored(), sendAmount, candidate.orderSize(), null);
        }

        double shotDv = candidate.sameBody() ? 1.0 : candidate.totalDv();
        long requiredEnergy = ModuleHammer.shotEnergyCost(shotDv);
        if (!hammer.canSpendShotEnergy(requiredEnergy)) {
            return new Result(
                HammerDispatchStatus.Code.NEED_ENERGY,
                requiredEnergy,
                hammer.energyStored(),
                sendAmount,
                candidate.orderSize(),
                null);
        }

        Plan plan = null;
        if (supplier != null && candidate.requester() != null && candidate.resource() != null && hammerModule != null) {
            plan = new Plan(
                supplier,
                candidate.requester(),
                candidate.resource(),
                hammerModule,
                hammer,
                sendAmount,
                candidate.orderSize(),
                requiredEnergy,
                candidate.sameBody() ? LogisticSignal.Scope.PLANETARY : LogisticSignal.Scope.SYSTEM,
                candidate.sameBody() ? 1 : candidate.tofTicks(),
                candidate.sameBody() ? 1.0 : candidate.departureDv(),
                shotDv,
                candidate.sameBody() ? 0.0 : candidate.tofOsu(),
                candidate.route());
        }
        return new Result(
            HammerDispatchStatus.Code.READY,
            requiredEnergy,
            hammer.energyStored(),
            sendAmount,
            candidate.orderSize(),
            plan);
    }

    public static long dispatchAmount(ModuleHammer hammer, long availableSurplus, long requestedAmount, int orderSize) {
        return Math.min(Math.min(Math.min(requestedAmount, availableSurplus), orderSize), hammer.maxBatchSize());
    }

    private static Candidate candidateFor(AutomatedFacility supplier, AutomatedFacility requester,
        ItemStackWrapper resource, long availableSurplus, long requestedAmount, LogisticsResourceConfig requesterCfg,
        ModuleInstance hammerModule, ModuleHammer hammer, double orbitalTime, RouteObserver routeObserver) {
        boolean sameBody = supplier.celestialObjectId.equals(requester.celestialObjectId);
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        boolean shareAnchor = GalaxiaCelestialAPI
            .sharesPlanetaryAnchor(root, supplier.celestialObjectId, requester.celestialObjectId);

        if (sameBody) {
            return new Candidate(
                true,
                true,
                true,
                requester,
                resource,
                availableSurplus,
                requestedAmount,
                requesterCfg.orderSize(),
                1.0,
                1.0,
                0.0,
                1,
                0.0,
                null);
        }

        OrbitalTransferPlanner.TransferRoute route = routeBetween(
            root,
            supplier,
            requester,
            orbitalTime,
            hammerModule,
            hammer,
            routeObserver);
        if (route == null) {
            return new Candidate(
                false,
                shareAnchor,
                false,
                requester,
                resource,
                availableSurplus,
                requestedAmount,
                requesterCfg.orderSize(),
                0.0,
                0.0,
                0.0,
                0,
                0.0,
                null);
        }
        return new Candidate(
            false,
            shareAnchor,
            true,
            requester,
            resource,
            availableSurplus,
            requestedAmount,
            requesterCfg.orderSize(),
            route.departureDv(),
            route.totalDv(),
            route.tofSeconds(),
            route.tofTicks(),
            route.tofOsu(),
            route);
    }

    private static OrbitalTransferPlanner.TransferRoute routeBetween(CelestialObject root, AutomatedFacility supplier,
        AutomatedFacility requester, double orbitalTime, ModuleInstance hammerModule, ModuleHammer hammer,
        RouteObserver routeObserver) {
        CelestialObject srcBody = GalaxiaCelestialAPI.findBodyById(root, supplier.celestialObjectId);
        CelestialObject dstBody = GalaxiaCelestialAPI.findBodyById(root, requester.celestialObjectId);
        CelestialObject attractor = srcBody != null ? GalaxiaCelestialAPI.findStar(root, srcBody) : null;
        if (srcBody == null || dstBody == null || attractor == null) return null;

        RouteObserver observer = routeObserver == null ? RouteObserver.NONE : routeObserver;
        observer.beforeRouteComputation(hammerModule, hammer);
        long routeStartNanos = System.nanoTime();
        try {
            return OrbitalTransferPlanner
                .computeRoute(root, attractor, srcBody, dstBody, orbitalTime, hammer.routePriority());
        } finally {
            observer.afterRouteComputation(System.nanoTime() - routeStartNanos);
        }
    }

    private static Result prefer(Result result, Result current) {
        if (current == null) return result;
        return priority(result.code()) > priority(current.code()) ? result : current;
    }

    private static int priority(HammerDispatchStatus.Code code) {
        return switch (code) {
            case NEED_ENERGY -> 90;
            case BLOCKED_BY_DV_LIMIT, BLOCKED_BY_TOF_LIMIT -> 80;
            case NEED_BIG_HAMMER -> 70;
            case ROUTE_UNAVAILABLE -> 60;
            case ORDER_BELOW_PACKAGE_SIZE -> 50;
            case NO_SURPLUS_AFTER_RESERVE -> 40;
            case NO_EXPORT_CONFIG -> 30;
            case WAITING_FOR_REQUEST -> 20;
            case READY -> 100;
        };
    }

    private static long inboundInTransitAmount(Iterable<LogisticsDelivery> deliveries, CelestialAsset.ID toAssetId,
        ItemStackWrapper resource) {
        long total = 0L;
        for (LogisticsDelivery task : deliveries) {
            if (!toAssetId.equals(task.data.toAssetId())) continue;
            if (!resource.equals(task.data.resourceId())) continue;
            total += task.data.amount();
        }
        return total;
    }
}
