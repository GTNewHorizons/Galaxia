package com.gtnewhorizons.galaxia.gt5;

import static com.gtnewhorizons.galaxia.gt5.SolarArrays.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SolarArraysTest {

    @Test
    void onlyConnectedPanelsInsideTheAreaContribute() {
        SolarArrays arrays = new SolarArrays(16);
        Map<Pos, Integer> blocks = new HashMap<>();
        var c = arrays.register(new Pos(0, 0, 0), false);
        blocks.put(new Pos(0, 1, 0), PANEL);
        blocks.put(new Pos(2, 1, 2), PANEL);
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(c));
        blocks.put(new Pos(1, 1, 0), PANEL);
        blocks.put(new Pos(2, 1, 0), PANEL);
        blocks.put(new Pos(2, 1, 1), PANEL);
        arrays.changed(new Pos(1, 1, 0));
        assertEquals(0, arrays.litPanels(c));
        settle(arrays, blocks);
        assertEquals(5, arrays.litPanels(c));
        assertEquals(0, arrays.tick(10000, p -> {
            fail("Unchanged farms must not read the world");
            return AIR;
        }));
    }

    @Test
    void overlappingCollectorsNeverCountTheSamePanelTwice() {
        SolarArrays arrays = new SolarArrays(16);
        Map<Pos, Integer> blocks = new HashMap<>();
        for (int x = -2; x <= 4; x++) for (int z = -2; z <= 2; z++) blocks.put(new Pos(x, 1, z), PANEL);
        var a = arrays.register(new Pos(0, 0, 0), false);
        var b = arrays.register(new Pos(2, 0, 0), false);
        settle(arrays, blocks);
        assertEquals(35, arrays.litPanels(a) + arrays.litPanels(b));
        arrays.unregister(a);
        assertEquals(0, arrays.litPanels(b));
        settle(arrays, blocks);
        assertEquals(25, arrays.litPanels(b));
    }

    @Test
    void lateralShadowsAndChangesDuringValidationAreResumedWithinOneBudget() {
        SolarArrays arrays = new SolarArrays(32);
        Map<Pos, Integer> blocks = new HashMap<>();
        blocks.put(new Pos(16, 4, -1), PANEL);
        var c = arrays.register(new Pos(15, 4, -1), true);
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(c));
        Pos shadow = new Pos(33, 4, -1);
        blocks.put(shadow, OPAQUE);
        for (int i = 0; i < 100000; i++) arrays.changed(shadow);
        assertEquals(0, arrays.litPanels(c));
        AtomicInteger reads = new AtomicInteger();
        int work = arrays.tick(17, p -> {
            reads.incrementAndGet();
            return blocks.getOrDefault(p, AIR);
        });
        assertTrue(work <= 17);
        assertTrue(reads.get() <= 17);
        blocks.remove(shadow);
        arrays.changed(shadow);
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(c));
        blocks.put(shadow, UNLOADED);
        arrays.chunkChanged(2, -1);
        settle(arrays, blocks);
        assertEquals(0, arrays.litPanels(c));
        blocks.remove(shadow);
        arrays.chunkChanged(2, -1);
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(c));
    }

    @Test
    void shadowReachIsFiniteAndInitializationStartsUnvalidated() {
        Map<Pos, Integer> blocks = new HashMap<>();
        blocks.put(new Pos(0, 1, 0), PANEL);
        blocks.put(new Pos(0, 18, 0), OPAQUE);
        SolarArrays shortRange = new SolarArrays(16), longRange = new SolarArrays(32);
        var a = shortRange.register(new Pos(0, 0, 0), false);
        var b = longRange.register(new Pos(0, 0, 0), false);
        assertEquals(0, shortRange.litPanels(a));
        settle(shortRange, blocks);
        settle(longRange, blocks);
        assertEquals(1, shortRange.litPanels(a));
        assertEquals(0, longRange.litPanels(b));
    }

    @Test
    void globalOpacityChangesRecheckPanelsAcrossTheCollectorChunkBoundary() {
        SolarArrays arrays = new SolarArrays(32);
        Map<Pos, Integer> blocks = new HashMap<>();
        var collector = arrays.register(new Pos(15, 4, -1), true);
        blocks.put(new Pos(16, 4, -1), PANEL);
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(collector));
        Pos glass = new Pos(24, 4, -1);
        blocks.put(glass, OPAQUE);
        arrays.invalidateAll();
        assertEquals(0, arrays.litPanels(collector));
        settle(arrays, blocks);
        assertEquals(0, arrays.litPanels(collector));
        blocks.remove(glass);
        arrays.invalidateAll();
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(collector));
    }

    @Test
    void removingTheLastCollectorStopsTrackingWorldChanges() {
        SolarArrays arrays = new SolarArrays(32);
        var collector = arrays.register(new Pos(0, 0, 0), false);
        arrays.unregister(collector);
        arrays.changed(new Pos(0, 8, 0));
        arrays.chunkChanged(0, 0);
        arrays.invalidateAll();
        assertFalse(arrays.hasWork(), "A world without collectors must not spend solar work");
        Map<Pos, Integer> blocks = new HashMap<>();
        blocks.put(new Pos(0, 1, 0), PANEL);
        var replacement = arrays.register(new Pos(0, 0, 0), false);
        assertEquals(0, arrays.litPanels(replacement));
        settle(arrays, blocks);
        assertEquals(1, arrays.litPanels(replacement));
    }

    @Test
    void bulkChangesStayWithinTheSharedBudgetAtBothReachCandidates() {
        for (int depth : new int[] { 16, 32 }) {
            SolarArrays arrays = new SolarArrays(depth);
            Map<Pos, Integer> blocks = new HashMap<>();
            for (int n = 0; n < 100; n++) {
                int x = n % 10 * 6, z = n / 10 * 6;
                arrays.register(new Pos(x, 0, z), false);
                for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) blocks.put(new Pos(x + u, 1, z + v), PANEL);
            }
            settle(arrays, blocks);
            long started = System.nanoTime();
            for (int n = 0; n < 100000; n++) arrays.changed(new Pos(n % 10 * 6, 8, n / 10 % 10 * 6));
            long mutationNanos = System.nanoTime() - started;
            AtomicInteger reads = new AtomicInteger();
            int ticks = 0, work = 0;
            started = System.nanoTime();
            while (arrays.hasWork() && ticks < 10000) {
                work += arrays.tick(2048, p -> {
                    reads.incrementAndGet();
                    return blocks.getOrDefault(p, AIR);
                });
                ticks++;
            }
            assertFalse(arrays.hasWork());
            assertTrue(work <= ticks * 2048);
            System.out.printf(
                "Solar depth=%d arrays=100 edits=100000 mutationsMs=%.3f validationMs=%.3f samples=%d work=%d budgetTicks=%d%n",
                depth,
                mutationNanos / 1e6,
                (System.nanoTime() - started) / 1e6,
                reads.get(),
                work,
                ticks);
        }
    }

    private static void settle(SolarArrays arrays, Map<Pos, Integer> blocks) {
        for (int i = 0; arrays.hasWork() && i < 10000; i++) {
            assertTrue(arrays.tick(128, p -> blocks.getOrDefault(p, AIR)) <= 128);
        }
        assertFalse(arrays.hasWork(), "Solar checks failed to converge");
    }
}
