package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product contracts: real Facility TANK capacity bounds inserts and atomic recipe exchanges. */
final class FacilityFluidCapacityTest {

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void noTankMeansNoFluidStorageAndRealTankCapacityIsSharedBetweenFluidTypes() {
        var facility = facility();
        var water = new FluidKey(FluidRegistry.WATER, null);
        var lava = new FluidKey(FluidRegistry.LAVA, null);
        assertEquals(0, facility.insert(water, 1));
        var tank = tank(facility);
        long capacity = tank.baseCapacity();
        assertEquals(capacity - 1, facility.insert(water, capacity - 1));
        assertEquals(1, facility.insert(lava, 2));
        assertEquals(0, facility.insert(water, 1));
        assertEquals(capacity - 1, facility.fluidAmount(water));
        assertEquals(1, facility.fluidAmount(lava));
    }

    @Test
    void fluidOutputOverfillRejectsWholeRecipeWithoutConsumingInputs() {
        var facility = facility();
        var tank = tank(facility);
        var water = new FluidKey(FluidRegistry.WATER, null);
        var lava = new FluidKey(FluidRegistry.LAVA, null);
        var input = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.insert(water, tank.baseCapacity() - 1);
        facility.insert(input, 2);
        var before = facility.inventorySnapshot();
        assertFalse(facility.tryExchange(Map.of(input, 1L), Map.of(lava, 2L)));
        assertEquals(before, facility.inventorySnapshot());
        assertTrue(facility.tryExchange(Map.of(input, 1L, water, 1L), Map.of(lava, 2L)));
        assertEquals(1, facility.itemAmount(input));
        assertEquals(2, facility.fluidAmount(lava));
    }

    static ModuleInstance tank(AutomatedFacility facility) {
        var module = FacilityModuleKind.TANK
            .create(StationTileCoord.of(1, 0), FacilityModuleKind.TANK.defaultShape(), ModuleTier.HV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return module;
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }
}
