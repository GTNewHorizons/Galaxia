package com.gtnewhorizons.galaxia.gt5;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/** Bounded array discovery and shadow work. Coordinates and dirty bounds never require world reads. */
public final class SolarArrays {

    public static final int UNLOADED = -1, AIR = 0, PANEL = 1, OPAQUE = 2;
    private static final int WIDTH = 5, RADIUS = 2, AREA = WIDTH * WIDTH;
    private final int depth;
    private final Map<Pos, Collector> collectors = new HashMap<>();
    private final NavigableMap<Long, NavigableMap<Long, Collector>> chunks = new TreeMap<>();
    private final LinkedHashMap<Long, Change> dirty = new LinkedHashMap<>();
    private final ArrayDeque<Collector> queue = new ArrayDeque<>();
    private long nextId;
    private boolean chooseDirty;
    private boolean globallyDirty;
    private Long globalChunkCursor;
    private long globalCollectorCursor;

    public SolarArrays(int depth) {
        if (depth < 1 || depth > 32) throw new IllegalArgumentException("Shadow depth must be in [1, 32]");
        this.depth = depth;
    }

    public record Pos(int x, int y, int z) {

        public Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    public final class Collector {

        private final Pos pos;
        private final boolean east;
        private final long id;
        private final Box volume;
        private Job job;
        private int lit;
        private boolean queued, blocked;

        private Collector(Pos pos, boolean east) {
            this.pos = pos;
            this.east = east;
            id = ++nextId;
            Pos a = panel(0), b = panel(AREA - 1);
            Pos end = forward(b, depth);
            volume = Box.between(a, end);
        }

        public Pos position() {
            return pos;
        }

        public boolean facesEast() {
            return east;
        }

        private Pos panel(int index) {
            int u = index % WIDTH - RADIUS, v = index / WIDTH - RADIUS;
            return east ? pos.offset(1, v, u) : pos.offset(u, 1, v);
        }

        private Pos forward(Pos p, int distance) {
            return east ? p.offset(distance, 0, 0) : p.offset(0, distance, 0);
        }
    }

    private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        static Box between(Pos a, Pos b) {
            return new Box(
                Math.min(a.x, b.x),
                Math.min(a.y, b.y),
                Math.min(a.z, b.z),
                Math.max(a.x, b.x),
                Math.max(a.y, b.y),
                Math.max(a.z, b.z));
        }

        Box merge(Box b) {
            return new Box(
                Math.min(minX, b.minX),
                Math.min(minY, b.minY),
                Math.min(minZ, b.minZ),
                Math.max(maxX, b.maxX),
                Math.max(maxY, b.maxY),
                Math.max(maxZ, b.maxZ));
        }

        boolean intersects(Box b) {
            return minX <= b.maxX && maxX >= b.minX
                && minY <= b.maxY
                && maxY >= b.minY
                && minZ <= b.maxZ
                && maxZ >= b.minZ;
        }
    }

    private final class Change {

        private Box box;
        private long revision, scanningRevision;
        private int chunkX, chunkZ;
        private long cursor;
        private boolean started;

        private Change(Box box) {
            this.box = box;
        }

        private boolean step() {
            int lowX = (box.minX - depth - 1) >> 4, highX = (box.maxX + RADIUS) >> 4;
            int lowZ = (box.minZ - RADIUS) >> 4, highZ = (box.maxZ + RADIUS) >> 4;
            if (!started) {
                started = true;
                scanningRevision = revision;
                chunkX = lowX;
                chunkZ = lowZ;
            }
            NavigableMap<Long, Collector> entries = chunks.get(key(chunkX, chunkZ));
            Map.Entry<Long, Collector> next = entries == null ? null : entries.higherEntry(cursor);
            if (next != null) {
                cursor = next.getKey();
                Collector c = next.getValue();
                if (c.volume.intersects(box)) invalidate(c);
                return false;
            }
            cursor = 0;
            if (++chunkZ <= highZ) return false;
            chunkZ = lowZ;
            if (++chunkX <= highX) return false;
            if (scanningRevision != revision) {
                started = false;
                return false;
            }
            return true;
        }
    }

    public Collector register(Pos pos, boolean east) {
        Collector existing = collectors.get(pos);
        if (existing != null) return existing;
        Collector c = new Collector(pos, east);
        collectors.put(pos, c);
        chunks.computeIfAbsent(key(pos.x >> 4, pos.z >> 4), ignored -> new TreeMap<>())
            .put(c.id, c);
        invalidate(c);
        changedPlane(c);
        return c;
    }

    public void unregister(Collector c) {
        if (!collectors.remove(c.pos, c)) return;
        NavigableMap<Long, Collector> bucket = chunks.get(key(c.pos.x >> 4, c.pos.z >> 4));
        bucket.remove(c.id);
        if (bucket.isEmpty()) chunks.remove(key(c.pos.x >> 4, c.pos.z >> 4));
        if (collectors.isEmpty()) {
            dirty.clear();
            queue.clear();
            globallyDirty = false;
            globalChunkCursor = null;
            globalCollectorCursor = 0;
            return;
        }
        changedPlane(c);
    }

    private void changedPlane(Collector c) {
        for (int i = 0; i < AREA; i++) changed(c.panel(i));
    }

    public void changed(Pos pos) {
        change(key(pos.x >> 4, pos.z >> 4), Box.between(pos, pos));
    }

    public void chunkChanged(int chunkX, int chunkZ) {
        change(
            key(chunkX, chunkZ),
            new Box(
                chunkX << 4,
                Integer.MIN_VALUE,
                chunkZ << 4,
                (chunkX << 4) + 15,
                Integer.MAX_VALUE,
                (chunkZ << 4) + 15));
    }

    private void change(long chunk, Box box) {
        if (collectors.isEmpty()) return;
        Change update = dirty.get(chunk);
        if (update == null) dirty.put(chunk, new Change(box));
        else {
            update.box = update.box.merge(box);
            update.revision++;
        }
    }

    private void invalidate(Collector c) {
        c.job = new Job(c);
        c.blocked = false;
        if (!c.queued) {
            c.queued = true;
            queue.addLast(c);
        }
    }

    public void invalidateAll() {
        if (collectors.isEmpty()) return;
        globallyDirty = true;
        globalChunkCursor = null;
        globalCollectorCursor = 0;
    }

    private void invalidateNextCollector() {
        var chunk = globalChunkCursor == null ? chunks.firstEntry() : chunks.ceilingEntry(globalChunkCursor);
        if (chunk == null) {
            globallyDirty = false;
            return;
        }
        if (!chunk.getKey()
            .equals(globalChunkCursor)) {
            globalChunkCursor = chunk.getKey();
            globalCollectorCursor = 0;
        }
        var collector = chunk.getValue()
            .higherEntry(globalCollectorCursor);
        if (collector != null) {
            globalCollectorCursor = collector.getKey();
            invalidate(collector.getValue());
        } else {
            var nextChunk = chunks.higherKey(globalChunkCursor);
            if (nextChunk == null) globallyDirty = false;
            else {
                globalChunkCursor = nextChunk;
                globalCollectorCursor = 0;
            }
        }
    }

    public boolean hasWork() {
        return globallyDirty || !dirty.isEmpty() || !queue.isEmpty();
    }

    /** Returns total work units, including candidate lookup, ownership checks and world samples. */
    public int tick(int budget, ToIntFunction<Pos> sample) {
        int used = 0;
        while (used < budget && hasWork()) {
            if (globallyDirty) {
                invalidateNextCollector();
                used++;
                continue;
            }
            chooseDirty = !chooseDirty;
            if (!dirty.isEmpty() && (chooseDirty || queue.isEmpty())) {
                var entry = dirty.entrySet()
                    .iterator()
                    .next();
                if (entry.getValue()
                    .step()) dirty.remove(entry.getKey());
            } else {
                Collector c = queue.removeFirst();
                c.queued = false;
                if (collectors.get(c.pos) == c && !c.job.step(sample)) {
                    c.queued = true;
                    queue.addLast(c);
                }
            }
            used++;
        }
        return used;
    }

    public int litPanels(Collector c) {
        if (globallyDirty || collectors.get(c.pos) != c || c.queued || c.blocked) return 0;
        Box v = c.volume;
        for (int x = v.minX >> 4; x <= v.maxX >> 4; x++) {
            for (int z = v.minZ >> 4; z <= v.maxZ >> 4; z++) {
                Change d = dirty.get(key(x, z));
                if (d != null && d.box.intersects(v)) return 0;
            }
        }
        return c.lit;
    }

    private final class Job {

        private final Collector c;
        private final boolean[] panels = new boolean[AREA], connected = new boolean[AREA];
        private final ArrayDeque<Integer> frontier = new ArrayDeque<>();
        private int phase, cursor, ownerCursor, ray = 1, visible;
        private Collector owner;

        private Job(Collector c) {
            this.c = c;
        }

        private boolean step(ToIntFunction<Pos> sample) {
            if (phase == 0) {
                int value = sample.applyAsInt(c.panel(cursor));
                if (value == UNLOADED) return blocked();
                panels[cursor++] = value == PANEL;
                if (cursor == AREA) {
                    phase = 1;
                    if (panels[AREA / 2]) {
                        connected[AREA / 2] = true;
                        frontier.add(AREA / 2);
                    }
                }
                return false;
            }
            if (phase == 1) {
                if (frontier.isEmpty()) {
                    phase = 2;
                    cursor = 0;
                    return false;
                }
                int p = frontier.removeFirst();
                int u = p % WIDTH, v = p / WIDTH;
                if (u > 0) connect(p - 1);
                if (u < WIDTH - 1) connect(p + 1);
                if (v > 0) connect(p - WIDTH);
                if (v < WIDTH - 1) connect(p + WIDTH);
                return false;
            }
            if (cursor == AREA) {
                c.lit = visible;
                return true;
            }
            if (!connected[cursor]) {
                nextPanel();
                return false;
            }
            Pos p = c.panel(cursor);
            if (phase == 2) {
                int u = ownerCursor % WIDTH - RADIUS, v = ownerCursor / WIDTH - RADIUS;
                Pos anchor = c.east ? p.offset(-1, v, u) : p.offset(u, -1, v);
                Collector candidate = collectors.get(anchor);
                if (candidate != null && candidate.east == c.east
                    && (owner == null || compare(candidate, owner, p) < 0)) owner = candidate;
                if (++ownerCursor == AREA) {
                    if (owner != c) nextPanel();
                    else phase = 3;
                }
                return false;
            }
            int value = sample.applyAsInt(c.forward(p, ray));
            if (value == UNLOADED) return blocked();
            if (value != AIR) nextPanel();
            else if (++ray > depth) {
                visible++;
                nextPanel();
            }
            return false;
        }

        private void connect(int i) {
            if (panels[i] && !connected[i]) {
                connected[i] = true;
                frontier.add(i);
            }
        }

        private void nextPanel() {
            cursor++;
            phase = 2;
            ownerCursor = 0;
            owner = null;
            ray = 1;
        }

        private boolean blocked() {
            c.blocked = true;
            c.lit = 0;
            return true;
        }
    }

    private static int compare(Collector a, Collector b, Pos panel) {
        int d = Long.compare(distance(a.pos, panel), distance(b.pos, panel));
        if (d != 0) return d;
        d = Integer.compare(a.pos.x, b.pos.x);
        if (d != 0) return d;
        d = Integer.compare(a.pos.y, b.pos.y);
        return d != 0 ? d : Integer.compare(a.pos.z, b.pos.z);
    }

    private static long distance(Pos a, Pos b) {
        long x = (long) a.x - b.x, y = (long) a.y - b.y, z = (long) a.z - b.z;
        return x * x + y * y + z * z;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
