package com.gtnewhorizons.galaxia.registry.outpost.module.operation;

public enum ModuleOperationKind {

    UPGRADE_REBUILD(true);

    private final boolean buildPhaseRequired;

    ModuleOperationKind(boolean buildPhaseRequired) {
        this.buildPhaseRequired = buildPhaseRequired;
    }

    public boolean buildPhaseRequired() {
        return buildPhaseRequired;
    }
}
