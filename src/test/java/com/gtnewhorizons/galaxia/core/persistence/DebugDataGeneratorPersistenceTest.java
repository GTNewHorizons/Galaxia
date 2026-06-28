package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkService;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class DebugDataGeneratorPersistenceTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();
    private static final UUID TEAM = new UUID(61L, 62L);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @AfterEach
    void clearState() {
        CelestialAssetStore.clear();
        SatelliteNetworkService.clear();
    }

    @Test
    void debugDataGeneratorStateSurvivesFacilityRoundTrip() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = facility();
        ModuleDebugDataGenerator generator = addGenerator(station);
        generator.configure(
            ModuleDebugDataGenerator.Config.consume(SatelliteDataType.PROSPECTING, 25L, 40, CelestialObjectId.EGORA));
        generator.advanceJob();
        generator.advanceJob();
        generator.consume(15L);

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        AutomatedFacility decoded = facility();
        manager.decodeFacilityState(decoded, encoded);

        ModuleDebugDataGenerator loaded = assertInstanceOf(
            ModuleDebugDataGenerator.class,
            decoded.modules()
                .get(0)
                .component());
        assertEquals(
            ModuleDebugDataGenerator.Mode.CONSUME,
            loaded.config()
                .mode());
        assertEquals(
            SatelliteDataType.PROSPECTING,
            loaded.config()
                .dataType());
        assertEquals(
            25L,
            loaded.config()
                .amountKb());
        assertEquals(
            40,
            loaded.config()
                .durationTicks());
        assertEquals(
            CelestialObjectId.EGORA,
            loaded.config()
                .originBodyId());
        assertEquals(2, loaded.jobProgressTicks());
        assertEquals(15L, loaded.consumedDeciKb());
    }

    @Test
    void staleDisabledDebugDataGeneratorLoadsActive() {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = facility();
        ModuleDebugDataGenerator generator = addGenerator(station);
        generator.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 25L, 40));

        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);
        encoded.modules.get(0).data.getAsJsonObject()
            .addProperty("enabled", false);
        AutomatedFacility decoded = facility();
        manager.decodeFacilityState(decoded, encoded);

        ModuleDebugDataGenerator loaded = assertInstanceOf(
            ModuleDebugDataGenerator.class,
            decoded.modules()
                .get(0)
                .component());
        assertTrue(loaded.enabled());
    }

    @Test
    void loadedDebugDataGeneratorsRegisterAsSatelliteEndpoints(@TempDir File tempDir) {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        CelestialAsset.ID producerId = CelestialAsset.ID.create();
        CelestialAsset.ID consumerId = CelestialAsset.ID.create();
        AutomatedFacility producerFacility = facility(producerId, CelestialObjectId.MARS);
        AutomatedFacility consumerFacility = facility(consumerId, CelestialObjectId.EGORA);
        ModuleDebugDataGenerator producer = addGenerator(producerFacility);
        ModuleDebugDataGenerator consumer = addGenerator(consumerFacility);
        producer.configure(ModuleDebugDataGenerator.Config.produce(SatelliteDataType.RESEARCH, 400L, 1));
        consumer.configure(ModuleDebugDataGenerator.Config.consume(SatelliteDataType.RESEARCH, 500L, 1, null));
        CelestialAssetStore.registerAsset(TEAM, producerFacility);
        CelestialAssetStore.registerAsset(TEAM, consumerFacility);
        manager.saveToSaveDirectory(tempDir);

        FacilityPersistenceManager reloaded = new FacilityPersistenceManager();
        reloaded.loadFromSaveDirectory(tempDir);
        SatelliteNetworkService.tickDataJobs();

        AutomatedFacility loadedProducerFacility = assertInstanceOf(
            AutomatedFacility.class,
            CelestialAssetStore.findAsset(producerId));
        AutomatedFacility loadedConsumerFacility = assertInstanceOf(
            AutomatedFacility.class,
            CelestialAssetStore.findAsset(consumerId));
        ModuleDebugDataGenerator loadedProducer = assertInstanceOf(
            ModuleDebugDataGenerator.class,
            loadedProducerFacility.modules()
                .get(0)
                .component());
        ModuleDebugDataGenerator loadedConsumer = assertInstanceOf(
            ModuleDebugDataGenerator.class,
            loadedConsumerFacility.modules()
                .get(0)
                .component());
        assertEquals(CelestialObjectId.EGORA, loadedProducer.detectedCounterpartBodyId());
        assertEquals(CelestialObjectId.MARS, loadedConsumer.detectedCounterpartBodyId());
    }

    private static AutomatedFacility facility() {
        return facility(ASSET_ID, CelestialObjectId.MARS);
    }

    private static AutomatedFacility facility(CelestialAsset.ID assetId, CelestialObjectId bodyId) {
        return new AutomatedFacility(
            assetId,
            bodyId,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleDebugDataGenerator addGenerator(AutomatedFacility station) {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.DEBUG_DATA_GENERATOR,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.HV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        station.stationLayout()
            .place(module);
        station.addModule(module);
        return (ModuleDebugDataGenerator) module.component();
    }
}
