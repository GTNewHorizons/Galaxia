package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelContainment;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Storage;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.WorkBill;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RocketPartDefTest {

    @BeforeAll
    static void initializeRegistries() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void authoredStorageAndWorkBillsKeepIndependentImmutableResources() {
        var fluid = new FluidKey(FluidRegistry.WATER, null);
        var compartments = new HashMap<FluidKey, Long>();
        compartments.put(fluid, 500L);
        var materials = new HashMap<InventoryKey, Long>();
        var item = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        materials.put(item, 4L);
        var original = RocketPartDef.structural(41, "test.frame", 1, 1, 1, null);
        var bill = new WorkBill(materials, 30, 40);
        var supplied = original.withStorage(new Storage(9, 2000, compartments))
            .withManufactureBill(bill);

        compartments.clear();
        materials.clear();

        assertEquals(
            0,
            original.storage()
                .itemSlots());
        assertNull(original.manufactureBill());
        assertNull(supplied.repairBill());
        assertEquals(
            9,
            supplied.storage()
                .itemSlots());
        assertEquals(
            2000,
            supplied.storage()
                .freightFluidCapacity());
        assertEquals(
            Map.of(fluid, 500L),
            supplied.storage()
                .propellantCapacities());
        assertEquals(
            Map.of(item, 4L),
            supplied.manufactureBill()
                .inputs());
        assertThrows(
            UnsupportedOperationException.class,
            () -> supplied.storage()
                .propellantCapacities()
                .clear());
        assertThrows(
            UnsupportedOperationException.class,
            () -> bill.inputs()
                .clear());

        var repairBill = new WorkBill(Map.of(item, 1L), 32, 100);
        var repairable = supplied.withRepairBill(repairBill);
        assertNull(supplied.repairBill());
        assertEquals(repairBill, repairable.repairBill());
        assertEquals(bill, repairable.manufactureBill());
    }

    @Test
    void replacingRegisteredContentUpdatesCatalogWithoutChangingDefinitionIdentity() {
        var registry = new RocketPartRegistry();
        var original = RocketPartDef.structural(42, "test.frame", 1, 1, 1, null);
        registry.register(original, RocketPartCategory.STRUCTURAL);
        var replacement = original.withManufactureBill(new WorkBill(Map.of(), 30, 40));

        registry.replace(replacement);

        assertSame(replacement, registry.get(original.id()));
        assertEquals(original.id(), replacement.id());
        assertSame(
            replacement,
            registry.getByCategory(RocketPartCategory.STRUCTURAL)
                .get(0));
        assertNull(replacement.repairBill());
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.register(replacement, RocketPartCategory.STRUCTURAL));
    }

    @Test
    void invalidResourceCapacityAndUnpaidWorkCannotBecomeDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new Storage(-1, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorkBill(Map.of(), 0, 40));
        assertThrows(IllegalArgumentException.class, () -> new WorkBill(Map.of(), 30, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketPartDef.Propellant(1, Double.NaN));
    }

    @Test
    void frameFuelMetadataAndTintSurviveResourceBinding() {
        var fuel = RocketFuel.builder("test_tank_fuel")
            .fluid(() -> FluidRegistry.WATER)
            .density(0.8)
            .color(0x123456)
            .containment(FuelContainment.CRYOGENIC)
            .engine(1000, 300, 400, ModuleRecipe.EMPTY)
            .build();
        var tank = new RocketPartDef(
            47,
            "test.tank",
            3,
            5,
            650,
            "fuel_tank_3x5",
            RocketPartDef.Kind.FUEL_TANK,
            0,
            0,
            new Storage(0, 0, Map.of()),
            null,
            null,
            null).withFuel(fuel, FuelTankFrame.MEDIUM_5);
        var key = new FluidKey(FluidRegistry.WATER, null);
        var bill = new WorkBill(Map.of(ItemStackWrapper.of(new ItemStack(Items.iron_ingot)), 2L), 480, 200);
        var bound = tank.withStorage(new Storage(0, 0, Map.of(key, 15000L)))
            .withManufactureBill(bill)
            .withRepairBill(bill);
        assertEquals(0x123456, bound.coreColor());
        assertSame(fuel, bound.fuel());
        assertSame(FuelTankFrame.MEDIUM_5, bound.tankFrame());
        assertEquals(
            15000L,
            bound.storage()
                .propellantCapacities()
                .get(key));
        assertTrue(FuelTankFrame.MEDIUM_5.canHold(fuel));
        assertFalse(FuelTankFrame.SLIM_3.canHold(fuel));
        assertEquals(
            "galaxia:textures/model/modules/fuel_tank_3x5/model.obj",
            bound.modelLocation()
                .toString());
    }

    @Test
    void authoredOreRecipesBecomeOnePaidWorkBillWithoutMutatingRegisteredStacks() {
        var registered = new ItemStack(Items.iron_ingot, 1);
        OreDictionary.registerOre("galaxiaTestRocketPlate", registered);
        var recipe = ModuleRecipe.builder()
            .ore("galaxiaTestRocketPlate", 3)
            .item(new ItemStack(Items.iron_ingot), 4)
            .item(new ItemStack(Items.gold_ingot), 2)
            .duration(320)
            .build();
        var bill = recipe.toWorkBill(480);
        assertEquals(
            7L,
            bill.inputs()
                .get(ItemStackWrapper.of(new ItemStack(Items.iron_ingot))));
        assertEquals(
            2L,
            bill.inputs()
                .get(ItemStackWrapper.of(new ItemStack(Items.gold_ingot))));
        assertEquals(480, bill.euPerTick());
        assertEquals(320, bill.ticks());
        assertEquals(1, registered.stackSize);
    }
}
