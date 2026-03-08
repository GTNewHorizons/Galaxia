package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class MonorailAnimationState {

    public static final float BLOCKS_PER_SECOND = 4.5f;
    private static final float GAP_BLOCKS = 1.0f;

    public enum Direction {
        TO_SILO,
        TO_MA
    }

    public static final class TransitEntry {

        public final int moduleId;
        public final Direction direction;
        public float hook1, hook2;
        public float prevHook1, prevHook2;

        TransitEntry(int moduleId, Direction direction, float hook1, float hook2) {
            this.moduleId = moduleId;
            this.direction = direction;
            this.hook1 = this.prevHook1 = hook1;
            this.hook2 = this.prevHook2 = hook2;
        }

        public float lerpHook1(float pt) {
            return prevHook1 + (hook1 - prevHook1) * pt;
        }

        public float lerpHook2(float pt) {
            return prevHook2 + (hook2 - prevHook2) * pt;
        }

        public float lerpMid(float pt) {
            return (lerpHook1(pt) + lerpHook2(pt)) * 0.5f;
        }
    }

    private final List<TransitEntry> toSilo = new ArrayList<>();
    private final List<TransitEntry> toMA = new ArrayList<>();

    public void tick(float pathLength) {
        if (toSilo.isEmpty() && toMA.isEmpty()) return;
        float speed = BLOCKS_PER_SECOND / (20f * pathLength);
        tickDirection(toSilo, pathLength, speed, true);
        tickDirection(toMA, pathLength, speed, false);
        removeCompleted();
    }

    private void tickDirection(List<TransitEntry> list, float pathLength, float speed, boolean toSiloDir) {
        for (int i = 0; i < list.size(); i++) {
            TransitEntry e = list.get(i);
            e.prevHook1 = e.hook1;
            e.prevHook2 = e.hook2;

            TransitEntry leader = (i > 0) ? list.get(i - 1) : null;
            float gap = GAP_BLOCKS / pathLength;
            float span = hookSpan(e.moduleId, pathLength);

            if (toSiloDir) {
                float c1 = e.hook1 + speed;
                if (leader != null) c1 = Math.min(c1, leader.hook2 - gap);
                e.hook1 = Math.min(c1, 1f + span); // let hook1 go past 1 until hook2 clears
                e.hook2 = e.hook1 - span;
            } else {
                float c1 = e.hook1 - speed;
                if (leader != null) c1 = Math.max(c1, leader.hook2 + gap);
                e.hook1 = Math.max(c1, -span); // let hook1 go past 0 until hook2 clears
                e.hook2 = e.hook1 + span;
            }
        }
    }

    private float hookSpan(int moduleId, float pathLength) {
        float height = moduleHeight(moduleId);
        return pathLength > 1e-3f ? height / pathLength : 0.1f;
    }

    private void removeCompleted() {
        toSilo.removeIf(e -> e.hook2 > 1f - 1e-4f);
        toMA.removeIf(e -> e.hook2 < 1e-4f);
    }

    public void enqueueToSilo(int moduleId, float pathLength) {
        toSilo.add(new TransitEntry(moduleId, Direction.TO_SILO, 0f, 0f));
    }

    public void enqueueToSilo(int moduleId) {
        enqueueToSilo(moduleId, 10f);
    }

    public void enqueueToMA(int moduleId, float pathLength) {
        toMA.add(new TransitEntry(moduleId, Direction.TO_MA, 1f, 1f));
    }

    public void enqueueToMA(int moduleId) {
        enqueueToMA(moduleId, 10f);
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
