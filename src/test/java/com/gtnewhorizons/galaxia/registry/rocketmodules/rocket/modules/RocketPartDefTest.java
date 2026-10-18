package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
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
}
