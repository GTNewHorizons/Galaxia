package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.orbital.LambertTransfer;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

final class OrbitalTransferClientStateTest {

    @Test
    void simulatedTransfersPruneOnlyAfterArrivalTime() {
        InterplanetaryTransferSystem.OrbitalTransferState state = new InterplanetaryTransferSystem.OrbitalTransferState();
        state.addTransfer(transfer("sim:one", 10.0, 20.0));

        state.pruneFinishedTransfers(19.9, 0.0);

        assertEquals(
            1,
            state.transfers()
                .size());

        state.pruneFinishedTransfers(20.0, 0.0);

        assertEquals(
            0,
            state.transfers()
                .size());
    }

    @Test
    void visibleTransfersAreLimitedToCurrentSolarSystem() {
        CelestialObject iliaView = star(CelestialObjectId.ILIA, "Ilia");
        CelestialObject iliaAnchor = star(CelestialObjectId.ILIA, "Ilia");
        CelestialObject vael = star(CelestialObjectId.VAEL, "Vael");
        InterplanetaryTransferJob iliaTransfer = transfer("logistics:ilia", iliaAnchor);
        InterplanetaryTransferJob vaelTransfer = transfer("logistics:vael", vael);
        InterplanetaryTransferSystem.OrbitalTransferState state = new InterplanetaryTransferSystem.OrbitalTransferState();
        state.addTransfer(iliaTransfer);
        state.addTransfer(vaelTransfer);

        assertEquals(
            1,
            state.transfersForSystem(iliaView)
                .size());
        assertSame(
            iliaTransfer,
            state.transfersForSystem(iliaView)
                .get(0));
    }

    @Test
    void transferIsCulledOnlyWhenBothEndpointBodiesAreCulled() {
        CelestialObject source = planet(CelestialObjectId.ROMULUS, "Romulus");
        CelestialObject destination = planet(CelestialObjectId.REMUS, "Remus");
        InterplanetaryTransferJob transfer = transferBetween("sim:endpoints", source, destination);

        assertFalse(
            InterplanetaryTransferSystem.OrbitalTransferRenderer
                .shouldRenderTransferForEndpointVisibility(transfer, endpointVisibility()));
        assertTrue(
            InterplanetaryTransferSystem.OrbitalTransferRenderer
                .shouldRenderTransferForEndpointVisibility(transfer, endpointVisibility(source)));
        assertTrue(
            InterplanetaryTransferSystem.OrbitalTransferRenderer
                .shouldRenderTransferForEndpointVisibility(transfer, endpointVisibility(destination)));
    }

    @Test
    void renderedLogisticsTransferUsesCheapestLambertBranchForFixedRouteTime() {
        CelestialRegistry.freezeAndBake();
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        InterplanetaryTransferSystem.OrbitalTransferSupport support = new InterplanetaryTransferSystem.OrbitalTransferSupport();
        CelestialObject source = GalaxiaCelestialAPI.get(CelestialObjectId.EGORA)
            .orElseThrow();
        CelestialObject destination = GalaxiaCelestialAPI.get(CelestialObjectId.PANSPIRA)
            .orElseThrow();
        CelestialObject star = GalaxiaCelestialAPI.findStar(root, source);
        double departureTime = 8850.0;
        double hohmannTof = star.getHohmannTof(source, destination, root, departureTime);

        for (int i = 0; i < 64; i++) {
            double tof = hohmannTof * (0.1 + (3.0 - 0.1) * i / 63.0);
            double expectedDepartureAngularMomentum = cheapestDepartureAngularMomentum(
                root,
                star,
                source,
                destination,
                departureTime,
                tof);
            if (!Double.isFinite(expectedDepartureAngularMomentum) || expectedDepartureAngularMomentum >= 0.0) continue;

            InterplanetaryTransferJob rendered = support
                .createTransferJob(root, source, destination, "Package", "Cargo", departureTime, tof);
            assertNotNull(rendered);
            assertTrue(initialPathAngularMomentum(rendered) < 0.0);
            return;
        }

        fail("Expected at least one fixed-time default transfer where the cheapest Lambert branch is retrograde");
    }

    @Test
    void fixedTransferDoesNotRenderLinearFallbackWhenLambertIsInvalid() {
        CelestialRegistry.freezeAndBake();
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        InterplanetaryTransferSystem.OrbitalTransferSupport support = new InterplanetaryTransferSystem.OrbitalTransferSupport();
        CelestialObject source = GalaxiaCelestialAPI.get(CelestialObjectId.EGORA)
            .orElseThrow();

        InterplanetaryTransferJob rendered = support
            .createTransferJob(root, source, source, "Package", "Cargo", 8850.0, 10.0);

        assertNull(rendered);
    }

    @Test
    void lambertStressReportAcceptsFirstValidTransferCandidate() {
        CelestialRegistry.freezeAndBake();
        CelestialObject root = GalaxiaCelestialAPI.getPrimaryRoot();
        CelestialObject star = GalaxiaCelestialAPI.get(CelestialObjectId.VAEL)
            .orElseThrow();

        InterplanetaryTransferSystem.LambertStressReport report = InterplanetaryTransferSystem
            .runLambertStress(root, star, 8850.0, 32, Double.MAX_VALUE);

        assertEquals(32, report.executedSimulations());
        assertTrue(report.hasEnoughPlanets());
        assertTrue(report.hasSuccesses());
        assertEquals(0, report.trajectoryFailures());
        assertTrue(report.totalNanos() > 0);
        assertTrue(report.routeScanNanos() > 0);
        assertTrue(report.trajectorySampleNanos() > 0);
        assertTrue(report.arrivalResolveNanos() > 0);
        assertTrue(report.lambertNanos() > 0);
        assertTrue(report.scanCandidateCount() > 0);
        assertTrue(report.lambertPairCount() > 0);
    }

    @Test
    void trajectorySamplerKeepsEccentricTransfersOnLambertEndpoint() {
        double[] xs = new double[96];
        double[] ys = new double[96];

        int pointCount = InterplanetaryTransferSystem.sampleTransferArcInto(
            0.0,
            0.0,
            0.5641535081906672,
            0.1822451582246629,
            0.008106056580890142,
            -1.775548501211689,
            59.469599437341095,
            1.0,
            xs,
            ys,
            96);

        assertEquals(96, pointCount);
        assertEquals(-0.0343398527204353, xs[pointCount - 1], 2e-3);
        assertEquals(1.476195470219518, ys[pointCount - 1], 2e-3);
    }

    private static double cheapestDepartureAngularMomentum(CelestialObject root, CelestialObject star,
        CelestialObject source, CelestialObject destination, double departureTime, double tof) {
        OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, departureTime);
        OrbitalMechanics.OrbitalState srcState = OrbitalMechanics.resolveWorldState(root, source, departureTime);
        OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
            .resolveWorldState(root, destination, departureTime + tof);
        OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics.resolveWorldState(root, star, departureTime + tof);
        assertNotNull(starAtDep);
        assertNotNull(srcState);
        assertNotNull(dstState);
        assertNotNull(starAtArr);

        double r1x = srcState.x() - starAtDep.x();
        double r1y = srcState.y() - starAtDep.y();
        double r2x = dstState.x() - starAtArr.x();
        double r2y = dstState.y() - starAtArr.y();
        double vsrcX = srcState.vx() - starAtDep.vx();
        double vsrcY = srcState.vy() - starAtDep.vy();
        double vdstX = dstState.vx() - starAtArr.vx();
        double vdstY = dstState.vy() - starAtArr.vy();
        double mu = Math.max(1e-6, star.mu());
        double minPeriapsis = Math.max(0.05, star.spriteSize() * 0.5);

        LambertTransfer.Solution prograde = LambertTransfer.between(r1x, r1y, r2x, r2y)
            .mu(mu)
            .minPeriapsis(minPeriapsis)
            .timeOfFlight(tof)
            .prograde(true)
            .evaluateAgainst(vsrcX, vsrcY, vdstX, vdstY);
        LambertTransfer.Solution retrograde = LambertTransfer.between(r1x, r1y, r2x, r2y)
            .mu(mu)
            .minPeriapsis(minPeriapsis)
            .timeOfFlight(tof)
            .prograde(false)
            .evaluateAgainst(vsrcX, vsrcY, vdstX, vdstY);
        LambertTransfer.Solution cheapest = prograde.valid()
            && (!retrograde.valid() || prograde.totalDv() <= retrograde.totalDv()) ? prograde : retrograde;
        if (!cheapest.valid()) return Double.NaN;
        return r1x * cheapest.dvy1() - r1y * cheapest.dvx1();
    }

    private static double initialPathAngularMomentum(InterplanetaryTransferJob transfer) {
        assertTrue(transfer.trajectoryPointCount() >= 2);
        double x0 = transfer.trajectoryXs()[0];
        double y0 = transfer.trajectoryYs()[0];
        double dx = transfer.trajectoryXs()[1] - x0;
        double dy = transfer.trajectoryYs()[1] - y0;
        return x0 * dy - y0 * dx;
    }

    private static InterplanetaryTransferJob transfer(String id, double departureTime, double arrivalTime) {
        return transfer(id, null, departureTime, arrivalTime);
    }

    private static InterplanetaryTransferJob transfer(String id, CelestialObject orbitAnchorBody) {
        return transfer(id, orbitAnchorBody, 10.0, 20.0);
    }

    private static InterplanetaryTransferJob transfer(String id, CelestialObject orbitAnchorBody, double departureTime,
        double arrivalTime) {
        return new InterplanetaryTransferJob(
            id,
            "Simulation",
            "Simulation",
            null,
            null,
            null,
            orbitAnchorBody,
            departureTime,
            arrivalTime,
            new double[] { 0.0, 1.0 },
            new double[] { 0.0, 1.0 },
            2,
            TransferPackageKind.HAMMER);
    }

    private static InterplanetaryTransferJob transferBetween(String id, CelestialObject source,
        CelestialObject destination) {
        return new InterplanetaryTransferJob(
            id,
            "Simulation",
            "Simulation",
            null,
            source,
            destination,
            null,
            10.0,
            20.0,
            new double[] { 0.0, 1.0 },
            new double[] { 0.0, 1.0 },
            2,
            TransferPackageKind.HAMMER);
    }

    private static InterplanetaryTransferSystem.OrbitalTransferRenderer.Callbacks endpointVisibility(
        CelestialObject... renderedBodies) {
        return new InterplanetaryTransferSystem.OrbitalTransferRenderer.Callbacks() {

            @Override
            public float worldToScreenX(double worldX) {
                return (float) worldX;
            }

            @Override
            public float worldToScreenY(double worldY) {
                return (float) worldY;
            }

            @Override
            public double[] getWorldPosition(CelestialObject body) {
                return null;
            }

            @Override
            public double getServerOrbitalTime() {
                return 0.0;
            }

            @Override
            public boolean isBodyRendered(CelestialObject body) {
                for (CelestialObject renderedBody : renderedBodies) {
                    if (renderedBody == body) return true;
                }
                return false;
            }
        };
    }

    private static CelestialObject star(CelestialObjectId id, String name) {
        return CelestialObject.builder()
            .id(id)
            .name(name)
            .objectClass(CelestialObject.Class.STAR)
            .build();
    }

    private static CelestialObject planet(CelestialObjectId id, String name) {
        return CelestialObject.builder()
            .id(id)
            .name(name)
            .objectClass(CelestialObject.Class.PLANET)
            .build();
    }
}
