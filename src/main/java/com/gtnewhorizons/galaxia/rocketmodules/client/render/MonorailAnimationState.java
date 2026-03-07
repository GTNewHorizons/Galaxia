package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MonorailAnimationState {

    public static final int TRANSIT_TICKS = 60;
    private static final float SPEED = 1.0f / TRANSIT_TICKS;
    private static final float GAP_BLOCKS = 1.0f;

    public enum Direction {
        TO_SILO,
        TO_MA
    }

    public static final class TransitEntry {

        public final int moduleId;
        public final Direction direction;
        public float progress;
        public float prevProgress;

        TransitEntry(int moduleId, Direction direction) {
            this.moduleId = moduleId;
            this.direction = direction;
            this.progress = (direction == Direction.TO_SILO) ? 1.0f : 0.0f;
            this.prevProgress = progress;
        }
    }

    private final List<TransitEntry> toSilo = new ArrayList<>();
    private final List<TransitEntry> toMA = new ArrayList<>();

    public void tick(float pathLength) {
        if (toSilo.isEmpty() && toMA.isEmpty()) return;

        tickDirection(toSilo, pathLength, true);
        tickDirection(toMA, pathLength, false);
        removeCompleted();
    }

    private void tickDirection(List<TransitEntry> list, float pathLength, boolean toSiloDir) {
        for (int i = 0; i < list.size(); i++) {
            TransitEntry e = list.get(i);
            e.prevProgress = e.progress;

            TransitEntry leader = (i > 0) ? list.get(i - 1) : null;
            float minGap = calculateMinGap(e.moduleId, pathLength);

            float candidate = toSiloDir ? e.progress - SPEED : e.progress + SPEED;
            if (leader != null) {
                candidate = toSiloDir ? Math.max(candidate, leader.progress + minGap)
                    : Math.min(candidate, leader.progress - minGap);
            }

            e.progress = toSiloDir ? Math.max(candidate, 0.0f) : Math.min(candidate, 1.0f);
        }
    }

    private float calculateMinGap(int moduleId, float pathLength) {
        float height = moduleHeight(moduleId);
        return (pathLength > 1e-3f) ? (height + GAP_BLOCKS) / pathLength : 0.15f;
    }

    private void removeCompleted() {
        toSilo.removeIf(e -> e.progress <= 0.0f);
        toMA.removeIf(e -> e.progress >= 1.0f);
    }

    public void enqueueToSilo(int moduleId) {
        toSilo.add(new TransitEntry(moduleId, Direction.TO_SILO));
    }

    public void enqueueToMA(int moduleId) {
        toMA.add(new TransitEntry(moduleId, Direction.TO_MA));
    }

    public void clear() {
        toSilo.clear();
        toMA.clear();
    }

    public List<TransitEntry> getEntries() {
        List<TransitEntry> all = new ArrayList<>(toSilo.size() + toMA.size());
        all.addAll(toSilo);
        all.addAll(toMA);
        return all;
    }

    private static float moduleHeight(int moduleId) {
        RocketModule m = ModuleRegistry.fromId(moduleId);
        return m != null ? (float) m.getHeight() : 1.0f;
    }
}
