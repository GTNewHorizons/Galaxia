package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.RecipeEnvironments;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileOxygenator;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = "gregtech")
public final class RoomPressureGameTests {

    private RoomPressureGameTests() {}

    private static MetaTileEntity controllerAt(WorldServer world, int x, int y, int z) {
        BaseMetaTileEntity tile = new BaseMetaTileEntity();
        tile.setWorldObj(world);
        tile.xCoord = x;
        tile.yCoord = y;
        tile.zCoord = z;
        MetaTileEntity machine = new MTEElectricBlastFurnace("room_condition_probe");
        machine.setBaseMetaTileEntity(tile);
        return machine;
    }

    /** Product contract: pressure survives without oxygen, equalizes through airflow and empties through a breach. */
    @GameTest(timeoutTicks = 600)
    public static void pressureIsIndependentAndFollowsAirflow(GameTestHelper helper) {
        Rooms rooms = new Rooms();
        double[] initial = new double[1];
        long[] observedAt = new long[1];
        ClientTest.scenario(helper)
            .server("build two closed orbital rooms with persisted atmosphere", () -> rooms.build(helper))
            .withinTicks(300)
            .awaitServer("both rooms and their shared airlock form", () -> {
                helper.assertTrue(
                    rooms.room(0)
                        .isStructureValid(),
                    "first room formed");
                helper.assertTrue(
                    rooms.room(12)
                        .isStructureValid(),
                    "second room formed");
                helper.assertEquals(
                    2,
                    rooms.lock()
                        .getStationControllers()
                        .size(),
                    "both sides tracked");
                helper.assertTrue(
                    rooms.room(0)
                        .isSealed(),
                    "first room sealed");
                helper.assertTrue(
                    rooms.room(12)
                        .isSealed(),
                    "second room sealed");
            })
            .server("observe atmosphere separately from oxygen", () -> {
                initial[0] = rooms.room(0)
                    .getPressureLevel();
                helper.assertTrue(initial[0] > 0, "persisted pressure remains after oxygenation stops");
                helper.assertFalse(
                    rooms.room(0)
                        .isOxygenated(),
                    "pressure does not imply breathable oxygen");
                helper.assertEquals(
                    0.0,
                    rooms.room(12)
                        .getPressureLevel(),
                    "second room is vacuum");
                helper.assertFalse(
                    RecipeEnvironments.NO_ATMOSPHERE.isSatisfied(controllerAt(rooms.world, 35, 131, 3)),
                    "pressurized room denies vacuum recipes");
                helper.assertTrue(
                    RecipeEnvironments.NO_ATMOSPHERE.isSatisfied(controllerAt(rooms.world, 35, 131, 9)),
                    "vacuum room accepts vacuum recipes");
                observedAt[0] = rooms.world.getTotalWorldTime();
            })
            .awaitServer("unpowered room loses pressure slowly", () -> {
                if (rooms.world.getTotalWorldTime() - observedAt[0] < 20)
                    throw new AssertionError("Waiting for passive loss");
                double pressure = rooms.room(0)
                    .getPressureLevel();
                helper
                    .assertTrue(pressure > 0 && pressure < initial[0], "pressure falls without vanishing immediately");
            })
            .server("opening the internal airlock conserves atmosphere", () -> {
                double expected = (rooms.room(0)
                    .getPressureLevel()
                    + rooms.room(12)
                        .getPressureLevel())
                    / 2;
                rooms.lock()
                    .toggleState(rooms.player);
                helper.assertTrue(
                    rooms.lock()
                        .isOpen(),
                    "internal airlock opened");
                double first = rooms.room(0)
                    .getPressureLevel();
                double second = rooms.room(12)
                    .getPressureLevel();
                helper.assertTrue(Math.abs(expected - first) < 0.000001, "equal volumes conserve atmosphere");
                helper.assertEquals(first, second, "connected rooms share pressure immediately");
                rooms.supplyOxygenator();
            })
            .withinTicks(150)
            .awaitServer("powered oxygenator fills both connected rooms", () -> {
                helper.assertTrue(
                    rooms.oxygenator()
                        .isWorking(),
                    "oxygenator has ordinary energy and oxygen");
                helper.assertTrue(
                    rooms.room(0)
                        .getPressureLevel() > 0.99,
                    "operating oxygenator fills the room");
                helper.assertEquals(
                    rooms.room(0)
                        .getPressureLevel(),
                    rooms.room(12)
                        .getPressureLevel(),
                    "filling follows open airflow");
            })
            .server("open the real room controller", () -> {
                rooms.player.setPositionAndUpdate(34.5, 129, 2.5);
                GuiFactories.tileEntity()
                    .open(rooms.player, 35, 131, 0);
            })
            .awaitClient(
                "room controller reaches the client",
                c -> GuiTestSupport.requireScreen("galaxia:station_controller"))
            .capture("room-pressure-filled")
            .server("open a wall to exterior vacuum", () -> {
                rooms.player.closeScreen();
                rooms.place(0, 3, 3, net.minecraft.init.Blocks.air, 0);
            })
            .withinTicks(150)
            .awaitServer("exterior breach empties the connected room", () -> {
                helper.assertFalse(
                    rooms.room(0)
                        .isStructureValid(),
                    "first room breached");
                helper.assertEquals(
                    0.0,
                    rooms.room(12)
                        .getPressureLevel(),
                    "connected atmosphere lost to exterior vacuum");
            })
            .succeed();
    }

    /** Harness exception: physical rooms with persisted pressure, without a separate test-only pressure setter. */
    private static final class Rooms {

        private final List<BlockPos> placed = new ArrayList<>();
        private WorldServer world;
        private EntityPlayerMP player;

        private void build(GameTestHelper helper) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            world = OrbitTestSupport.enterOrbit(helper, player, 27.5, 131, 3.5);
            helper.afterTest(() -> placed.forEach(pos -> world.setBlockToAir(pos.x(), pos.y(), pos.z())));
            for (int x = 0; x <= 6; x++) for (int y = 0; y <= 6; y++) for (int z = 0; z <= 12; z++) {
                helper.assertTrue(world.isAirBlock(32 + x, 128 + y, z), "fixture space is empty");
                if (x == 0 || x == 6 || y == 0 || y == 6 || z == 0 || z == 6 || z == 12) {
                    place(x, y, z, GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(), 0);
                }
            }
            for (int x = 1; x <= 5; x++) for (int y = 1; y <= 5; y++) {
                boolean frame = x == 1 || x == 5 || y == 1 || y == 5;
                place(
                    x,
                    y,
                    6,
                    frame ? GalaxiaBlocksEnum.AIRLOCK_CASING.get() : GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                    frame ? 0 : BlockAirlockDoor.encodeMeta(false, BlockAirlockDoor.ORIENT_Z));
            }
            place(1, 3, 6, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0);
            lock().setFacing(ForgeDirection.NORTH);
            lock().setProximityOpening(false);
            lock().setProximityAutoClose(false);
            lock().setAutoSealOnLeak(false);
            for (int z : new int[] { 0, 12 }) {
                place(3, 3, z, GalaxiaBlocksEnum.STATION_CONTROLLER.get(), 0);
                TileStation room = room(z);
                NBTTagCompound saved = new NBTTagCompound();
                room.writeToNBT(saved);
                saved.setDouble("roomPressure", z == 0 ? 1 : 0);
                room.readFromNBT(saved);
                room.setOwner(GTTeamsCompat.getTeam(player));
                room.setFacing(z == 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH);
            }
        }

        private void place(int x, int y, int z, Block block, int meta) {
            BlockPos pos = new BlockPos(32 + x, 128 + y, z);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlock(pos.x(), pos.y(), pos.z(), block, meta, 3);
        }

        private TileStation room(int z) {
            return (TileStation) world.getTileEntity(35, 131, z);
        }

        private TileEntityAirlock lock() {
            return (TileEntityAirlock) world.getTileEntity(33, 131, 6);
        }

        private void supplyOxygenator() {
            place(0, 1, 2, GregTechAPI.sBlockMachines, 0);
            BaseMetaTileEntity hatch = (BaseMetaTileEntity) world.getTileEntity(32, 129, 2);
            hatch.setInitialValuesAsNBT(
                null,
                (short) ItemList.Hatch_Energy_HV.get(1)
                    .getItemDamage());
            hatch.setStoredEU(1000000);
            place(2, 1, 3, GalaxiaBlocksEnum.OXYGENATOR.get(), 0);
            oxygenator().getTank()
                .fill(new FluidStack(FluidRegistry.getFluid("oxygen"), 1000), true);
        }

        private TileOxygenator oxygenator() {
            return (TileOxygenator) world.getTileEntity(34, 129, 3);
        }
    }
}
