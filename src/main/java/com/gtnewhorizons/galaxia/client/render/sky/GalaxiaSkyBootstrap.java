package com.gtnewhorizons.galaxia.client.render.sky;

import net.minecraft.util.ResourceLocation;

/**
 * Minimal registration example.
 * Keep the art-direction data here and leave the renderer generic.
 */
public final class GalaxiaSkyBootstrap {

    private GalaxiaSkyBootstrap() {}

    public static void clientInit() {
        EnhancedSkyRender.bootstrapDefaults();

        EnhancedSkyRender.registerPreset(
            42,
            EnhancedSkyRender.preset("planet_42")
                .brightStars(96, 0.25f, 1.10f, true)
                .billboardLayer(
                    EnhancedSkyRender.billboard(
                        new ResourceLocation("galaxia:textures/sky/planet42_nebula.png"),
                        14.0f,
                        7.0f,
                        18.0f,
                        0.95f,
                        0.15f,
                        1.0f))
                .domeLayer(
                    EnhancedSkyRender.dome(
                        new ResourceLocation("galaxia:textures/sky/planet42_milkyway.png"),
                        1.0f,
                        0.15f,
                        0.95f,
                        140.0f,
                        64,
                        32,
                        0.0f,
                        false)));
    }
}
