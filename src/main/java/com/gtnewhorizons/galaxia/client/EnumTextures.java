package com.gtnewhorizons.galaxia.client;

import static com.gtnewhorizons.galaxia.utility.ResourceLocationGalaxia.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

public enum EnumTextures {

    // Gui
    OXYGEN_BG("textures/gui/oxygen_bar_bg.png"),
    OXYGEN_FILL("textures/gui/oxygen_bar_fill.png"),
    TEMP_BG("textures/gui/temp_bar_bg.png"),
    TEMP_FILL_HOT("textures/gui/temp_bar_fill_hot.png"),
    TEMP_FILL_COLD("textures/gui/temp_bar_fill_cold.png"),

    // Space Objects
    AMBERGRIS("textures/environment/ambergris.png"),
    ANAMNESIS("textures/environment/anamnesis.png"),
    ATARAXIA("textures/environment/ataraxia.png"),
    EDIACARA("textures/environment/ediacara.png"),
    EGORA("textures/environment/egora.png"),
    HEMATERIA("textures/environment/hemateria.png"),
    MIRAGE("textures/environment/mirage.png"),
    MYKELIA("textures/environment/mykelia.png"),
    PERIHELIA("textures/environment/perihelia.png"),
    PLEURA("textures/environment/pleura.png"),
    TENEBRAE("textures/environment/tenebrae.png"),
    VIRIDIS("textures/environment/viridis.png"),

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
