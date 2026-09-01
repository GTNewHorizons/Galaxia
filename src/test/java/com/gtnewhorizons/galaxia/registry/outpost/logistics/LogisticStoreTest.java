package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class LogisticStoreTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void cleanup() {
        LogisticStore.clearDeliveries();
        CelestialAssetStore.clear();
    }

    @Test
    void arrivedDeliveryKeepsRemainderPendingWhenDestinationInventoryIsFull() {
        UUID teamId = UUID.randomUUID();
        AutomatedFacility source = facility();
        AutomatedFacility destination = facility();
        ItemStackWrapper filler = new ItemStackWrapper(Items.diamond, 0, null);
        ItemStackWrapper delivered = new ItemStackWrapper(Items.iron_ingot, 0, null);
        destination.insert(filler, 998);
        CelestialAssetStore.registerAsset(teamId, source);
        CelestialAssetStore.registerAsset(teamId, destination);

        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                delivered,
                5L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectKey,
                destination.celestialObjectKey,
                0,
                0,
                null));

        LogisticsDelivery pending = LogisticStore.activeDeliveries()
            .get(0);
        LogisticStore.tickDeliveries();

        assertEquals(2L, destination.itemAmount(delivered));
        assertEquals(
            1,
            LogisticStore.activeDeliveries()
                .size());
        assertSame(
            pending,
            LogisticStore.activeDeliveries()
                .get(0));
        assertEquals(3L, pending.data.amount());

        destination.extract(filler, 3);
        LogisticStore.tickDeliveries();

        assertEquals(5L, destination.itemAmount(delivered));
        assertEquals(
            0,
            LogisticStore.activeDeliveries()
                .size());
    }

    @Test
    void inboundInTransitAmountCountsOnlyMatchingDeliveries() {
        AutomatedFacility source = facility();
        AutomatedFacility destination = facility();
        AutomatedFacility otherDestination = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        ItemStackWrapper otherResource = new ItemStackWrapper(Items.diamond, 1, null);

        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                resource,
                5L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectKey,
                destination.celestialObjectKey,
                0,
                0,
                null));
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                resource,
                7L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectKey,
                destination.celestialObjectKey,
                0,
                0,
                null));
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                otherResource,
                11L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectKey,
                destination.celestialObjectKey,
                0,
                0,
                null));
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                otherDestination.assetId,
                resource,
                13L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectKey,
                otherDestination.celestialObjectKey,
                0,
                0,
                null));

        assertEquals(12L, LogisticStore.inboundInTransitAmount(destination.assetId, resource));
    }

    @Test
    void upkeepAutoOrderUsesCoreImportConfigForRequest() {
        AutomatedFacility station = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        station.insert(resource, 3);
        station.setBound(resource, 5, true);
        station.setUpkeepReserve(resource, 10L);
        station.setUpkeepAutoOrder(resource, true);

        LogisticSignal signal = signalFor(LogisticStore.collectSignals(List.of(station)), station, resource);
        assertEquals(-12L, signal.amount());
    }

    @Test
    void coreImportConfigEmitsRequestUpToConfiguredReserve() {
        AutomatedFacility station = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        station.insert(resource, 3);
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(15, 64, true, false));

        LogisticSignal signal = signalFor(LogisticStore.collectSignals(List.of(station)), station, resource);
        assertEquals(resource, signal.resourceId());
        assertEquals(-12L, signal.amount());
    }

    @Test
    void completeSnapshotPreservesDistinctSourcesAndDropsAbsentSources() {
        AutomatedFacility supplier = facility();
        AutomatedFacility requester = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.insert(resource, 20);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(5, 64, false, true));
        requester.insert(resource, 3);
        requester.logisticsConfig.set(resource, new LogisticsResourceConfig(15, 64, true, false));

        List<LogisticSignal> complete = LogisticStore.collectSignals(List.of(supplier, requester));

        assertEquals(2, complete.size());
        assertEquals(15L, signalFor(complete, supplier, resource).amount());
        assertEquals(-12L, signalFor(complete, requester, resource).amount());
        assertFalse(
            signalFor(complete, supplier, resource).outpostAssetId()
                .equals(signalFor(complete, requester, resource).outpostAssetId()));

        List<LogisticSignal> replacement = LogisticStore.collectSignals(List.of(requester));
        assertEquals(List.of(signalFor(complete, requester, resource)), replacement);
        assertTrue(
            LogisticStore.collectSignals(List.of())
                .isEmpty());
    }

    @Test
    void systemGroupingRetainsIndividualSourceContributions() {
        AutomatedFacility first = facility();
        AutomatedFacility second = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        first.insert(resource, 9);
        first.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));
        second.logisticsConfig.set(resource, new LogisticsResourceConfig(4, 64, true, false));
        List<LogisticSignal> signals = LogisticStore.collectSignals(List.of(first, second));

        Map<CelestialObjectKey, List<LogisticSignal>> grouped = LogisticStore
            .groupSignals(signals, LogisticSignal.Scope.SYSTEM);

        assertEquals(List.of(signals), List.copyOf(grouped.values()));
    }

    @Test
    void recipientSelectionDoesNotExposeAnotherTeamsSignals() {
        UUID firstTeam = UUID.randomUUID();
        UUID secondTeam = UUID.randomUUID();
        AutomatedFacility first = facility();
        AutomatedFacility second = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.diamond, 0, null);
        first.logisticsConfig.set(resource, new LogisticsResourceConfig(5, 64, true, false));
        second.logisticsConfig.set(resource, new LogisticsResourceConfig(7, 64, true, false));
        CelestialAssetStore.registerAsset(firstTeam, first);
        CelestialAssetStore.registerAsset(secondTeam, second);
        List<LogisticSignal> signals = LogisticStore.collectSignals(List.of(first, second));

        assertEquals(List.of(signalFor(signals, first, resource)), LogisticStore.signalsOwnedBy(firstTeam, signals));
        assertEquals(List.of(signalFor(signals, second, resource)), LogisticStore.signalsOwnedBy(secondTeam, signals));
    }

    @Test
    void stationWithoutControllerIsNotASignalSource() {
        Station station = new Station(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            Buildable.Status.OPERATIONAL);
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(12, 64, true, false));

        assertTrue(
            LogisticStore.collectSignals(List.of(station))
                .isEmpty());
    }

    @Test
    void distributedInventoryContentsFeedTheSameSignalCalculation() {
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        PhysicalInventoryAsset physical = new PhysicalInventoryAsset(Map.of(resource, 11L));
        physical.logisticsConfig.set(resource, new LogisticsResourceConfig(3, 64, false, true));

        LogisticSignal signal = signalFor(LogisticStore.collectSignals(List.of(physical)), physical, resource);

        assertEquals(8L, signal.amount());
    }

    private static LogisticSignal signalFor(List<LogisticSignal> signals, CelestialAsset asset,
        ItemStackWrapper resource) {
        return signals.stream()
            .filter(signal -> asset.assetId.equals(signal.outpostAssetId()))
            .filter(signal -> resource.equals(signal.resourceId()))
            .findFirst()
            .orElseThrow();
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static final class PhysicalInventoryAsset extends CelestialAsset implements IDistributedInventory {

        private final Map<ItemStackWrapper, Long> items;

        private PhysicalInventoryAsset(Map<ItemStackWrapper, Long> items) {
            super(
                CelestialAsset.ID.create(),
                CelestialObjectId.OVERWORLD,
                CelestialAsset.Kind.STATION,
                Buildable.Status.OPERATIONAL,
                null);
            this.items = new LinkedHashMap<>(items);
        }

        @Override
        public Map<ItemStackWrapper, Long> getItemAmounts() {
            return items;
        }

        @Override
        public boolean tryConsumeEnergy(long powerDraw) {
            return false;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public Stream<ModuleInstance> forEachModule() {
            return Stream.empty();
        }

        @Override
        public void tick() {}
    }
}
