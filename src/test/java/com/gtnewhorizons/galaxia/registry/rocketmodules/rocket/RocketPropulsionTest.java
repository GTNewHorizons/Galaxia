package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RocketPropulsionTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    // Product contract: actual companion supplies bound usable fuel, and the unused remainder still has mass.
    @Test
    void actualMixtureDeterminesMassAndDeltaVWithoutCountingFreightContents() {
        var fuel = new FluidKey(FluidRegistry.WATER, null);
        var oxidizer = new FluidKey(FluidRegistry.LAVA, null);
        var definition = new RocketPartDef(
            1,
            "test_engine",
            1,
            1,
            10,
            null,
            RocketPartDef.Kind.ENGINE,
            0,
            0,
            new RocketPartDef.Storage(1, 1000, Map.of(fuel, 100L, oxidizer, 100L)),
            new RocketPartDef.Engine(
                1000,
                1000,
                Map.of(fuel, new RocketPartDef.Propellant(1, 2), oxidizer, new RocketPartDef.Propellant(2, 1))),
            null,
            null);
        var blueprint = new RocketBlueprint();
        assertTrue(blueprint.addPart(new RocketPartInstance(definition, 0, 0, 0, false)));
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            blueprint);
        var module = Rocket.Module.produced(
            rocket.id(),
            rocket.buildSlots()
                .getFirst(),
            100,
            new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()));
        assertTrue(rocket.acceptProduced(module));
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        assertEquals(10, module.fillPropellant(fuel.toStack(10), false));
        assertEquals(10, module.fillPropellant(oxidizer.toStack(10), false));

        var actual = rocket.propulsion();

        assertNotNull(actual);
        assertEquals(10, actual.structuralMassKg());
        assertEquals(40, actual.wetMassKg());
        assertEquals(20, actual.usablePropellantKg());
        assertEquals(20, actual.inertMassKg());
        assertEquals(1000 * Math.log(2), actual.usableDeltaVMps(), 1e-9);
        assertEquals(
            40,
            actual.localVehicle()
                .wetMassKg());
        assertEquals(null, module.insertCargo(new ItemStack(Items.iron_ingot, 64), false));
        assertEquals(500, module.fillFreight(new FluidStack(FluidRegistry.LAVA, 500), false));
        assertEquals(actual, rocket.propulsion());
    }
}
