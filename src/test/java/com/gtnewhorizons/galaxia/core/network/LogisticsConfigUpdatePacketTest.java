package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class LogisticsConfigUpdatePacketTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @BeforeEach
    @AfterEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        LogisticStore.clearSignals();
    }

    @Test
    void applyPreservesNonAutomatedAssetBehavior() {
        Station station = new Station(CelestialAsset.ID.create(), CelestialObjectId.MARS, Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, station);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(
            station.assetId,
            resource,
            new LogisticsResourceConfig(12, 64, true, true),
            LogisticsConfigAccessMode.IMPORT_ONLY);

        assertTrue(packet.apply(TEAM));
        LogisticsResourceConfig config = station.logisticsConfig.get(resource);
        assertTrue(config.isImportEnabled());
        assertFalse(config.isSupplyEnabled());
        assertEquals(1, station.getStateRevision());

        assertTrue(
            LogisticsConfigUpdatePacket.remove(station.assetId, resource)
                .apply(TEAM));
        assertEquals(LogisticsResourceConfig.DEFAULT, station.logisticsConfig.get(resource));
        assertEquals(2, station.getStateRevision());
    }

    @Test
    void applyRejectsAutomatedFacilityWithoutMutation() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(
            facility.assetId,
            resource,
            new LogisticsResourceConfig(12, 64, true, false));

        assertFalse(packet.apply(TEAM));
        assertEquals(LogisticsResourceConfig.DEFAULT, facility.logisticsConfig.get(resource));
        assertEquals(0, facility.getStateRevision());
    }
}
