package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint;

import com.gtnewhorizons.galaxia.client.EnumTextures;

public enum RocketPartCategory {

    CAPSULES(EnumTextures.SILO_CATEGORY_CAPSULES, "galaxia.rocket_editor.category.capsules"),
    FUEL_TANKS(EnumTextures.SILO_CATEGORY_FUEL_TANKS, "galaxia.rocket_editor.category.fuel_tanks"),
    LIQUID_ENGINES(EnumTextures.SILO_CATEGORY_LIQUID_ENGINES, "galaxia.rocket_editor.category.liquid_engines"),
    DECOUPLERS(EnumTextures.SILO_CATEGORY_DECOUPLERS, "galaxia.rocket_editor.category.decouplers"),
    CABINS(EnumTextures.SILO_CATEGORY_CABINS, "galaxia.rocket_editor.category.cabins"),
    STRUCTURAL(EnumTextures.SILO_CATEGORY_STRUCTURAL, "galaxia.rocket_editor.category.structural");

    public final EnumTextures icon;
    public final String langKey;

    RocketPartCategory(EnumTextures icon, String langKey) {
        this.icon = icon;
        this.langKey = langKey;
    }
}
