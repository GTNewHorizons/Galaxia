package com.gtnewhorizons.galaxia.registry.outpost.station;

public enum MutationKind {
    PLACE,
    DECONSTRUCT,
    // T3.4: SET_TIER is live for capacity modules (invalidates CAPACITY_CLUSTERS)
    SET_TIER,
    // TODO: To be implemented in T7.4
    SET_PARALLEL,
    // TODO: To be implemented in T7.4
    SET_ENABLED
}
