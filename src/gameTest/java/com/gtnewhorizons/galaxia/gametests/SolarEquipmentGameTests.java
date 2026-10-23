package com.gtnewhorizons.galaxia.gametests;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.gt5.SolarArrayTracking;
import com.gtnewhorizons.galaxia.gt5.SolarArrays;
import com.gtnewhorizons.galaxia.gt5.SolarEquipment;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;
import com.gtnewhorizons.horizonqa.api.client.ClientTest;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.metatileentity.IConnectable;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.blocks.ItemMachines;

@GameTestHolder(value = "galaxia", clientOnly = true, requiredMods = { "gregtech" })
public final class SolarEquipmentGameTests {

    static {
        GameRegistry.registerTileEntity(Receiver.class, "galaxia_qa_solar_receiver");
    }

    private SolarEquipmentGameTests() {}

    /** Integration contract: actual GT packets follow array geometry, demand, native cable loss and overload. */
    @GameTest(timeoutTicks = 1600)
    public static void solarFactoryLifecycle(GameTestHelper helper) {
        Fixture f = new Fixture();
        ClientTest.scenario(helper)
            .defaultTimeoutTicks(160)
            .server("prepare surface array", () -> f.prepare(helper))
            .awaitServer("surface panel supplies one daylight amp", () -> f.assertOutput(32, ForgeDirection.UP))
            .server("surface night", () -> f.world.setWorldTime(18000))
            .awaitServer("surface night produces nothing", () -> f.assertAvailable(0))
            .server("move to orbital workshop", f::enterOrbit)
            .awaitClient("client arrives in orbit", c -> {
                if (net.minecraft.client.Minecraft.getMinecraft().thePlayer.dimension != f.world.provider.dimensionId)
                    throw new AssertionError("Client not in orbit yet");
            })
            .server("build a wall facing the fixed sun", () -> f.build(true))
            .awaitServer("orbital panel offers two amps and accepts only demand", () -> {
                f.assertOutput(64, ForgeDirection.EAST);
                if (f.source()
                    .acceptedEUPerTick() != 32) throw new AssertionError("Demand was not respected");
            })
            .server("disconnect demand", () -> {
                f.receiver.accept = false;
                f.deadline = f.world.getTotalWorldTime() + 20;
            })
            .awaitServer("unused generation is discarded", () -> {
                if (f.world.getTotalWorldTime() < f.deadline) throw new AssertionError("Waiting without demand");
                if (f.source()
                    .acceptedEUPerTick() != 0
                    || f.base()
                        .getStoredEU() != 0)
                    throw new AssertionError("Unused generation accumulated");
            })
            .server("restore demand without a burst", () -> {
                f.receiver.limit = 1000;
                f.receiver.peakEU = 0;
                f.receiver.accept = true;
            })
            .awaitServer("only current tick power is available", () -> {
                if (f.receiver.peakEU == 0) throw new AssertionError("Waiting for a packet");
                if (f.receiver.peakEU > 64) throw new AssertionError("Stored output burst");
                f.assertAvailable(64);
            })
            .server(
                "insert lateral shade without world notification flags",
                () -> f.put(f.x + 9, f.y, f.z, Blocks.stone, 0))
            .awaitServer("lateral block stops generation", () -> f.assertAvailable(0))
            .server("remove shade without notifications", () -> f.put(f.x + 9, f.y, f.z, Blocks.air, 0))
            .awaitServer("shade removal is discovered", () -> f.assertAvailable(64))
            .server("insert transparent glass", () -> f.put(f.x + 9, f.y, f.z, Blocks.glass, 0))
            .awaitServer("glass transmits sunlight", () -> f.assertAvailable(64))
            .server(
                "change global block opacity without changing block identity",
                () -> Blocks.glass.setLightOpacity(255))
            .awaitServer("opacity mutation invalidates exposure", () -> f.assertAvailable(0))
            .server("restore opacity", () -> Blocks.glass.setLightOpacity(f.glassOpacity))
            .awaitServer("restored opacity resumes output", () -> f.assertAvailable(64))
            .server("complete the connected 5 by 5 plane", f::completePlane)
            .awaitServer("complete orbital array", () -> f.assertAvailable(1600))
            .server("mass tool style edits", f::bulkEdit)
            .awaitServer("bounded work finishes after bulk edits", () -> {
                var m = SolarArrayTracking.metrics();
                if (m.workUnits() > SolarArrayTracking.WORK_PER_TICK || m.worldSamples() > m.workUnits())
                    throw new AssertionError("Shared solar work allowance exceeded");
                f.assertAvailable(1600);
            })
            .server("measure the two finite reach candidates", f::measure)
            .server("use native copper cable", f::installCable)
            .awaitServer("native cable applies loss and demand acceptance", () -> {
                if (f.receiver.voltage <= 0 || f.receiver.voltage >= 32)
                    throw new AssertionError("No native cable loss observed");
                if (f.source()
                    .acceptedEUPerTick() != 32) throw new AssertionError("Cable path ignored accepted amperage");
            })
            .server("overload the native cable", () -> f.receiver.limit = 1000)
            .awaitServer(
                "native overload burns the cable",
                () -> {
                    if (f.world.getBlock(f.x - 1, f.y, f.z) == f.cableBlock)
                        throw new AssertionError("Waiting for native overload");
                })
            .server("reload the collector from native NBT", f::reload)
            .awaitServer("reloaded collector validates again", () -> f.assertAvailable(1600))
            .server("face the fixed orbital sun at night clock", () -> {
                f.world.setWorldTime(18000);
                f.player.playerNetServerHandler.setPlayerLocation(f.x - 5, f.y + 2, f.z + 8, -90, 0);
                if (f.world.provider.calculateCelestialAngle(0, 0)
                    != f.world.provider.calculateCelestialAngle(18000, 0))
                    throw new AssertionError("Orbit still has a day/night angle cycle");
            })
            .awaitServer("orbital power stays constant at the night clock", () -> f.assertAvailable(1600))
            .awaitClient("camera faces the orbital sun", c -> assertView(-90, 0))
            .capture("solar-orbit-sun-east")
            .server(
                "look at the planet below",
                () -> f.player.playerNetServerHandler.setPlayerLocation(f.x - 5, f.y + 2, f.z + 8, -90, 90))
            .awaitClient("camera looks down at the planet", c -> assertView(-90, 90))
            .capture("solar-orbit-planet-below")
            .succeed();
    }

    private static void assertView(float yaw, float pitch) {
        var player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
        if (Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(player.rotationYaw - yaw)) > 0.01
            || Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(player.prevRotationYaw - yaw)) > 0.01
            || Math.abs(player.rotationPitch - pitch) > 0.01
            || Math.abs(player.prevRotationPitch - pitch) > 0.01) {
            throw new AssertionError("Waiting for the client camera pose");
        }
    }

    /** Harness exception: a native IEnergyConnected consumer records actual accepted packets without storing power. */
    public static final class Receiver extends TileEntityChest implements IEnergyConnected {

        private boolean accept = true;
        private long limit = 1, voltage, peakEU, seenTick = Long.MIN_VALUE, tickEU;

        @Override
        public long injectEnergyUnits(ForgeDirection side, long volts, long amps) {
            if (!accept) return 0;
            long now = worldObj.getTotalWorldTime();
            if (now != seenTick) {
                seenTick = now;
                tickEU = 0;
            }
            long used = Math.min(amps, Math.max(0, limit - tickEU / volts));
            voltage = volts;
            tickEU += used * volts;
            peakEU = Math.max(peakEU, tickEU);
            return used;
        }

        @Override
        public boolean inputEnergyFrom(ForgeDirection side) {
            return true;
        }

        @Override
        public boolean outputsEnergyTo(ForgeDirection side) {
            return false;
        }

        @Override
        public byte getColorization() {
            return -1;
        }

        @Override
        public byte setColorization(byte color) {
            return -1;
        }
    }

    private record Original(Block block, int metadata, NBTTagCompound nbt) {}

    private static final class Fixture {

        private EntityPlayerMP player;
        private World world;
        private int x, y = 128, z, originalDimension, glassOpacity;
        private double oldX, oldY, oldZ;
        private float oldYaw, oldPitch;
        private boolean oldFlying, oldAllowed, oldRaining, oldThundering;
        private long deadline;
        private Receiver receiver;
        private Block cableBlock;
        private final Map<World, Map<BlockPos, Original>> saved = new LinkedHashMap<>();
        private final Map<World, Long> times = new HashMap<>();

        private void prepare(GameTestHelper helper) {
            player = (EntityPlayerMP) helper.getWorld().playerEntities.get(0);
            originalDimension = player.dimension;
            oldX = player.posX;
            oldY = player.posY;
            oldZ = player.posZ;
            oldYaw = player.rotationYaw;
            oldPitch = player.rotationPitch;
            oldFlying = player.capabilities.isFlying;
            oldAllowed = player.capabilities.allowFlying;
            glassOpacity = Blocks.glass.getLightOpacity();
            helper.afterTest(this::cleanup);
            player.capabilities.allowFlying = true;
            player.capabilities.isFlying = true;
            player.sendPlayerAbilities();
            world = helper.getWorld();
            oldRaining = world.getWorldInfo()
                .isRaining();
            oldThundering = world.getWorldInfo()
                .isThundering();
            x = (((int) player.posX + 32) >> 4) * 16 + 15;
            z = (int) player.posZ + 48;
            player.playerNetServerHandler.setPlayerLocation(x - 4, y + 3, z + 8, 0, 0);
            times.put(world, world.getWorldTime());
            world.getWorldInfo()
                .setRaining(false);
            world.getWorldInfo()
                .setThundering(false);
            world.setWorldTime(6000);
            build(false);
        }

        private void enterOrbit() {
            OrbitTestSupport.moveTo(player, OrbitTestSupport.overworldOrbit(), x - 4, y + 3, z + 8);
            world = MinecraftServer.getServer()
                .worldServerForDimension(OrbitTestSupport.overworldOrbit());
            times.put(world, world.getWorldTime());
        }

        private void build(boolean east) {
            for (int cx = (x - 3) >> 4; cx <= (x + 35) >> 4; cx++)
                for (int cz = (z - 3) >> 4; cz <= (z + 3) >> 4; cz++) world.getChunkFromChunkCoords(cx, cz);
            for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) for (int d = 0; d <= 32; d++) {
                if (east) put(x + 1 + d, y + v, z + u, Blocks.air, 0);
                else put(x + u, y + 1 + d, z + v, Blocks.air, 0);
            }
            put(x, y, z, Blocks.air, 0);
            ItemStack stack = SolarEquipment.collector.getStackForm(1);
            if (!((ItemMachines) stack.getItem())
                .placeBlockAt(stack, player, world, x, y, z, 1, .5f, .5f, .5f, stack.getItemDamage()))
                throw new AssertionError("Collector placement failed");
            put(east ? x + 1 : x, east ? y : y + 1, z, SolarEquipment.panel, 3);
            installReceiver(east ? x - 1 : x, east ? y : y - 1, z);
        }

        private void installReceiver(int px, int py, int pz) {
            put(px, py, pz, Blocks.chest, 3);
            receiver = new Receiver();
            world.setTileEntity(px, py, pz, receiver);
        }

        private IGregTechTileEntity base() {
            return (IGregTechTileEntity) world.getTileEntity(x, y, z);
        }

        private SolarEquipment.Dynamo source() {
            return (SolarEquipment.Dynamo) base().getMetaTileEntity();
        }

        private void assertAvailable(long expected) {
            if (source().availableEUPerTick() != expected)
                throw new AssertionError("Expected " + expected + ", got " + source().availableEUPerTick());
        }

        private void assertOutput(long expected, ForgeDirection facing) {
            assertAvailable(expected);
            if (base().getFrontFacing() != facing) throw new AssertionError("Collector did not autoalign");
            int px = x + facing.offsetX, py = y + facing.offsetY;
            if (world.getTileEntity(px, py, z) != null) throw new AssertionError("Passive panel has a tile entity");
            if (world.getBlockMetadata(px, py, z) != (facing == ForgeDirection.EAST ? 1 : 0))
                throw new AssertionError("Panel did not autoalign");
            if (receiver.peakEU == 0) throw new AssertionError("No native EU accepted");
        }

        private void completePlane() {
            receiver.limit = 1;
            put(x + 9, y, z, Blocks.air, 0);
            for (int u = -2; u <= 2; u++)
                for (int v = -2; v <= 2; v++) put(x + 1, y + v, z + u, SolarEquipment.panel, 3);
        }

        private void bulkEdit() {
            long started = System.nanoTime();
            for (int n = 0; n < 2000; n++)
                put(x + 8, y + n % 5 - 2, z + n / 5 % 5 - 2, n % 2 == 0 ? Blocks.stone : Blocks.air, 0);
            for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) put(x + 8, y + v, z + u, Blocks.air, 0);
            Galaxia.LOG.info("Solar native bulk edits=2025 mutationMs={}", (System.nanoTime() - started) / 1e6);
        }

        private void measure() {
            for (int depth : new int[] { 16, 32 }) {
                SolarArrays model = new SolarArrays(depth);
                model.register(new SolarArrays.Pos(x, y, z), true);
                long started = System.nanoTime();
                int ticks = 0, work = 0;
                int[] reads = { 0 };
                while (model.hasWork() && ticks < 1000) {
                    work += model.tick(SolarArrayTracking.WORK_PER_TICK, p -> {
                        reads[0]++;
                        Block b = world.getBlock(p.x(), p.y(), p.z());
                        return b == SolarEquipment.panel ? SolarArrays.PANEL
                            : b.getLightOpacity(world, p.x(), p.y(), p.z()) > 0 ? SolarArrays.OPAQUE : SolarArrays.AIR;
                    });
                    ticks++;
                }
                if (model.hasWork()) throw new AssertionError("Native ray benchmark did not converge");
                Galaxia.LOG.info(
                    "Solar native depth={} samples={} work={} budgetTicks={} validationMs={}",
                    depth,
                    reads[0],
                    work,
                    ticks,
                    (System.nanoTime() - started) / 1e6);
            }
        }

        private void installCable() {
            put(x - 1, y, z, Blocks.air, 0);
            installReceiver(x - 2, y, z);
            ItemStack wire = GTOreDictUnificator.get(OrePrefixes.cableGt01, Materials.Copper, 1);
            if (!((ItemBlock) wire.getItem())
                .placeBlockAt(wire, player, world, x - 1, y, z, 5, .5f, .5f, .5f, wire.getItemDamage()))
                throw new AssertionError("Cable placement failed");
            cableBlock = world.getBlock(x - 1, y, z);
            var pipe = (gregtech.api.metatileentity.BaseMetaPipeEntity) world.getTileEntity(x - 1, y, z);
            var connectable = (IConnectable) pipe.getMetaTileEntity();
            connectable.connect(ForgeDirection.EAST);
            connectable.connect(ForgeDirection.WEST);
        }

        private void reload() {
            put(x - 1, y, z, Blocks.air, 0);
            installReceiver(x - 1, y, z);
            TileEntity old = world.getTileEntity(x, y, z);
            NBTTagCompound nbt = new NBTTagCompound();
            old.writeToNBT(nbt);
            old.onChunkUnload();
            world.removeTileEntity(x, y, z);
            TileEntity restored = TileEntity.createAndLoadEntity(nbt);
            world.setTileEntity(x, y, z, restored);
            if (source().availableEUPerTick() != 0) throw new AssertionError("Reload trusted an old exposure snapshot");
        }

        private void put(int px, int py, int pz, Block block, int flags) {
            var originals = saved.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
            originals.computeIfAbsent(new BlockPos(px, py, pz), pos -> {
                Block old = world.getBlock(px, py, pz);
                NBTTagCompound nbt = null;
                TileEntity te = world.getTileEntity(px, py, pz);
                if (te != null) {
                    nbt = new NBTTagCompound();
                    te.writeToNBT(nbt);
                }
                return new Original(old, world.getBlockMetadata(px, py, pz), nbt);
            });
            world.setBlock(px, py, pz, block, 0, flags);
        }

        private void cleanup() {
            Blocks.glass.setLightOpacity(glassOpacity);
            for (var entry : saved.entrySet()) for (var block : entry.getValue()
                .entrySet()) {
                    var p = block.getKey();
                    var old = block.getValue();
                    entry.getKey()
                        .setBlock(p.x(), p.y(), p.z(), old.block, old.metadata, 3);
                    if (old.nbt != null) entry.getKey()
                        .setTileEntity(p.x(), p.y(), p.z(), TileEntity.createAndLoadEntity(old.nbt));
                }
            times.forEach(World::setWorldTime);
            World surface = MinecraftServer.getServer()
                .worldServerForDimension(originalDimension);
            surface.getWorldInfo()
                .setRaining(oldRaining);
            surface.getWorldInfo()
                .setThundering(oldThundering);
            OrbitTestSupport.moveTo(player, originalDimension, oldX, oldY, oldZ);
            player.playerNetServerHandler.setPlayerLocation(oldX, oldY, oldZ, oldYaw, oldPitch);
            player.capabilities.isFlying = oldFlying;
            player.capabilities.allowFlying = oldAllowed;
            player.sendPlayerAbilities();
        }
    }
}
