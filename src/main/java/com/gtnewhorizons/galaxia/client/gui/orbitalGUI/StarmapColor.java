package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.cleanroommc.modularui.utils.GlStateManager;

/**
 * ARGB helpers shared by every starmap renderer.
 * <p>
 * The starmap draws with the fixed-function pipeline, so colours travel as packed ARGB ints and are unpacked only at
 * the {@link GlStateManager} boundary.
 */
final class StarmapColor {

    private StarmapColor() {}

    /** Scales the alpha channel of a packed ARGB colour, clamped to the byte range. */
    static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Applies a packed ARGB colour to the fixed-function pipeline. */
    static void apply(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        GlStateManager.color(r, g, b, a);
    }
}
