package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleShapeTest {

    @BeforeAll
    static void initModules() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void tilesRotateAroundAnchorInQuarterTurns() {
        StationTileCoord anchor = StationTileCoord.of(4, 4);

        assertArrayEquals(
            new StationTileCoord[] { StationTileCoord.of(4, 4), StationTileCoord.of(4, 5), StationTileCoord.of(3, 4),
                StationTileCoord.of(3, 5) },
            ModuleShape.QUAD_2x2.tiles(anchor, 1));
        assertArrayEquals(
            new StationTileCoord[] { StationTileCoord.of(4, 4), StationTileCoord.of(3, 4), StationTileCoord.of(4, 3),
                StationTileCoord.of(3, 3) },
            ModuleShape.QUAD_2x2.tiles(anchor, 2));
        assertArrayEquals(
            new StationTileCoord[] { StationTileCoord.of(4, 4), StationTileCoord.of(4, 3), StationTileCoord.of(5, 4),
                StationTileCoord.of(5, 3) },
            ModuleShape.QUAD_2x2.tiles(anchor, 3));
    }

    @Test
    void moduleRotationIsNormalized() {
        ModuleInstance module = FacilityModuleKind.STORAGE
            .create(StationTileCoord.of(0, 0), ModuleShape.QUAD_2x2, ModuleTier.HV);

        module.setRotation(5);

        assertEquals(1, module.rotation());

        module.setRotation(-1);

        assertEquals(3, module.rotation());
    }

    @Test
    void rotatedShapeTilesKeepTheirBaseTextureCells() {
        StationTileCoord anchor = StationTileCoord.of(4, 4);

        assertEquals(2, ModuleShape.L_2x2.textureGridWidth());
        assertEquals(2, ModuleShape.L_2x2.textureGridHeight());
        assertEquals(new ModuleShape.TextureTile(0, 0), ModuleShape.L_2x2.textureTile(anchor, anchor, 1));
        assertEquals(
            new ModuleShape.TextureTile(0, 1),
            ModuleShape.L_2x2.textureTile(anchor, StationTileCoord.of(3, 4), 1));
        assertEquals(
            new ModuleShape.TextureTile(1, 1),
            ModuleShape.L_2x2.textureTile(anchor, StationTileCoord.of(3, 5), 1));
    }
}
