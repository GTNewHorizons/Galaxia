package com.gtnewhorizons.galaxia.registry.satellite;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaSatelliteAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class GalaxiaSatelliteAPITest {

    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000108");

    @BeforeAll
    static void bootstrapRegistry() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void clearState() {
        SatelliteNetworkService.clear();
    }

    @Test
    void exposesReadOnlyNetworkBandwidthAndPendingDataByBody() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        CelestialObjectKey overworld = CelestialObjectKey.registered(CelestialObjectId.OVERWORLD);
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteDataBufferStore store = SatelliteNetworkService.dataBuffers(TEAM);
        store.finishProduction(mars, prospecting, SatelliteBandwidthFormatter.kilobits(15L));
        registerConsumer(overworld, SatelliteDataType.PROSPECTING, 15L);
        SatelliteNetworkService.rebuild(TEAM, nodes(), capacity(), store);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, mars, SatelliteKind.COMMUNICATION, 1);
        CelestialAssetStore.SERVER.setSatelliteCount(TEAM, mars, SatelliteKind.PROSPECTING, 2);

        assertEquals(1, GalaxiaSatelliteAPI.count(TEAM, mars, SatelliteKind.COMMUNICATION));
        assertEquals(SatelliteKind.COMMUNICATION.effectPerSatellite(), GalaxiaSatelliteAPI.bandwidth(TEAM, mars));
        assertEquals(
            2 * SatelliteKind.PROSPECTING.effectPerSatellite(),
            GalaxiaSatelliteAPI.miningSpeedBonus(TEAM, mars));
        assertEquals(10L, GalaxiaSatelliteAPI.localCapacityKbps(TEAM, mars));
        assertEquals(10L, GalaxiaSatelliteAPI.localUsedKbps(TEAM, mars));
        assertEquals(10L, GalaxiaSatelliteAPI.pathCapacityKbps(TEAM, mars, overworld));
        assertFalse(GalaxiaSatelliteAPI.canStartProcess(TEAM, mars, prospecting));

        List<GalaxiaSatelliteAPI.PendingData> pending = GalaxiaSatelliteAPI.pendingData(TEAM, mars);

        assertEquals(1, pending.size());
        assertEquals(
            mars,
            pending.get(0)
                .bodyKey());
        assertEquals(
            prospecting,
            pending.get(0)
                .key());
        assertEquals(
            SatelliteBandwidthFormatter.kilobits(15L),
            pending.get(0)
                .deciKb());
    }

    @Test
    void processCanStartWhenBodyBufferIsWithinLocalCapacity() {
        CelestialObjectKey mars = CelestialObjectKey.registered(CelestialObjectId.MARS);
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteNetworkService.rebuild(TEAM, nodes(), capacity(), SatelliteNetworkService.dataBuffers(TEAM));

        assertTrue(GalaxiaSatelliteAPI.canStartProcess(TEAM, mars, prospecting));
    }

    @Test
    void pendingDataApiCanExposeMinorBodyKeys() {
        CelestialObjectKey asteroid = CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN));
        SatelliteDataKey prospecting = SatelliteDataKey.any(SatelliteDataType.PROSPECTING);
        SatelliteDataBufferStore store = new SatelliteDataBufferStore();
        store.finishProduction(asteroid, prospecting, SatelliteBandwidthFormatter.kilobits(15L));
        registerConsumer(
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            SatelliteDataType.PROSPECTING,
            15L);
        SatelliteNetworkService.rebuild(
            TEAM,
            List.of(
                new SatelliteNetworkGraph.Node(asteroid, null, 1, 0.0D, 0.0D, 1.0D),
                node(CelestialObjectId.OVERWORLD, 10.0D)),
            Map.of(asteroid, 10L, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), 10L),
            store);

        List<GalaxiaSatelliteAPI.PendingData> pending = GalaxiaSatelliteAPI.pendingData(TEAM, asteroid);

        assertEquals(1, pending.size());
        assertEquals(
            asteroid,
            pending.get(0)
                .bodyKey());
        assertEquals(
            List.of(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD)),
            pending.get(0)
                .destinationBodyKeys());
    }

    private static List<SatelliteNetworkGraph.Node> nodes() {
        return List.of(node(CelestialObjectId.MARS, 0.0D), node(CelestialObjectId.OVERWORLD, 10.0D));
    }

    private static SatelliteNetworkGraph.Node node(CelestialObjectId id, double x) {
        return new SatelliteNetworkGraph.Node(CelestialObjectKey.registered(id), null, id.ordinal(), x, 0.0D, 1.0D);
    }

    private static Map<CelestialObjectKey, Long> capacity() {
        return Map.of(
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            10L,
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            10L);
    }

    private static void registerConsumer(CelestialObjectKey bodyKey, SatelliteDataType type, long amountKb) {
        AutomatedFacility facility = new AutomatedFacility(
            com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.ID.create(),
            bodyKey,
            com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        ModuleInstance instance = FacilityModuleKind.DEBUG_DATA_GENERATOR
            .create(StationTileCoord.of(0, 0), ModuleShape.SINGLE, ModuleTier.HV);
        instance.updateStatus(Buildable.Status.OPERATIONAL);
        addModule(facility, instance);
        ((ModuleDebugDataGenerator) instance.component())
            .configure(ModuleDebugDataGenerator.Config.consume(type, amountKb, 1, null));
        SatelliteNetworkService.refreshAssetEndpoints(TEAM, facility);
    }
}
