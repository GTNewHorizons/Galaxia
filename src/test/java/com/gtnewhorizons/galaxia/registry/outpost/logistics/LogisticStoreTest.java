package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

final class LogisticStoreTest {

    @BeforeAll
    static void initRegistries() {
        TestFMLRegistry.init();
        CelestialRegistry.freezeAndBake();
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
        destination.updateItems(filler, 998);
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
                source.celestialObjectId,
                destination.celestialObjectId,
                0,
                0));

        LogisticsDelivery pending = LogisticStore.activeDeliveries()
            .get(0);
        LogisticStore.tickDeliveries();

        assertEquals(2L, destination.getItemAmount(delivered));
        assertEquals(
            1,
            LogisticStore.activeDeliveries()
                .size());
        assertSame(
            pending,
            LogisticStore.activeDeliveries()
                .get(0));
        assertEquals(3L, pending.data.amount());

        destination.updateItems(filler, -3);
        LogisticStore.tickDeliveries();

        assertEquals(5L, destination.getItemAmount(delivered));
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
                source.celestialObjectId,
                destination.celestialObjectId,
                0,
                0));
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                resource,
                7L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectId,
                destination.celestialObjectId,
                0,
                0));
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                destination.assetId,
                otherResource,
                11L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectId,
                destination.celestialObjectId,
                0,
                0));
        LogisticStore.addDelivery(
            LogisticsDelivery.createWithTrajectory(
                source.assetId,
                otherDestination.assetId,
                resource,
                13L,
                1,
                LogisticSignal.Scope.SYSTEM,
                source.celestialObjectId,
                otherDestination.celestialObjectId,
                0,
                0));

        assertEquals(12L, LogisticStore.inboundInTransitAmount(destination.assetId, resource));
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
