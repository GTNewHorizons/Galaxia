package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleLayerRendererTest {

    @Test
    void textureRegionFollowsRotatedModuleFootprint() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.HAMMER,
            StationTileCoord.of(4, 4),
            ModuleShape.QUAD_2x2,
            ModuleTier.IV);
        module.setRotation(1);

        ModuleLayerRenderer.TextureRegion anchor = ModuleLayerRenderer.textureRegion(module, StationTileCoord.of(4, 4));
        ModuleLayerRenderer.TextureRegion left = ModuleLayerRenderer.textureRegion(module, StationTileCoord.of(3, 4));
        ModuleLayerRenderer.TextureRegion lowerLeft = ModuleLayerRenderer
            .textureRegion(module, StationTileCoord.of(3, 5));

        assertRegion(anchor, 0.5f, 0f, 1f, 0.5f);
        assertRegion(left, 0f, 0f, 0.5f, 0.5f);
        assertRegion(lowerLeft, 0f, 0.5f, 0.5f, 1f);
    }

    private static void assertRegion(ModuleLayerRenderer.TextureRegion region, float u0, float v0, float u1, float v1) {
        assertEquals(u0, region.u0());
        assertEquals(v0, region.v0());
        assertEquals(u1, region.u1());
        assertEquals(v1, region.v1());
    }
}
