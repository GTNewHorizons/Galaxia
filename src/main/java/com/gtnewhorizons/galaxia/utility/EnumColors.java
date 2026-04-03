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

    // Warning(s)
    Warning(0xFF4444),

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

    // Debug overlay
    DebugOverlayTitle(0xFFFF5555),
    DebugOverlayInfo(0x88FF88),
    DebugOverlayFollow(0xFFDD88),

    // Other UI elements
    OrbitEllipse(0xEBFFFFFF), // 0.92 alpha white
    SpriteTint(0xFFFFFFFF),

    // Celestial Map

    MAP_COLOR_OVERLAY_BG(0xAA09111B),
    MAP_COLOR_MODAL_BG(0xFF121B28),
    MAP_COLOR_MODAL_HEADER(0xFF22324A),
    MAP_COLOR_MODAL_ACCENT(0xFF59BFD9),
    MAP_COLOR_MODAL_DANGER_BG(0xFF1A1012),
    MAP_COLOR_MODAL_DANGER_ACCENT(0xFFD14A4A),
    MAP_COLOR_MODAL_WARNING_BG(0xFF121B28),
    MAP_COLOR_MODAL_WARNING_ACCENT(0xFFE6B35A),
    MAP_COLOR_TEXT_TITLE(0xFFFFFFFF),
    MAP_COLOR_TEXT_SECTION(0xFF5A63FF),
    MAP_COLOR_TEXT_BODY(0xFFD9E0FF),
    MAP_COLOR_TEXT_MUTED(0xFF9AA7B8),
    MAP_COLOR_TEXT_DANGER(0xFFFF5A5A),
    MAP_COLOR_TEXT_WARNING(0xFFFFD59A),
    MAP_COLOR_TEXT_DANGER_BODY(0xFFFFB3B3),
    MAP_COLOR_SCROLL_BG(0x3318273A),
    MAP_COLOR_ROW_BG(0x55213144),
    MAP_COLOR_RENAME_BORDER(0xFF7FB6FF),
    MAP_COLOR_RENAME_INPUT_BG(0xFF0F1621),
    MAP_COLOR_BTN_ENABLED_DEFAULT(0xFF2D435D),
    MAP_COLOR_BTN_ENABLED_HOVERED(0xFF3A5678),
    MAP_COLOR_BTN_DISABLED(0xFF243041),
    MAP_COLOR_BTN_BORDER_ENABLED(0xFF7FB6FF),
    MAP_COLOR_BTN_BORDER_DISABLED(0xFF556577),
    MAP_COLOR_BTN_DANGER_DEFAULT(0xFF5A1E24),
    MAP_COLOR_BTN_DANGER_HOVERED(0xFF6D252D),
    MAP_COLOR_BTN_DANGER_BORDER(0xFFFF5A5A),
    MAP_COLOR_TEXT_BTN_ENABLED(0xFFFFFFFF),
    MAP_COLOR_TEXT_BTN_DISABLED(0xFF94A0AF),
    MAP_COLOR_TRANSFER_ROW_BG(0x55213144),

    // Add more colors here
    ; // leave trailing semicolon

    private static final String PREFIX = "galaxia.color.override.";
    private final int defaultColor;

    EnumColors(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    /**
     * Gets the colour as a parsed form if possible, or default.
     * <br>
     * Optional resource pack color override
     * <p>
     * Examples (lowercase):
     * - <code>galaxia.color.override.title=FFFFFF</code>
     * - <code>galaxia.color.override.subtitle=CD7F32</code>
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
