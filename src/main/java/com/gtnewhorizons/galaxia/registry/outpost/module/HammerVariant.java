package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum HammerVariant {

    BASE(500_000L),
    BIG(8_000_000L);

    private final long shotEnergyEu;

    HammerVariant(long shotEnergyEu) {
        this.shotEnergyEu = shotEnergyEu;
    }

    public long shotEnergyEu() {
        return shotEnergyEu;
    }
}
