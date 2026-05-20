package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

public interface IRocketPartDef {

    String MODULE_DOMAIN = "textures/model/modules/";

    int id();

    String name();

    int width();

    int height();

    int weight();

    String assetFolder();

    default ResourceLocation modelLocation() {
        return LocationGalaxia(MODULE_DOMAIN + assetFolder() + "/model.obj");
    }

    default ResourceLocation spriteLocation() {
        return LocationGalaxia(MODULE_DOMAIN + assetFolder() + "/schematic_sprite.png");
    }

    default ResourceLocation textureLocation() {
        return LocationGalaxia(MODULE_DOMAIN + assetFolder() + "/texture.png");
    }
}
