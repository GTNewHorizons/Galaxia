package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import org.lwjgl.opengl.GL11;

public class MonorailPath {

    private final double startX, startY, startZ;
    private final double endX, endY, endZ;
    private final double totalLength;
    private final int segmentCount;
    private final double dx, dy, dz;

    public MonorailPath(double sx, double sy, double sz, double ex, double ey, double ez, double segmentSize) {
        this.startX = sx;
        this.startY = sy;
        this.startZ = sz;
        this.endX = ex;
        this.endY = ey;
        this.endZ = ez;

        double rawDx = ex - sx, rawDy = ey - sy, rawDz = ez - sz;
        this.totalLength = Math.sqrt(rawDx * rawDx + rawDy * rawDy + rawDz * rawDz);

        if (totalLength < 1e-9) {
            this.dx = 0;
            this.dy = 1;
            this.dz = 0;
        } else {
            this.dx = rawDx / totalLength;
            this.dy = rawDy / totalLength;
            this.dz = rawDz / totalLength;
        }

        this.segmentCount = Math.max(1, (int) Math.ceil(totalLength / segmentSize));
    }

    public double[] pointAt(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[] { startX + (endX - startX) * t, startY + (endY - startY) * t,
            startZ + (endZ - startZ) * t };
    }

    public double[] pointAtBlocks(double progress, double blockOffset) {
        if (totalLength < 1e-9) return pointAt(progress);
        return pointAt(progress + blockOffset / totalLength);
    }

    public float getYawDegrees() {
        return (float) Math.toDegrees(Math.atan2(dx, dz));
    }

    public float getPitchDegrees() {
        return (float) Math.toDegrees(Math.asin(dy));
    }

    public void applyRailRotation() {
        GL11.glRotatef(getYawDegrees(), 0f, 1f, 0f);
        GL11.glRotatef(-getPitchDegrees(), 1f, 0f, 0f);
    }

    public double getTotalLength() {
        return totalLength;
    }

    public int getSegmentCount() {
        return segmentCount;
    }
}
