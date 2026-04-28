package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum HammerVariant {

    BASE,
    BIG;

    private static final HammerVariant[] VALUES = values();

    public byte toByte() {
        return (byte) ordinal();
    }

    public static HammerVariant fromByte(byte ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) return BASE;
        return VALUES[ordinal];
    }
}
