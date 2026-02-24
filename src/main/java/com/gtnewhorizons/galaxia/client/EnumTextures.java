package com.gtnewhorizons.galaxia.client;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

public enum EnumTextures {

    OXYGEN_BG("textures/gui/oxygen_bar_bg.png"),
    OXYGEN_FILL("textures/gui/oxygen_bar_fill.png"),
    TEMP_BG("textures/gui/temp_bar_bg.png"),
    TEMP_FILL_HOT("textures/gui/temp_bar_fill_hot.png"),
    TEMP_FILL_COLD("textures/gui/temp_bar_fill_cold.png"),

    // Add more textures here
    ; // leave trailing semicolon

    private final ResourceLocation texture;

    EnumTextures(String location) {
        this.texture = LocationGalaxia(location);
    }

    public ResourceLocation get() {
        return texture;
    }
}
