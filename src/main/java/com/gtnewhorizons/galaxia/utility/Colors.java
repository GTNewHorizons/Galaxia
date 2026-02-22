package com.gtnewhorizons.galaxia.utility;

import java.util.Locale;

import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.FMLLog;

public enum Colors {

    Transparent(0xFF),
    Title(0xFFFFFF),
    SubTitle(0xAAAAFF),
    Value(0xFFFFFF),

    IconGreen(0x55FF55);
    // Add more colors here

    private static final String PREFIX = "galaxia.color.override.";
    private final int defaultColor;

    Colors(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    // Optional resource pack color override
    // Examples (lowercase):
    // galaxia.color.override.title=FFFFFF
    // galaxia.color.override.subtitle=CD7F32
    public int getColor() {
        String key = getUnlocalized();
        if (!StatCollector.canTranslate(key)) {
            return defaultColor;
        }

        return parseColor(StatCollector.translateToLocal(key), defaultColor);
    }

    public String getUnlocalized() {
        return PREFIX + name().toLowerCase(Locale.ROOT);
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return fallback;
        }

        if (value.startsWith("#")) {
            value = value.substring(1);
        } else if (value.startsWith("0x") || value.startsWith("0X")) {
            value = value.substring(2);
        }

        try {
            return Integer.parseUnsignedInt(value, 16);
        } catch (NumberFormatException e) {
            FMLLog.warning("Invalid color override: %s", raw);
            return fallback;
        }
    }
}
