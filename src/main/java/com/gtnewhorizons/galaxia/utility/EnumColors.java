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

    // Icon(s)
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

    // Celestial Map
    MapBackground(0xFF0F1621),
    MapCelestialLabelText(0xFFFFFFFF),
    MapStatusText(0xAAFFFFFF),
    MapTitleBannerBackground(0xEE162133),
    MapTitleBannerBorder(0xFF7FB6FF),
    MapSelectionOverlay(0xFF18C8FF),

    MapBodyBlackHole(0xFF5A4B7A),
    MapBodyStar(0xFFFFD36B),
    MapBodyGasGiant(0xFFD9A066),
    MapBodyPlanet(0xFF7FC7A6),
    MapBodyMoon(0xFFD8DCE6),
    MapBodyAsteroid(0xFF9CA3AF),
    MapBodyStation(0xFF89C2FF),
    MapBodyComet(0xFFAEE7FF),

    MapColorOverlayBackground(0xAA09111B),
    MapColorModalBackground(0xFF121B28),
    MapColorModalHeader(0xFF22324A),
    MapColorModalAccent(0xFF59BFD9),
    MapColorModalDangerBackground(0xFF1A1012),
    MapColorModalDangerAccent(0xFFD14A4A),
    MapColorModalWarningBackground(0xFF121B28),
    MapColorModalWarningAccent(0xFFE6B35A),
    MapColorTextTitle(0xFFFFFFFF),
    MapColorTextSection(0xFF5A63FF),
    MapColorTextBody(0xFFD9E0FF),
    MapColorTextMuted(0xFF9AA7B8),
    MapColorTextDanger(0xFFFF5A5A),
    MapColorTextWarning(0xFFFFD59A),
    MapColorTextDangerBody(0xFFFFB3B3),
    MapColorScrollBackground(0x3318273A),
    MapColorRowBackground(0x55213144),
    MapColorRenameBorder(0xFF7FB6FF),
    MapColorRenameInputBackground(0xFF0F1621),
    MapColorButtonEnabledDefault(0xFF2D435D),
    MapColorButtonEnabledHovered(0xFF3A5678),
    MapColorButtonDisabled(0xFF243041),
    MapColorButtonBorderEnabled(0xFF7FB6FF),
    MapColorButtonBorderDisabled(0xFF556577),
    MapColorButtonDangerDefault(0xFF5A1E24),
    MapColorButtonDangerHovered(0xFF6D252D),
    MapColorButtonDangerBorder(0xFFFF5A5A),
    MapColorTextButtonEnabled(0xFFFFFFFF),
    MapColorTextButtonDisabled(0xFF94A0AF),
    MapColorTransferRowBackground(0x55213144),

    // Other UI elements
    OrbitEllipse(0xEBFFFFFF), // 0.92 alpha white
    SpriteTint(0xFFFFFFFF),

    // Debug overlay
    MapDebugPanelBackground(0x990B111C),
    MapDebugBodyHitzones(0xFF7FFFD4),
    MapDebugToggleHint(0xFFB8C7D9),
    MapDebugHitboxOutline(0xFF00E5FF),
    MapDebugHitboxCenter(0xFF9BFF7A),
    DebugOverlayTitle(0xFFFF5555),
    DebugOverlayInfo(0x88FF88),
    DebugOverlayFollow(0xFFDD88),

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
