package com.gtnewhorizons.galaxia.core.starmap.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.network.AssetUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StarmapServerActionsTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @BeforeEach
    @AfterEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void renameAssetMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();

        boolean result = AssetUpdatePacket.rename(facility.assetId, "Renamed Station")
            .mutateNoChecks(TEAM, facility);

        assertTrue(result);
        assertEquals(
            "Renamed Station",
            CelestialAssetStore.SERVER.findAssetInternal(facility.assetId)
                .displayName());
    }

    @Test
    void startDeconstructionMutatesServerAndReturnsImmediateFullSync() {
        AutomatedFacility facility = addFacilityToServer();
        facility.updateStatus(Buildable.Status.CONSTRUCTION_SITE);

        boolean result = AssetUpdatePacket.create(facility.assetId, AssetUpdatePacket.Action.START_DECONSTRUCTION)
            .mutateNoChecks(TEAM, facility);

        assertTrue(result);
        assertEquals(
            Buildable.Status.DECONSTRUCTION,
            CelestialAssetStore.SERVER.findAssetInternal(facility.assetId)
                .status());
    }

    @Test
    void cancelConstructionRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();
        facility.updateStatus(Buildable.Status.CONSTRUCTION_SITE);

        boolean result = AssetUpdatePacket.create(facility.assetId, AssetUpdatePacket.Action.CANCEL_CONSTRUCTION)
            .mutateNoChecks(TEAM, facility);

        assertTrue(result);
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    @Test
    void destroyAssetRemovesServerAssetAndReturnsRemovalSync() {
        AutomatedFacility facility = addFacilityToServer();

        boolean result = AssetUpdatePacket.create(facility.assetId, AssetUpdatePacket.Action.DESTROY_ASSET)
            .mutateNoChecks(TEAM, facility);

        assertTrue(result);
        assertNull(CelestialAssetStore.SERVER.findAssetInternal(facility.assetId));
    }

    private static AutomatedFacility addFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }
}
