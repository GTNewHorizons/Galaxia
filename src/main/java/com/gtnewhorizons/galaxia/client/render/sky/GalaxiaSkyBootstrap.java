package com.gtnewhorizons.galaxia.client.render.sky;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender.BillboardLayer;
import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender.DomeLayer;
import com.gtnewhorizons.galaxia.client.render.sky.EnhancedSkyRender.SkyPreset;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;

public final class GalaxiaSkyBootstrap {

    public static void clientInit() {
        SkyPreset milkyWayPreset = EnhancedSkyRender.preset("milky_way")

            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/orion_nebula.png"),
                    1,
                    14.0f,
                    14.0f,
                    0.75f,
                    0.0f,
                    0.85f,
                    false,
                    0xA1B2C3D4L))
            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/lagoon_nebula.png"),
                    1,
                    10.0f,
                    10.0f,
                    0.65f,
                    0.0f,
                    0.80f,
                    false,
                    0xB2C3D4E5L))
            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/eagle_nebula.png"),
                    1,
                    9.0f,
                    9.0f,
                    0.60f,
                    0.0f,
                    0.75f,
                    false,
                    0xC3D4E5F6L))

            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/crab_nebula.png"),
                    1,
                    5.5f,
                    5.5f,
                    0.80f,
                    0.0f,
                    0.90f,
                    false,
                    0xD4E5F601L))
            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/dumbbell_nebula.png"),
                    1,
                    4.5f,
                    4.5f,
                    0.80f,
                    0.0f,
                    0.90f,
                    false,
                    0xF601234EL))
            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/ring_nebula.png"),
                    1,
                    3.5f,
                    3.5f,
                    0.85f,
                    0.0f,
                    0.95f,
                    false,
                    0xE5F60112L))

            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/planetary_nebula.png"),
                    2,
                    2.5f,
                    4.0f,
                    0.80f,
                    0.0f,
                    0.90f,
                    true,
                    0x01234567L))

            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/nebula/supernova.png"),
                    1,
                    4.0f,
                    4.0f,
                    0.95f,
                    0.0f,
                    1.00f,
                    false,
                    0x12345678L))

            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/galaxy/spiral_galaxy.png"),
                    2,
                    4.5f,
                    7.5f,
                    0.55f,
                    0.0f,
                    0.70f,
                    true,
                    0x23456789L))
            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/galaxy/elliptical_galaxy.png"),
                    2,
                    3.5f,
                    5.5f,
                    0.50f,
                    0.0f,
                    0.65f,
                    true,
                    0x3456789AL))
            .billboardLayer(
                new BillboardLayer(
                    LocationGalaxia("textures/environment/galaxy/lenticular_galaxy.png"),
                    1,
                    4.0f,
                    6.0f,
                    0.50f,
                    0.0f,
                    0.65f,
                    true,
                    0x456789ABL))

            .domeLayer(new DomeLayer(LocationGalaxia("textures/environment/galaxy/img.png"), 1.0f, 0.0f, 0.55f));

        EnhancedSkyRender.registerPreset(milkyWayPreset, 0, DimensionEnum.MOON.getId());
    }
}
