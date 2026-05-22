package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Items;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class CoreDispatchPlannerTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @AfterEach
    void cleanup() {
        LogisticStore.clearDeliveries();
        LogisticStore.clearSignals();
    }

    @Test
    void upkeepAutoOrderDispatchesThroughCoreWithoutHammer() {
        AutomatedFacility supplier = facility();
        AutomatedFacility requester = facility();
        ItemStackWrapper resource = new ItemStackWrapper(Items.iron_ingot, 0, null);
        supplier.logisticsConfig.set(resource, new LogisticsResourceConfig(16, 64, false, true));
        supplier.updateItems(resource, 128);
        requester.setUpkeepReserve(resource, 48);
        requester.setUpkeepAutoOrder(resource, true);

        boolean dispatched = CoreDispatchPlanner.tryDispatch(supplier, requester, resource, 0.0D);

        assertTrue(dispatched);
        assertEquals(80L, supplier.getItemAmount(resource));
        assertEquals(
            1,
            LogisticStore.activeDeliveries()
                .size());
        LogisticsDelivery delivery = LogisticStore.activeDeliveries()
            .get(0);
        assertEquals(supplier.assetId, delivery.data.fromAssetId());
        assertEquals(requester.assetId, delivery.data.toAssetId());
        assertEquals(resource, delivery.data.resourceId());
        assertEquals(48L, delivery.data.amount());
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
