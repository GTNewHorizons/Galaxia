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
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IDistributedInventory;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
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
        LogisticStore.addDelivery(delivery(source, destination, delivered, 5L, 1));
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
        assertTrue(
            LogisticStore.activeDeliveries()
                .isEmpty());
    }

    @Test
    void missingDestinationRefundsCargoToSurvivingSource() {
        DeliveryScenario scenario = deliveryScenario();
        assertAfterEndpointLoss(scenario, scenario.source(), scenario.source());
    }

    @Test
    void missingSourceStillDeliversCargoToSurvivingDestination() {
        DeliveryScenario scenario = deliveryScenario();
        assertAfterEndpointLoss(scenario, scenario.destination(), scenario.destination());
    }

    @Test
    void missingEndpointsRemoveOwnerlessDelivery() {
        assertAfterEndpointLoss(deliveryScenario(), null, null);
    }

    @Test
    void missingSourceAndUnsupportedDestinationRemoveOwnerlessDelivery() {
        UUID teamId = UUID.randomUUID();
        AutomatedFacility source = facility();
        CelestialAsset destination = new TestLogisticsAsset(CelestialAsset.Kind.SATELLITE);
        ItemStackWrapper delivered = new ItemStackWrapper(Items.iron_ingot, 0, null);
        CelestialAssetStore.registerAsset(teamId, source);
        CelestialAssetStore.registerAsset(teamId, destination);
        LogisticStore.addDelivery(delivery(source, destination, delivered, 5L, 1));
        CelestialAssetStore.clear();
        CelestialAssetStore.registerAsset(teamId, destination);

        LogisticStore.tickDeliveries();

        assertTrue(
            LogisticStore.activeDeliveries()
                .isEmpty());
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
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(10, 1, true, false));
        station.applyCommand(
            new FacilityCommand.SetInventoryBound(station.assetId, BoundKind.ITEM_LOWER, resource, 5),
            FacilityCommand.Authority.NONE);

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
        TestPhysicalInventoryAsset physical = new TestPhysicalInventoryAsset(Map.of(resource, 11L));
        physical.logisticsConfig.set(resource, new LogisticsResourceConfig(3, 64, false, true));

        LogisticSignal signal = signalFor(LogisticStore.collectSignals(List.of(physical)), physical, resource);

        assertEquals(8L, signal.amount());
    }

    @Test
    void stationSupplySignalUsesOnlyCannonPackages() {
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        TestStationAsset station = new TestStationAsset(Map.of(resource, 40L), Map.of());
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));

        assertTrue(
            LogisticStore.collectSignals(List.of(station))
                .isEmpty());
    }

    @Test
    void stationSupplySignalReportsCannonPackages() {
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        TestStationAsset station = new TestStationAsset(Map.of(resource, 40L), Map.of(resource, 12L));
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(0, 64, false, true));

        LogisticSignal signal = signalFor(LogisticStore.collectSignals(List.of(station)), station, resource);

        assertEquals(12L, signal.amount());
    }

    @Test
    void stationSupplySignalAppliesReserveToEachCannonBuffer() {
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        TestStationAsset station = new TestStationAsset(
            Map.of(),
            List.of(Map.of(resource, 40L), Map.of(resource, 40L)));
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(50, 64, false, true));

        assertTrue(
            LogisticStore.collectSignals(List.of(station))
                .isEmpty());
    }

    @Test
    void stationImportSignalIgnoresCannonPackages() {
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        TestStationAsset station = new TestStationAsset(Map.of(resource, 100L), Map.of(resource, 32L));
        station.logisticsConfig.set(resource, new LogisticsResourceConfig(120, 64, true, true));

        LogisticSignal signal = signalFor(LogisticStore.collectSignals(List.of(station)), station, resource);

        assertEquals(-20L, signal.amount());
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

    private static DeliveryScenario deliveryScenario() {
        UUID teamId = UUID.randomUUID();
        AutomatedFacility source = facility();
        AutomatedFacility destination = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        CelestialAssetStore.registerAsset(teamId, source);
        CelestialAssetStore.registerAsset(teamId, destination);
        LogisticStore.addDelivery(delivery(source, destination, resource, 5L, 1));
        return new DeliveryScenario(teamId, source, destination, resource);
    }

    private static void assertAfterEndpointLoss(DeliveryScenario scenario, CelestialAsset survivor,
        AutomatedFacility recipient) {
        CelestialAssetStore.clear();
        if (survivor != null) CelestialAssetStore.registerAsset(scenario.teamId(), survivor);
        LogisticStore.tickDeliveries();
        if (recipient != null) assertEquals(5L, recipient.itemAmount(scenario.resource()));
        assertTrue(
            LogisticStore.activeDeliveries()
                .isEmpty());
    }

    private static LogisticsDelivery delivery(CelestialAsset source, CelestialAsset destination,
        ItemStackWrapper resource, long amount, int ticks) {
        return LogisticsDelivery.createWithTrajectory(
            source.assetId,
            destination.assetId,
            resource,
            amount,
            ticks,
            LogisticSignal.Scope.SYSTEM,
            source.celestialObjectKey,
            destination.celestialObjectKey,
            0,
            0,
            null);
    }

    private record DeliveryScenario(UUID teamId, AutomatedFacility source, AutomatedFacility destination,
        ItemStackWrapper resource) {}

}

class TestLogisticsAsset extends CelestialAsset {

    TestLogisticsAsset(Kind kind) {
        super(ID.create(), CelestialObjectId.OVERWORLD, kind, Buildable.Status.OPERATIONAL);
    }

    @Override
    public boolean tryConsumeEnergy(long powerDraw) {
        return false;
    }

    @Override
    public long getEnergyStored() {
        return 0L;
    }

    @Override
    public Stream<ModuleInstance> forEachModule() {
        return Stream.empty();
    }

    @Override
    public void tick() {}
}

final class TestPhysicalInventoryAsset extends TestLogisticsAsset implements IDistributedInventory {

    private final Map<ItemStackWrapper, Long> items;
    private final ItemStackWrapper acceptedResource;
    private final long freeSpace;

    TestPhysicalInventoryAsset(Map<ItemStackWrapper, Long> items) {
        this(items, null, 0L);
    }

    TestPhysicalInventoryAsset(ItemStackWrapper acceptedResource, long freeSpace) {
        this(Map.of(), acceptedResource, freeSpace);
    }

    private TestPhysicalInventoryAsset(Map<ItemStackWrapper, Long> items, ItemStackWrapper acceptedResource,
        long freeSpace) {
        super(Kind.STATION);
        this.items = new LinkedHashMap<>(items);
        this.acceptedResource = acceptedResource;
        this.freeSpace = freeSpace;
    }

    @Override
    public Map<ItemStackWrapper, Long> getItemAmounts() {
        return items;
    }

    @Override
    public long getFreeItemSpace(ItemStackWrapper item) {
        return acceptedResource != null && acceptedResource.equals(item) ? freeSpace : 0L;
    }
}

final class TestStationAsset extends Station {

    private final List<IDistributedInventory> inventories;
    private final List<Map<ItemStackWrapper, Long>> cannonItems;

    TestStationAsset(Map<ItemStackWrapper, Long> items, Map<ItemStackWrapper, Long> cannonItems) {
        this(items, List.of(cannonItems));
    }

    TestStationAsset(Map<ItemStackWrapper, Long> items, List<Map<ItemStackWrapper, Long>> cannonItems) {
        super(ID.create(), CelestialObjectId.OVERWORLD, Buildable.Status.OPERATIONAL);
        inventories = List.of(new TestPhysicalInventoryAsset(items));
        this.cannonItems = List.copyOf(cannonItems);
    }

    @Override
    public TileStation getTileController() {
        return new TileStation();
    }

    @Override
    public List<IDistributedInventory> getChildren() {
        return inventories;
    }

    @Override
    public Map<ItemStackWrapper, Long> getCannonChestItems() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>();
        cannonItems.forEach(items -> items.forEach((key, amount) -> result.merge(key, amount, Long::sum)));
        return result;
    }

    @Override
    public long getCannonSupplyAmount(ItemStackWrapper resource, long reserve) {
        return cannonItems.stream()
            .mapToLong(items -> Math.max(items.getOrDefault(resource, 0L) - reserve, 0L))
            .sum();
    }

}
