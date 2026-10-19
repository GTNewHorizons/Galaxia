package com.gtnewhorizons.galaxia.gametests;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.screen.ModularScreen;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationInputBus;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationInputHatch;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputBus;
import com.gtnewhorizons.galaxia.compat.gt.station.MTEHatchStationOutputHatch;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.TileStationItemVault;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.blocks.ItemMachines;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class StationStorageGameTests {

    private StationStorageGameTests() {}

    /**
     * Product contract: machines of a station share its storage without pipes. Item stores in its rooms hold what
     * station input buses hand over, station output buses stock themselves from it, fluids move the same way through
     * station hatches, and plain energy and dynamo hatches trade energy with its stores.
     */
    @GameTest(timeoutTicks = 1600)
    public static void stationHatchesShareTheStationStorage(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(300)
            .server("build a storage room in orbit", () -> fixture.build(helper))
            .awaitServer("the room formed with its stores", fixture::assertStoresAttached)
            .server("put the station hatches inside", fixture::placeHatches)
            .server(
                "hand items to the input bus",
                () -> fixture.gt(Fixture.INPUT_BUS)
                    .setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 32)))
            .awaitServer("the vault holds the items", () -> {
                fixture.assertVault(Blocks.cobblestone, 32);
                if (fixture.gt(Fixture.INPUT_BUS)
                    .getStackInSlot(0) != null) throw new AssertionError("Output bus kept the items");
            })
            .server("leave dirt in the output bus and open it holding cobblestone", () -> {
                fixture.gt(Fixture.OUTPUT_BUS)
                    .setInventorySlotContents(1, new ItemStack(Blocks.dirt, 5));
                fixture.open(Fixture.OUTPUT_BUS);
                fixture.player.inventory.setItemStack(new ItemStack(Blocks.cobblestone, 64));
                fixture.player.updateHeldItem();
            })
            .awaitClient("the output bus screen is open with cobblestone in hand", c -> {
                requireOpenScreen();
                ItemStack held = Minecraft.getMinecraft().thePlayer.inventory.getItemStack();
                if (held == null || held.stackSize != 64) throw new AssertionError("Cursor holds " + held);
            })
            .click(GuiTestSupport.control("station.stock.0"))
            .awaitServer("the stock slot marks cobblestone and the hand keeps its stack", () -> {
                MTEHatchStationOutputBus bus = fixture.outputBus();
                ItemStack marked = bus.getStock(0);
                if (marked == null || marked.getItem() != new ItemStack(Blocks.cobblestone).getItem()
                    || marked.stackSize != 1) throw new AssertionError("Stock slot holds " + marked);
                ItemStack held = fixture.player.inventory.getItemStack();
                if (held == null || held.stackSize != 64) throw new AssertionError("Hand holds " + held);
            })
            .server("close the output bus", () -> {
                fixture.player.inventory.setItemStack(null);
                fixture.player.updateHeldItem();
                fixture.player.closeScreen();
            })
            .awaitServer("the output bus stocked itself and sent the dirt back", () -> {
                ItemStack stocked = fixture.gt(Fixture.OUTPUT_BUS)
                    .getStackInSlot(0);
                if (stocked == null || stocked.getItem() != new ItemStack(Blocks.cobblestone).getItem()
                    || stocked.stackSize != 32) throw new AssertionError("Input bus holds " + stocked);
                if (fixture.gt(Fixture.OUTPUT_BUS)
                    .getStackInSlot(1) != null) throw new AssertionError("Dirt stayed in the output bus");
                fixture.assertVault(Blocks.cobblestone, 0);
                fixture.assertVault(Blocks.dirt, 5);
            })
            .server("open the output bus with an empty hand", () -> fixture.open(Fixture.OUTPUT_BUS))
            .awaitClient("output bus screen open to take from", c -> requireOpenScreen())
            .click(GuiTestSupport.control("station.held.0"))
            .awaitServer("the hand takes the stocked items", () -> {
                ItemStack held = fixture.player.inventory.getItemStack();
                if (held == null || held.getItem() != new ItemStack(Blocks.cobblestone).getItem()
                    || held.stackSize != 32) throw new AssertionError("Hand holds " + held);
                if (fixture.gt(Fixture.OUTPUT_BUS)
                    .getStackInSlot(0) != null) throw new AssertionError("The items stayed in the output bus");
            })
            .server("put the taken items away and close the output bus", () -> {
                fixture.player.inventory.setItemStack(null);
                fixture.player.updateHeldItem();
                fixture.player.closeScreen();
            })
            .server(
                "fill the input hatch",
                () -> ((MTEHatchStationInputHatch) fixture.gt(Fixture.INPUT_HATCH)
                    .getMetaTileEntity()).fill(new FluidStack(FluidRegistry.WATER, 4000), true))
            .awaitServer("the station tank holds the water", () -> fixture.assertTank(4000))
            .server(
                "stock water in the output hatch",
                () -> ((MTEHatchStationOutputHatch) fixture.gt(Fixture.OUTPUT_HATCH)
                    .getMetaTileEntity()).setStock(0, new FluidStack(FluidRegistry.WATER, 1)))
            .awaitServer("the output hatch filled itself", () -> {
                MTEHatchStationOutputHatch hatch = (MTEHatchStationOutputHatch) fixture.gt(Fixture.OUTPUT_HATCH)
                    .getMetaTileEntity();
                FluidStack held = hatch.getFluid(0);
                int expected = Math.min(4000, hatch.getCapacity());
                if (held == null || held.getFluid() != FluidRegistry.WATER || held.amount != expected) {
                    throw new AssertionError("Input hatch holds " + held);
                }
                fixture.assertTank(4000 - expected);
            })
            .server("a generator charges its dynamo hatch", fixture::chargeDynamo)
            .awaitServer("plain hatches trade energy through the station", () -> {
                if (fixture.gt(Fixture.DYNAMO_HATCH)
                    .getStoredEU() > 0) throw new AssertionError("Dynamo hatch kept its energy");
                if (fixture.gt(Fixture.ENERGY_HATCH)
                    .getStoredEU() <= 0) throw new AssertionError("Energy hatch got no energy");
            })
            .server("open the output bus", () -> fixture.open(Fixture.OUTPUT_BUS))
            .awaitClient("output bus screen open", c -> requireOpenScreen())
            .capture("station-output-bus")
            .server("close the output bus", () -> fixture.player.closeScreen())
            .server("open the output hatch", () -> fixture.open(Fixture.OUTPUT_HATCH))
            .awaitClient("output hatch screen open", c -> requireOpenScreen())
            .capture("station-output-hatch")
            .server("close the output hatch", () -> fixture.player.closeScreen())
            .server("open the input bus", () -> fixture.open(Fixture.INPUT_BUS))
            .awaitClient("input bus screen open", c -> requireOpenScreen())
            .capture("station-input-bus")
            .server("close the input bus", () -> fixture.player.closeScreen())
            .server(
                "open the vault",
                () -> GalaxiaBlocksEnum.STATION_ITEM_VAULT.get()
                    .onBlockActivated(
                        fixture.world,
                        fixture.x + Fixture.VAULT.x(),
                        fixture.y + Fixture.VAULT.y(),
                        fixture.z + Fixture.VAULT.z(),
                        fixture.player,
                        1,
                        0.5f,
                        0.5f,
                        0.5f))
            .awaitClient("vault screen open", c -> GuiTestSupport.requireScreen("galaxia:station_item_vault"))
            .capture("station-item-vault")
            .server("close the vault", () -> fixture.player.closeScreen())
            .succeed();
    }

    private static void requireOpenScreen() {
        if (ModularScreen.getCurrent() == null) throw new AssertionError("No screen is open");
    }

    private static final class Fixture {

        static final BlockPos VAULT = new BlockPos(2, 1, 2);
        static final BlockPos INPUT_BUS = new BlockPos(4, 1, 2);
        static final BlockPos OUTPUT_BUS = new BlockPos(6, 1, 2);
        static final BlockPos TANK_POS = new BlockPos(2, 1, 5);
        static final BlockPos INPUT_HATCH = new BlockPos(4, 1, 5);
        static final BlockPos OUTPUT_HATCH = new BlockPos(6, 1, 5);
        static final BlockPos BATTERY_POS = new BlockPos(2, 3, 4);
        static final BlockPos ENERGY_HATCH = new BlockPos(4, 3, 4);
        static final BlockPos DYNAMO_HATCH = new BlockPos(6, 3, 4);

        private final List<BlockPos> placed = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private final int x = 96;
        private final int y = 128;
        private final int z = 0;

        void build(GameTestHelper helper) {
            StationTestStores.register();
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            world = OrbitTestSupport.enterOrbit(helper, player, x + 4.5, y + 2, z + 4.5);
            helper.afterTest(() -> { for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z()); });
            for (int dx = 0; dx <= 8; dx++) for (int dy = 0; dy <= 8; dy++) for (int dz = 0; dz <= 8; dz++) {
                if (dx == 0 || dx == 8 || dy == 0 || dy == 8 || dz == 0 || dz == 8) {
                    place(new BlockPos(dx, dy, dz), GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get());
                }
            }
            place(new BlockPos(4, 4, 0), GalaxiaBlocksEnum.STATION_CONTROLLER.get());
            place(VAULT, GalaxiaBlocksEnum.STATION_ITEM_VAULT.get());
            placeMachine(TANK_POS, ItemList.Super_Tank_LV.get(1));
            placeMachine(BATTERY_POS, ItemList.Transformer_LV_ULV.get(1));
            TileStation room = room();
            room.setOwner(GTTeamsCompat.getTeam(player));
            room.setFacing(ForgeDirection.SOUTH);
        }

        void assertStoresAttached() {
            TileStation room = room();
            room.markStructureDirty();
            if (!room.isStructureValid()) throw new AssertionError("Storage room did not form");
            StationGraph graph = room.getGraph();
            if (graph == null || !graph.getItemCapacity()
                .equals(BigInteger.valueOf(TileStationItemVault.CAPACITY))) {
                throw new AssertionError("Vault is not part of the station storage");
            }
            if (graph.getFluidCapacity()
                .signum() <= 0) throw new AssertionError("Tank is not part of the station storage");
            if (graph.getEnergyCapacity()
                .signum() <= 0) throw new AssertionError("Energy store is not part of the station");
        }

        void placeHatches() {
            placeMachine(INPUT_BUS, new ItemStack(GregTechAPI.sBlockMachines, 1, MTEHatchStationInputBus.ID));
            placeMachine(OUTPUT_BUS, new ItemStack(GregTechAPI.sBlockMachines, 1, MTEHatchStationOutputBus.ID));
            placeMachine(INPUT_HATCH, new ItemStack(GregTechAPI.sBlockMachines, 1, MTEHatchStationInputHatch.ID));
            placeMachine(OUTPUT_HATCH, new ItemStack(GregTechAPI.sBlockMachines, 1, MTEHatchStationOutputHatch.ID));
            placeMachine(ENERGY_HATCH, ItemList.Hatch_Energy_LV.get(1));
            placeMachine(DYNAMO_HATCH, ItemList.Hatch_Dynamo_IV.get(1));
        }

        /** Fills the dynamo hatch the way a generator would */
        void chargeDynamo() {
            IGregTechTileEntity dynamo = gt(DYNAMO_HATCH);
            dynamo.increaseStoredEnergyUnits(dynamo.getEUCapacity(), true);
        }

        void assertVault(Block block, long amount) {
            TileStationItemVault vault = (TileStationItemVault) world
                .getTileEntity(x + VAULT.x(), y + VAULT.y(), z + VAULT.z());
            long held = vault.getAmount(ItemStackWrapper.of(new ItemStack(block)));
            if (held != amount) throw new AssertionError("Vault holds " + held + " of " + block);
        }

        void assertTank(int amount) {
            MTEDigitalTankBase tank = (MTEDigitalTankBase) gt(TANK_POS).getMetaTileEntity();
            if (tank.getFluidAmount() != amount) throw new AssertionError("Tank holds " + tank.getFluidAmount());
        }

        void open(BlockPos pos) {
            GregTechAPI.sBlockMachines.onBlockActivated(
                world,
                x + pos.x(),
                y + pos.y(),
                z + pos.z(),
                player,
                ForgeDirection.UP.ordinal(),
                0.5f,
                0.5f,
                0.5f);
        }

        MTEHatchStationOutputBus outputBus() {
            return (MTEHatchStationOutputBus) gt(OUTPUT_BUS).getMetaTileEntity();
        }

        IGregTechTileEntity gt(BlockPos pos) {
            return (IGregTechTileEntity) world.getTileEntity(x + pos.x(), y + pos.y(), z + pos.z());
        }

        TileStation room() {
            return (TileStation) world.getTileEntity(x + 4, y + 4, z);
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
            if (!(gt(pos).getMetaTileEntity() instanceof MetaTileEntity)) {
                throw new AssertionError("No machine at " + at);
            }
        }
    }
}
