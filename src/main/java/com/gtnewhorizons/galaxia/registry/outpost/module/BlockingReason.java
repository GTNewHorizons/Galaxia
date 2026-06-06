package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum BlockingReason {

    NONE,
    INVALID_RECIPE,
    WRONG_ENVIRONMENT,
    PLAYER_DISABLED,
    UPKEEP_SHORTAGE,
    POWER_SHORTAGE,
    MISSING_INPUT,
    OUTPUT_LIMIT,
    STORAGE_CAPACITY,
    SCHEDULER_IDLE;
}
