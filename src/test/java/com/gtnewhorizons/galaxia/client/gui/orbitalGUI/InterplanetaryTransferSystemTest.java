package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.AbsolutePosition;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyProperties;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;

class InterplanetaryTransferSystemTest {

    private static final double POSITION_TOLERANCE = 1e-2;
    private static final int TRAJECTORY_SAMPLES = 64;
    private static final double MAX_SEGMENT_TO_AVERAGE_RATIO = 15.0;
    private static final double MAX_TURN_ANGLE_RADIANS = Math.toRadians(140.0);
    private static final double MIN_HEADING_SEGMENT_LENGTH = 1e-5;

    @Test
    void transferJobIsCreated() {
        OrbitalCelestialBody root = createTestSystem();
        OrbitalCelestialBody source = root.children()
            .get(0);
        OrbitalCelestialBody destination = root.children()
            .get(1);

        InterplanetaryTransferJob transfer = new InterplanetaryTransferSystem.OrbitalTransferSupport()
            .createTransferJob(root, source, destination, "Test Transfer", "Empty", 0.0, 40.0);

        assertNotNull(transfer);
        assertTrue(Double.isFinite(transfer.departureTime()), "Non-finite departure time");
        assertTrue(Double.isFinite(transfer.arrivalTime()), "Non-finite arrival time");
        assertTrue(transfer.arrivalTime() > transfer.departureTime(), "Arrival must be after departure");
        assertTrue(transfer.trajectoryPointCount() > 0, "Trajectory must not be empty");
    }

    @Test
    void transferTrajectoryPointsAreFinite() {
        OrbitalCelestialBody root = createTestSystem();
        OrbitalCelestialBody source = root.children()
            .get(0);
        OrbitalCelestialBody destination = root.children()
            .get(1);

        InterplanetaryTransferJob transfer = new InterplanetaryTransferSystem.OrbitalTransferSupport()
            .createTransferJob(root, source, destination, "Test Transfer", "Empty", 12.0, 55.0);

        assertNotNull(transfer);
        for (int i = 0; i < transfer.trajectoryPointCount(); i++) {
            assertTrue(Double.isFinite(transfer.trajectoryXs()[i]), "Non-finite worldX in trajectory");
            assertTrue(Double.isFinite(transfer.trajectoryYs()[i]), "Non-finite worldY in trajectory");
        }
    }

    @Test
    void currentTransferPointInterpolatesCorrectly() {
        OrbitalCelestialBody root = createTestSystem();
        OrbitalCelestialBody source = root.children()
            .get(0);
        OrbitalCelestialBody destination = root.children()
            .get(1);

        InterplanetaryTransferJob transfer = new InterplanetaryTransferSystem.OrbitalTransferSupport()
            .createTransferJob(root, source, destination, "Test Transfer", "Empty", 0.0, 40.0);

        assertNotNull(transfer);

        InterplanetaryTransferSystem.MutableTransferPoint startPoint = new InterplanetaryTransferSystem.MutableTransferPoint();
        InterplanetaryTransferSystem.MutableTransferPoint endPoint = new InterplanetaryTransferSystem.MutableTransferPoint();
        InterplanetaryTransferSystem.writeCurrentTransferPoint(transfer, transfer.departureTime(), startPoint);
        InterplanetaryTransferSystem.writeCurrentTransferPoint(transfer, transfer.arrivalTime(), endPoint);

        assertTrue(startPoint.valid());
        assertTrue(endPoint.valid());
        assertTrue(Double.isFinite(startPoint.worldX()));
        assertTrue(Double.isFinite(startPoint.worldY()));
        assertTrue(Double.isFinite(endPoint.worldX()));
        assertTrue(Double.isFinite(endPoint.worldY()));
    }

    @Test
    void lambertSolverReturnsValidVelocities() {
        // Simple test: two positions 180 degrees apart at radius 1
        // Hohmann half-ellipse: r1=1, r2=2, mu=1
        double mu = 1.0;
        double r1 = 1.0, r2 = 2.0;
        // TOF for Hohmann = pi * sqrt(((r1+r2)/2)^3 / mu)
        double sma = (r1 + r2) / 2.0;
        double tof = Math.PI * Math.sqrt(sma * sma * sma / mu);

        InterplanetaryTransferSystem.MutableLambertSolution result = new InterplanetaryTransferSystem.MutableLambertSolution();
        boolean success = InterplanetaryTransferSystem.solveLambertInto(r1, 0, -r2, 0, tof, mu, true, result);
        assertTrue(success, "Lambert solver should return a result for valid Hohmann transfer");
        assertTrue(Double.isFinite(result.departureVelocityX()));
        assertTrue(Double.isFinite(result.departureVelocityY()));
        assertTrue(Double.isFinite(result.arrivalVelocityX()));
        assertTrue(Double.isFinite(result.arrivalVelocityY()));
    }

    @Test
    void hemateriaToOrbitingPanspiraSpeedFactorTransfersAreCreated() {
        OrbitalCelestialBody root = createVaelHemateriaPanspiraSystem();
        OrbitalCelestialBody hemateria = root.children()
            .get(0);
        OrbitalCelestialBody panspira = root.children()
            .get(1);
        InterplanetaryTransferSystem.OrbitalTransferSupport transferSupport = new InterplanetaryTransferSystem.OrbitalTransferSupport();

        for (double speedFactor : new double[] { 2.0, 3.0, 5.0, 10.0 }) {
            double transferDuration = transferSupport
                .getTransferDurationForSpeedFactor(root, hemateria, panspira, 0.0, speedFactor);
            InterplanetaryTransferJob transfer = transferSupport
                .createTransferJob(root, hemateria, panspira, "Hemateria -> Panspira", "Empty", 0.0, transferDuration);

            assertNotNull(transfer, "Missing transfer for x=" + speedFactor);
            assertTrue(Double.isFinite(transfer.departureTime()), "Non-finite departure for x=" + speedFactor);
            assertTrue(Double.isFinite(transfer.arrivalTime()), "Non-finite arrival for x=" + speedFactor);
            assertTrajectoryHasNoAbruptTurns(transfer, speedFactor);
        }
    }

    private static void assertTrajectoryHasNoAbruptTurns(InterplanetaryTransferJob transfer, double speedFactor) {
        InterplanetaryTransferSystem.MutableTransferPoint point = new InterplanetaryTransferSystem.MutableTransferPoint();
        InterplanetaryTransferSystem.writeCurrentTransferPoint(transfer, transfer.departureTime(), point);
        assertTrue(point.valid());

        double previousPointX = point.worldX();
        double previousPointY = point.worldY();

        double previousDx = 0.0;
        double previousDy = 0.0;
        double totalSegmentLength = 0.0;
        double maxSegmentLength = 0.0;
        int segmentCount = 0;

        for (int i = 1; i <= TRAJECTORY_SAMPLES; i++) {
            double sampleTime = transfer.departureTime() + transfer.duration() * i / (double) TRAJECTORY_SAMPLES;
            InterplanetaryTransferSystem.writeCurrentTransferPoint(transfer, sampleTime, point);
            assertTrue(point.valid(), "Missing sample point " + i + " for x=" + speedFactor);
            assertTrue(Double.isFinite(point.worldX()), "Non-finite x at sample " + i + " for x=" + speedFactor);
            assertTrue(Double.isFinite(point.worldY()), "Non-finite y at sample " + i + " for x=" + speedFactor);

            double dx = point.worldX() - previousPointX;
            double dy = point.worldY() - previousPointY;
            double segmentLength = Math.hypot(dx, dy);
            totalSegmentLength += segmentLength;
            maxSegmentLength = Math.max(maxSegmentLength, segmentLength);
            segmentCount++;

            double previousLength = Math.hypot(previousDx, previousDy);
            if (previousLength > MIN_HEADING_SEGMENT_LENGTH && segmentLength > MIN_HEADING_SEGMENT_LENGTH) {
                double cosAngle = clamp(
                    (previousDx * dx + previousDy * dy) / (previousLength * segmentLength),
                    -1.0,
                    1.0);
                double turnAngle = Math.acos(cosAngle);
                assertTrue(
                    turnAngle <= MAX_TURN_ANGLE_RADIANS,
                    "Abrupt turn " + Math.toDegrees(turnAngle) + " deg at sample " + i + " for x=" + speedFactor);
            }

            previousDx = dx;
            previousDy = dy;
            previousPointX = point.worldX();
            previousPointY = point.worldY();
        }

        double averageSegmentLength = totalSegmentLength / Math.max(1, segmentCount);
        assertTrue(averageSegmentLength > 0.0, "Degenerate trajectory for x=" + speedFactor);
        assertTrue(
            maxSegmentLength <= averageSegmentLength * MAX_SEGMENT_TO_AVERAGE_RATIO,
            "Potential trajectory jump for x=" + speedFactor
                + ": max segment "
                + maxSegmentLength
                + " avg "
                + averageSegmentLength);
    }

    private static OrbitalCelestialBody createTestSystem() {
        OrbitalCelestialBody source = createBody(
            "source",
            CelestialObjectClass.PLANET,
            OrbitalParams.circular(1.2, 0.35, 0.1),
            0.18,
            8.0,
            0.5,
            Collections.emptyList());
        OrbitalCelestialBody destination = createBody(
            "destination",
            CelestialObjectClass.PLANET,
            OrbitalParams.circular(2.2, 0.22, 1.6),
            0.22,
            11.0,
            0.6,
            Collections.emptyList());

        return createBody(
            "root",
            CelestialObjectClass.STAR,
            OrbitalParams.circular(0.0, 0.0),
            0.45,
            30.0,
            8.0,
            Arrays.asList(source, destination));
    }

    private static OrbitalCelestialBody createVaelHemateriaPanspiraSystem() {
        OrbitalCelestialBody hemateria = createBody(
            "hemateria",
            CelestialObjectClass.PLANET,
            OrbitalParams.circular(3.2, 0.16, 0.75),
            0.42,
            26.0,
            0.95,
            Collections.emptyList());
        OrbitalCelestialBody panspira = createBody(
            "panspira",
            CelestialObjectClass.PLANET,
            OrbitalParams.circular(2.0, 0.24, 0.35),
            0.22,
            11.0,
            0.6,
            Collections.emptyList());

        return createBody(
            "vael",
            CelestialObjectClass.STAR,
            OrbitalParams.circular(0.0, 0.0),
            0.45,
            30.0,
            7.0,
            Arrays.asList(hemateria, panspira));
    }

    private static OrbitalCelestialBody createBody(String id, CelestialObjectClass objectClass,
        OrbitalParams orbitalParams, double spriteSize, double mu, double sphereOfInfluenceRadius,
        List<OrbitalCelestialBody> children) {
        return new OrbitalCelestialBody(
            id,
            id,
            "",
            0,
            null,
            objectClass,
            orbitalParams,
            new AbsolutePosition(0.0, 0.0),
            null,
            spriteSize,
            CelestialBodyProperties.builder()
                .standardGravitationalParameter(mu)
                .sphereOfInfluenceRadius(sphereOfInfluenceRadius)
                .build(),
            children);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
