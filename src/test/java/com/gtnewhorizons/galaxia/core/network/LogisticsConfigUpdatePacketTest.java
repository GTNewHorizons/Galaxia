package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;

final class LogisticsConfigUpdatePacketTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        TestFMLRegistry.init();
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
    void applyBumpsSyncRevisionForLogisticsConfigDelta() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(
            facility.assetId,
            resource,
            new LogisticsResourceConfig(12, 64, true, false));

        AssetSyncPacket sync = packet.apply(TEAM);

        assertEquals(1, facility.getSyncRevision());
        assertEquals(1, sync.syncRevision());
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
