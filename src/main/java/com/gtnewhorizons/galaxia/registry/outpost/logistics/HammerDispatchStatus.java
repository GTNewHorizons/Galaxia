package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

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

    public record Status(Code code, long requiredEnergy, long storedEnergy, long sendAmount, int orderSize) {}

    public static Status evaluate(AutomatedFacility supplier, ModuleInstance hammerModule, Iterable<?> assets,
        double orbitalTime) {
        return HammerDispatchPlanner.evaluate(supplier, hammerModule, assets, orbitalTime)
            .toStatus();
    }

}
