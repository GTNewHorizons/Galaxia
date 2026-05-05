package com.gtnewhorizons.galaxia.core.starmap.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import io.netty.buffer.Unpooled;

final class StarmapActionPayloadCodecTest {

    @Test
    void createAssetRoundTripPreservesFields() {
        StarmapActionPayload payload = StarmapActionPayload.createAsset(
            CelestialObjectId.MARS,
            "Mars Automated Station",
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        StarmapActionPayload decoded = roundTrip(payload);

        assertEquals(StarmapAction.CREATE_ASSET, decoded.action());
        assertEquals(CelestialObjectId.MARS, decoded.bodyId());
        assertEquals("Mars Automated Station", decoded.displayName());
        assertEquals(CelestialAsset.Kind.AUTOMATED_STATION, decoded.assetKind());
        assertEquals(Buildable.Status.OPERATIONAL, decoded.assetStatus());
    }

    @Test
    void buildModuleRoundTripPreservesPlacementFields() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        StationTileCoord coord = StationTileCoord.of(1, 0);
        StarmapActionPayload payload = StarmapActionPayload
            .buildModule(assetId, FacilityModuleKind.STORAGE, ModuleShape.SINGLE, ModuleTier.HV, true, coord);

        StarmapActionPayload decoded = roundTrip(payload);

        assertEquals(StarmapAction.BUILD_MODULE, decoded.action());
        assertEquals(assetId, decoded.assetId());
        assertEquals(FacilityModuleKind.STORAGE, decoded.moduleKind());
        assertEquals(ModuleShape.SINGLE, decoded.moduleShape());
        assertEquals(ModuleTier.HV, decoded.moduleTier());
        assertEquals(true, decoded.instantBuild());
        assertEquals(coord, decoded.tileCoord());
    }

    @Test
    void renameAssetRoundTripPreservesDisplayName() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        StarmapActionPayload payload = StarmapActionPayload.renameAsset(assetId, "Renamed Station");

        StarmapActionPayload decoded = roundTrip(payload);

        assertEquals(StarmapAction.RENAME_ASSET, decoded.action());
        assertEquals(assetId, decoded.assetId());
        assertEquals("Renamed Station", decoded.displayName());
    }

    private static StarmapActionPayload roundTrip(StarmapActionPayload payload) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        StarmapActionPayloadCodec.write(buf, payload);
        return StarmapActionPayloadCodec.read(buf);
    }
}
