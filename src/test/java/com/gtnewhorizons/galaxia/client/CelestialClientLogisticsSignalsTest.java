package com.gtnewhorizons.galaxia.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CelestialClientLogisticsSignalsTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void clearClientState() {
        CelestialClient.clearLocalState();
    }

    @Test
    void flatSourceSignalsPreserveCurrentSystemAndPlanetAggregates() {
        AutomatedFacility first = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility second = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility mars = facility(CelestialObjectId.MARS);
        ItemStackWrapper iron = new ItemStackWrapper(Items.iron_ingot, 0, null);
        ItemStackWrapper diamond = new ItemStackWrapper(Items.diamond, 0, null);
        List<LogisticSignal> signals = List
            .of(signal(first, iron, 8L), signal(second, iron, -8L), signal(mars, diamond, 5L));
        int initialRevision = CelestialClient.clientSignalRevision();

        CelestialClient.updateClientSignals(signals);

        assertEquals(initialRevision + 1, CelestialClient.clientSignalRevision());
        assertEquals(
            Map.of(iron.toKey(), 0L, diamond.toKey(), 5L),
            CelestialClient.clientSignalsForSystem(first.systemKey));
        assertEquals(Map.of(iron.toKey(), 0L), CelestialClient.clientSignalsForPlanet(first.planetaryAnchorBodyKey));
        assertEquals(Map.of(diamond.toKey(), 5L), CelestialClient.clientSignalsForPlanet(mars.planetaryAnchorBodyKey));
    }

    @Test
    void clearingLocalStateRemovesReceivedSignals() {
        AutomatedFacility facility = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        CelestialClient.updateClientSignals(List.of(signal(facility, resource, 3L)));

        CelestialClient.clearLocalState();

        assertTrue(
            CelestialClient.clientSignalsForSystem(facility.systemKey)
                .isEmpty());
        assertTrue(
            CelestialClient.clientSignalsForPlanet(facility.planetaryAnchorBodyKey)
                .isEmpty());
        assertEquals(0, CelestialClient.clientSignalRevision());
    }

    @Test
    void equalSignalContentDoesNotAdvanceRevision() {
        AutomatedFacility facility = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        List<LogisticSignal> snapshot = List.of(signal(facility, resource, 3L));
        CelestialClient.updateClientSignals(snapshot);
        int revision = CelestialClient.clientSignalRevision();

        CelestialClient.updateClientSignals(List.copyOf(snapshot));

        assertEquals(revision, CelestialClient.clientSignalRevision());
    }

    @Test
    void equalDeliveryContentDoesNotAdvanceRevision() {
        AutomatedFacility source = facility(CelestialObjectId.OVERWORLD);
        AutomatedFacility destination = facility(CelestialObjectId.OVERWORLD);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        LogisticsDelivery.ID id = LogisticsDelivery.ID.create();
        CelestialClient.updateClientDeliveries(List.of(delivery(id, source, destination, resource)));
        int revision = CelestialClient.clientDeliveryRevision();

        CelestialClient.updateClientDeliveries(List.of(delivery(id, source, destination, resource)));

        assertEquals(revision, CelestialClient.clientDeliveryRevision());
    }

    private static LogisticSignal signal(AutomatedFacility facility, ItemStackWrapper resource, long amount) {
        return new LogisticSignal(
            facility.assetId,
            facility.systemKey,
            resource,
            amount,
            LogisticSignal.Scope.SYSTEM,
            facility.celestialObjectKey,
            facility.planetaryAnchorBodyKey);
    }

    private static AutomatedFacility facility(CelestialObjectId body) {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            body,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static LogisticsDelivery delivery(LogisticsDelivery.ID id, AutomatedFacility source,
        AutomatedFacility destination, ItemStackWrapper resource) {
        return LogisticsDelivery.createWithTrajectory(
            id,
            source.assetId,
            destination.assetId,
            resource,
            3L,
            20,
            LogisticSignal.Scope.SYSTEM,
            source.celestialObjectKey,
            destination.celestialObjectKey,
            0.0,
            0.0,
            null);
    }
}
