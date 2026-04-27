package com.gtnewhorizons.galaxia.client;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

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

    SELECTION_FRAME("textures/gui/selection_frame.png"),
    HAZARD_COLD("textures/gui/icon_cold.png"),
    HAZARD_OXYGEN("textures/gui/icon_no_oxygen.png"),
    HAZARD_RADIATION("textures/gui/icon_radiation.png"),

    // Space Body Icons for Galactic map
    ICON_AMBERGRIS("textures/gui/bodyicons/icon_ambergris.png"),
    ICON_ANAMNESIS("textures/gui/bodyicons/icon_anamnesis.png"),
    ICON_ATARAXIA("textures/gui/bodyicons/icon_ataraxia.png"),
    ICON_EDIACARA("textures/gui/bodyicons/icon_ediacara.png"),
    ICON_EGORA("textures/gui/bodyicons/icon_egora.png"),
    ICON_HEMATERIA("textures/gui/bodyicons/icon_hemateria.png"),
    ICON_MYKELIA("textures/gui/bodyicons/icon_mykelia.png"),
    ICON_PLEURA("textures/gui/bodyicons/icon_pleura.png"),
    ICON_TENEBRAE("textures/gui/bodyicons/icon_tenebrae.png"),
    ICON_THEIA("textures/gui/bodyicons/icon_theia.png"),
    ICON_VIRIDIS("textures/gui/bodyicons/icon_viridis.png"),

    // Space Object Icons for Galactic map
    ICON_STATION("textures/gui/bodyicons/station.png"),
    ICON_STATION_AUTOMATED("textures/gui/bodyicons/station_automated.png"),
    ICON_OUTPOST_AUTOMATED("textures/gui/bodyicons/outpost_automated.png"),

    // Asset panel / transfer package icons
    ICON_CAP_MINING("textures/gui/outpost_mining.png"),
    ICON_CAP_PRODUCTION("textures/gui/outpost_processing.png"),
    ICON_CAP_CONSTRUCTION("textures/gui/outpost_building.png"),
    ICON_CAP_DECONSTRUCTION("textures/gui/outpost_destroying.png"),
    ICON_WARN_POWERFAIL("textures/gui/outpost_powerfail.png"),
    ICON_WARN_GENERIC("textures/gui/outpost_warning.png"),
    ICON_MISSING("textures/gui/asset_panel/missing.png"),
    ICON_TRANSFER_HAMMER("textures/items/module/item_hammer_package.png"),

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
