package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;

public final class HammerDispatchStatus {

    private HammerDispatchStatus() {}

    public enum Code {

        READY(100),
        WAITING_FOR_REQUEST(20),
        NO_EXPORT_CONFIG(30),
        NO_SURPLUS_AFTER_RESERVE(40),
        DESTINATION_LACKS_PACKAGE_SPACE(55),
        DESTINATION_CAPACITY_BLOCKED(55),
        NEED_BIG_HAMMER(70),
        ROUTE_UNAVAILABLE(60),
        BLOCKED_BY_DV_LIMIT(80),
        BLOCKED_BY_TOF_LIMIT(80),
        NEED_ENERGY(90);

        private final int priority;

        Code(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    public record Candidate(boolean sameBody, boolean shareAnchor, boolean routeAvailable, long availableSurplus,
        long requestedAmount, int orderSize, double departureDv, double totalDv, double tofSeconds) {}

    public record Status(Code code, long requiredEnergy, long storedEnergy, long sendAmount, int orderSize) {

        public static Status simple(Code code, ModuleHammer hammer) {
            return new Status(code, 0L, hammer.energyStored(), 0L, 0);
        }
    }

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, double orbitalTime) {
        return evaluate(supplier, hammerModule, CelestialAssetStore.allAssets(), orbitalTime);
    }

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        double orbitalTime) {
        return HammerDispatchPlanner.evaluate(supplier, hammerModule, assets, orbitalTime)
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
