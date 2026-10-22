package com.gtnewhorizons.galaxia.registry.satellite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

class ProspectingFlowTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @BeforeEach
    void reset() {
        CelestialAssetStore.SERVER.clearInternal();
        SatelliteNetworkService.clear();
    }

    @Test
    void realSatelliteFlowRequiresPaidProcessingAndExpiresWithoutDelivery() {
        UUID team = UUID.randomUUID();
        UUID otherTeam = UUID.randomUUID();
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialAssetStore.SERVER.setSatelliteCount(team, body, SatelliteKind.PROSPECTING, 1);
        var computer = new ComputerDataProcessor(
            UUID.randomUUID(),
            team,
            body,
            SatelliteDataType.PROSPECTING,
            SatelliteDataType.PROCESSED_PROSPECTING);
        computer.setProcessingCapacity(5L);
        SatelliteNetworkService.registerEndpoint(team, computer);

        SatelliteNetworkService.tickDataJobs();
        assertEquals(0.0D, SatelliteNetworkService.miningYieldBonus(team, body));
        assertEquals(0L, computer.process(request -> 0L));
        assertEquals(1L, computer.process(request -> request));
        SatelliteNetworkService.tickDataJobs();
        assertTrue(SatelliteNetworkService.miningYieldBonus(team, body) > 0.0D);
        assertEquals(0.0D, SatelliteNetworkService.miningYieldBonus(otherTeam, body));

        SatelliteNetworkService.tickDataJobs();
        assertEquals(0.0D, SatelliteNetworkService.miningYieldBonus(team, body));
        SatelliteNetworkService.clear();
        assertEquals(0.0D, SatelliteNetworkService.miningYieldBonus(team, body));
    }

    @Test
    void receiverConsumesReturnedFlowBeyondItsOwnGenerationRate() {
        UUID team = UUID.randomUUID();
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var key = SatelliteDataKey.origin(SatelliteDataType.PROCESSED_PROSPECTING, body);
        CelestialAssetStore.SERVER.setSatelliteCount(team, body, SatelliteKind.PROSPECTING, 1);
        long room = SatelliteNetworkService.outputRoom(team, body, key);
        SatelliteNetworkService.offerData(team, body, key, 1L);
        SatelliteNetworkService.tickDataJobs();
        double oneUnitBonus = SatelliteNetworkService.miningYieldBonus(team, body);

        SatelliteNetworkService.offerData(team, body, key, 50L);
        SatelliteNetworkService.tickDataJobs();

        assertTrue(SatelliteNetworkService.miningYieldBonus(team, body) > oneUnitBonus);
        assertEquals(room, SatelliteNetworkService.outputRoom(team, body, key));
    }

    @Test
    void addedHardwareCapacityProcessesSeveralOriginsInTheSameTick() {
        UUID team = UUID.randomUUID();
        var location = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        var mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var egora = CelestialObjectKey.registered(CelestialObjectId.EGORA);
        SatelliteNetworkService.rebuild(
            team,
            List.of(new SatelliteNetworkGraph.Node(location, 0D, 0D, 0D)),
            Map.of(location, 10000L),
            new SatelliteDataBufferStore());
        var computer = new ComputerDataProcessor(
            UUID.randomUUID(),
            team,
            location,
            SatelliteDataType.PROSPECTING,
            SatelliteDataType.PROCESSED_PROSPECTING);
        computer.setProcessingCapacity(1000L);
        long accepted = computer.accept(SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, mars), 500L)
            + computer.accept(SatelliteDataKey.origin(SatelliteDataType.PROSPECTING, egora), 500L);
        assertEquals(1000L, accepted);
        var marsOutput = SatelliteDataKey.origin(SatelliteDataType.PROCESSED_PROSPECTING, mars);
        var egoraOutput = SatelliteDataKey.origin(SatelliteDataType.PROCESSED_PROSPECTING, egora);
        long room = SatelliteNetworkService.outputRoom(team, location, marsOutput);

        assertEquals(1000L, computer.process(requested -> requested));
        assertEquals(500L, room - SatelliteNetworkService.outputRoom(team, location, marsOutput));
        assertEquals(500L, room - SatelliteNetworkService.outputRoom(team, location, egoraOutput));
    }

    @Test
    void everyFullHundredPercentAddsOneBaseYieldAndTheRemainderRollsOnce() {
        Random random = new Random(107L);
        for (int i = 0; i < 100; i++) {
            assertEquals(1, SatelliteNetworkService.mineralYieldMultiplier(0.0D, random));
            assertEquals(2, SatelliteNetworkService.mineralYieldMultiplier(1.0D, random));
            assertEquals(4, SatelliteNetworkService.mineralYieldMultiplier(3.0D, random));
            int factor = SatelliteNetworkService.mineralYieldMultiplier(2.5D, random);
            assertTrue(factor == 3 || factor == 4);
        }
    }
}
