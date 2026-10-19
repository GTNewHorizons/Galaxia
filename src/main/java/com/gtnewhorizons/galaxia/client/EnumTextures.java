package com.gtnewhorizons.galaxia.client;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.drawable.UITexture;
import com.gtnewhorizons.galaxia.core.Galaxia;

public enum EnumTextures {

    // Gui
    OXYGEN_BG("textures/gui/oxygen_bar_bg.png"),
    OXYGEN_FILL("textures/gui/oxygen_bar_fill.png"),
    TEMP_BG("textures/gui/temp_bar_bg.png"),
    TEMP_FILL_HOT("textures/gui/temp_bar_fill_hot.png"),
    TEMP_FILL_COLD("textures/gui/temp_bar_fill_cold.png"),

    // Space Objects
    AMBERGRIS("textures/environment/planets/ambergris.png"),
    ANAMNESIS("textures/environment/planets/anamnesis.png"),
    ATARAXIA("textures/environment/planets/ataraxia.png"),
    EDIACARA("textures/environment/planets/ediacara.png"),
    EGORA("textures/environment/planets/egora.png"),
    MARS("textures/environment/planets/mars.png"),
    MIRAGE("textures/environment/planets/mirage.png"),
    MYKELIA("textures/environment/planets/mykelia.png"),
    PERIHELIA("textures/environment/planets/perihelia.png"),
    PLEURA("textures/environment/planets/pleura.png"),
    TENEBRAE("textures/environment/planets/tenebrae.png"),
    OVERWORLD("textures/environment/planets/overworld.png"),
    MOON("textures/environment/planets/moon.png"),

    CRAB_NEBULA("textures/environment/nebula/crab_nebula.png"),
    DUMBBELL_NEBULA("textures/environment/nebula/dumbbell_nebula.png"),
    EAGLE_NEBULA("textures/environment/nebula/eagle_nebula.png"),
    LAGOON_NEBULA("textures/environment/nebula/lagoon_nebula.png"),
    ORION_NEBULA("textures/environment/nebula/orion_nebula.png"),
    PLANETARY_NEBULA("textures/environment/nebula/planetary_nebula.png"),
    RING_NEBULA("textures/environment/nebula/ring_nebula.png"),
    SUPERNOVA("textures/environment/nebula/supernova.png"),

    ELLIPTICAL_GALAXY("textures/environment/galaxy/elliptical_galaxy.png"),
    LENTICULAR_GALAXY("textures/environment/galaxy/lenticular_galaxy.png"),
    SPIRAL_GALAXY("textures/environment/galaxy/spiral_galaxy.png"),

    HAZARD_COLD("textures/gui/icon_cold.png"),
    HAZARD_OXYGEN("textures/gui/icon_no_oxygen.png"),
    HAZARD_RADIATION("textures/gui/icon_radiation.png"),

    // Space Body Icons for Galactic map
    ICON_AMBERGRIS("textures/gui/bodyicons/icon_ambergris.png"),
    ICON_ANAMNESIS("textures/gui/bodyicons/icon_anamnesis.png"),
    ICON_ATARAXIA("textures/gui/bodyicons/icon_ataraxia.png"),
    ICON_EDIACARA("textures/gui/bodyicons/icon_ediacara.png"),
    ICON_EGORA("textures/gui/bodyicons/icon_egora.png"),
    ICON_MARS("textures/gui/bodyicons/icon_mars.png"),
    ICON_MYKELIA("textures/gui/bodyicons/icon_mykelia.png"),
    ICON_PLEURA("textures/gui/bodyicons/icon_pleura.png"),
    ICON_TENEBRAE("textures/gui/bodyicons/icon_tenebrae.png"),
    ICON_MOON("textures/gui/bodyicons/icon_moon.png"),
    ICON_OVERWORLD("textures/gui/bodyicons/icon_overworld.png"),

    // Space Object Icons for Galactic map
    ICON_STATION("textures/gui/bodyicons/station.png"),
    ICON_STATION_AUTOMATED("textures/gui/bodyicons/station_automated.png"),
    ICON_OUTPOST_AUTOMATED("textures/gui/bodyicons/outpost_automated.png"),
    ICON_SATELLITE("textures/items/parts/satellite_dish.png"),

    // Asset panel / transfer package icons
    ICON_CAP_MINING("textures/gui/outpost_mining.png"),
    ICON_CAP_PRODUCTION("textures/gui/outpost_processing.png"),
    ICON_CAP_CONSTRUCTION("textures/gui/outpost_building.png"),
    ICON_CAP_DECONSTRUCTION("textures/gui/outpost_destroying.png"),
    ICON_WARN_POWERFAIL("textures/gui/outpost_powerfail.png"),
    ICON_WARN_GENERIC("textures/gui/outpost_warning.png"),
    ICON_STATION_CORE_DIRECTION("textures/gui/station/core_direction_arrow.png"),
    ICON_STATION_ALERT_WARNING("textures/gui/station/alert_warning.png"),
    ICON_STATION_ALERT_ERROR("textures/gui/station/alert_error.png"),
    ICON_STATION_ITEM_INTERACTIONS("textures/gui/station/item_interactions.png"),
    ICON_STATION_INVENTORY_ALL("textures/gui/station/inventory_all.png"),
    ICON_STATION_INVENTORY_AMOUNT("textures/gui/station/inventory_amount.png"),
    ICON_STATION_INVENTORY_BOUNDS("textures/gui/station/inventory_bounds.png"),
    ICON_STATION_INVENTORY_VOID("textures/gui/station/inventory_void.png"),
    ICON_MISSING("textures/gui/asset_panel/missing.png"),
    ICON_TRANSFER_HAMMER("textures/items/module/item_hammer_package.png"),

    // Silo GUI
    SILO_BLUEPRINT("textures/gui/silo/blueprint.png"),
    SILO_BLUEPRINT_TILE("textures/gui/silo/blueprint_tile.png"),
    SILO_GUI_BASE("textures/gui/silo/gui_base.png"),

    // Categories
    SILO_CATEGORY_BASE("textures/gui/silo/categories/category_base.png"),
    SILO_CATEGORY_BASE_HOVERED("textures/gui/silo/categories/category_base_hovered.png"),
    SILO_CATEGORY_BASE_SELECTED("textures/gui/silo/categories/category_base_selected.png"),
    SILO_CATEGORY_CABINS("textures/gui/silo/categories/category_cabins.png"),
    SILO_CATEGORY_CAPSULES("textures/gui/silo/categories/category_capsules.png"),
    SILO_CATEGORY_DECOUPLERS("textures/gui/silo/categories/category_decouplers.png"),
    SILO_CATEGORY_FUEL_TANKS("textures/gui/silo/categories/category_fuel_tanks.png"),
    SILO_CATEGORY_LIQUID_ENGINES("textures/gui/silo/categories/category_liquid_engines.png"),
    SILO_CATEGORY_STRUCTURAL("textures/gui/silo/categories/category_structural.png"),
    SILO_PART_BACKGROUND("textures/gui/silo/categories/part_bg.png"),
    SILO_PART_BACKGROUND_SELECTED("textures/gui/silo/categories/part_bg_selected.png"),

    // Fields
    SILO_FIELD_ICON_LEFT("textures/gui/silo/fields/icon_left.png"),
    SILO_FIELD_ICON_MIDDLE("textures/gui/silo/fields/icon_middle.png"),
    SILO_FIELD_ICON_RIGHT("textures/gui/silo/fields/icon_right.png"),
    SILO_FIELD_TEXT_LEFT("textures/gui/silo/fields/text_left.png"),
    SILO_FIELD_TEXT_MIDDLE("textures/gui/silo/fields/text_middle.png"),
    SILO_FIELD_TEXT_RIGHT("textures/gui/silo/fields/text_right.png"),

    // Icons
    SILO_CLEAR_ICON("textures/gui/silo/fields/clear_icon.png"),
    SILO_DESTINATION_ICON("textures/gui/silo/fields/destination_icon.png"),
    SILO_ORDER_MODULES_ICON("textures/gui/silo/fields/order_modules_icon.png"),
    SILO_SAVE_BLUEPRINT_ICON("textures/gui/silo/fields/save_blueprint_icon.png"),
    SILO_ENTER_ROCKET_ICON("textures/gui/silo/fields/enter_rocket_icon.png"),

    // Scroll Bars

    // Stages
    SILO_STAGE_ADD("textures/gui/silo/stages/stage_add.png"),
    SILO_STAGE_ADD_HOVERED("textures/gui/silo/stages/stage_add_hovered.png"),
    SILO_STAGE_REMOVE("textures/gui/silo/stages/stage_remove.png"),
    SILO_STAGE_REMOVE_HOVERED("textures/gui/silo/stages/stage_remove_hovered.png"),
    SILO_STAGE_BURN_LIQUID("textures/gui/silo/stages/stage_burn_liquid.png"),
    SILO_STAGE_BURN_SOLID("textures/gui/silo/stages/stage_burn_solid.png"),
    SILO_STAGE_DECOUPLE("textures/gui/silo/stages/stage_decouple.png"),
    SILO_STAGE_DECOUPLE_SIDE("textures/gui/silo/stages/stage_decouple_side.png"),
    SILO_STAGE_EMPTY("textures/gui/silo/stages/stage_empty.png"),
    SILO_STAGE_EMPTY_HOVERED("textures/gui/silo/stages/stage_empty_hovered.png"),
    SILO_STAGE_MULTIPLE_BOTTOM("textures/gui/silo/stages/stage_multiple_bottom.png"),
    SILO_STAGE_MULTIPLE_BOTTOM_HOVERED("textures/gui/silo/stages/stage_multiple_bottom_hovered.png"),
    SILO_STAGE_MULTIPLE_MIDDLE("textures/gui/silo/stages/stage_multiple_middle.png"),
    SILO_STAGE_MULTIPLE_MIDDLE_HOVERED("textures/gui/silo/stages/stage_multiple_middle_hovered.png"),
    SILO_STAGE_MULTIPLE_TOP("textures/gui/silo/stages/stage_multiple_top.png"),
    SILO_STAGE_MULTIPLE_TOP_HOVERED("textures/gui/silo/stages/stage_multiple_top_hovered.png"),
    SILO_STAGE_PARACHUTE("textures/gui/silo/stages/stage_parachute.png"),
    SILO_STAGE_SINGLE("textures/gui/silo/stages/stage_single.png"),
    SILO_STAGE_SINGLE_HOVERED("textures/gui/silo/stages/stage_single_hovered.png"),
    SILO_RETURN_ICON("textures/gui/silo/fields/return_icon.png"),
    ICON_CYCLE("textures/gui/common/icons/cycle.png"),
    SILO_CARGO_ICON("textures/gui/silo/fields/cargo_icon.png"),
    SILO_LAUNCH_ICON("textures/gui/silo/fields/launch_icon.png"),

    // Rocket panels, stretchable so every popup shares the silo frame
    GUI_PANEL("textures/gui/common/panel.png", 16, 2),
    GUI_WELL("textures/gui/common/well.png", 16, 2),
    GUI_BUTTON("textures/gui/common/button.png", 16, 2),
    GUI_BUTTON_HOVERED("textures/gui/common/button_hovered.png", 16, 2),
    GUI_BUTTON_SELECTED("textures/gui/common/button_selected.png", 16, 2),
    GUI_BUTTON_DISABLED("textures/gui/common/button_disabled.png", 16, 2),
    GUI_PANEL_TRANSLUCENT("textures/gui/common/panel_translucent.png", 16, 16, 2, true),
    GUI_SCROLLBAR_TRACK("textures/gui/common/scrollbar_track.png", 6, 8, 3, 2),
    GUI_SCROLLBAR_TRACK_HOVERED("textures/gui/common/scrollbar_track_hovered.png", 6, 8, 3, 2),
    GUI_SCROLLBAR_HANDLE("textures/gui/common/scrollbar_handle.png"),
    GUI_SCROLLBAR_HANDLE_HOVERED("textures/gui/common/scrollbar_handle_hovered.png"),
    GUI_TITLEBAR_GRIP("textures/gui/common/titlebar_grip.png"),
    ICON_CLOSE("textures/gui/common/icons/close.png"),
    GUI_SLOT("textures/gui/common/slot.png"),
    GUI_ARROW("textures/gui/common/arrow.png"),
    STARMAP_SIDEBAR_ARROW("textures/gui/starmap/sidebar_arrow.png"),
    STARMAP_SIDEBAR_ARROW_DOWN("textures/gui/starmap/sidebar_arrow_down.png"),
    GUI_LED_ON("textures/gui/common/led_on.png"),
    GUI_LED_OFF("textures/gui/common/led_off.png"),
    GUI_LED_WAIT("textures/gui/common/led_wait.png"),
    GUI_STATION_LINK_ON("textures/gui/station/station_link_on.png"),
    GUI_STATION_LINK_OFF("textures/gui/station/station_link_off.png"),
    ICON_SETTINGS("textures/gui/common/icons/settings.png"),
    GUI_RADAR_CELL("textures/gui/common/radar_cell.png"),
    ICON_OXYGEN_COLLECTOR("textures/blocks/machine/oxygen_collector_on.png"),
    ICON_OXYGEN_PYLON("textures/blocks/machine/oxygen_pylon_on.png"),
    AIRLOCK_PROXIMITY_OPEN("textures/gui/station/airlock/proximity_open.png"),
    AIRLOCK_PROXIMITY_CLOSE("textures/gui/station/airlock/proximity_close.png"),
    AIRLOCK_REDSTONE_CONTROL("textures/gui/station/airlock/redstone_control.png"),
    AIRLOCK_MANUAL_CLICK("textures/gui/station/airlock/manual_click.png"),
    AIRLOCK_AUTO_SEAL("textures/gui/station/airlock/auto_seal.png"),

    // Add more textures here
    ; // leave trailing semicolon

    private final ResourceLocation texture;
    private final UITexture uiTexture;

    EnumTextures(String location) {
        this.texture = LocationGalaxia(location);
        this.uiTexture = UITexture.fullImage(Galaxia.MODID, location);
    }

    /**
     * A square texture whose border stays fixed while the centre stretches
     *
     * @param location Path inside the mod's assets
     * @param size     Width and height of the image in pixels
     * @param border   Width of the fixed border in pixels
     */
    EnumTextures(String location, int size, int border) {
        this.texture = LocationGalaxia(location);
        this.uiTexture = UITexture.builder()
            .location(Galaxia.MODID, location)
            .imageSize(size, size)
            .adaptable(border)
            .build();
    }

    /**
     * A texture whose border stays fixed while the centre stretches
     *
     * @param location    Path inside the mod's assets
     * @param width       Width of the image in pixels
     * @param height      Height of the image in pixels
     * @param border      Width of the fixed border in pixels
     * @param translucent Whether the image has see-through pixels that have to be blended
     */
    EnumTextures(String location, int width, int height, int border, boolean translucent) {
        this.texture = LocationGalaxia(location);
        UITexture.Builder builder = UITexture.builder()
            .location(Galaxia.MODID, location)
            .imageSize(width, height)
            .adaptable(border);
        if (translucent) builder.nonOpaque();
        this.uiTexture = builder.build();
    }

    /**
     * A vertical strip whose caps stay fixed while its middle stretches
     *
     * @param location Path inside the mod's assets
     * @param width    Width of the image in pixels
     * @param height   Height of the image in pixels
     * @param top      Height of the fixed top cap
     * @param bottom   Height of the fixed bottom cap
     */
    EnumTextures(String location, int width, int height, int top, int bottom) {
        this.texture = LocationGalaxia(location);
        this.uiTexture = UITexture.builder()
            .location(Galaxia.MODID, location)
            .imageSize(width, height)
            .adaptable(0, top, 0, bottom)
            .build();
    }

    public ResourceLocation get() {
        return texture;
    }

    public UITexture getImage() {
        return uiTexture;
    }
}
