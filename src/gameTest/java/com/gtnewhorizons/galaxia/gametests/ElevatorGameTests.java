package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.gt.elevator.MTEGalaxiaSpaceElevator;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.TileElevatorDock;
import com.gtnewhorizons.galaxia.registry.block.special.TileStationItemVault;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class ElevatorGameTests {

    private ElevatorGameTests() {}

    /**
     * Product contract: a space elevator on a planet sends what is put into its input bus up to the dock straight
     * above it in that planet's orbit, and carries players up there. A dock inside a station room unloads into the
     * station's storage.
     */
    @GameTest(timeoutTicks = 2400)
    public static void elevatorDeliversToTheDockInOrbit(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("build the elevator with an input bus", () -> fixture.build(helper))
            .awaitServer("the elevator formed", fixture::assertFormed)
            .server("put a dock in orbit above it", fixture::placeDock)
            .server(
                "load the input bus",
                () -> fixture.bus()
                    .setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 32)))
            .awaitServer("the cobblestone reached the dock", fixture::assertDelivered)
            .server("turn the elevator off", fixture::turnOff)
            .awaitServer("the dock stays connected to the turned off elevator", fixture::assertStillLinked)
            .server(
                "turn the elevator on",
                () -> fixture.elevator()
                    .getBaseMetaTileEntity()
                    .enableWorking())
            .server(
                "look at the elevator",
                () -> fixture.player.playerNetServerHandler
                    .setPlayerLocation(fixture.x + 0.5, fixture.y + 30, fixture.z - 70.5, 0, 0))
            .awaitClient("camera at the elevator", c -> {
                if (Math.abs(Minecraft.getMinecraft().thePlayer.posZ - (fixture.z - 70.5)) > 1) {
                    throw new AssertionError("Camera has not moved yet");
                }
            })
            .capture("elevator-cable")
            .server(
                "ride up to the dock",
                () -> fixture.elevator()
                    .rideToDock(fixture.player))
            .awaitClient("arrived at the dock in orbit", c -> {
                if (Minecraft.getMinecraft().theWorld.provider.dimensionId != OrbitTestSupport.overworldOrbit()) {
                    throw new AssertionError("Player is not in orbit yet");
                }
            })
            .server(
                "open the dock",
                () -> GalaxiaBlocksEnum.ELEVATOR_DOCK.get()
                    .onBlockActivated(
                        fixture.orbit,
                        fixture.dockX,
                        fixture.dockY,
                        fixture.dockZ,
                        fixture.player,
                        1,
                        0.5f,
                        0.5f,
                        0.5f))
            .awaitClient("dock screen open", c -> GuiTestSupport.requireScreen("galaxia:elevator_dock"))
            .capture("elevator-dock-gui")
            .server("close the dock", () -> fixture.player.closeScreen())
            .server(
                "look up at the dock from the side",
                () -> fixture.player.playerNetServerHandler
                    .setPlayerLocation(fixture.dockX + 0.5, fixture.dockY - 6, fixture.dockZ - 12.5, 0, -20))
            .awaitClient("the linked dock in view", c -> {
                World client = Minecraft.getMinecraft().theWorld;
                if (Math.abs(Minecraft.getMinecraft().thePlayer.posZ - (fixture.dockZ - 12.5)) > 1) {
                    throw new AssertionError("Camera has not moved yet");
                }
                if (!(client
                    .getTileEntity(fixture.dockX, fixture.dockY, fixture.dockZ) instanceof TileElevatorDock dock)
                    || !dock.isLinked()) {
                    throw new AssertionError("Client does not see the linked dock yet");
                }
            })
            .capture("elevator-dock")
            .server("build a station room with a vault around the dock", fixture::enclose)
            .awaitServer("the dock unloaded into the station", fixture::assertUnloaded)
            .succeed();
    }

    private static final class Fixture {

        private World world;
        private WorldServer orbit;
        private EntityPlayerMP player;
        private int x, y, z;
        private int dockX, dockY, dockZ;
        private final List<BlockPos> roomBlocks = new ArrayList<>();
        private long watchEnd;

        void build(GameTestHelper helper) {
            world = helper.getWorld();
            player = (EntityPlayerMP) world.playerEntities.get(0);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            // A chunk corner keeps the whole elevator in predictable chunks
            x = (((int) player.posX + 200) >> 4 << 4) + 8;
            y = 120;
            z = ((int) player.posZ >> 4 << 4) + 8;
            double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
            int home = player.dimension;
            helper.afterTest(() -> {
                OrbitTestSupport.moveTo(player, home, oldX, oldY, oldZ);
                clear(world, x - 30, y - 45, z - 30, x + 30, y + 5, z + 30);
                if (orbit == null) return;
                orbit.setBlockToAir(dockX, dockY, dockZ);
                for (BlockPos pos : roomBlocks) orbit.setBlockToAir(pos.x(), pos.y(), pos.z());
            });
            player.setPositionAndUpdate(x + 0.5, y + 4, z - 20.5);

            ItemStack controller = new ItemStack(GregTechAPI.sBlockMachines, 1, MTEGalaxiaSpaceElevator.ID);
            ((ItemMachines) controller.getItem()).placeBlockAt(
                controller,
                player,
                world,
                x,
                y,
                z,
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                MTEGalaxiaSpaceElevator.ID);
            elevator().getBaseMetaTileEntity()
                .setFrontFacing(ForgeDirection.NORTH);
            // Three stacked triggers build tier three motors
            elevator().construct(new ItemStack(Blocks.dirt, 3), false);

            // The casings around the controller take hatches
            ItemStack bus = ItemList.Hatch_Input_Bus_LV.get(1);
            world.setBlockToAir(x + 1, y, z);
            ((ItemMachines) bus.getItem()).placeBlockAt(
                bus,
                player,
                world,
                x + 1,
                y,
                z,
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                bus.getItemDamage());
        }

        void assertFormed() {
            MTEGalaxiaSpaceElevator elevator = elevator();
            elevator.checkStructure(true, elevator.getBaseMetaTileEntity());
            if (!elevator.mMachine) throw new AssertionError("Elevator did not form");
            if (elevator.getMotorTier() <= 0) throw new AssertionError("Elevator has no motors");
        }

        void placeDock() {
            orbit = MinecraftServer.getServer()
                .worldServerForDimension(OrbitTestSupport.overworldOrbit());
            // Straight above the cable, which stands well behind the controller
            dockX = elevator().getCable().xCoord;
            dockY = 100;
            dockZ = elevator().getCable().zCoord;
            orbit.theChunkProviderServer.loadChunk(dockX >> 4, dockZ >> 4);
            orbit.setBlock(dockX, dockY, dockZ, GalaxiaBlocksEnum.ELEVATOR_DOCK.get(), 0, 3);
            if (elevator().findDock() == null) throw new AssertionError("Elevator does not see the dock");
        }

        void turnOff() {
            elevator().getBaseMetaTileEntity()
                .disableWorking();
            watchEnd = orbit.getTotalWorldTime() + 160;
        }

        /** Watches the dock longer than a link lasts without a word from the elevator */
        void assertStillLinked() {
            TileElevatorDock dock = (TileElevatorDock) orbit.getTileEntity(dockX, dockY, dockZ);
            if (!dock.isLinked()) throw new AssertionError("The dock lost the turned off elevator");
            if (orbit.getTotalWorldTime() < watchEnd) throw new AssertionError("Still watching the dock");
        }

        /** A room with the dock in its middle and a vault beside it */
        void enclose() {
            int ox = dockX - 3, oy = dockY - 3, oz = dockZ - 3;
            for (int dx = 0; dx <= 6; dx++) for (int dy = 0; dy <= 6; dy++) for (int dz = 0; dz <= 6; dz++) {
                if (dx == 0 || dx == 6 || dy == 0 || dy == 6 || dz == 0 || dz == 6) {
                    roomBlock(ox + dx, oy + dy, oz + dz, GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get());
                }
            }
            roomBlock(dockX + 1, dockY, dockZ, GalaxiaBlocksEnum.STATION_ITEM_VAULT.get());
            roomBlock(dockX, dockY, oz, GalaxiaBlocksEnum.STATION_CONTROLLER.get());
            TileStation room = (TileStation) orbit.getTileEntity(dockX, dockY, oz);
            room.setOwner(GTTeamsCompat.getTeam(player));
            room.setFacing(ForgeDirection.SOUTH);
        }

        void assertUnloaded() {
            TileElevatorDock dock = (TileElevatorDock) orbit.getTileEntity(dockX, dockY, dockZ);
            for (int i = 0; i < dock.getSizeInventory(); i++) {
                if (dock.getStackInSlot(i) != null)
                    throw new AssertionError("Dock still holds " + dock.getStackInSlot(i));
            }
            TileStationItemVault vault = (TileStationItemVault) orbit.getTileEntity(dockX + 1, dockY, dockZ);
            long held = vault.getAmount(ItemStackWrapper.of(new ItemStack(Blocks.cobblestone)));
            if (held != 32) throw new AssertionError("Vault holds " + held + " cobblestone");
        }

        private void roomBlock(int bx, int by, int bz, Block block) {
            roomBlocks.add(new BlockPos(bx, by, bz));
            orbit.setBlock(bx, by, bz, block, 0, 3);
        }

        void assertDelivered() {
            if (!(orbit.getTileEntity(dockX, dockY, dockZ) instanceof TileElevatorDock dock)) {
                throw new AssertionError("Dock is gone");
            }
            int cobble = 0;
            for (int i = 0; i < dock.getSizeInventory(); i++) {
                ItemStack stack = dock.getStackInSlot(i);
                if (stack != null && stack.getItem() == new ItemStack(Blocks.cobblestone).getItem()) {
                    cobble += stack.stackSize;
                }
            }
            if (cobble != 32) throw new AssertionError("Dock holds " + cobble + " cobblestone");
            if (!dock.isLinked()) throw new AssertionError("Dock does not see the elevator");
        }

        MTEGalaxiaSpaceElevator elevator() {
            return (MTEGalaxiaSpaceElevator) ((IGregTechTileEntity) world.getTileEntity(x, y, z)).getMetaTileEntity();
        }

        IGregTechTileEntity bus() {
            return (IGregTechTileEntity) world.getTileEntity(x + 1, y, z);
        }

        private static void clear(World world, int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
                if (!world.isAirBlock(bx, by, bz)) world.setBlock(bx, by, bz, Blocks.air, 0, 2);
            }
        }
    }
}
