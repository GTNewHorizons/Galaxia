package com.gtnewhorizons.galaxia.registry.outpost.module;

public final class ModuleMaintenanceBay implements ModuleComponent {

    private byte parallel = 1;

    @Override
    public byte getParallel() {
        return parallel;
    }

    @Override
    public void setParallel(byte parallel) {
        // Frozen — no GUI control; parallel is always 1
    }
}
