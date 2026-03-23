package com.gtnewhorizons.galaxia.utility;

import java.util.Locale;

import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.FMLLog;

/**
 * ENUM for custom colours to be implemented in UIs and such
 */
public enum EnumColors {

    Transparent(0xFF),
    Title(0xFFFFFF),
    SubTitle(0xAAAAFF),
    Value(0xFFFFFF),

    // Icon
    IconGreen(0x55FF55),

    // Effect(s)
    EffectBad(0x66CCFF),

    // Map Sidebar
    MapSidebarBackground(0xE60F1621),
    MapSidebaSearchLabel(0x99FFFFFF),
    MapSidebarSearchInput(0xFFFFFFFF),
    MapSidebarListNormal(0xFFCCEEFF),
    MapSidebarListHovered(0xFF88EEFF),

    // Map
    MapBackground(0xFF0F1621),
    MapCelestialLabelText(0xFFFFFFFF),
    MapStatusText(0xAAFFFFFF),
    MapCelestialBlackHole(0xFF111111),
    MapCelestialStar(0xFFFFEE88),
    MapCelestialPlanet(0xFF44AAFF),
    MapCelestialMoon(0xFFEEEEEE),
    MapCelestialDefault(0xFF00FF99),

    // Debug overlay
    DebugOverlayTitle(0xFFFF5555),
    DebugOverlayInfo(0x88FF88),
    DebugOverlayFollow(0xFFDD88),

    // Other UI elements
    OrbitEllipse(0xEBFFFFFF), // 0.92 alpha white
    SpriteTint(0xFFFFFFFF);

    // Add more colors here
    ; // leave trailing semicolon

    private static final String PREFIX = "galaxia.color.override.";
    private final int defaultColor;

    EnumColors(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    // Optional resource pack color override
    // Examples (lowercase):
    // galaxia.color.override.title=FFFFFF
    // galaxia.color.override.subtitle=CD7F32

    /**
     * Gets the colour as a parsed form if possible, or default:
     * Optional resource pack color override
     * Examples (lowercase):
     * - galaxia.color.override.title=FFFFFF
     * - galaxia.color.override.subtitle=CD7F32
     *
     * @return Parsed colour from ENUM, or default
     */
    public int getColor() {
        String key = getUnlocalized();
        if (!StatCollector.canTranslate(key)) {
            return defaultColor;
        }

        return parseColor(StatCollector.translateToLocal(key), defaultColor);
    }

    /**
     * Gets the unlocalized colour name
     *
     * @return Unlocalized colour name
     */
    public String getUnlocalized() {
        return PREFIX + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Colour parser given a colour string
     *
     * @param raw      The string to parse
     * @param fallback A default colour if parsing failed
     * @return Color parsed, or fallback if failed
     */
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
