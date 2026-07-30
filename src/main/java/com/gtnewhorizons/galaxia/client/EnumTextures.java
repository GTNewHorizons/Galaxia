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
    SILO_SCROLL_BAR_BOTTOM("textures/gui/silo/scroll_bars/bar_bottom.png"),
    SILO_SCROLL_BAR_BOTTOM_HOVERED("textures/gui/silo/scroll_bars/bar_bottom_hovered.png"),
    SILO_SCROLL_BAR_HANDLE("textures/gui/silo/scroll_bars/bar_handle.png"),
    SILO_SCROLL_BAR_HANDLE_HOVERED("textures/gui/silo/scroll_bars/bar_handle_hovered.png"),
    SILO_SCROLL_BAR_MIDDLE("textures/gui/silo/scroll_bars/bar_middle.png"),
    SILO_SCROLL_BAR_MIDDLE_HOVERED("textures/gui/silo/scroll_bars/bar_middle_hovered.png"),
    SILO_SCROLL_BAR_TOP("textures/gui/silo/scroll_bars/bar_top.png"),
    SILO_SCROLL_BAR_TOP_HOVERED("textures/gui/silo/scroll_bars/bar_top_hovered.png"),

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

    // Add more textures here
    ; // leave trailing semicolon

    private final ResourceLocation texture;
    private final UITexture uiTexture;

    EnumTextures(String location) {
        this.texture = LocationGalaxia(location);
        this.uiTexture = UITexture.fullImage(Galaxia.MODID, location);
    }

    public ResourceLocation get() {
        return texture;
    }

    public UITexture getImage() {
        return uiTexture;
    }
}
