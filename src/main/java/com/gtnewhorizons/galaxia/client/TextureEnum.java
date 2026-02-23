package com.gtnewhorizons.galaxia.client;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

public enum TextureEnum {

    OXYGEN_BG("textures/gui/oxygen_bar_bg.png"),
    OXYGEN_FILL("textures/gui/oxygen_bar_fill.png"),
    TEMP_BG("textures/gui/temp_bar_bg.png"),
    TEMP_FILL("textures/gui/temp_bar_fill.png");

    private final ResourceLocation texture;

    TextureEnum(String location) {
        this.texture = LocationGalaxia(location);
    }

    public ResourceLocation get() {
        return texture;
    }
}
