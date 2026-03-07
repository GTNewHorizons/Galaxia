package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side animation state for one Silo's monorail.
 *
 * <p>
 * The path is defined from <b>Silo (t=0) to MA (t=1)</b>.
 *
 * <ul>
 * <li><b>Ordering modules (MA → Silo):</b> entries start at {@code progress=1.0}
 * and move toward {@code 0}. Leader = lowest progress (furthest toward Silo).</li>
 * <li><b>Returning modules (Silo → MA):</b> entries start at {@code progress=0.0}
 * and move toward {@code 1}. Leader = highest progress (furthest toward MA).</li>
 * </ul>
 *
 * <p>
 * Spacing between consecutive modules is {@code module.getHeight() + GAP_BLOCKS},
 * converted to progress-unit gap using path length, so modules never clip.
 */
@SideOnly(Side.CLIENT)
public class MonorailAnimationState {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Fixed transit time in ticks regardless of rail length. */
    public static final int TRANSIT_TICKS = 60; // 3 seconds at 20 TPS

    /** Extra gap between consecutive modules in metres, on top of module height. */
    private static final float GAP_METRES = 1.0f;

    /** Progress advance per tick (same for both directions). */
    private static final float SPEED = 1.0f / TRANSIT_TICKS;

    // -----------------------------------------------------------------------
    // Transit entry
    // -----------------------------------------------------------------------

    /** Direction a train of entries travels on the path. */
    public enum Direction {
        /** MA → Silo: progress starts at 1.0, decreases to 0.0. */
        TO_SILO,
        /** Silo → MA: progress starts at 0.0, increases to 1.0. */
        TO_MA
    }

    /**
     * One module currently riding the rail.
     * {@code progress} is always in [0, 1] on the Silo↔MA path.
     * {@code prevProgress} is the value at the start of the last tick,
     * used for sub-tick interpolation with {@code partialTick}.
     */
    public static final class TransitEntry {

        public final int moduleId;
        public final Direction direction;

        /** Progress at the END of the last completed tick. */
        public float progress;

        /** Progress at the START of the last completed tick (for partialTick lerp). */
        public float prevProgress;

        TransitEntry(int moduleId, Direction direction) {
            this.moduleId = moduleId;
            this.direction = direction;
            // Spawn at the correct end of the rail
            this.progress = (direction == Direction.TO_SILO) ? 1.0f : 0.0f;
            this.prevProgress = this.progress;
        }
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    /**
     * All in-transit entries.
     * Ordering: entries are appended as they are enqueued.
     * Within a direction group, index 0 is always the <em>leader</em>
     * (furthest along toward the destination).
     */
    private final List<TransitEntry> entries = new ArrayList<>();

    // -----------------------------------------------------------------------
    // API
    // -----------------------------------------------------------------------

    /**
     * Advance the animation by one game tick.
     * Must only be called client-side from {@code updateEntity}.
     *
     * @param pathLength Current straight-line length of the rail in metres.
     */
    public void tick(float pathLength) {
        if (entries.isEmpty()) return;

        for (int i = 0; i < entries.size(); i++) {
            TransitEntry e = entries.get(i);

            e.prevProgress = e.progress;

            // Find the nearest leader: the entry with the same direction
            // that was enqueued before this one (lower index).
            TransitEntry leader = null;
            for (int j = i - 1; j >= 0; j--) {
                if (entries.get(j).direction == e.direction) {
                    leader = entries.get(j);
                    break;
                }
            }

            if (e.direction == Direction.TO_SILO) {
                // Moving from 1.0 → 0.0, progress decreases
                float candidate = e.progress - SPEED;

                if (leader != null) {
                    // Must not get closer than minGap ahead of the leader
                    float moduleHeight = moduleHeight(e.moduleId);
                    float minGap = (pathLength > 1e-3f) ? (moduleHeight + GAP_METRES) / pathLength : 0.15f;
                    // Leader is at lower progress, so we cannot go below leader.progress + minGap
                    float floor = leader.progress + minGap;
                    candidate = Math.max(candidate, floor);
                }

                e.progress = Math.max(candidate, 0.0f);
            } else {
                // Moving from 0.0 → 1.0, progress increases
                float candidate = e.progress + SPEED;

                if (leader != null) {
                    float moduleHeight = moduleHeight(e.moduleId);
                    float minGap = (pathLength > 1e-3f) ? (moduleHeight + GAP_METRES) / pathLength : 0.15f;
                    // Leader is at higher progress; we cannot exceed leader.progress - minGap
                    float ceiling = leader.progress - minGap;
                    candidate = Math.min(candidate, ceiling);
                }

                e.progress = Math.min(candidate, 1.0f);
            }
        }

        // Remove modules that have completed the journey
        entries.removeIf(
            e -> (e.direction == Direction.TO_SILO && e.progress <= 0.0f)
                || (e.direction == Direction.TO_MA && e.progress >= 1.0f));
    }

    /**
     * Enqueue a new module travelling <b>MA → Silo</b>.
     * Called when the server confirms a module was added to the Silo stack.
     *
     * @param moduleId Registry ID of the module.
     */
    public void enqueueToSilo(int moduleId) {
        entries.add(new TransitEntry(moduleId, Direction.TO_SILO));
    }

    /**
     * Enqueue a new module travelling <b>Silo → MA</b>.
     * Called when the server confirms a module was removed from the Silo stack
     * (return-modules action).
     *
     * @param moduleId Registry ID of the module.
     */
    public void enqueueToMA(int moduleId) {
        entries.add(new TransitEntry(moduleId, Direction.TO_MA));
    }

    /**
     * Discard all in-transit entries immediately.
     * Call this when the module list is force-reset on the client.
     */
    public void clear() {
        entries.clear();
    }

    /** @return Live list of entries; do not mutate outside this class. */
    public List<TransitEntry> getEntries() {
        return entries;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the height (≈ length along rail) of a module by ID.
     * Falls back to 1.0 if the ID is unknown.
     */
    private static float moduleHeight(int moduleId) {
        RocketModule m = ModuleRegistry.fromId(moduleId);
        return (m != null) ? (float) m.getHeight() : 1.0f;
    }
}
