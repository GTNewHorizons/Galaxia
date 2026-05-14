package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class HammerTrajectoryLoadTracker {

    private static final int WINDOW_TICKS = 200;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    private static final long[] allWindow = new long[WINDOW_TICKS];
    private static final Map<UUID, TeamWindow> teamWindows = new LinkedHashMap<>();
    private static final Map<UUID, Long> currentTeamNanos = new LinkedHashMap<>();

    private static int windowIndex;
    private static int sampleCount;
    private static boolean tickOpen;
    private static long allWindowSumNanos;
    private static long currentAllNanos;

    private HammerTrajectoryLoadTracker() {}

    public static void beginTick() {
        beginTick(true);
    }

    public static void beginTick(boolean enabled) {
        if (!enabled) {
            tickOpen = false;
            currentAllNanos = 0L;
            currentTeamNanos.clear();
            return;
        }
        if (tickOpen) endTick();
        tickOpen = true;
        currentAllNanos = 0L;
        currentTeamNanos.clear();
    }

    public static void recordRouteComputation(UUID teamId, long elapsedNanos) {
        if (!tickOpen || elapsedNanos <= 0L) return;
        currentAllNanos += elapsedNanos;
        if (teamId != null) {
            currentTeamNanos.merge(teamId, elapsedNanos, Long::sum);
        }
    }

    public static void endTick() {
        if (!tickOpen) return;

        allWindowSumNanos -= allWindow[windowIndex];
        allWindow[windowIndex] = currentAllNanos;
        allWindowSumNanos += currentAllNanos;

        for (UUID teamId : currentTeamNanos.keySet()) {
            teamWindows.computeIfAbsent(teamId, ignored -> new TeamWindow());
        }

        Iterator<Map.Entry<UUID, TeamWindow>> iterator = teamWindows.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TeamWindow> entry = iterator.next();
            long nanos = currentTeamNanos.getOrDefault(entry.getKey(), 0L);
            TeamWindow window = entry.getValue();
            window.set(windowIndex, nanos);
            if (sampleCount >= WINDOW_TICKS - 1 && window.sumNanos == 0L) {
                iterator.remove();
            }
        }

        if (sampleCount < WINDOW_TICKS) sampleCount++;
        windowIndex = (windowIndex + 1) % WINDOW_TICKS;
        tickOpen = false;
        currentAllNanos = 0L;
        currentTeamNanos.clear();
    }

    public static Snapshot snapshot(UUID teamId) {
        int samples = sampleCount;
        if (samples <= 0) return new Snapshot(0.0, 0.0);
        TeamWindow teamWindow = teamId == null ? null : teamWindows.get(teamId);
        long ownNanos = teamWindow == null ? 0L : teamWindow.sumNanos;
        return new Snapshot(toMsPerTick(ownNanos, samples), toMsPerTick(allWindowSumNanos, samples));
    }

    public static void reset() {
        for (int i = 0; i < allWindow.length; i++) {
            allWindow[i] = 0L;
        }
        teamWindows.clear();
        currentTeamNanos.clear();
        windowIndex = 0;
        sampleCount = 0;
        tickOpen = false;
        allWindowSumNanos = 0L;
        currentAllNanos = 0L;
    }

    private static double toMsPerTick(long nanos, int samples) {
        return nanos / NANOS_PER_MILLISECOND / samples;
    }

    public record Snapshot(double ownMsPerTick, double allMsPerTick) {}

    private static final class TeamWindow {

        private final long[] values = new long[WINDOW_TICKS];
        private long sumNanos;

        private void set(int index, long nanos) {
            sumNanos -= values[index];
            values[index] = nanos;
            sumNanos += nanos;
        }
    }
}
