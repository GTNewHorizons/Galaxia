package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;

import org.lwjgl.opengl.GL11;

public class MonorailPath {

    public static final class Segment {

        public final double sx, sy, sz;
        public final double ex, ey, ez;
        public final double length;
        public final double dx, dy, dz;
        public final double tStart;
        public final double tEnd;

        Segment(double sx, double sy, double sz, double ex, double ey, double ez, double totalLength,
            double lengthBefore) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.ex = ex;
            this.ey = ey;
            this.ez = ez;

            double rdx = ex - sx, rdy = ey - sy, rdz = ez - sz;
            this.length = Math.sqrt(rdx * rdx + rdy * rdy + rdz * rdz);

            if (this.length < 1e-9) {
                this.dx = 0;
                this.dy = 1;
                this.dz = 0;
            } else {
                this.dx = rdx / this.length;
                this.dy = rdy / this.length;
                this.dz = rdz / this.length;
            }

            this.tStart = (totalLength > 1e-9) ? lengthBefore / totalLength : 0.0;
            this.tEnd = (totalLength > 1e-9) ? (lengthBefore + this.length) / totalLength : 1.0;
        }

        public float getYawDegrees() {
            return (float) Math.toDegrees(Math.atan2(dx, dz));
        }

        public float getPitchDegrees() {
            return (float) Math.toDegrees(Math.asin(dy));
        }

        double[] pointLocal(double localT) {
            return new double[] { sx + (ex - sx) * localT, sy + (ey - sy) * localT, sz + (ez - sz) * localT };
        }
    }

    private final List<Segment> segments;
    private final double totalLength;

    public MonorailPath(double sx, double sy, double sz, double ex, double ey, double ez) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] { sx, sy, sz });
        pts.add(new double[] { ex, ey, ez });
        this.segments = buildSegments(pts);
        this.totalLength = computeTotal(segments);
    }

    public MonorailPath(List<double[]> waypoints) {
        this.segments = buildSegments(waypoints);
        this.totalLength = computeTotal(segments);
    }

    public static MonorailPath fromWaypoints(List<ChunkCoordinates> waypoints, double renderOffX, double renderOffY,
        double renderOffZ, double railYOffset, int originX, int originY, int originZ) {
        List<double[]> pts = new ArrayList<>(waypoints.size());
        for (ChunkCoordinates c : waypoints) {
            double wx = renderOffX + (c.posX - originX) + 0.5;
            double wy = renderOffY + (c.posY - originY) + 1.0 + railYOffset;
            double wz = renderOffZ + (c.posZ - originZ) + 0.5;
            pts.add(new double[] { wx, wy, wz });
        }
        return new MonorailPath(pts);
    }

    public double[] pointAt(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return pointAtUnclamped(t);
    }

    public double[] pointAtUnclamped(double t) {
        Segment seg = segmentAt(t);
        double localT = (seg.tEnd > seg.tStart) ? (t - seg.tStart) / (seg.tEnd - seg.tStart) : 0.0;
        return seg.pointLocal(localT);
    }

    public Segment segmentAt(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        for (int i = 0; i < segments.size() - 1; i++) {
            if (t < segments.get(i).tEnd) return segments.get(i);
        }
        return segments.get(segments.size() - 1);
    }

    public void applyRailRotation(double t) {
        Segment seg = segmentAt(t);
        GL11.glRotatef(seg.getYawDegrees(), 0f, 1f, 0f);
        GL11.glRotatef(-seg.getPitchDegrees(), 1f, 0f, 0f);
    }

    public void applyRailRotation() {
        applyRailRotation(0.0);
    }

    public double getTotalLength() {
        return totalLength;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public double blockOffsetToT(double blocks) {
        return (totalLength > 1e-9) ? blocks / totalLength : 0.0;
    }

    public double[] pointAtBlocks(double progress, double blockOffset) {
        if (totalLength < 1e-9) return pointAt(progress);
        return pointAt(progress + blockOffset / totalLength);
    }

    public float getYawDegrees() {
        return segments.isEmpty() ? 0f
            : segments.get(0)
                .getYawDegrees();
    }

    public float getPitchDegrees() {
        return segments.isEmpty() ? 0f
            : segments.get(0)
                .getPitchDegrees();
    }

    public int getSegmentCount() {
        return segments.size();
    }

    private static List<Segment> buildSegments(List<double[]> pts) {
        double rawTotal = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            double ddx = b[0] - a[0], ddy = b[1] - a[1], ddz = b[2] - a[2];
            rawTotal += Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        }

        List<Segment> segs = new ArrayList<>();
        double lengthBefore = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            Segment seg = new Segment(a[0], a[1], a[2], b[0], b[1], b[2], rawTotal, lengthBefore);
            segs.add(seg);
            lengthBefore += seg.length;
        }
        return segs;
    }

    private static double computeTotal(List<Segment> segs) {
        double t = 0;
        for (Segment s : segs) t += s.length;
        return t;
    }
}
