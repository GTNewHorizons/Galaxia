package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum ModuleState {

    RUNNING,
    IDLE,
    DISABLED,
    BLOCKED,
    PAUSED;

    private static final ModuleState[] VALUES = values();

    public byte toByte() {
        return (byte) ordinal();
    }

    public static ModuleState fromByte(byte ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return IDLE;
        return VALUES[ordinal];
    }
}
