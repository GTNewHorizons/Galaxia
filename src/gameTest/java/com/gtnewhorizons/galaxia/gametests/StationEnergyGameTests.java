package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputBus;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class StationEnergyGameTests {

    private StationEnergyGameTests() {}

    /**
     * Product contract: a station runs on plain GT hatches without an LSC. An energy hatch built into a room's wall
     * takes energy from cables outside into the station, a dynamo hatch inside a room hands a generator's energy to
     * it, and the energy hatches of machines inside are fed from both. Station hatches may be built into the wall too.
     */
    @GameTest(timeoutTicks = 1200)
    public static void plainHatchesPowerTheStation(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(300)
            .server("build a room with hatches in its walls", () -> fixture.build(helper))
            .awaitServer("the room formed around its wall hatches", fixture::assertRoomReady)
            .awaitServer("a cable into the wall hatch feeds a machine inside", () -> {
                // The cable outside keeps the wall hatch full
                fixture.charge(Fixture.WALL_ENERGY);
                if (fixture.gt(Fixture.CONSUMER)
                    .getStoredEU() <= 0) throw new AssertionError("Machine inside got nothing from the wall hatch");
            })
            .server("the cable stops, a generator inside starts", () -> {
                IGregTechTileEntity wall = fixture.gt(Fixture.WALL_ENERGY);
                wall.decreaseStoredEnergyUnits(wall.getStoredEU(), true);
                IGregTechTileEntity consumer = fixture.gt(Fixture.CONSUMER);
                consumer.decreaseStoredEnergyUnits(consumer.getStoredEU(), true);
                fixture.charge(Fixture.DYNAMO);
            })
            .awaitServer("the dynamo inside feeds the machine", () -> {
                if (fixture.gt(Fixture.CONSUMER)
                    .getStoredEU() <= 0) throw new AssertionError("Machine inside got nothing from the dynamo");
            })
            .succeed();
    }

    private static final class Fixture {

        /** In the west wall, fed from outside */
        static final BlockPos WALL_ENERGY = new BlockPos(0, 3, 3);
        /** In the east wall */
        static final BlockPos WALL_BUS = new BlockPos(6, 3, 3);
        static final BlockPos DYNAMO = new BlockPos(2, 1, 2);
        static final BlockPos CONSUMER = new BlockPos(4, 1, 4);

        private final List<BlockPos> placed = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private final int x = 192;
        private final int y = 128;
        private final int z = 0;

        void build(GameTestHelper helper) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            world = OrbitTestSupport.enterOrbit(helper, player, x + 3.5, y + 2, z + 3.5);
            helper.afterTest(() -> { for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z()); });
            for (int dx = 0; dx <= 6; dx++) for (int dy = 0; dy <= 6; dy++) for (int dz = 0; dz <= 6; dz++) {
                if (dx == 0 || dx == 6 || dy == 0 || dy == 6 || dz == 0 || dz == 6) {
                    place(new BlockPos(dx, dy, dz), GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get());
                }
            }
            place(new BlockPos(3, 3, 0), GalaxiaBlocksEnum.STATION_CONTROLLER.get());
            placeMachine(WALL_ENERGY, ItemList.Hatch_Energy_IV.get(1));
            placeMachine(WALL_BUS, new ItemStack(GregTechAPI.sBlockMachines, 1, MTEHatchStationOutputBus.ID));
            placeMachine(DYNAMO, ItemList.Hatch_Dynamo_IV.get(1));
            placeMachine(CONSUMER, ItemList.Hatch_Energy_LV.get(1));
            room().setOwner(GTTeamsCompat.getTeam(player));
            room().setFacing(ForgeDirection.SOUTH);
        }

        void assertRoomReady() {
            TileStation room = room();
            room.markStructureDirty();
            if (!room.isStructureValid()) throw new AssertionError("Room did not form with hatches in its walls");
            if (!room.isSealed()) throw new AssertionError("Room with hatches in its walls is not sealed");
            if (room.getGraph() == null || room.getGraph()
                .getEnergyCapacity()
                .signum() <= 0) throw new AssertionError("The room has no energy sources");
            if (!((MTEHatchStationOutputBus) gt(WALL_BUS).getMetaTileEntity()).isLinked()) {
                throw new AssertionError("Station bus in the wall does not reach the station");
            }
        }

        void charge(BlockPos pos) {
            IGregTechTileEntity hatch = gt(pos);
            hatch.increaseStoredEnergyUnits(hatch.getEUCapacity() - hatch.getStoredEU(), true);
        }

        TileStation room() {
            return (TileStation) world.getTileEntity(x + 3, y + 3, z);
        }

        IGregTechTileEntity gt(BlockPos pos) {
            return (IGregTechTileEntity) world.getTileEntity(x + pos.x(), y + pos.y(), z + pos.z());
        }

        void place(BlockPos pos, Block block) {
            BlockPos at = new BlockPos(x + pos.x(), y + pos.y(), z + pos.z());
            if (!placed.contains(at)) placed.add(at);
            world.setBlock(at.x(), at.y(), at.z(), block, 0, 3);
        }

        void placeMachine(BlockPos pos, ItemStack machine) {
            BlockPos at = new BlockPos(x + pos.x(), y + pos.y(), z + pos.z());
            if (!placed.contains(at)) placed.add(at);
            world.setBlockToAir(at.x(), at.y(), at.z());
            ((ItemMachines) machine.getItem()).placeBlockAt(
                machine,
                player,
                world,
                at.x(),
                at.y(),
                at.z(),
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                machine.getItemDamage());
        }
    }
}
