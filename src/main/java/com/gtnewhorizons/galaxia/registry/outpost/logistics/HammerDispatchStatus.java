package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

public final class HammerDispatchStatus {

    private HammerDispatchStatus() {}

    public enum Code {
        READY,
        WAITING_FOR_REQUEST,
        NO_EXPORT_CONFIG,
        NO_SURPLUS_AFTER_RESERVE,
        ORDER_BELOW_PACKAGE_SIZE,
        NEED_BIG_HAMMER,
        ROUTE_UNAVAILABLE,
        BLOCKED_BY_DV_LIMIT,
        BLOCKED_BY_TOF_LIMIT,
        NEED_ENERGY
    }

    public record Candidate(boolean sameBody, boolean shareAnchor, boolean routeAvailable, long availableSurplus,
        long requestedAmount, int orderSize, double departureDv, double totalDv, double tofSeconds) {}

    public record Status(Code code, long requiredEnergy, long storedEnergy, long sendAmount, int orderSize) {

        public static Status simple(Code code, ModuleHammer hammer) {
            return new Status(code, 0L, hammer.energyStored(), 0L, 0);
        }
    }

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, double orbitalTime) {
        return evaluate(
            supplier,
            hammerModule,
            CelestialAssetStore.allAssets(),
            LogisticStore.activeDeliveries(),
            orbitalTime);
    }

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        Iterable<LogisticsDelivery> deliveries, double orbitalTime) {
        return HammerDispatchPlanner.evaluate(supplier, hammerModule, assets, deliveries, orbitalTime)
            .toStatus();
    }

    public static Status evaluateCandidate(ModuleHammer hammer, Candidate candidate) {
        return HammerDispatchPlanner
            .evaluateCandidate(hammer, HammerDispatchPlanner.Candidate.fromStatusCandidate(candidate))
            .toStatus();
    }

    public static long dispatchAmount(ModuleHammer hammer, long availableSurplus, long requestedAmount, int orderSize) {
        return HammerDispatchPlanner.dispatchAmount(hammer, availableSurplus, requestedAmount, orderSize);
    }
}
