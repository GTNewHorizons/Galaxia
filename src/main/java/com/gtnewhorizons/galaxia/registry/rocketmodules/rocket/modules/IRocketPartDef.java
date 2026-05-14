package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

public sealed interface IRocketPartDef permits LanderPartDef,RiderPartDef,StructuralPartDef,FuelTankPartDef,FunctionalPartDef,EnginePartDef,DecouplerPartDef,CapsulePartDef {

    String MODULE_DOMAIN = "textures/model/modules/";

    int id();

    String name();

    double width();

    double height();

    double weight();

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

    default int getWidthCells() {
        return (int) Math.ceil(width() / 3.0);
    }

    default int getHeightCells() {
        return (int) Math.ceil(height() / 3.0);
    }
}
