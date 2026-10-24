package com.gtnewhorizons.galaxia.gametests;

import static com.gtnewhorizons.galaxia.gametests.GuiTestSupport.requireScreen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class RocketGameTests {

    private RocketGameTests() {}

    /** Physical GT inputs feed canonical buffers, while unloaded cargo enters native output buses. */
    @GameTest(timeoutTicks = 1600)
    public static void hatchesSupplyCanonicalRocketMachines(GameTestHelper helper) {
        var fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build silo and assembler structures with GT hatches", () -> fixture.build(helper))
            .awaitServer("structures recognize their hatches and rail terminals", fixture::assertFormed)
            .server("supply actual input buses and a fluid hatch", () -> {
                fixture.machine(-2, -1, 0)
                    .setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 32));
                fixture.machine(11, 2, 6)
                    .setInventorySlotContents(0, new ItemStack(Items.iron_ingot, 12));
                var hatch = (MTEHatchInput) fixture.machine(2, -1, 0)
                    .getMetaTileEntity();
                if (hatch.fill(Materials.Water.getFluid(1000), true) != 1000)
                    throw new AssertionError("Input hatch rejected water");
            })
            .awaitServer("assembler imports its real bus contents", () -> {
                ItemStack stack = fixture.assembler.getStackInSlot(0);
                if (stack == null || stack.getItem() != Items.iron_ingot || stack.stackSize != 12)
                    throw new AssertionError("Assembler did not import its input bus");
                if (fixture.machine(11, 2, 6)
                    .getStackInSlot(0) != null) throw new AssertionError("Imported items remained in the input bus");
            })
            .server("simulate and commit mission loading through the hatch view", () -> {
                var input = fixture.silo.loadingInventory();
                var cargo = ItemStackWrapper.of(new ItemStack(Blocks.cobblestone));
                if (input.updateItems(cargo, -7, true) != 7 || fixture.machine(-2, -1, 0)
                    .getStackInSlot(0).stackSize != 32)
                    throw new AssertionError("Item extraction simulation mutated the bus");
                if (input.updateItems(cargo, -7, false) != 7 || fixture.machine(-2, -1, 0)
                    .getStackInSlot(0).stackSize != 25)
                    throw new AssertionError("Item extraction did not debit exactly seven items");
                var water = FluidKey.of(Materials.Water.getFluid(1));
                if (input.updateFluids(water, -250, true) != 250 || input.getFluidAmount(water) != 1000)
                    throw new AssertionError("Fluid extraction simulation mutated the hatch");
                if (input.updateFluids(water, -250, false) != 250 || input.getFluidAmount(water) != 750)
                    throw new AssertionError("Fluid extraction did not debit exactly 250 mB");
                var output = fixture.target.loadingInventory();
                if (output.updateItems(cargo, 8, true) != 8 || fixture.machine(41, -1, 0)
                    .getStackInSlot(0) != null) throw new AssertionError("Output simulation mutated the bus");
                if (output.updateItems(cargo, 8, false) != 8 || fixture.machine(41, -1, 0)
                    .getStackInSlot(0).stackSize != 8) throw new AssertionError("Unload did not enter the output bus");
                if (output.getItemAmount(cargo) != 0)
                    throw new AssertionError("Unloaded output was exposed again as a loading input");
            })
            .server("open the canonical assembler", () -> {
                fixture.player.setPositionAndUpdate(fixture.x + 10.5, fixture.y + 3, fixture.z + 2.5);
                GuiFactories.tileEntity()
                    .open(fixture.player, fixture.x + 10, fixture.y + 1, fixture.z + 6);
            })
            .awaitClient("assembler inventory screen opens", c -> requireScreen("galaxia:module_assembler"))
            .capture("canonical-assembler-with-native-input-bus")
            .succeed();
    }

    /** Six-face rails rebuild cached routes after a player removes a shaft segment. */
    @GameTest(timeoutTicks = 800)
    public static void railsConnectVerticallyAndInvalidateBrokenRoutes(GameTestHelper helper) {
        var fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("place a vertical rail route with a corner", () -> {
                fixture.prepare(helper);
                fixture.place(0, 0, 0, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0);
                fixture.place(0, 1, 0, GalaxiaBlocksEnum.GANTRY.get(), 0);
                fixture.place(0, 2, 0, GalaxiaBlocksEnum.GANTRY.get(), 0);
                fixture.place(1, 2, 0, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0);
                TileEntityGantryTerminal start = fixture.tile(0, 0, 0);
                TileEntityGantryTerminal end = fixture.tile(1, 2, 0);
                var network = start.getNetwork();
                if (network.size() != 4 || network.route(start, end)
                    .size() != 4) throw new AssertionError("Vertical rails did not produce a four-node route");
                if (start.getNeighbours()
                    .getFirst() != fixture.<TileEntityGantry>tile(0, 1, 0))
                    throw new AssertionError("Start did not connect to the rail on its upper face");
                fixture.world.setBlockToAir(fixture.x, fixture.y + 1, fixture.z);
                if (network.isValid() || start.getNetwork()
                    .canReach(start, end))
                    throw new AssertionError("Removed shaft segment remained in the cached route");
            })
            .succeed();
    }

    /** Wrench orientation and team ownership coexist with the persistent silo endpoint. */
    @GameTest(timeoutTicks = 1200)
    public static void wrenchTurnsAndMirrorsSilo(GameTestHelper helper) {
        var fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("turn and mirror a claimed silo before construction", () -> {
                fixture.prepare(helper);
                fixture.place(0, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
                fixture.silo = fixture.tile(0, 0, 0);
                fixture.claim(fixture.silo);
                fixture.silo.setFacing(ForgeDirection.NORTH);
                if (!fixture.silo.onWrench(fixture.player, ForgeDirection.EAST) || fixture.silo.getExtendedFacing()
                    .getDirection() != ForgeDirection.EAST)
                    throw new AssertionError("Wrenching did not turn the silo east");
                if (fixture.silo.onWrench(fixture.player, ForgeDirection.UP))
                    throw new AssertionError("A silo must not face up");
                fixture.player.setSneaking(true);
                boolean mirrored;
                try {
                    mirrored = fixture.silo.onWrench(fixture.player, ForgeDirection.EAST);
                } finally {
                    fixture.player.setSneaking(false);
                }
                if (!mirrored || fixture.silo.getExtendedFacing()
                    .getFlip() != Flip.HORIZONTAL) throw new AssertionError("Sneak-wrenching did not mirror the silo");
                fixture.constructAndRecord(fixture.silo);
                fixture.silo.markStructureDirty();
            })
            .awaitServer(
                "the turned mirrored silo forms",
                () -> { if (!fixture.silo.isStructureValid()) throw new AssertionError("Turned silo did not form"); })
            .succeed();
    }

    private static final class Fixture {

        private final List<BlockPos> placed = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private int x, y, z;
        private TileEntitySilo silo, target;
        private TileEntityModuleAssembler assembler;

        private void prepare(GameTestHelper helper) {
            world = helper.getWorld();
            player = (EntityPlayerMP) world.playerEntities.get(0);
            x = (int) player.posX + 24;
            y = 140;
            z = (int) player.posZ;
            double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
            boolean oldAllowFlying = player.capabilities.allowFlying, oldFlying = player.capabilities.isFlying;
            player.capabilities.allowFlying = true;
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            helper.afterTest(() -> {
                player.closeScreen();
                for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z());
                player.capabilities.allowFlying = oldAllowFlying;
                player.capabilities.isFlying = oldFlying;
                player.sendPlayerAbilities();
                player.setPositionAndUpdate(oldX, oldY, oldZ);
            });
        }

        private void claim(TileEntitySilo silo) {
            silo.machineIdentity()
                .claim(GTTeamsCompat.getTeam(player));
            silo.endpointBinding()
                .selectStandalone();
        }

        private void build(GameTestHelper helper) {
            prepare(helper);
            place(0, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
            silo = tile(0, 0, 0);
            claim(silo);
            silo.setFacing(ForgeDirection.NORTH);
            constructAndRecord(silo);
            place(10, 1, 6, GalaxiaBlocksEnum.ASSEMBLER_CONTROLLER.get(), 0);
            assembler = tile(10, 1, 6);
            assembler.machineIdentity()
                .claim(GTTeamsCompat.getTeam(player));
            assembler.setFacing(ForgeDirection.NORTH);
            constructAndRecord(assembler);
            place(4, 1, 3, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0);
            place(9, 1, 3, GalaxiaBlocksEnum.GANTRY_TERMINAL.get(), 0);
            for (int dx = 5; dx <= 8; dx++) place(dx, 1, 3, GalaxiaBlocksEnum.GANTRY.get(), 0);
            place(40, 0, 0, GalaxiaBlocksEnum.SILO_CONTROLLER.get(), 0);
            target = tile(40, 0, 0);
            claim(target);
            target.setFacing(ForgeDirection.NORTH);
            constructAndRecord(target);
            placeMachine(2, -1, 0, ItemList.Hatch_Input_IV.get(1));
            placeMachine(-2, -1, 0, ItemList.Hatch_Input_Bus_LV.get(1));
            placeMachine(41, -1, 0, ItemList.Hatch_Output_Bus_LV.get(1));
            placeMachine(11, 2, 6, ItemList.Hatch_Input_Bus_LV.get(1));
            silo.markStructureDirty();
            target.markStructureDirty();
            assembler.markStructureDirty();
        }

        private void assertFormed() {
            if (!silo.isStructureValid() || !target.isStructureValid() || !assembler.isStructureValid())
                throw new AssertionError("A rocket multiblock did not form");
            if (silo.getHatches()
                .inputHatchCount() != 1
                || silo.getHatches()
                    .inputBusCount() != 1
                || target.getHatches()
                    .outputBusCount() != 1
                || assembler.getHatches()
                    .inputBusCount() != 1)
                throw new AssertionError("Native hatches were not discovered");
            var network = silo.getGantryTerminal()
                .getNetwork();
            if (network.size() != 6 || network.getProviders()
                .size() != 1
                || network.getReceivers()
                    .size() != 1)
                throw new AssertionError("Network did not index the assembler and silo terminals");
        }

        private void constructAndRecord(GalaxiaMultiblockBase<?> multiblock) {
            List<BlockPos> before = new ArrayList<>();
            for (int dx = -6; dx <= 50; dx++) for (int dy = -3; dy <= 13; dy++) for (int dz = -6; dz <= 12; dz++) {
                if (!world.isAirBlock(x + dx, y + dy, z + dz)) before.add(new BlockPos(x + dx, y + dy, z + dz));
            }
            multiblock.construct(null, false);
            for (int dx = -6; dx <= 50; dx++) for (int dy = -3; dy <= 13; dy++) for (int dz = -6; dz <= 12; dz++) {
                BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                if (!world.isAirBlock(pos.x(), pos.y(), pos.z()) && !before.contains(pos) && !placed.contains(pos))
                    placed.add(pos);
            }
        }

        private void place(int dx, int dy, int dz, Block block, int meta) {
            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlock(pos.x(), pos.y(), pos.z(), block, meta, 3);
        }

        private void placeMachine(int dx, int dy, int dz, ItemStack machine) {
            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlockToAir(pos.x(), pos.y(), pos.z());
            ((ItemMachines) machine.getItem()).placeBlockAt(
                machine,
                player,
                world,
                pos.x(),
                pos.y(),
                pos.z(),
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                machine.getItemDamage());
        }

        @SuppressWarnings("unchecked")
        private <T> T tile(int dx, int dy, int dz) {
            return (T) world.getTileEntity(x + dx, y + dy, z + dz);
        }

        private IGregTechTileEntity machine(int dx, int dy, int dz) {
            return tile(dx, dy, dz);
        }
    }
}
