package com.gtnewhorizons.galaxia.core.starmap.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class StarmapActionPayloadTest {

    @Test
    void createAssetPayloadCapturesServerAuthoritativeCreateRequest() {
        StarmapActionPayload payload = StarmapActionPayload.createAsset(
            CelestialObjectId.MARS,
            "Mars Automated Station",
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        assertEquals(StarmapAction.CREATE_ASSET, payload.action());
        assertEquals(CelestialObjectId.MARS, payload.bodyId());
        assertEquals("Mars Automated Station", payload.displayName());
        assertEquals(CelestialAsset.Kind.AUTOMATED_STATION, payload.assetKind());
        assertEquals(Buildable.Status.OPERATIONAL, payload.assetStatus());
    }

    @Test
    void buildModulePayloadCapturesPlacementRequest() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        StationTileCoord coord = StationTileCoord.of(1, 0);

        StarmapActionPayload payload = StarmapActionPayload
            .buildModule(assetId, FacilityModuleKind.STORAGE, ModuleShape.SINGLE, ModuleTier.HV, true, coord);

        assertEquals(StarmapAction.BUILD_MODULE, payload.action());
        assertEquals(assetId, payload.assetId());
        assertEquals(FacilityModuleKind.STORAGE, payload.moduleKind());
        assertEquals(ModuleShape.SINGLE, payload.moduleShape());
        assertEquals(ModuleTier.HV, payload.moduleTier());
        assertEquals(true, payload.instantBuild());
        assertEquals(coord, payload.tileCoord());
    }

    @Test
    void rejectsAssetOnlyPayloadWithoutAssetId() {
        assertThrows(
            IllegalArgumentException.class,
            () -> StarmapActionPayload.assetOnly(StarmapAction.DESTROY_ASSET, null));
    }
}
