package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
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
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        LogisticStore.clearSignals();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
        LogisticStore.clearSignals();
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

    @Test
    void importOnlyLogisticsUpdateCannotEnableSupply() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(
            facility.assetId,
            resource,
            new LogisticsResourceConfig(12, 64, true, true),
            LogisticsConfigAccessMode.IMPORT_ONLY);

        packet.apply(TEAM);

        LogisticsResourceConfig config = facility.logisticsConfig.get(resource);
        assertTrue(config.isImportEnabled());
        assertFalse(config.isSupplyEnabled());
    }

    @Test
    void disablingCoreImportClearsUpkeepAutoRequest() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        facility.setUpkeepAutoOrder(resource, true);
        facility.logisticsConfig.set(resource, new LogisticsResourceConfig(12, 64, true, false));
        LogisticsConfigUpdatePacket packet = new LogisticsConfigUpdatePacket(
            facility.assetId,
            resource,
            new LogisticsResourceConfig(12, 64, false, false),
            LogisticsConfigAccessMode.IMPORT_ONLY);

        packet.apply(TEAM);

        assertFalse(facility.isUpkeepAutoOrderEnabled(resource));
    }

    @Test
    void importOnlyLogisticsUpdateRequestsEffectiveUpkeepLowerBound() {
        AutomatedFacility facility = addFacilityToServer();
        ItemStackWrapper resource = new ItemStackWrapper(Items.redstone, 0, null);
        facility.updateItems(resource, 3);
        facility.setBound(resource, 5, true);
        facility.setUpkeepReserve(resource, 10L);

        AssetSyncPacket sync = new LogisticsConfigUpdatePacket(
            facility.assetId,
            resource,
            facility.logisticsConfig.get(resource)
                .withImportEnabled(true),
            LogisticsConfigAccessMode.IMPORT_ONLY).apply(TEAM);

        LogisticSignal signal = logisticsSignalFor(facility, resource);
        assertEquals(-12L, signal.amount());
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

    private static LogisticSignal logisticsSignalFor(AutomatedFacility facility, ItemStackWrapper resource) {
        return LogisticStore.allSignalsForScope(LogisticSignal.Scope.SYSTEM)
            .values()
            .stream()
            .flatMap(List::stream)
            .filter(signal -> facility.assetId.equals(signal.outpostAssetId()))
            .filter(signal -> resource.equals(signal.resourceId()))
            .findFirst()
            .orElseThrow();
    }
}
