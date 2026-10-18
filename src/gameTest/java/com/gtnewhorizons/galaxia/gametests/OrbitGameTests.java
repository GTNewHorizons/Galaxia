package com.gtnewhorizons.galaxia.gametests;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.client.gui.GuiPlanetTeleporter;
import com.gtnewhorizons.galaxia.compat.gt.OrbitMachineRules;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionTeleport;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class OrbitGameTests {

    private OrbitGameTests() {}

    /**
     * Product contract: every planet has an orbit above it, a weightless vacuum with the planet below, and stations
     * form there as part of that planet.
     */
    @GameTest(timeoutTicks = 1200)
    public static void stationFormsInWeightlessOrbit(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(400)
            .server("fly up into the Overworld orbit", () -> fixture.enter(helper))
            .awaitClient("the client is in orbit", c -> {
                if (Minecraft.getMinecraft().theWorld.provider.dimensionId != OrbitTestSupport.overworldOrbit()) {
                    throw new AssertionError("Client is not in orbit yet");
                }
            })
            .server("orbits are weightless vacuum", fixture::assertVacuum)
            .server("look down at the planet", () -> fixture.look(90))
            .awaitClient("looking down", c -> fixture.assertPitch(90))
            .capture("orbit-planet-below")
            .server("look down at an angle", () -> fixture.look(35))
            .awaitClient("looking down at an angle", c -> fixture.assertPitch(35))
            .capture("orbit-planet-ahead")
            .client("see far like a player", c -> {
                var settings = Minecraft.getMinecraft().gameSettings;
                int distance = settings.renderDistanceChunks;
                settings.renderDistanceChunks = 16;
                c.afterTest(() -> settings.renderDistanceChunks = distance);
            })
            .server("climb high above the station", () -> fixture.climb(250, 90))
            .awaitClient("high up and looking down", c -> fixture.assertCamera(250, 90))
            .capture("orbit-planet-below-high")
            .server("look down at an angle from up high", () -> fixture.climb(250, 35))
            .awaitClient("high up and looking at an angle", c -> fixture.assertCamera(250, 35))
            .capture("orbit-planet-ahead-high")
            .server("the sun stands low in the west", () -> {
                World planet = helper.getWorld();
                long time = planet.getWorldTime();
                helper.afterTest(() -> planet.setWorldTime(time));
                planet.setWorldTime(5000);
                fixture.climb(250, 0);
                fixture.turn(90, 0);
            })
            .awaitClient("facing the low sun", c -> {
                fixture.assertCamera(250, 0);
                assertClientTime(5000);
            })
            .capture("orbit-sun-low")
            .server(
                "the sun sets",
                () -> helper.getWorld()
                    .setWorldTime(6400))
            .awaitClient("the sun sinks through the glow of the planet", c -> assertClientTime(6400))
            .capture("orbit-sunset")
            .server("the sun is behind the planet", () -> {
                helper.getWorld()
                    .setWorldTime(12000);
                fixture.turn(0, 45);
            })
            .awaitClient("facing the sun below", c -> {
                fixture.assertPitch(45);
                assertClientTime(12000);
            })
            .capture("orbit-sun-behind-planet")
            .server("back down to the station", () -> fixture.climb(fixture.y + 9, -10))
            .server("look along the horizon", () -> fixture.look(-10))
            .awaitClient("looking ahead", c -> fixture.assertPitch(-10))
            .capture("orbit-horizon")
            .server("build a sealed room", fixture::buildRoom)
            .awaitServer("the room formed", fixture::assertRoomFormed)
            .server("the room is a station of the Overworld", fixture::assertStation)
            .succeed();
    }

    /**
     * Product contract: in orbit a multiblock that pollutes forms but never starts, whatever the pollution config says,
     * while clean multiblocks run as usual and the same polluting machine works on a planet.
     */
    @GameTest(timeoutTicks = 600)
    public static void pollutingMultiblocksStopInOrbit(GameTestHelper helper) {
        Fixture fixture = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(300)
            .server("fly up into the Overworld orbit", () -> fixture.enter(helper))
            .server("a blast furnace and a vacuum freezer in orbit", () -> {
                fixture.placeMachine(0, 0, 0, ItemList.Machine_Multi_BlastFurnace.get(1));
                fixture.placeMachine(4, 0, 0, ItemList.Machine_Multi_VacuumFreezer.get(1));
            })
            .server("only the polluting furnace is held back", () -> {
                MTEMultiBlockBase furnace = fixture.multiblock(fixture.world, 0, 0, 0);
                MTEMultiBlockBase freezer = fixture.multiblock(fixture.world, 4, 0, 0);
                if (!OrbitMachineRules.isExhaustBlocked(furnace)) throw new AssertionError("Furnace may run in orbit");
                if (OrbitMachineRules.isExhaustBlocked(freezer)) throw new AssertionError("Freezer is held back");
                if (checkRecipe(furnace)) throw new AssertionError("Furnace started a recipe in orbit");
                if (furnace.getCheckRecipeResult() != OrbitMachineRules.NO_EXHAUST_IN_ORBIT) {
                    throw new AssertionError("Furnace shows " + furnace.getCheckRecipeResult());
                }
            })
            .server("the same furnace on the planet", () -> {
                World planet = helper.getWorld();
                fixture.placeMachineIn(planet, 0, 200, 0, ItemList.Machine_Multi_BlastFurnace.get(1));
                if (OrbitMachineRules.isExhaustBlocked(fixture.multiblock(planet, 0, 200, 0))) {
                    throw new AssertionError("Furnace is held back on the planet");
                }
            })
            .succeed();
    }

    /**
     * Product contract: the teleporter lists each planet with its orbit beside it, and moves the player within the
     * dimension they are already in as well as between dimensions.
     */
    @GameTest(timeoutTicks = 800)
    public static void teleporterPairsPlanetsWithOrbits(GameTestHelper helper) {
        EntityPlayerMP player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
        double oldX = player.posX, oldY = player.posY, oldZ = player.posZ;
        int home = player.dimension;
        helper.afterTest(() -> {
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
            OrbitTestSupport.moveTo(player, home, oldX, oldY, oldZ);
        });
        double targetX = Math.floor(oldX) + 40.5, targetZ = Math.floor(oldZ) + 0.5;
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(300)
            .server("each orbit follows its planet", () -> {
                List<DimensionTeleport.Destination> destinations = DimensionTeleport.destinations();
                for (int i = 0; i < destinations.size(); i++) {
                    DimensionTeleport.Destination destination = destinations.get(i);
                    if (!destination.orbit()) continue;
                    int surface = OrbitDimensions.byDimension(destination.dimension())
                        .orElseThrow()
                        .surface()
                        .getId();
                    if (i == 0 || destinations.get(i - 1)
                        .dimension() != surface) {
                        throw new AssertionError(destination.name() + " does not follow its planet");
                    }
                }
            })
            .server("morning on the planet", () -> {
                World planet = helper.getWorld();
                long time = planet.getWorldTime();
                helper.afterTest(() -> planet.setWorldTime(time));
                planet.setWorldTime(1000);
                player.playerNetServerHandler.setPlayerLocation(oldX, oldY + 60, oldZ, -90, -14);
            })
            .awaitClient("facing the morning sun", c -> {
                assertClientTime(1000);
                if (Math.abs(Minecraft.getMinecraft().thePlayer.rotationPitch + 14) > 1) {
                    throw new AssertionError("Camera has not turned yet");
                }
            })
            .capture("overworld-morning-sun")
            .client(
                "open the teleporter",
                c -> Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiPlanetTeleporter()))
            .awaitClient("teleporter open", c -> {
                if (!(Minecraft.getMinecraft().currentScreen instanceof GuiPlanetTeleporter)) {
                    throw new AssertionError("Teleporter is not open");
                }
            })
            .capture("planet-teleporter")
            .client("teleport within the dimension", c -> {
                Minecraft.getMinecraft()
                    .displayGuiScreen(null);
                Galaxia.GALAXIA_NETWORK.sendToServer(new TeleportRequestPacket(home, targetX, 120, targetZ));
            })
            .awaitClient("moved within the dimension", c -> {
                var self = Minecraft.getMinecraft().thePlayer;
                if (Math.abs(self.posX - targetX) > 0.5 || Math.abs(self.posZ - targetZ) > 0.5) {
                    throw new AssertionError("Player stayed at " + self.posX + ", " + self.posZ);
                }
            })
            .client(
                "teleport into the Moon orbit",
                c -> Galaxia.GALAXIA_NETWORK.sendToServer(new TeleportRequestPacket(moonOrbit(), 5.5, 110, 5.5)))
            .awaitClient("arrived in orbit", c -> {
                var self = Minecraft.getMinecraft().thePlayer;
                if (self.dimension != moonOrbit() || Math.abs(self.posX - 5.5) > 0.5
                    || Math.abs(self.posZ - 5.5) > 0.5) {
                    throw new AssertionError("Player is not at the orbit target yet");
                }
            })
            .server(
                "look down at the Moon",
                () -> player.playerNetServerHandler.setPlayerLocation(player.posX, player.posY, player.posZ, 0, 35))
            .awaitClient("looking down", c -> {
                if (Math.abs(Minecraft.getMinecraft().thePlayer.rotationPitch - 35) > 1) {
                    throw new AssertionError("Camera has not turned yet");
                }
            })
            .capture("moon-orbit-below")
            .succeed();
    }

    private static void assertClientTime(long time) {
        long now = Minecraft.getMinecraft().theWorld.getWorldTime() % 24000;
        if (Math.abs(now - time) > 40) throw new AssertionError("Client time is " + now);
    }

    private static int moonOrbit() {
        return OrbitDimensions.all()
            .stream()
            .filter(orbit -> orbit.surface() == DimensionEnum.MOON)
            .findFirst()
            .orElseThrow(() -> new AssertionError("The Moon has no orbit"))
            .dimensionId();
    }

    /** Runs the recipe check every multiblock goes through */
    private static boolean checkRecipe(MTEMultiBlockBase machine) {
        try {
            Method check = MTEMultiBlockBase.class.getDeclaredMethod("checkRecipe");
            check.setAccessible(true);
            return (boolean) check.invoke(machine);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot run the recipe check", e);
        }
    }

    private static final class Fixture {

        private final List<BlockPos> placed = new ArrayList<>();
        private final List<Runnable> cleanup = new ArrayList<>();
        private World world;
        private EntityPlayerMP player;
        private final int x = 0;
        private final int y = 100;
        private final int z = 0;

        void enter(GameTestHelper helper) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            world = OrbitTestSupport.enterOrbit(helper, player, x + 3.5, y + 9, z - 4.5);
            helper.afterTest(() -> { for (BlockPos pos : placed) world.setBlockToAir(pos.x(), pos.y(), pos.z()); });
        }

        void assertVacuum() {
            DimensionDef def = CelestialDimensionMaterializer.findDefinitionById(OrbitTestSupport.overworldOrbit())
                .orElseThrow(() -> new AssertionError("Orbit has no dimension definition"));
            if (def.gravity() != 0) throw new AssertionError("Orbit gravity is " + def.gravity());
            if (GalaxiaAPI.getGravity(player) != 0) throw new AssertionError("Player feels gravity in orbit");
            if (def.effects()
                .getOxygenPercent(world) != 0
                || def.effects()
                    .getPressure(world) != 0)
                throw new AssertionError("Orbit is not a vacuum");
        }

        void look(float pitch) {
            player.playerNetServerHandler.setPlayerLocation(player.posX, player.posY, player.posZ, 0, pitch);
        }

        void turn(float yaw, float pitch) {
            player.playerNetServerHandler.setPlayerLocation(player.posX, player.posY, player.posZ, yaw, pitch);
        }

        void climb(double height, float pitch) {
            player.playerNetServerHandler.setPlayerLocation(player.posX, height, player.posZ, 0, pitch);
        }

        void assertCamera(double height, float pitch) {
            assertPitch(pitch);
            if (Math.abs(Minecraft.getMinecraft().thePlayer.boundingBox.minY - height) > 1) {
                throw new AssertionError("Camera has not climbed yet");
            }
        }

        void assertPitch(float pitch) {
            if (Math.abs(Minecraft.getMinecraft().thePlayer.rotationPitch - pitch) > 1) {
                throw new AssertionError("Camera has not turned yet");
            }
        }

        /** A 7x7x7 shell of station blocks with the controller in the north wall */
        void buildRoom() {
            for (int dx = 0; dx <= 6; dx++) for (int dy = 0; dy <= 6; dy++) for (int dz = 0; dz <= 6; dz++) {
                if (dx == 0 || dx == 6 || dy == 0 || dy == 6 || dz == 0 || dz == 6) {
                    place(dx, dy, dz, GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get());
                }
            }
            place(3, 3, 0, GalaxiaBlocksEnum.STATION_CONTROLLER.get());
            TileStation room = room();
            room.setOwner(GTTeamsCompat.getTeam(player));
            room.setFacing(ForgeDirection.SOUTH);
        }

        void assertRoomFormed() {
            TileStation room = room();
            room.markStructureDirty();
            if (!room.isStructureValid()) throw new AssertionError("Station room did not form in orbit");
        }

        void assertStation() {
            Station station = room().getBackingStation();
            if (station == null) throw new AssertionError("No station asset for the room");
            if (!station.celestialObjectKey.equals(CelestialObjectKey.registered(CelestialObjectId.OVERWORLD))) {
                throw new AssertionError("Station belongs to " + station.celestialObjectKey);
            }
        }

        void placeMachine(int dx, int dy, int dz, ItemStack machine) {
            placeMachineIn(world, x + dx, y + dy, z + dz, machine);
            placed.add(new BlockPos(x + dx, y + dy, z + dz));
        }

        void placeMachineIn(World target, int px, int py, int pz, ItemStack machine) {
            target.setBlockToAir(px, py, pz);
            ((ItemMachines) machine.getItem()).placeBlockAt(
                machine,
                player,
                target,
                px,
                py,
                pz,
                ForgeDirection.UP.ordinal(),
                0.5F,
                0.5F,
                0.5F,
                machine.getItemDamage());
            if (target != world) cleanup.add(() -> target.setBlockToAir(px, py, pz));
        }

        MTEMultiBlockBase multiblock(World target, int dx, int dy, int dz) {
            int px = target == world ? x + dx : dx, py = target == world ? y + dy : dy,
                pz = target == world ? z + dz : dz;
            if (!(target.getTileEntity(px, py, pz) instanceof IGregTechTileEntity gt)
                || !(gt.getMetaTileEntity() instanceof MTEMultiBlockBase multi)) {
                throw new AssertionError("No multiblock controller at " + px + ", " + py + ", " + pz);
            }
            return multi;
        }

        TileStation room() {
            return (TileStation) world.getTileEntity(x + 3, y + 3, z);
        }

        void place(int dx, int dy, int dz, Block block) {
            BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
            if (!placed.contains(pos)) placed.add(pos);
            world.setBlock(pos.x(), pos.y(), pos.z(), block, 0, 3);
        }
    }
}
