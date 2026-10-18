package com.gtnewhorizons.galaxia.registry.celestial;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityTransportPlatform;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class NativeTransportControllerTest {

    @BeforeAll
    static void initialize() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
        TileEntity.addMapping(TileEntityTransportPlatform.class, "test.galaxia.landing_pad");
        TileEntity.addMapping(TileEntitySilo.class, "test.galaxia.silo");
        TileEntity.addMapping(TileEntityGantryTerminal.class, "test.galaxia.native_terminal");
    }

    /** Bug regression: the Pad deck checks the direction in which its controller was placed. */
    @Test
    void padPlacementSetsTheStructureOrientation() {
        var pad = new TileEntityTransportPlatform();
        pad.setPlacedFacing(ForgeDirection.SOUTH);
        assertEquals(
            ForgeDirection.SOUTH,
            pad.getCurrentFacing()
                .getDirection());
    }

    /** Product contract: Pad geometry follows its real deck and reservation does not fake arrival. */
    @Test
    void freeDestinationPadCanAdmitADifferentBodyReservationWithoutClaimingArrival() {
        var team = UUID.randomUUID();
        var pad = new FormedPad();
        pad.machineIdentity()
            .claim(team);
        pad.endpointBinding()
            .selectStandalone();
        pad.endpointBinding()
            .formed(
                CelestialAssetStore.SERVER,
                pad.machineIdentity(),
                team,
                CelestialObjectKey.registered(CelestialObjectId.MARS),
                pad.endpointKind(),
                new CelestialAsset.PhysicalLocation(0, new BlockPos(0, 0, 0)),
                null);
        try {
            var design = new RocketBlueprint();
            design.addPart(new RocketPartInstance(RocketPartDef.structural(1, "frame", 1, 1, 1, null), 0, 0, 0, false));
            var rocket = Rocket
                .construct(team, CelestialObjectKey.registered(CelestialObjectId.OVERWORLD), UUID.randomUUID(), design);
            assertTrue(
                rocket.acceptProduced(
                    Rocket.Module.produced(
                        rocket.id(),
                        rocket.buildSlots()
                            .getFirst(),
                        100,
                        new Rocket.Holder(Rocket.HolderKind.SILO_BUFFER, rocket.assemblySiloId()))));
            assertTrue(rocket.assemble(rocket.assemblySiloId()));
            var registry = new Rocket.Registry();
            registry.register(rocket);
            assertTrue(pad.reserveArrival(registry, rocket));
            assertFalse(pad.acceptArrival(registry, rocket));
            pad.xCoord = 10;
            pad.yCoord = 20;
            pad.zCoord = 30;
            pad.setPlacedFacing(ForgeDirection.EAST);
            assertEquals(new BlockPos(8, 21, 30), pad.vehiclePosition());
        } finally {
            CelestialAssetStore.SERVER.destroyAssetInternal(
                pad.endpointBinding()
                    .assetId());
        }
    }

    private static final class FormedPad extends TileEntityTransportPlatform {

        private FormedPad() {
            structureValid = true;
        }

        @Override
        public void markDirty() {}
    }

    /** Integration contract: native controller saves carry exact machine and backing asset identity. */
    @Test
    void padAndSiloSaveTheirOwnEndpointLifetime() {
        var team = UUID.randomUUID();
        var store = new CelestialAssetStore();
        var body = CelestialObjectKey.registered(CelestialObjectId.MARS);
        var location = new CelestialAsset.PhysicalLocation(7, new BlockPos(1, 2, 3));
        var pad = new TileEntityTransportPlatform();
        pad.machineIdentity()
            .claim(team);
        pad.endpointBinding()
            .selectStandalone();
        pad.endpointBinding()
            .formed(store, pad.machineIdentity(), team, body, pad.endpointKind(), location, null);
        var padSave = new NBTTagCompound();
        pad.writeToNBT(padSave);
        var restoredPad = new TileEntityTransportPlatform();
        restoredPad.readFromNBT(padSave);
        assertEquals(
            pad.machineIdentity()
                .id(),
            restoredPad.machineIdentity()
                .id());
        assertEquals(
            pad.endpointBinding()
                .assetId(),
            restoredPad.endpointBinding()
                .assetId());
        assertNotEquals(
            pad.machineIdentity()
                .id(),
            new TileEntityTransportPlatform().machineIdentity()
                .id());

        var silo = new TileEntitySilo();
        silo.machineIdentity()
            .claim(team);
        silo.endpointBinding()
            .selectStandalone();
        silo.endpointBinding()
            .formed(store, silo.machineIdentity(), team, body, silo.endpointKind(), location, null);
        var siloSave = new NBTTagCompound();
        silo.writeToNBT(siloSave);
        var restoredSilo = new TileEntitySilo();
        restoredSilo.readFromNBT(siloSave);
        assertEquals(
            silo.machineIdentity()
                .id(),
            restoredSilo.machineIdentity()
                .id());
        assertEquals(
            silo.endpointBinding()
                .assetId(),
            restoredSilo.endpointBinding()
                .assetId());
    }

    /** Integration contract: a native Pad is a distinct Gantry participant, never an Assembler or Silo. */
    @Test
    void gantryConnectsTheExactNativePadWithoutConflatingItsRole() {
        var pad = new TileEntityTransportPlatform();
        var team = UUID.randomUUID();
        pad.machineIdentity()
            .claim(team);
        var terminal = new TileEntityGantryTerminal();
        terminal.connectPlatform(pad);
        assertSame(pad, terminal.getPlatform());
        assertNull(terminal.getSilo());
        assertNull(terminal.getAssembler());
        assertEquals(
            pad.machineIdentity()
                .id(),
            terminal.controllerIdentity()
                .id());
        assertSame(
            terminal,
            GantryAPI.findControllerTerminal(
                terminal,
                pad.machineIdentity()
                    .id()));
        var saved = new NBTTagCompound();
        terminal.writeToNBT(saved);
        var restored = new TileEntityGantryTerminal();
        restored.readFromNBT(saved);
        assertEquals(terminal.terminalId(), restored.terminalId());
        assertNull(restored.controller());
    }
}
