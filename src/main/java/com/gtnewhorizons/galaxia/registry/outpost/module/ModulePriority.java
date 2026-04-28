package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum ModulePriority {

    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    private static final ModulePriority[] VALUES = values();

    public byte toByte() {
        return (byte) ordinal();
    }

    public static ModulePriority fromByte(byte ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return NORMAL;
        return VALUES[ordinal];
    }
}
