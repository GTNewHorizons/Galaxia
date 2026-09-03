package com.gtnewhorizons.galaxia.core.network;

import static com.gtnewhorizons.galaxia.registry.outpost.FacilityTestFixtures.addModule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationPacketRoundTripTest {

    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @BeforeEach
    void clearClientStore() {
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @Test
    void canonicalNetworkStateRegistersEveryAssetKindWithAuthoritativeStateAndTeam() {
        Station station = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.MOON,
            Buildable.Status.IN_CONSTRUCTION);
        station.setController(new BlockPos(3, 5, 7));
        Satellite satellite = new Satellite(
            CelestialAsset.ID.create(),
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            Buildable.Status.OPERATIONAL,
            SatelliteKind.PROSPECTING);
        AutomatedFacility facility = facility();
        ItemStackWrapper item = new ItemStackWrapper(Items.iron_ingot, 0, null);
        facility.restoreInventory(Map.of(item, 9L));
        facility.restoreFilters(List.of("ore:iron"), true);
        facility.logisticsConfig.set(item, new LogisticsResourceConfig(4, 8, true, false));

        AssetStateSync.Client client = new AssetStateSync.Client(assetId -> {});
        receive(
            client,
            AssetSyncPacket.state(TEAM, station, Map.of())
                .withPublishedRevision(1L));
        receive(
            client,
            AssetSyncPacket.state(TEAM, satellite, Map.of())
                .withPublishedRevision(1L));
        receive(
            client,
            AssetSyncPacket.state(TEAM, facility, Map.of())
                .withPublishedRevision(1L));

        Station decodedStation = (Station) CelestialAssetStore.CLIENT.findAssetInternal(station.assetId);
        Satellite decodedSatellite = (Satellite) CelestialAssetStore.CLIENT.findAssetInternal(satellite.assetId);
        AutomatedFacility decodedFacility = (AutomatedFacility) CelestialAssetStore.CLIENT
            .findAssetInternal(facility.assetId);
        assertEquals(station.getController(), decodedStation.getController());
        assertEquals(Buildable.Status.IN_CONSTRUCTION, decodedStation.status());
        assertEquals(SatelliteKind.PROSPECTING, decodedSatellite.satelliteKind());
        assertEquals(700L, decodedFacility.getEnergyStored());
        assertEquals(
            1,
            decodedFacility.modules()
                .size());
        assertEquals(
            9L,
            decodedFacility.itemSnapshot()
                .get(item));
        assertTrue(
            decodedFacility.filtersSnapshot()
                .get(true)
                .contains("ore:iron"));
        assertEquals(facility.logisticsConfig.snapshot(), decodedFacility.logisticsConfig.snapshot());
        assertEquals(TEAM, CelestialAssetStore.CLIENT.getTeamIdInternal(station.assetId));
        assertEquals(TEAM, CelestialAssetStore.CLIENT.getTeamIdInternal(satellite.assetId));
        assertEquals(TEAM, CelestialAssetStore.CLIENT.getTeamIdInternal(facility.assetId));
    }

    @Test
    void canonicalStateStoresPlannedConstructionOnlyForAutomatedFacilities() {
        Station station = new Station(CelestialAsset.ID.create(), CelestialObjectId.MOON, Buildable.Status.OPERATIONAL);
        AutomatedFacility facility = facility();
        facility.setConstructionInventory(Map.of(new ItemStack(Items.iron_ingot), 7L));

        NBTTagCompound stationState = AssetState.encode(TEAM, station);
        NBTTagCompound facilityState = AssetState.encode(TEAM, facility);

        assertFalse(stationState.hasKey("construction"));
        assertFalse(facilityState.hasKey("construction"));
        assertTrue(
            facilityState.getCompoundTag("facility")
                .hasKey("construction"));
        AutomatedFacility decoded = (AutomatedFacility) AssetState.decode(facilityState)
            .asset();
        assertEquals(
            1,
            decoded.getConstructionInventory()
                .size());
        Map.Entry<ItemStack, Long> restored = decoded.getConstructionInventory()
            .entrySet()
            .iterator()
            .next();
        assertEquals(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), ItemStackWrapper.of(restored.getKey()));
        assertEquals(7L, restored.getValue());

        stationState.setTag("construction", new NBTTagList());
        assertThrows(IllegalStateException.class, () -> AssetState.decode(stationState));
    }

    @Test
    void canonicalNetworkReplacementPreservesAssetIdentityAndClearsAbsentState() {
        AutomatedFacility current = facility();
        current.restoreFilters(List.of("ore:old"), true);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, current);
        AutomatedFacility authoritative = new AutomatedFacility(
            current.assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.DISABLED);
        authoritative.setEnergyStored(250L);

        AssetStateSync.Client client = new AssetStateSync.Client(assetId -> {});
        receive(
            client,
            AssetSyncPacket.state(TEAM, authoritative, Map.of())
                .withPublishedRevision(2L));

        assertSame(current, CelestialAssetStore.CLIENT.findAssetInternal(current.assetId));
        assertEquals(250L, current.getEnergyStored());
        assertEquals(Buildable.Status.DISABLED, current.status());
        assertTrue(
            current.modules()
                .isEmpty());
        assertTrue(
            current.filtersSnapshot()
                .getOrDefault(true, List.of())
                .isEmpty());
    }

    @Test
    void immutableIdentityMismatchesRequestOneRecoveryWithoutPartialMutation() {
        AutomatedFacility current = facility();
        current.setEnergyStored(100L);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, current);
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        AutomatedFacility wrongBody = new AutomatedFacility(
            current.assetId,
            CelestialObjectId.MOON,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.DISABLED);
        wrongBody.setEnergyStored(200L);
        receive(
            client,
            AssetSyncPacket.state(TEAM, wrongBody, Map.of())
                .withPublishedRevision(2L));

        AutomatedFacility wrongTeam = new AutomatedFacility(
            current.assetId,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.DISABLED);
        wrongTeam.setEnergyStored(300L);
        receive(
            client,
            AssetSyncPacket.state(UUID.randomUUID(), wrongTeam, Map.of())
                .withPublishedRevision(3L));

        assertSame(current, CelestialAssetStore.CLIENT.findAssetInternal(current.assetId));
        assertEquals(100L, current.getEnergyStored());
        assertEquals(Buildable.Status.OPERATIONAL, current.status());
        assertEquals(List.of(current.assetId), transport.recoveryRequests);
    }

    @Test
    void satelliteKindMismatchDoesNotReplaceExistingClientObject() {
        CelestialAsset.ID assetId = CelestialAsset.ID.create();
        Satellite current = new Satellite(
            assetId,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            Buildable.Status.OPERATIONAL,
            SatelliteKind.COMMUNICATION);
        CelestialAssetStore.CLIENT.registerAssetInternal(TEAM, current);
        Satellite incompatible = new Satellite(
            assetId,
            CelestialObjectKey.registered(CelestialObjectId.MARS),
            Buildable.Status.OPERATIONAL,
            SatelliteKind.PROSPECTING);
        RecordingTransport transport = new RecordingTransport();
        AssetStateSync.Client client = new AssetStateSync.Client(transport);

        receive(
            client,
            AssetSyncPacket.state(TEAM, incompatible, Map.of())
                .withPublishedRevision(2L));

        assertSame(current, CelestialAssetStore.CLIENT.findAssetInternal(assetId));
        assertEquals(SatelliteKind.COMMUNICATION, current.satelliteKind());
        assertEquals(List.of(assetId), transport.recoveryRequests);
    }

    private static AutomatedFacility facility() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        facility.setEnergyStored(700L);
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, null, ModuleShape.SINGLE, ModuleTier.IV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        module.initAnchor(StationTileCoord.of(2, 3));
        addModule(facility, module);
        facility.stationLayout()
            .place(module);
        return facility;
    }

    private static void receive(AssetStateSync.Client client, AssetSyncPacket packet) {
        for (AssetStateFramePacket frame : AssetStateSync.Server.frame(packet)) client.receive(frame);
    }

    private static final class RecordingTransport implements AssetStateSync.ClientTransport {

        private final List<CelestialAsset.ID> recoveryRequests = new ArrayList<>();

        @Override
        public void requestFull(CelestialAsset.ID assetId) {
            recoveryRequests.add(assetId);
        }
    }
}
