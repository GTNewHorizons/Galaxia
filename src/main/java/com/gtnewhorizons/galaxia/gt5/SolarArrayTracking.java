package com.gtnewhorizons.galaxia.gt5;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.galaxia.registry.celestial.SolarIllumination;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server lifecycle and one shared work allowance for all loaded solar arrays. */
public final class SolarArrayTracking {

    public static final SolarArrayTracking EVENTS = new SolarArrayTracking();
    public static final int WORK_PER_TICK = 2048;
    public static final int SHADOW_DEPTH = 32;
    private static final Map<World, State> WORLDS = new WeakHashMap<>();
    private static int cursor;
    private static volatile boolean opacityDirty;
    private static volatile Metrics metrics = new Metrics(0, 0, 0);

    private SolarArrayTracking() {}

    private static final class State {

        private final SolarArrays arrays = new SolarArrays(SHADOW_DEPTH);
        private long intensityTick = Long.MIN_VALUE;
        private double intensity;
        private int reads;
    }

    public record Metrics(int workUnits, int worldSamples, long nanoseconds) {}

    public static Metrics metrics() {
        return metrics;
    }

    public static SolarArrays arrays(World world) {
        return WORLDS.computeIfAbsent(world, ignored -> new State()).arrays;
    }

    public static int litPanels(SolarArrays arrays, SolarArrays.Collector collector) {
        return opacityDirty ? 0 : arrays.litPanels(collector);
    }

    public static double intensity(World world) {
        State state = WORLDS.get(world);
        if (state == null) return 0;
        if (state.intensityTick != world.getTotalWorldTime()) {
            state.intensity = SolarIllumination.currentIntensity(world);
            state.intensityTick = world.getTotalWorldTime();
        }
        return state.intensity;
    }

    public static void changed(World world, int x, int y, int z) {
        if (world == null || world.isRemote) return;
        State state = WORLDS.get(world);
        if (state != null) state.arrays.changed(new SolarArrays.Pos(x, y, z));
    }

    public static void opacityChanged() {
        opacityDirty = true;
    }

    @SubscribeEvent
    public void loaded(ChunkEvent.Load event) {
        changedChunk(event);
    }

    @SubscribeEvent
    public void unloaded(ChunkEvent.Unload event) {
        changedChunk(event);
    }

    private static void changedChunk(ChunkEvent event) {
        if (event.world.isRemote) return;
        State state = WORLDS.get(event.world);
        if (state != null) state.arrays.chunkChanged(event.getChunk().xPosition, event.getChunk().zPosition);
    }

    @SubscribeEvent
    public void unloaded(WorldEvent.Unload event) {
        if (!event.world.isRemote) WORLDS.remove(event.world);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long started = System.nanoTime();
        if (opacityDirty) {
            opacityDirty = false;
            for (State state : WORLDS.values()) state.arrays.invalidateAll();
        }
        var worlds = new ArrayList<>(WORLDS.entrySet());
        for (State state : WORLDS.values()) state.reads = 0;
        int used = 0, idle = 0;
        while (!worlds.isEmpty() && used < WORK_PER_TICK && idle < worlds.size()) {
            var entry = worlds.get(Math.floorMod(cursor++, worlds.size()));
            State state = entry.getValue();
            int work = state.arrays
                .tick(Math.min(128, WORK_PER_TICK - used), pos -> sample(entry.getKey(), state, pos));
            used += work;
            idle = work == 0 ? idle + 1 : 0;
        }
        int reads = WORLDS.values()
            .stream()
            .mapToInt(s -> s.reads)
            .sum();
        metrics = new Metrics(used, reads, System.nanoTime() - started);
    }

    private static int sample(World world, State state, SolarArrays.Pos pos) {
        state.reads++;
        if (pos.y() < 0 || pos.y() >= world.getActualHeight()) return SolarArrays.AIR;
        if (!world.getChunkProvider()
            .chunkExists(pos.x() >> 4, pos.z() >> 4)) return SolarArrays.UNLOADED;
        Block block = world.getBlock(pos.x(), pos.y(), pos.z());
        if (block == SolarEquipment.panel) return SolarArrays.PANEL;
        return block.getLightOpacity(world, pos.x(), pos.y(), pos.z()) > 0 ? SolarArrays.OPAQUE : SolarArrays.AIR;
    }
}
