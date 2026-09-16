package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.special.BlockAirlockDoor;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileEntityAirlock;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class AirlockGuiGameTests {

    private AirlockGuiGameTests() {}

    /** Product contract: rotating the slab keeps the door face on its broad surfaces and metal on its edges. */
    @GameTest(timeoutTicks = 600)
    public static void rotatedDoorsUseSeparateEdgeTexture(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("display door panels on all three axes", () -> {
                fixture.prepare(helper);
                for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
                    fixture.place(
                        a,
                        3 + b,
                        8,
                        GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                        BlockAirlockDoor.encodeMeta(false, BlockAirlockDoor.ORIENT_Z));
                    fixture.place(
                        6,
                        3 + a,
                        7 + b,
                        GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                        BlockAirlockDoor.encodeMeta(false, BlockAirlockDoor.ORIENT_X));
                    fixture.place(
                        10 + a,
                        4,
                        7 + b,
                        GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                        BlockAirlockDoor.encodeMeta(false, BlockAirlockDoor.ORIENT_Y));
                }
                fixture.place(16, 10, 0, GalaxiaBlocksEnum.AIRLOCK_CASING.get(), 0);
                fixture.player.playerNetServerHandler.setPlayerLocation(fixture.x + 16.5, 139, fixture.z + 0.5, 50, 30);
            })
            .awaitClient("door display reaches the client", c -> {
                if (Minecraft.getMinecraft().theWorld.getBlock(fixture.x + 6, 132, fixture.z + 8)
                    != GalaxiaBlocksEnum.AIRLOCK_DOOR.get()) {
                    throw new AssertionError("Door display is not loaded");
                }
            })
            .client("show the texture comparison without HUD or clouds", c -> {
                var settings = Minecraft.getMinecraft().gameSettings;
                boolean oldHud = settings.hideGUI;
                boolean oldClouds = settings.clouds;
                c.afterTest(() -> {
                    settings.hideGUI = oldHud;
                    settings.clouds = oldClouds;
                });
                settings.hideGUI = true;
                settings.clouds = false;
            })
            .capture("airlock-axis-textures")
            .client("only the broad faces use the connected door texture", c -> {
                Block door = GalaxiaBlocksEnum.AIRLOCK_DOOR.get();
                for (ForgeDirection axis : new ForgeDirection[] { ForgeDirection.UP, ForgeDirection.NORTH,
                    ForgeDirection.EAST }) {
                    for (boolean open : new boolean[] { false, true }) {
                        int meta = BlockAirlockDoor.encodeMeta(open, BlockAirlockDoor.orientationForAxis(axis));
                        var front = door.getIcon(axis.ordinal(), meta);
                        if (front == null || front != door.getIcon(
                            axis.getOpposite()
                                .ordinal(),
                            meta)) {
                            throw new AssertionError("Opposite broad faces must share the door texture");
                        }
                        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                            if (side != axis && side != axis.getOpposite()
                                && door.getIcon(side.ordinal(), meta) == front) {
                                throw new AssertionError("A thin edge is incorrectly using the connected door texture");
                            }
                        }
                    }
                }
            })
            .succeed();
    }

    /** Bug regression: the controller's client-visible state follows opening and closing without reopening a GUI. */
    @GameTest(timeoutTicks = 600)
    public static void controllerStateTracksDoorTransitions(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build rooms", () -> fixture.build(helper))
            .awaitServer("rooms ready", fixture::assertRoomsReady)
            .awaitClient("controller reaches the client", c -> fixture.assertClientOpen(false))
            .server("open with redstone", () -> fixture.power(true))
            .awaitServer("door opens on the server", () -> fixture.assertOpen(true))
            .awaitClient("controller shows the open state", c -> fixture.assertClientOpen(true))
            .server("close with redstone", () -> fixture.power(false))
            .awaitServer("door closes on the server", () -> fixture.assertOpen(false))
            .awaitClient("controller shows the closed state", c -> fixture.assertClientOpen(false))
            .succeed();
    }

    /** Bug regression: ordinary door items build the orientation selected by the airlock, not the player's facing. */
    @GameTest(timeoutTicks = 400)
    public static void survivalConstructionUsesNormalDoorItems(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("construct doors in each orientation using normal inventory items", () -> {
                fixture.prepare(helper);
                for (ForgeDirection facing : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.EAST,
                    ForgeDirection.UP }) {
                    fixture.prepareConstruction(facing);
                    int built = fixture.lock()
                        .survivalConstruct(null, 64, fixture.buildEnvironment());
                    if (built <= 0) throw new AssertionError("No construction progress with normal materials");
                    fixture.lock()
                        .updateEntity();
                    if (!fixture.lock()
                        .isStructureValid()) {
                        throw new AssertionError("Survival construction did not complete for " + facing);
                    }
                    fixture.clearBlocks();
                }
            })
            .succeed();
    }

    /** Bug regression: rebuilding one missing panel skips the correct panels and consumes only its replacement. */
    @GameTest(timeoutTicks = 400)
    public static void survivalConstructionResumesPastCorrectDoors(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("repair a missing panel without replacing correct doors", () -> {
                fixture.prepare(helper);
                fixture.prepareConstruction(ForgeDirection.NORTH);
                fixture.lock()
                    .construct(null, false);
                BlockPos missing = fixture.placed.stream()
                    .filter(
                        pos -> fixture.world.getBlock(pos.x(), pos.y(), pos.z())
                            == GalaxiaBlocksEnum.AIRLOCK_DOOR.get())
                    .reduce((first, second) -> second)
                    .orElseThrow();
                fixture.world.setBlockToAir(missing.x(), missing.y(), missing.z());
                InventoryBasic materials = fixture.materials();
                int built = fixture.lock()
                    .survivalConstruct(
                        null,
                        64,
                        ISurvivalBuildEnvironment.create(IItemSource.fromInventory(materials), fixture.player));
                if (built != 1 || materials.getStackInSlot(1).stackSize != 63) {
                    throw new AssertionError("Repair must replace exactly one door using one normal door item");
                }
                fixture.lock()
                    .updateEntity();
                if (!fixture.lock()
                    .isStructureValid()) throw new AssertionError("Repaired airlock did not form");
            })
            .succeed();
    }

    /** Bug regression: a valid one-block-wide doorway supports proximity opening without a ticking-tile crash. */
    @GameTest(timeoutTicks = 400)
    public static void narrowDoorwaySupportsProximityOpening(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build a narrow standalone airlock", () -> {
                fixture.prepare(helper);
                for (int dx = 0; dx <= 2; dx++) for (int dy = 3; dy <= 6; dy++) {
                    boolean frame = dx == 0 || dx == 2 || dy == 3 || dy == 6;
                    fixture.place(
                        dx,
                        dy,
                        6,
                        frame ? GalaxiaBlocksEnum.AIRLOCK_CASING.get() : GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                        frame ? 0 : BlockAirlockDoor.encodeMeta(false, BlockAirlockDoor.ORIENT_Z));
                }
                fixture.place(1, 3, 6, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0);
                fixture.lock()
                    .setFacing(ForgeDirection.NORTH);
                fixture.lock()
                    .updateEntity();
                if (!fixture.lock()
                    .isStructureValid()) throw new AssertionError("Narrow doorway did not form");
                fixture.player.setPositionAndUpdate(fixture.x + 1.5, 132, fixture.z + 5.5);
            })
            .server("open safely when a player approaches", () -> {
                TileEntityAirlock lock = fixture.lock();
                try {
                    lock.setProximityOpening(true);
                    lock.updateEntity();
                    if (!lock.isOpen()) throw new AssertionError("Narrow doorway did not detect the player");
                } finally {
                    // A regression must fail this scenario without crashing subsequent world ticks.
                    lock.setProximityOpening(false);
                }
            })
            .succeed();
    }

    /** Product contract: a room breach overrides sustained redstone and repairs restore automatic opening. */
    @GameTest(timeoutTicks = 1200)
    public static void autoSealClosesPoweredDoorAndRecovers(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build two rooms and their shared airlock", () -> fixture.build(helper))
            .awaitServer("both rooms are linked and sealed", fixture::assertRoomsReady)
            .server("power the airlock", () -> fixture.power(true))
            .awaitServer("safe powered door opens", () -> fixture.assertOpen(true))
            .server("breach the second room", () -> fixture.breach(true))
            .awaitServer("auto-seal closes despite sustained power", () -> fixture.assertOpen(false))
            .server("begin sustained safety check", fixture::beginSafetyCheck)
            .awaitServer("door stays closed without oscillating", () -> fixture.assertStableDoor(false))
            .server(
                "disable Auto-seal",
                () -> fixture.lock()
                    .setAutoSealOnLeak(false))
            .awaitServer("disabled Auto-seal permits redstone opening", () -> fixture.assertOpen(true))
            .server(
                "enable Auto-seal again",
                () -> fixture.lock()
                    .setAutoSealOnLeak(true))
            .awaitServer("enabled Auto-seal closes the unsafe door", () -> fixture.assertOpen(false))
            .server("repair the second room", () -> fixture.breach(false))
            .awaitServer("repaired rooms are linked and sealed", fixture::assertRoomsReady)
            .awaitServer("sustained power opens the safe door again", () -> fixture.assertOpen(true))
            .succeed();
    }

    /** Product contract: deliberate manual opening warns the player and protection resumes after closing. */
    @GameTest(timeoutTicks = 1200)
    public static void manualOverrideWarnsAndResets(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        WarningListener warning = new WarningListener();
        ClientTest.scenario(helper)
            .server("build rooms", () -> fixture.build(helper))
            .awaitServer("rooms ready", fixture::assertRoomsReady)
            .client("listen for the player warning", c -> {
                MinecraftForge.EVENT_BUS.register(warning);
                c.afterTest(() -> MinecraftForge.EVENT_BUS.unregister(warning));
            })
            .server("breach one room and apply power", () -> {
                fixture.breach(true);
                fixture.power(true);
            })
            .awaitServer("room breach is confirmed", fixture::assertBreached)
            .server("manually open the door", fixture::clickDoor)
            .awaitServer("manual opening overrides Auto-seal", () -> fixture.assertOpen(true))
            .awaitClient(
                "player receives the depressurization warning",
                c -> {
                    if (!warning.received) throw new AssertionError("No depressurization warning reached the player");
                })
            .server("begin override stability check", fixture::beginSafetyCheck)
            .awaitServer("Auto-seal does not fight the manual opening", () -> fixture.assertStableDoor(true))
            .server("manually close the door", fixture::clickDoor)
            .awaitServer("manual closing restores protection", () -> fixture.assertOpen(false))
            .server("begin restored protection check", fixture::beginSafetyCheck)
            .awaitServer("redstone cannot reopen into vacuum", () -> fixture.assertStableDoor(false))
            .succeed();
    }

    /** Product contract: proximity automation cannot reopen an airlock while the adjacent room is breached. */
    @GameTest(timeoutTicks = 1200)
    public static void proximityCannotReopenIntoVacuum(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build rooms", () -> fixture.build(helper))
            .awaitServer("rooms ready", fixture::assertRoomsReady)
            .server("approach the door with proximity opening enabled", () -> {
                fixture.lock()
                    .setProximityOpening(true);
                fixture.player.setPositionAndUpdate(fixture.x + 3.5, 129, fixture.z + 5.0);
            })
            .awaitServer("safe proximity opening works", () -> fixture.assertOpen(true))
            .server("breach the second room", () -> fixture.breach(true))
            .awaitServer("Auto-seal closes near the player", () -> fixture.assertOpen(false))
            .server("begin proximity safety check", fixture::beginSafetyCheck)
            .awaitServer("proximity cannot reopen into vacuum", () -> fixture.assertStableDoor(false))
            .succeed();
    }

    public static final class WarningListener {

        private boolean received;

        @SubscribeEvent
        public void chat(ClientChatReceivedEvent event) {
            if (event.message.getUnformattedText()
                .equals(StatCollector.translateToLocal("galaxia.airlock.warning.depressurization"))) received = true;
        }
    }

    /** Bug regression: Refresh must recheck the repaired structure on the server, not just reopen its screen. */
    @GameTest(timeoutTicks = 1200)
    public static void refreshRechecksRepairedAirlock(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build rooms", () -> fixture.build(helper))
            .awaitServer("rooms ready", fixture::assertRoomsReady)
            .server("open the airlock screen", fixture::openScreen)
            .awaitClient("screen ready", c -> GuiTestSupport.requireScreen("galaxia:airlock_controller"))
            .server("repair the airlock after a failed structure check", fixture::repairFrameAfterCheck)
            .click(GuiTestSupport.control("airlock.refresh"))
            .withinTicks(20)
            .awaitServer(
                "Refresh forms the repaired airlock immediately",
                () -> {
                    if (!fixture.lock()
                        .isStructureValid()) throw new AssertionError("Refresh did not recheck the structure");
                })
            .succeed();
    }

    /** Bug regression: a replacement airlock must rediscover rooms that remain structurally valid. */
    @GameTest(timeoutTicks = 1200)
    public static void replacementAirlockRecoversRoomConnections(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .server("build rooms", () -> fixture.build(helper))
            .awaitServer("rooms ready", fixture::assertRoomsReady)
            .server("replace the controller without dismantling either room", () -> {
                fixture.place(1, 3, 6, Blocks.air, 0);
                fixture.place(1, 3, 6, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0);
                fixture.lock()
                    .setFacing(ForgeDirection.NORTH);
                fixture.lock()
                    .updateEntity();
                if (!fixture.lock()
                    .isStructureValid()) throw new AssertionError("Replacement airlock did not form");
                fixture.room(0)
                    .markStructureDirty();
                fixture.room(12)
                    .markStructureDirty();
            })
            .awaitServer("valid rooms register with the replacement", fixture::assertRoomsReady)
            .succeed();
    }

    private static final class Fixture {

        private final List<BlockPos> placed = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private int x;
        private int z;
        private long safetyCheckEnd;
        private boolean unexpectedDoorState;

        void prepare(GameTestHelper helper) {
            world = helper.getWorld();
            player = (EntityPlayerMP) world.playerEntities.get(0);
            x = (int) player.posX + 32;
            z = (int) player.posZ;
            double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
            helper.afterTest(() -> {
                player.closeScreen();
                for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z());
                player.setPositionAndUpdate(oldX, oldY, oldZ);
            });
        }

        void build(GameTestHelper helper) {
            prepare(helper);
            for (int dx = 0; dx <= 6; dx++) for (int dy = 0; dy <= 6; dy++) for (int dz = 0; dz <= 12; dz++) {
                if (!world.isAirBlock(x + dx, 128 + dy, z + dz)) {
                    throw new AssertionError("Fixture space is occupied");
                }
                if (dx == 0 || dx == 6 || dy == 0 || dy == 6 || dz == 0 || dz == 6 || dz == 12) {
                    place(dx, dy, dz, GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(), 0);
                }
            }
            for (int dx = 1; dx <= 5; dx++) for (int dy = 1; dy <= 5; dy++) {
                boolean frame = dx == 1 || dx == 5 || dy == 1 || dy == 5;
                place(
                    dx,
                    dy,
                    6,
                    frame ? GalaxiaBlocksEnum.AIRLOCK_CASING.get() : GalaxiaBlocksEnum.AIRLOCK_DOOR.get(),
                    frame ? 0 : BlockAirlockDoor.encodeMeta(false, BlockAirlockDoor.ORIENT_Z));
            }
            place(1, 3, 6, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0);
            lock().setFacing(ForgeDirection.NORTH);
            for (int dz : new int[] { 0, 12 }) {
                place(3, 3, dz, GalaxiaBlocksEnum.STATION_CONTROLLER.get(), 0);
                TileStation room = room(dz);
                room.setOwner(GTTeamsCompat.getTeam(player));
                room.setFacing(dz == 0 ? ForgeDirection.SOUTH : ForgeDirection.NORTH);
            }
        }

        void place(int dx, int dy, int dz, Block block, int meta) {
            BlockPos pos = new BlockPos(x + dx, 128 + dy, z + dz);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlock(pos.x(), pos.y(), pos.z(), block, meta, 3);
        }

        void clearBlocks() {
            for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z());
            placed.clear();
        }

        void prepareConstruction(ForgeDirection facing) {
            place(1, 3, 6, GalaxiaBlocksEnum.AIRLOCK_CONTROLLER.get(), 0);
            lock().setFacing(facing);
            TileEntityAirlock.STRUCTURE_DEFINITION.iterate(
                TileEntityAirlock.STRUCTURE_PIECE_MAIN,
                world,
                lock().getCurrentFacing(),
                x + 1,
                131,
                z + 6,
                TileEntityAirlock.CONTROLLER_OFFSET_X,
                TileEntityAirlock.CONTROLLER_OFFSET_Y,
                TileEntityAirlock.CONTROLLER_OFFSET_Z,
                (_, _, bx, by, bz, _, _, _) -> {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!placed.contains(pos)) {
                        if (!world.isAirBlock(bx, by, bz)) throw new AssertionError("Construction space is occupied");
                        placed.add(pos);
                    }
                    return true;
                });
        }

        InventoryBasic materials() {
            InventoryBasic inventory = new InventoryBasic("Airlock materials", false, 2);
            inventory.setInventorySlotContents(0, new ItemStack(GalaxiaBlocksEnum.AIRLOCK_CASING.get(), 64));
            inventory.setInventorySlotContents(1, new ItemStack(GalaxiaBlocksEnum.AIRLOCK_DOOR.get(), 64));
            return inventory;
        }

        ISurvivalBuildEnvironment buildEnvironment() {
            return ISurvivalBuildEnvironment.create(IItemSource.fromInventory(materials()), player);
        }

        TileEntityAirlock lock() {
            return (TileEntityAirlock) world.getTileEntity(x + 1, 131, z + 6);
        }

        TileStation room(int dz) {
            return (TileStation) world.getTileEntity(x + 3, 131, z + dz);
        }

        void assertRoomsReady() {
            if (!lock().isStructureValid() || lock().getStationControllers()
                .size() != 2
                || !room(0).isStructureValid()
                || !room(12).isStructureValid()
                || !room(0).isSealed()
                || !room(12).isSealed()) {
                throw new AssertionError("Expected two sealed rooms linked to a formed airlock");
            }
        }

        void power(boolean powered) {
            place(1, 3, 7, powered ? Blocks.redstone_block : Blocks.air, 0);
        }

        void openScreen() {
            player.setPositionAndUpdate(x + 2.5, 129, z + 4.5);
            GuiFactories.tileEntity()
                .open(player, x + 1, 131, z + 6);
        }

        void repairFrameAfterCheck() {
            place(5, 3, 6, Blocks.air, 0);
            lock().markStructureDirty();
            lock().updateEntity();
            if (lock().isStructureValid()) throw new AssertionError("Broken frame was accepted");
            place(5, 3, 6, GalaxiaBlocksEnum.AIRLOCK_CASING.get(), 0);
        }

        void breach(boolean breached) {
            place(5, 3, 12, breached ? Blocks.air : GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(), 0);
            room(12).markStructureDirty();
        }

        void assertBreached() {
            if (room(12).isStructureValid() || !lock().isExternalConnection()) {
                throw new AssertionError("Room breach has not been processed");
            }
            assertOpen(false);
        }

        void clickDoor() {
            GalaxiaBlocksEnum.AIRLOCK_DOOR.get()
                .onBlockActivated(world, x + 3, 131, z + 6, player, ForgeDirection.NORTH.ordinal(), 0.5F, 0.5F, 0.5F);
        }

        void beginSafetyCheck() {
            safetyCheckEnd = world.getTotalWorldTime() + 25;
            unexpectedDoorState = false;
        }

        void assertStableDoor(boolean expected) {
            if (lock().isOpen() != expected) unexpectedDoorState = true;
            if (unexpectedDoorState) throw new AssertionError("Door changed state during the safety check");
            assertOpen(expected);
            if (world.getTotalWorldTime() < safetyCheckEnd) throw new AssertionError("Still checking door stability");
        }

        void assertOpen(boolean expected) {
            if (lock().isOpen() != expected) throw new AssertionError("Expected door open=" + expected);
            BlockAirlockDoor door = (BlockAirlockDoor) GalaxiaBlocksEnum.AIRLOCK_DOOR.get();
            if (door.isOpen(world, x + 3, 131, z + 6) != expected) {
                throw new AssertionError("Door blocks disagree with the controller");
            }
        }

        void assertClientOpen(boolean expected) {
            if (!(Minecraft.getMinecraft().theWorld.getTileEntity(x + 1, 131, z + 6) instanceof TileEntityAirlock lock)
                || !lock.isStructureValid()
                || lock.isOpen() != expected) {
                throw new AssertionError("Client controller must display open=" + expected);
            }
        }
    }
}
