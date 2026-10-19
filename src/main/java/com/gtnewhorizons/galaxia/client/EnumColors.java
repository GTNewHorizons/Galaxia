package com.gtnewhorizons.galaxia.client;

import java.util.Locale;

import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.FMLLog;

/**
 * ENUM for custom colors to be implemented in UIs and such
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

    MapSidebaSearchLabel(0x99FFFFFF),

    // Map
    MapBackground(0xC01F1F2E),
    MapCelestialLabelText(0xFFFFFFFF),

    // Debug overlay
    DebugOverlayTitle(0xFFFF5555),
    DebugOverlayInfo(0x88FF88),
    DebugOverlayFollow(0xFFDD88),

    // Other UI elements
    OrbitEllipse(0xEBFFFFFF), // 0.92 alpha white
    SpriteTint(0xFFFFFFFF),

    // Celestial Map

    MAP_COLOR_MODAL_BG(0xFF46475C),
    MAP_COLOR_TEXT_TITLE(0xFFFFFFFF),
    MAP_COLOR_TEXT_SECTION(0xFF8D93AE),
    MAP_COLOR_TEXT_BODY(0xFFD8DCEB),
    MAP_COLOR_TEXT_MUTED(0xFF8D93AE),
    MAP_COLOR_TEXT_DANGER(0xFFE36D6D),
    MAP_COLOR_TEXT_WARNING(0xFFE8C65A),
    MAP_COLOR_SCROLL_BG(0xFF27283B),
    MAP_COLOR_RENAME_INPUT_BG(0xFF27283B),
    MAP_COLOR_BTN_ENABLED_DEFAULT(0xFF3B3C52),
    MAP_COLOR_BTN_ENABLED_HOVERED(0xFF42435A),
    MAP_COLOR_TRANSFER_PATH(0x886DDCFF),
    MAP_COLOR_TRANSFER_PREVIEW_PATH(0xAA00FFCC),
    MAP_COLOR_TITLE_BANNER_TEXT(0xFFFFFFFF),
    MAP_COLOR_SELECTION_HIGHLIGHT(0xFF18C8FF),
    MAP_COLOR_SPHERE_OF_INFLUENCE_FILL(0x3300FF66),
    MAP_COLOR_DEBUG_PANEL_BG(0xCC27283B),
    MAP_COLOR_DEBUG_TITLE(0xFF7FFFD4),
    MAP_COLOR_DEBUG_INFO(0xFFB8C7D9),
    MAP_COLOR_DEBUG_HITBOX(0xFF00E5FF),
    MAP_COLOR_DEBUG_CENTER(0xFF9BFF7A),
    MAP_COLOR_BODY_GALAXY(0xFFFFFFFF),
    MAP_COLOR_BODY_BLACK_HOLE(0xFF5A4B7A),
    MAP_COLOR_BODY_STAR(0xFFFFD36B),
    MAP_COLOR_BODY_GAS_GIANT(0xFFD9A066),
    MAP_COLOR_BODY_PLANET(0xFF7FC7A6),
    MAP_COLOR_BODY_MOON(0xFFD8DCE6),
    MAP_COLOR_BODY_ASTEROID(0xFF9CA3AF),
    MAP_COLOR_BODY_STATION(0xFF89C2FF),
    MAP_COLOR_BODY_COMET(0xFFAEE7FF),
    MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED(0xFF5FD87A),
    MAP_COLOR_STATION_PANEL_BG(0xFF46475C),
    MAP_COLOR_STATION_TILE_BORDER_DEFAULT(0xFF54556B),
    MAP_COLOR_STATION_TILE_BORDER_HOVERED(0xFF7FA6E0),
    MAP_COLOR_STATION_TILE_BORDER_SELECTED(0xFFFFFFFF),
    MAP_COLOR_STATION_TILE_EMPTY_FILL(0x3354556B),
    MAP_COLOR_STATION_TILE_EMPTY_BORDER(0xFF54556B),
    MAP_COLOR_STATION_TILE_UNDER_CONSTRUCTION(0x66FFD59A),
    MAP_COLOR_STATION_TILE_UNDER_DECONSTRUCTION(0x66FF5A5A),
    MAP_COLOR_STATION_TILE_DISABLED_DIM(0x80000000),
    MAP_COLOR_STATION_TILE_BLOCKED(0x66FF0000),
    MAP_COLOR_STATION_TILE_PREVIEW_FALLBACK_FILL(0x884A5360),
    MAP_COLOR_STATION_TILE_PREVIEW_DIM(0x55000000),
    MAP_COLOR_STATION_PICKER_COMPATIBLE(0xFF9099A4),
    MAP_COLOR_STATION_PICKER_COMPATIBLE_SECONDARY(0xCC59616A),
    MAP_COLOR_STATION_PICKER_SELECTED(0xFF35D06F),
    MAP_COLOR_STATION_PICKER_SELECTED_SECONDARY(0xCC249650),
    MAP_COLOR_STATION_PICKER_DECONSTRUCT_SELECTED(0xFFFF4F4F),
    MAP_COLOR_STATION_DEBUG_NEIGHBOR_FILL(0x4400FF00),
    MAP_COLOR_STATION_DEBUG_NEIGHBOR_BORDER(0xFF00FF00),
    MAP_COLOR_STATION_CATEGORY_COMMAND(0xFF3A5678),
    MAP_COLOR_STATION_CATEGORY_MINING_SUPPORT(0xFF6B5020),
    MAP_COLOR_STATION_CATEGORY_LOGISTICS(0xFF2D6A7A),
    MAP_COLOR_STATION_CATEGORY_STORAGE(0xFF4A4A4A),
    MAP_COLOR_STATION_CATEGORY_POWER(0xFF2F5A3A),
    MAP_COLOR_STATION_CATEGORY_PROCESSING(0xFF4A2B66),
    MAP_COLOR_STATION_CATEGORY_HABITATION(0xFF5A4030),
    MAP_COLOR_STATION_CATEGORY_INFRASTRUCTURE(0xFF3A5A5A),
    MAP_COLOR_STATION_CATEGORY_SUPPORT(0xFF4A6B4A),
    MAP_COLOR_STATION_CONNECTOR_ACTIVE(0xFF1E385A),
    MAP_COLOR_STATION_CONNECTOR_INACTIVE(0x661E385A),

    // Add more colors here
    ; // leave trailing semicolon

    private static final String PREFIX = "galaxia.color.override.";
    private final int defaultColor;

    private String unlocalizedName;
    private int cachedColor;
    private boolean hasCachedColor = false;

    EnumColors(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    /**
     * Gets the color as a parsed form if possible, or default.
     * <br>
     * Optional resource pack color override
     * <p>
     * Examples (lowercase):
     * - <code>galaxia.color.override.title=FFFFFF</code>
     * - <code>galaxia.color.override.subtitle=CD7F32</code>
     *
     * @return Parsed color from ENUM, or default
     */
    public int getColor() {
        if (!hasCachedColor) {
            updateCachedColor();
        }
        return cachedColor;
    }

    public void updateCachedColor() {
        String key = getUnlocalized();
        if (!StatCollector.canTranslate(key)) {
            cachedColor = defaultColor;
        } else {
            cachedColor = parseColor(StatCollector.translateToLocal(key), defaultColor);
        }
        hasCachedColor = true;
    }

    /**
     * Gets the unlocalized color name
     *
     * @return Unlocalized color name
     */
    public String getUnlocalized() {
        if (unlocalizedName == null) {
            unlocalizedName = PREFIX + name().toLowerCase(Locale.ROOT);
        }
        return unlocalizedName;
    }

    /**
     * Color parser given a color string
     *
     * @param raw      The string to parse
     * @param fallback A default color if parsing failed
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
