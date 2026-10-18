package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RocketCargoTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
        FluidContainerRegistry.registerFluidContainer(
            new FluidStack(FluidRegistry.WATER, 1000),
            new ItemStack(Items.water_bucket),
            new ItemStack(Items.bucket));
    }

    // Product contract: transfer simulation is side-effect free, slots are bounded and freight is not propulsion.
    @Test
    void cargoTransfersRespectRealCapacityAndKeepPropellantSeparate() {
        Rocket rocket = storageRocket();
        var item = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        var water = new FluidKey(FluidRegistry.WATER, null);
        var lava = new FluidKey(FluidRegistry.LAVA, null);
        assertEquals(64, rocket.insertCargo(item, 128, true));
        assertEquals(0, rocket.cargoAmount(item));
        assertEquals(64, rocket.insertCargo(item, 128, false));
        assertEquals(64, rocket.cargoAmount(item));
        assertEquals(32, rocket.extractCargo(item, 32, true));
        assertEquals(64, rocket.cargoAmount(item));
        assertEquals(64, rocket.extractCargo(item, 128, false));
        assertEquals(0, rocket.cargoAmount(item));
        assertEquals(1000, rocket.insertCargo(lava, 2000, false));
        assertEquals(0, rocket.insertCargo(water, 10, false));
        assertEquals(500, rocket.refuel(water, 500, true));
        assertEquals(
            0,
            rocket.module(
                rocket.buildSlots()
                    .getFirst()
                    .moduleId())
                .propellantContents()
                .get(water));
        assertEquals(500, rocket.refuel(water, 500, false));
        assertEquals(0, rocket.cargoAmount(water));
        assertEquals(1000, rocket.cargoAmount(lava));
        assertEquals(1000, rocket.extractCargo(lava, 2000, false));
        assertEquals(
            500,
            rocket.module(
                rocket.buildSlots()
                    .getFirst()
                    .moduleId())
                .propellantContents()
                .get(water));
    }

    // Product contract: filled containers cannot bypass bulk freight storage, while empty containers remain items.
    @Test
    void itemCargoRejectsFilledFluidContainersWithoutConsumingTheItem() {
        Rocket rocket = storageRocket();
        var module = rocket.modules()
            .getFirst();
        var filled = new ItemStack(Items.water_bucket);
        var remainder = module.insertCargo(filled, false);
        assertNotNull(remainder);
        assertEquals(1, remainder.stackSize);
        assertEquals(1, filled.stackSize);
        assertTrue(
            module.cargoContents()
                .isEmpty());
        assertEquals(0, rocket.insertCargo(ItemStackWrapper.of(filled), 1, true));
        assertEquals(null, module.insertCargo(new ItemStack(Items.bucket), false));
        assertEquals(
            1,
            module.cargoContents()
                .getFirst().stackSize);
    }

    private static Rocket storageRocket() {
        var water = new FluidKey(FluidRegistry.WATER, null);
        var definition = RocketPartDef.structural(1, "test_storage", 1, 1, 10, null)
            .withStorage(new RocketPartDef.Storage(1, 1000, Map.of(water, 1000L)));
        var blueprint = new RocketBlueprint();
        assertTrue(blueprint.addPart(new RocketPartInstance(definition, 0, 0, 0, false)));
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            blueprint);
        assertTrue(
            rocket.acceptProduced(
                Rocket.Module.produced(
                    rocket.id(),
                    rocket.buildSlots()
                        .getFirst(),
                    40,
                    new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))));
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        return rocket;
    }
}
