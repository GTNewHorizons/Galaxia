package com.gtnewhorizons.galaxia.gametests;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiFactories;
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

        void build(GameTestHelper helper) {
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
    }
}
