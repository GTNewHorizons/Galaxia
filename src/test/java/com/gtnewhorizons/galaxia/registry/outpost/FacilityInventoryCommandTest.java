package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityInventoryCommandTest {

    private static final FacilityCommand.Authority CREATIVE = new FacilityCommand.Authority(true, false);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void insertRequiresCreativeAuthorityAndPositiveWellFormedInput() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        FluidKey fluid = new FluidKey(new Fluid("galaxia.command_test_fluid"), null);

        FacilityCommand.Result unauthorized = facility.applyCommand(
            new FacilityCommand.AdjustInventory(facility.assetId, item, FacilityCommand.InventoryAdjustment.INSERT, 5L),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result nonpositive = facility.applyCommand(
            new FacilityCommand.AdjustInventory(facility.assetId, item, FacilityCommand.InventoryAdjustment.INSERT, 0L),
            CREATIVE);
        FacilityCommand.Result missingDirection = facility
            .applyCommand(new FacilityCommand.AdjustInventory(facility.assetId, item, null, 5L), CREATIVE);
        FacilityCommand.Result missingResource = facility.applyCommand(
            new FacilityCommand.AdjustInventory(facility.assetId, null, FacilityCommand.InventoryAdjustment.INSERT, 5L),
            CREATIVE);

        assertEquals(FacilityCommand.Rejection.CREATIVE_MODE_REQUIRED, unauthorized.rejection());
        assertEquals(FacilityCommand.Rejection.INVALID_INVENTORY_ADJUSTMENT, nonpositive.rejection());
        assertEquals(FacilityCommand.Rejection.INVALID_INVENTORY_ADJUSTMENT, missingDirection.rejection());
        assertEquals(FacilityCommand.Rejection.INVALID_RESOURCE, missingResource.rejection());

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.AdjustInventory(
                    facility.assetId,
                    item,
                    FacilityCommand.InventoryAdjustment.INSERT,
                    5L),
                CREATIVE));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.AdjustInventory(
                    facility.assetId,
                    fluid,
                    FacilityCommand.InventoryAdjustment.INSERT,
                    7L),
                CREATIVE));

        assertEquals(5L, facility.itemAmount(item));
        assertEquals(7L, facility.fluidAmount(fluid));
    }

    @Test
    void extractAndClearReturnNoOpWhenResourceIsEmpty() {
        AutomatedFacility facility = facility();
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        FluidKey fluid = new FluidKey(new Fluid("galaxia.command_test_clear_fluid"), null);
        facility.insert(item, 9L);
        facility.insert(fluid, 12L);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.AdjustInventory(
                    facility.assetId,
                    item,
                    FacilityCommand.InventoryAdjustment.EXTRACT,
                    4L),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.ClearInventoryResource(facility.assetId, fluid),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.UNCHANGED,
            facility.applyCommand(
                new FacilityCommand.ClearInventoryResource(facility.assetId, fluid),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.AdjustInventory(
                    facility.assetId,
                    item,
                    FacilityCommand.InventoryAdjustment.EXTRACT,
                    100L),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.UNCHANGED,
            facility.applyCommand(
                new FacilityCommand.AdjustInventory(
                    facility.assetId,
                    item,
                    FacilityCommand.InventoryAdjustment.EXTRACT,
                    1L),
                FacilityCommand.Authority.NONE));

        assertEquals(0L, facility.itemAmount(item));
        assertEquals(0L, facility.fluidAmount(fluid));
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
