package com.gtnewhorizons.galaxia.rocketmodules.client.render;

/**
 * Immutable description of a monorail run between two world positions.
 *
 * <p>
 * This is the <b>foundation for gantry mechanics</b>: any future entity
 * (gantry car, module transfer arm, etc.) that needs to move along the rail
 * should:
 * <ol>
 * <li>Obtain a {@code MonorailPath} from the linked Silo/MA pair.</li>
 * <li>Store a {@code double progress} in [0 .. 1] representing how far
 * along the rail the car has travelled.</li>
 * <li>Call {@link #pointAt(double)} each tick to get world XYZ.</li>
 * <li>Call {@link #getDirection()} to orient the car model.</li>
 * </ol>
 *
 * <p>
 * The path is always a straight line in 3-D space. Curved or multi-segment
 * rails can be added later by replacing the internals without changing the API.
 */
public class MonorailPath {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final double startX, startY, startZ;
    private final double endX, endY, endZ;

    /** Total length of the rail in metres. */
    private final double totalLength;

    /** Number of tiled segments that fit in the total length. */
    private final int segmentCount;

    /** Normalised direction vector from start to end. */
    private final double[] direction;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * @param sx          Start X (world)
     * @param sy          Start Y (world)
     * @param sz          Start Z (world)
     * @param ex          End X (world)
     * @param ey          End Y (world)
     * @param ez          End Z (world)
     * @param segmentSize Preferred segment repeat length in metres.
     *                    The last segment may be shorter.
     */
    public MonorailPath(double sx, double sy, double sz, double ex, double ey, double ez, double segmentSize) {
        this.startX = sx;
        this.startY = sy;
        this.startZ = sz;
        this.endX = ex;
        this.endY = ey;
        this.endZ = ez;

        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        this.totalLength = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double len = totalLength < 1e-9 ? 1.0 : totalLength;
        this.direction = new double[] { dx / len, dy / len, dz / len };

        // At least 1 segment even if the blocks are adjacent
        this.segmentCount = Math.max(1, (int) Math.ceil(totalLength / segmentSize));
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * World position at progress {@code t} ∈ [0, 1].
     * t=0 → start (Silo), t=1 → end (ModuleAssembler).
     *
     * @return double[3] {x, y, z}
     */
    public double[] pointAt(double t) {
        t = Math.max(0, Math.min(1, t));
        return new double[] { startX + (endX - startX) * t, startY + (endY - startY) * t,
            startZ + (endZ - startZ) * t };
    }

    /**
     * Normalised direction vector from start to end.
     * Safe to use as a rotation axis or for orienting the gantry car.
     *
     * @return double[3] unit vector
     */
    public double[] getDirection() {
        return direction.clone();
    }

    /** Total straight-line length of this rail in metres. */
    public double getTotalLength() {
        return totalLength;
    }

    /** Number of repeated visual segments the renderer will draw. */
    public int getSegmentCount() {
        return segmentCount;
    }

    // Start/end accessors — useful for gantry car spawn/despawn logic
    public double getStartX() {
        return startX;
    }

    public double getStartY() {
        return startY;
    }

    public double getStartZ() {
        return startZ;
    }

    public double getEndX() {
        return endX;
    }

    public double getEndY() {
        return endY;
    }

    public double getEndZ() {
        return endZ;
    }

    /**
     * Converts a world position back to a progress value.
     * Projects the point onto the rail direction.
     * Useful for checking where a gantry car currently is.
     *
     * @return progress ∈ [0, 1]
     */
    public double progressOf(double wx, double wy, double wz) {
        if (totalLength < 1e-9) return 0;
        double dx = wx - startX, dy = wy - startY, dz = wz - startZ;
        double dot = dx * direction[0] + dy * direction[1] + dz * direction[2];
        return Math.max(0, Math.min(1, dot / totalLength));
    }

    @Override
    public String toString() {
        return String.format(
            "MonorailPath[(%.1f,%.1f,%.1f)→(%.1f,%.1f,%.1f) len=%.2fm segs=%d]",
            startX,
            startY,
            startZ,
            endX,
            endY,
            endZ,
            totalLength,
            segmentCount);
    }
}
