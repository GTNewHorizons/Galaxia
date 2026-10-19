package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.TileStationItemVault;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileStationDevice;
import com.gtnewhorizons.galaxia.registry.items.GalaxiaItemList;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class StationDevicesGameTests {

    private StationDevicesGameTests() {}

    /**
     * Product contract: the machines that keep a room liveable work wherever they stand in it, on the station's energy
     * and on what they consume: the oxygenator on oxygen from the station's storage, the air purifier and the
     * withering blocker on cartridges they take from there, and the coils on energy alone. They take energy from cables
     * too. The room counts the machines that work.
     */
    @GameTest(timeoutTicks = 1600)
    public static void machinesKeepTheRoomLiveable(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("build a room with its machines in orbit", () -> fixture.build(helper))
            .awaitServer("the room formed with its stores", fixture::assertRoomReady)
            .server("a cable feeds a coil", fixture::assertCableEnergy)
            .server("charge the station and stock what the machines use", fixture::stock)
            .awaitServer("every machine works and the room is liveable", fixture::assertLiveable)
            .server("open the oxygenator", () -> fixture.open(Fixture.OXYGENATOR))
            .awaitClient("oxygenator screen open", c -> requireDeviceScreen())
            .capture("station-oxygenator")
            .server("close the oxygenator", () -> fixture.player.closeScreen())
            .server("open the air purifier", () -> fixture.open(Fixture.PURIFIER))
            .awaitClient("purifier screen open", c -> requireDeviceScreen())
            .capture("station-air-purifier")
            .server("close the air purifier", () -> fixture.player.closeScreen())
            .succeed();
    }

    private static void requireDeviceScreen() {
        GuiTestSupport.requireScreen("galaxia:station_device");
        if (ModularScreen.getCurrent() == null) throw new AssertionError("No screen is open");
    }

    private static final class Fixture {

        static final BlockPos VAULT = new BlockPos(1, 1, 1);
        static final BlockPos TANK = new BlockPos(3, 1, 1);
        static final BlockPos BATTERY = new BlockPos(5, 1, 1);
        static final BlockPos OXYGENATOR = new BlockPos(1, 1, 4);
        static final BlockPos PURIFIER = new BlockPos(3, 1, 4);
        static final BlockPos WITHER_BLOCKER = new BlockPos(5, 1, 4);
        static final BlockPos COOLING_COIL = new BlockPos(1, 1, 6);
        /** In the wall instead of inside the room */
        static final BlockPos HEATING_COIL = new BlockPos(8, 3, 4);

        private final List<BlockPos> placed = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private final int x = 160;
        private final int y = 128;
        private final int z = 0;
        private long filtersBefore;
        private long witherBefore;

        void build(GameTestHelper helper) {
            StationTestStores.register();
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            world = OrbitTestSupport.enterOrbit(helper, player, x + 4.5, y + 2, z + 2.5);
            helper.afterTest(() -> { for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z()); });
            for (int dx = 0; dx <= 8; dx++) for (int dy = 0; dy <= 8; dy++) for (int dz = 0; dz <= 8; dz++) {
                if (dx == 0 || dx == 8 || dy == 0 || dy == 8 || dz == 0 || dz == 8) {
                    place(new BlockPos(dx, dy, dz), GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get());
                }
            }
            place(new BlockPos(4, 4, 0), GalaxiaBlocksEnum.STATION_CONTROLLER.get());
            place(VAULT, GalaxiaBlocksEnum.STATION_ITEM_VAULT.get());
            placeMachine(TANK, ItemList.Super_Tank_LV.get(1));
            placeMachine(BATTERY, ItemList.Transformer_LV_ULV.get(1));
            place(OXYGENATOR, GalaxiaBlocksEnum.OXYGENATOR.get());
            place(PURIFIER, GalaxiaBlocksEnum.AIR_PURIFIER.get());
            place(WITHER_BLOCKER, GalaxiaBlocksEnum.WITHER_BLOCKER.get());
            place(COOLING_COIL, GalaxiaBlocksEnum.COOLING_COIL.get());
            place(HEATING_COIL, GalaxiaBlocksEnum.HEATING_COIL.get());
            room().setOwner(GTTeamsCompat.getTeam(player));
            room().setFacing(ForgeDirection.SOUTH);
        }

        void assertRoomReady() {
            TileStation room = room();
            room.markStructureDirty();
            if (!room.isStructureValid()) throw new AssertionError("Room did not form");
            if (!room.isSealed()) throw new AssertionError("Room is not sealed");
            StationGraph graph = room.getGraph();
            if (graph == null || graph.getEnergyCapacity()
                .signum() <= 0
                || graph.getFluidCapacity()
                    .signum() <= 0
                || graph.getItemCapacity()
                    .signum() <= 0) {
                throw new AssertionError("The room's stores are not attached");
            }
        }

        /** A coil takes energy from a cable like any GT machine */
        void assertCableEnergy() {
            long accepted = ((IEnergyConnected) tile(COOLING_COIL)).injectEnergyUnits(ForgeDirection.UP, 32, 1);
            if (accepted != 1) throw new AssertionError("Coil refused energy from a cable");
        }

        void stock() {
            StationTestStores.batteryStored = 500_000_000L;
            TileStationItemVault.HANDLER
                .insertItem(vault(), new ItemStack(GalaxiaItemList.AIR_FILTER.getItem(), 2), true);
            TileStationItemVault.HANDLER
                .insertItem(vault(), new ItemStack(GalaxiaItemList.WITHER_CARTRIDGE.getItem(), 2), true);
            filtersBefore = cartridges(GalaxiaItemList.AIR_FILTER);
            witherBefore = cartridges(GalaxiaItemList.WITHER_CARTRIDGE);
            tank().fill(new FluidStack(FluidRegistry.getFluid("oxygen"), 16_000), true);
        }

        long cartridges(GalaxiaItemList cartridge) {
            return vault().getAmount(ItemStackWrapper.of(new ItemStack(cartridge.getItem())));
        }

        void assertLiveable() {
            for (BlockPos pos : List.of(OXYGENATOR, PURIFIER, WITHER_BLOCKER, COOLING_COIL, HEATING_COIL)) {
                if (!((TileStationDevice) tile(pos)).isWorking()) {
                    throw new AssertionError(world.getBlock(x + pos.x(), y + pos.y(), z + pos.z()) + " does not work");
                }
            }
            TileStation room = room();
            if (!room.hasAirPurifier()) throw new AssertionError("Room has no working air purifier");
            if (!room.hasWitherBlocker()) throw new AssertionError("Room has no working withering blocker");
            if (room.getCoolingModifier() <= 0) throw new AssertionError("Room is not cooled");
            if (room.getHeatingModifier() <= 0) throw new AssertionError("Room is not heated by the wall coil");
            if (room.getOxygenLevel() <= 0) throw new AssertionError("Room gets no oxygen");
            if (tank().getFluidAmount() >= 16_000)
                throw new AssertionError("Oxygenator took no oxygen from the station");
            if (cartridges(GalaxiaItemList.AIR_FILTER) != filtersBefore - 1) {
                throw new AssertionError("Purifier did not take a filter from the station");
            }
            if (cartridges(GalaxiaItemList.WITHER_CARTRIDGE) != witherBefore - 1) {
                throw new AssertionError("Withering blocker did not take a cartridge from the station");
            }
        }

        void open(BlockPos pos) {
            Block block = world.getBlock(x + pos.x(), y + pos.y(), z + pos.z());
            block.onBlockActivated(world, x + pos.x(), y + pos.y(), z + pos.z(), player, 1, 0.5f, 0.5f, 0.5f);
        }

        TileStation room() {
            return (TileStation) world.getTileEntity(x + 4, y + 4, z);
        }

        TileStationItemVault vault() {
            return (TileStationItemVault) world.getTileEntity(x + VAULT.x(), y + VAULT.y(), z + VAULT.z());
        }

        MTEDigitalTankBase tank() {
            return (MTEDigitalTankBase) ((IGregTechTileEntity) world
                .getTileEntity(x + TANK.x(), y + TANK.y(), z + TANK.z())).getMetaTileEntity();
        }

        Object tile(BlockPos pos) {
            return world.getTileEntity(x + pos.x(), y + pos.y(), z + pos.z());
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
