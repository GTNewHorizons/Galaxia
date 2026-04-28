package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum ModuleTier {

    NONE,
    HV,
    EV,
    IV,
    LuV;

    private static final ModuleTier[] VALUES = values();

    public byte toByte() {
        return (byte) ordinal();
    }

    public static ModuleTier fromByte(byte ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return NONE;
        return VALUES[ordinal];
    }
}
