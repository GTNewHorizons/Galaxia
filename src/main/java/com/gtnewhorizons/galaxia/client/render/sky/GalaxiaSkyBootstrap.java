package com.gtnewhorizons.galaxia.client.render.sky;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender.BillboardLayer;
import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender.DomeLayer;
import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender.SkyPreset;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class GalaxiaSkyBootstrap {

    public static void clientInit() {
        SkyPreset milkyWayPreset = EnhancedSkyRender.preset("milky_way")
            .brightStars(64, 0.25f, 0.85f, true)
            .billboardLayer(
                new BillboardLayer(LocationGalaxia("textures/sky/nebula_01.png"), 22, 6.0f, 0.20f, 0.15f, 0.95f))
            .billboardLayer(
                new BillboardLayer(LocationGalaxia("textures/sky/quasar_01.png"), 5, 1.8f, 0.45f, 0.35f, 1.00f))
            .domeLayer(new DomeLayer(LocationGalaxia("textures/sky/milky_way.png"), 1.0f, 0.55f, 0.20f));

        EnhancedSkyRender.registerPreset(milkyWayPreset, 0, DimensionEnum.MOON.getId());
    }
}
