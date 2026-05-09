package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum MinerFocusTier {

    NONE(0),
    I(20),
    II(40),
    III(60);

    public static final int ALIGNMENT_REQUIRED_TICKS = 60 * 60 * 20;

    private final int bonusPercent;

    MinerFocusTier(int bonusPercent) {
        this.bonusPercent = bonusPercent;
    }

    public int bonusPercent() {
        return bonusPercent;
    }

    public MinerFocusTier next() {
        MinerFocusTier[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
