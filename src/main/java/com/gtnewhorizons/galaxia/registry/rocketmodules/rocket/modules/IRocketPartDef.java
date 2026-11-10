package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

public interface IRocketPartDef {

    String MODULE_DOMAIN = "textures/model/modules/";

    /**
     * Runtime index assigned at registration. Identical on client and server, but not stable between versions, so
     * never persist it - use {@link #key()} instead
     */
    int id();

    /** Stable identifier used for saving */
    String key();

    /** Localized display name */
    String name();

    int width();

    int height();

    /** Dry mass of the module in kg */
    int weight();

    String assetFolder();

    RocketPartCategory category();

    /** Items the Module Assembler consumes to build one of these */
    ModuleRecipe recipe();

    default ResourceLocation modelLocation() {
        return assetFolder() == null ? null : LocationGalaxia(MODULE_DOMAIN + assetFolder() + "/model.obj");
    }

    default ResourceLocation textureLocation() {
        return assetFolder() == null ? null : LocationGalaxia(MODULE_DOMAIN + assetFolder() + "/texture.png");
    }

    /**
     * color multiplier applied to the model group named {@code core}, if the model has one
     *
     * @return RGB color, or -1 to render the core untinted
     */
    default int coreColor() {
        return -1;
    }
}
