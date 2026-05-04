package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.Station;

final class AssetCreatePacketTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        CelestialRegistry.freezeAndBake();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void createsAutomatedFacilityOnServerAndReturnsFullSync() {
        AssetCreatePacket packet = new AssetCreatePacket(
            CelestialObjectId.MARS,
            "Mars Automated Station",
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        AssetSyncPacket sync = AssetCreatePacket.createOnServer(TEAM, packet);

        CelestialAsset created = CelestialAssetStore.SERVER.allAssetsInternal()
            .get(0);
        assertInstanceOf(AutomatedFacility.class, created);
        assertNotNull(CelestialAssetStore.SERVER.findAssetInternal(created.assetId));
        assertNotNull(sync);
    }

    @Test
    void createsBaseStationOnServerAndReturnsFullSync() {
        AssetCreatePacket packet = new AssetCreatePacket(
            CelestialObjectId.MARS,
            "Mars Station",
            CelestialAsset.Kind.STATION,
            Buildable.Status.OPERATIONAL);

        AssetSyncPacket sync = AssetCreatePacket.createOnServer(TEAM, packet);

        CelestialAsset created = CelestialAssetStore.SERVER.allAssetsInternal()
            .get(0);
        assertInstanceOf(Station.class, created);
        assertNotNull(CelestialAssetStore.SERVER.findAssetInternal(created.assetId));
        assertNotNull(sync);
    }
}
