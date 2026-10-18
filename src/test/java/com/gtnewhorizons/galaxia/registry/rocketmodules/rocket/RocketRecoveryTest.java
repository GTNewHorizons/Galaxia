package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class RocketRecoveryTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
    }

    // Product contract: a dropped item is a one-use claim to the original concrete module, including every store.
    @Test
    void destructionAndRecoveryRetainContentsAndRejectDuplicatedClaimsAfterReload() {
        var fluid = new FluidKey(FluidRegistry.WATER, null);
        var definitions = new RocketPartRegistry();
        var definition = new RocketPartDef(
            92,
            "recovery_test_module",
            1,
            1,
            100,
            null,
            RocketPartDef.Kind.CARGO,
            0,
            0,
            new RocketPartDef.Storage(1, 1000, Map.of(fluid, 1000L)),
            null,
            null,
            null);
        definitions.register(definition, RocketPartCategory.STRUCTURAL);
        var design = new RocketBlueprint();
        assertTrue(design.addPart(new RocketPartInstance(definition, 0, 0, 0, false)));
        var rocket = Rocket.construct(
            UUID.randomUUID(),
            CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
            UUID.randomUUID(),
            design);
        var module = Rocket.Module.produced(
            rocket.id(),
            rocket.buildSlots()
                .getFirst(),
            43,
            new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()));
        assertTrue(rocket.acceptProduced(module));
        assertTrue(rocket.assemble(rocket.assemblySiloId()));
        assertNull(module.insertCargo(new ItemStack(Items.iron_ingot, 7), false));
        assertEquals(123, module.fillFreight(fluid.toStack(123), false));
        assertEquals(456, module.fillPropellant(fluid.toStack(456), false));
        var drops = rocket.dropModules(new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()));
        assertEquals(1, drops.size());
        assertEquals(Rocket.Status.INCOMPLETE, rocket.status());
        assertTrue(
            rocket.dropModules(new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))
                .isEmpty());
        var drop = Rocket.ModuleDrop.deserializeNBT(
            drops.getFirst()
                .serializeNBT());
        var registry = new Rocket.Registry();
        registry.register(rocket);
        registry.restoreNBT(registry.serializeNBT(), definitions);
        rocket = registry.find(rocket.id());
        module = rocket.module(module.id());
        assertEquals(43, module.durabilityPercent());
        assertEquals(
            7,
            module.cargoContents()
                .getFirst().stackSize);
        assertEquals(123, module.freightContents().amount);
        assertEquals(
            456,
            module.propellantContents()
                .get(fluid));
        UUID pad = UUID.randomUUID(), silo = UUID.randomUUID();
        var recoveryPlace = new VehicleSchedule.Place(
            rocket.bodyKey(),
            VehicleSchedule.Context.ORBIT,
            new VehicleSchedule.Exact(
                pad,
                com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset.Endpoint.Kind.LANDING_PAD));
        assertFalse(rocket.recoverDrop(UUID.randomUUID(), drop, recoveryPlace, silo));
        assertTrue(rocket.recoverDrop(rocket.teamId(), drop, recoveryPlace, silo));
        assertFalse(rocket.recoverDrop(rocket.teamId(), drop, recoveryPlace, silo));
        assertEquals(recoveryPlace, rocket.location());
        assertEquals(Rocket.Status.DISASSEMBLED, rocket.status());
        assertEquals(
            43,
            rocket.module(drop.moduleId())
                .durabilityPercent());
        assertEquals(
            7,
            rocket.module(drop.moduleId())
                .cargoContents()
                .getFirst().stackSize);
        assertNotNull(Rocket.deserializeNBT(rocket.serializeNBT(), definitions));
    }
}
