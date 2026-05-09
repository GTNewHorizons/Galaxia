package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

public enum ModuleOperationPhase {

    WAITING_FOR_MATERIALS,
    BUILDING,
    REFUNDING,
    COMPLETE,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETE || this == CANCELLED;
    }
}
