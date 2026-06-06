package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly;

public enum RocketBuildStatus {

    IDLE, // nothing designed
    DESIGNED, // designed but not ordered
    ASSEMBLING, // ordered and being assembled
    READY, // fully assembled and ready for start
    LAUNCHED; // rocket departed

    public boolean canEdit() {
        return this == IDLE || this == DESIGNED;
    }

    public boolean canLaunch() {
        return this == READY;
    }

    public boolean canOrder() {
        return this == DESIGNED;
    }
}
