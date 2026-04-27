package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.GlStateManager;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.orbital.LambertTransfer;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

// ---------------------------------------------------------------------------
// Package-level records
// ---------------------------------------------------------------------------

/**
 * Visual classification of an in-flight transfer package. Drives which sprite the orbital
 * renderer blits at the transfer point. New kinds are added by extending this enum and
 * wiring an entry in {@link TransferPackageIcons#texture}.
 */
enum TransferPackageKind {

    HAMMER;

    String displayName() {
        return "Hammer Package";
    }
}

record InterplanetaryTransferJob(String transferId, String displayName, String inventorySummary,
    CelestialObject rootBody, CelestialObject sourceBody, CelestialObject destinationBody,
    CelestialObject orbitAnchorBody, double departureTime, double arrivalTime, double[] trajectoryXs,
    double[] trajectoryYs, int trajectoryPointCount, TransferPackageKind packageKind) {

    public InterplanetaryTransferJob {
        transferId = transferId == null ? "" : transferId;
        displayName = displayName == null ? "" : displayName;
        inventorySummary = inventorySummary == null ? "Empty" : inventorySummary;
        packageKind = packageKind == null ? TransferPackageKind.HAMMER : packageKind;
        trajectoryPointCount = Math.max(
            0,
            Math.min(
                trajectoryPointCount,
                Math.min(
                    trajectoryXs == null ? 0 : trajectoryXs.length,
                    trajectoryYs == null ? 0 : trajectoryYs.length)));
        trajectoryXs = trajectoryPointCount == 0 ? new double[0] : Arrays.copyOf(trajectoryXs, trajectoryPointCount);
        trajectoryYs = trajectoryPointCount == 0 ? new double[0] : Arrays.copyOf(trajectoryYs, trajectoryPointCount);
    }

    double duration() {
        return Math.max(1e-6, arrivalTime - departureTime);
    }

    double progress(double currentTime) {
        double d = duration();
        double t = (currentTime - departureTime) / d;
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t;
    }

    boolean isFinished(double currentTime) {
        return currentTime >= arrivalTime;
    }

    boolean isLogisticsTransfer() {
        return transferId != null && transferId.startsWith("logistics:");
    }
}

// ---------------------------------------------------------------------------
// Main class
// ---------------------------------------------------------------------------

public final class InterplanetaryTransferSystem {

    private static final int PREVIEW_TRAJECTORY_SAMPLES = 96;

    private InterplanetaryTransferSystem() {}

    public record LambertStressReport(int requestedSimulations, int executedSimulations, int candidatePlanetCount,
        int successfulTransfers, double averageTotalDv, double bestTotalDv, double worstTotalDv) {

        public LambertStressReport {
            requestedSimulations = Math.max(0, requestedSimulations);
            executedSimulations = Math.max(0, executedSimulations);
            candidatePlanetCount = Math.max(0, candidatePlanetCount);
            successfulTransfers = Math.max(0, successfulTransfers);
        }

        public int failedTransfers() {
            return Math.max(0, executedSimulations - successfulTransfers);
        }

        public boolean hasEnoughPlanets() {
            return candidatePlanetCount >= 2;
        }

        public boolean hasSuccesses() {
            return successfulTransfers > 0;
        }
    }

    public static final class MutableTransferPoint {

        private double worldX = 0.0;
        private double worldY = 0.0;
        private boolean valid = false;

        public double worldX() {
            return worldX;
        }

        public double worldY() {
            return worldY;
        }

        public boolean valid() {
            return valid;
        }

        private void set(double worldX, double worldY) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.valid = true;
        }

        private void clear() {
            this.worldX = 0.0;
            this.worldY = 0.0;
            this.valid = false;
        }
    }

    // -----------------------------------------------------------------------
    // Public API: getCurrentTransferPoint
    // -----------------------------------------------------------------------

    public static boolean writeCurrentTransferPoint(InterplanetaryTransferJob transfer, double currentTime,
        MutableTransferPoint out) {
        if (out == null) return false;
        if (transfer == null) {
            out.clear();
            return false;
        }
        int pointCount = transfer.trajectoryPointCount();
        if (pointCount <= 0) {
            out.set(0.0, 0.0);
            return true;
        }
        if (pointCount == 1) {
            out.set(transfer.trajectoryXs()[0], transfer.trajectoryYs()[0]);
            return true;
        }
        double dep = transfer.departureTime();
        double arr = transfer.arrivalTime();
        double dur = Math.max(1e-12, arr - dep);
        double t = (currentTime - dep) / dur;
        if (t <= 0.0) {
            out.set(transfer.trajectoryXs()[0], transfer.trajectoryYs()[0]);
            return true;
        }
        if (t >= 1.0) {
            out.set(transfer.trajectoryXs()[pointCount - 1], transfer.trajectoryYs()[pointCount - 1]);
            return true;
        }
        double idx = t * (pointCount - 1);
        int lo = (int) idx;
        int hi = Math.min(lo + 1, pointCount - 1);
        double frac = idx - lo;
        double ax = transfer.trajectoryXs()[lo];
        double ay = transfer.trajectoryYs()[lo];
        double bx = transfer.trajectoryXs()[hi];
        double by = transfer.trajectoryYs()[hi];
        out.set(ax + (bx - ax) * frac, ay + (by - ay) * frac);
        return true;
    }

    private static double effectiveTransferTime(InterplanetaryTransferJob transfer, double currentTime,
        double logisticsCurrentTime) {
        return currentTime;
    }

    private static double effectiveTransferTimeScale(InterplanetaryTransferJob transfer, double currentTimeScale) {
        return currentTimeScale;
    }

    // -----------------------------------------------------------------------
    /**
     * Propagates a 2-body orbit and returns world-frame trajectory points.
     * anchorX/Y is the world position of the attractor at departure time.
     */
    static int sampleTransferArcInto(double ax, double ay, double rx1, double ry1, double vx1, double vy1, double tof,
        double mu, double[] outXs, double[] outYs, int n) {
        if (outXs == null || outYs == null) return 0;
        int sampleCount = Math.max(2, Math.min(n, Math.min(outXs.length, outYs.length)));
        if (sampleCount <= 0) return 0;
        OrbitalMechanics.OrbitalState state = new OrbitalMechanics.OrbitalState(rx1, ry1, vx1, vy1);
        double dt = tof / (sampleCount - 1);
        for (int i = 0; i < sampleCount; i++) {
            if (i > 0) {
                state = OrbitalMechanics.propagateTwoBodyState(state, mu, dt);
                if (state == null) return i;
            }
            outXs[i] = ax + state.x();
            outYs[i] = ay + state.y();
        }
        return sampleCount;
    }

    // -----------------------------------------------------------------------
    // Helper methods (delegates to shared OrbitalTransferPlanner)
    // -----------------------------------------------------------------------

    private static CelestialObject findHostStar(CelestialObject root, CelestialObject target) {
        return GalaxiaCelestialAPI.findStar(root, target);
    }

    public static LambertStressReport runLambertStress(CelestialObject root, CelestialObject star, double globalTime,
        int simulations, double maxDvLimit) {
        int requested = Math.max(0, simulations);
        if (requested == 0 || root == null || star == null || star.objectClass() != CelestialObject.Class.STAR) {
            return new LambertStressReport(requested, 0, 0, 0, 0.0, 0.0, 0.0);
        }

        List<CelestialObject> candidatePlanets = new ArrayList<>();
        collectStressPlanets(star, candidatePlanets);
        if (candidatePlanets.size() < 2) {
            return new LambertStressReport(requested, 0, candidatePlanets.size(), 0, 0.0, 0.0, 0.0);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int successCount = 0;
        double sumDv = 0.0;
        double bestDv = Double.POSITIVE_INFINITY;
        double worstDv = 0.0;
        double dvLimit = Math.max(0.0, maxDvLimit);

        for (int i = 0; i < requested; i++) {
            int sourceIndex = random.nextInt(candidatePlanets.size());
            int destinationIndex = random.nextInt(candidatePlanets.size() - 1);
            if (destinationIndex >= sourceIndex) destinationIndex++;

            CelestialObject source = candidatePlanets.get(sourceIndex);
            CelestialObject destination = candidatePlanets.get(destinationIndex);

            double departureOffset = random.nextDouble(0.0, 600.0);
            double departureTime = globalTime + departureOffset;
            double solvedDv = findBestLambertWithinDvLimit(root, star, source, destination, departureTime, dvLimit);
            if (solvedDv < 0.0) continue;

            successCount++;
            sumDv += solvedDv;
            if (solvedDv < bestDv) bestDv = solvedDv;
            if (solvedDv > worstDv) worstDv = solvedDv;
        }

        double avgDv = successCount > 0 ? sumDv / successCount : 0.0;
        double clampedBestDv = successCount > 0 ? bestDv : 0.0;
        double clampedWorstDv = successCount > 0 ? worstDv : 0.0;
        return new LambertStressReport(
            requested,
            requested,
            candidatePlanets.size(),
            successCount,
            avgDv,
            clampedBestDv,
            clampedWorstDv);
    }

    private static void collectStressPlanets(CelestialObject current, List<CelestialObject> out) {
        if (current == null || out == null) return;
        CelestialObject.Class objectClass = current.objectClass();
        if (objectClass == CelestialObject.Class.PLANET || objectClass == CelestialObject.Class.GAS_GIANT) {
            out.add(current);
        }
        for (CelestialObject child : GalaxiaCelestialAPI.getChildren(current)) {
            collectStressPlanets(child, out);
        }
    }

    private static double findBestLambertWithinDvLimit(CelestialObject root, CelestialObject star,
        CelestialObject origin, CelestialObject destination, double departureTime, double dvLimit) {
        if (root == null || star == null || origin == null || destination == null || origin == destination) return -1.0;

        double minPeriapsis = Math.max(0.05, star.spriteSize() * 0.5);

        TransferScanner.ScanResult result = TransferScanner.scan(
            root,
            origin,
            destination,
            star,
            departureTime,
            minPeriapsis,
            (current, best) -> current.totalDv() <= dvLimit && current.tof() < best.tof());

        return result.isValid() ? result.totalDv() : -1.0;
    }

    // -----------------------------------------------------------------------
    // updatePreview (called from OrbitalView)
    // -----------------------------------------------------------------------

    public static void updatePreview(OrbitalTransferSimulatorState state, CelestialObject root, double globalTime) {
        if (state == null || !state.isOpen()) return;
        CelestialObject origin = state.originBody();
        CelestialObject dest = state.destinationBody();
        if (origin == null || dest == null || origin == dest) {
            state.clearPreview();
            return;
        }

        CelestialObject star = findHostStar(root, origin);
        CelestialObject destStar = findHostStar(root, dest);
        if (star == null || star != destStar) {
            state.clearPreview();
            return;
        }

        double sliderDv = state.sliderDv();
        if (sliderDv <= 0.0) {
            state.clearPreview();
            return;
        }
        TransferOptimizationMode optimizationMode = state.optimizationMode();

        double minPeriapsis = Math.max(0.05, star.spriteSize() * 0.5);
        double mu = Math.max(1e-6, star.mu());

        TransferScanner.ScanResult best = TransferScanner
            .scan(root, origin, dest, star, globalTime, minPeriapsis, (current, bestResult) -> {
                if (current.totalDv() > sliderDv) return false;
                if (!bestResult.isValid()) return true;
                if (optimizationMode == TransferOptimizationMode.MIN_TOF) {
                    return current.tof() < bestResult.tof();
                } else {
                    return current.totalDv() < bestResult.totalDv()
                        || (Math.abs(current.totalDv() - bestResult.totalDv()) < 1e-9
                            && current.tof() < bestResult.tof());
                }
            });

        if (!best.isValid()) {
            state.clearPreview();
            return;
        }

        OrbitalMechanics.OrbitalState srcStateDep = OrbitalMechanics.resolveWorldState(root, origin, globalTime);
        OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, globalTime);

        double dvDep = best.depDv();
        double dvCap = best.totalDv() - best.depDv();

        if (srcStateDep != null && starAtDep != null) {
            double vsrcX = srcStateDep.vx() - starAtDep.vx();
            double vsrcY = srcStateDep.vy() - starAtDep.vy();
            double vdstX = best.dstState()
                .vx()
                - best.attractorAtArr()
                    .vx();
            double vdstY = best.dstState()
                .vy()
                - best.attractorAtArr()
                    .vy();
            dvDep = Math.hypot(
                best.solution()
                    .dvx1() - vsrcX,
                best.solution()
                    .dvy1() - vsrcY);
            dvCap = Math.hypot(
                vdstX - best.solution()
                    .dvx2(),
                vdstY - best.solution()
                    .dvy2());
        }

        state.ensurePreviewCapacity(PREVIEW_TRAJECTORY_SAMPLES);
        int previewPointCount = sampleTransferArcInto(
            best.anchorX(),
            best.anchorY(),
            best.r1x(),
            best.r1y(),
            best.solution()
                .dvx1(),
            best.solution()
                .dvy1(),
            best.tof(),
            mu,
            state.previewXs(),
            state.previewYs(),
            PREVIEW_TRAJECTORY_SAMPLES);

        state.setPreview(previewPointCount, best.tof(), dvDep + dvCap, dvDep, dvCap);
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferState
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferState {

        private final List<InterplanetaryTransferJob> transfers = new ArrayList<>();
        private int version = 0;
        private InterplanetaryTransferJob hoveredTransfer = null;
        private int hoverX = 0;
        private int hoverY = 0;

        List<InterplanetaryTransferJob> transfers() {
            return transfers;
        }

        int version() {
            return version;
        }

        InterplanetaryTransferJob hoveredTransfer() {
            return hoveredTransfer;
        }

        int hoverX() {
            return hoverX;
        }

        int hoverY() {
            return hoverY;
        }

        void addTransfer(InterplanetaryTransferJob transfer) {
            if (transfer == null) return;
            transfers.add(transfer);
            version++;
        }

        void replaceTransfersMatching(java.util.function.Predicate<InterplanetaryTransferJob> predicate,
            List<InterplanetaryTransferJob> replacements) {
            boolean changed = transfers.removeIf(predicate);
            if (replacements != null && !replacements.isEmpty()) {
                transfers.addAll(replacements);
                changed = true;
            }
            if (changed) version++;
        }

        void updateHoveredTransfer(InterplanetaryTransferJob transfer, int mouseX, int mouseY) {
            hoveredTransfer = transfer;
            hoverX = mouseX;
            hoverY = mouseY;
        }

        void pruneFinishedTransfers(double currentTime, double logisticsCurrentTime) {
            if (transfers.isEmpty()) return;
            if (transfers.removeIf(t -> t.isFinished(effectiveTransferTime(t, currentTime, logisticsCurrentTime)))) {
                version++;
                if (hoveredTransfer != null && hoveredTransfer
                    .isFinished(effectiveTransferTime(hoveredTransfer, currentTime, logisticsCurrentTime))) {
                    hoveredTransfer = null;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferSupport
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSupport {

        private static final double DEFAULT_TRANSFER_DURATION = 72.0;
        private static final int TRAJECTORY_SAMPLES = 96;

        InterplanetaryTransferJob createTransferJob(CelestialObject root, CelestialObject sourceBody,
            CelestialObject destinationBody, String transferName, String inventorySummary, double departureTime) {
            return createTransferJob(
                root,
                sourceBody,
                destinationBody,
                transferName,
                inventorySummary,
                departureTime,
                getTransferDuration(sourceBody, destinationBody));
        }

        InterplanetaryTransferJob createTransferJob(CelestialObject root, CelestialObject sourceBody,
            CelestialObject destinationBody, String transferName, String inventorySummary, double departureTime,
            double duration) {
            if (root == null || sourceBody == null || destinationBody == null) return null;
            CelestialObject star = findHostStar(root, sourceBody);
            CelestialObject destStar = findHostStar(root, destinationBody);
            if (star == null || star != destStar) return null;

            double tof = Math.max(1.0, duration);
            double mu = Math.max(1e-6, star.mu());

            OrbitalMechanics.OrbitalState starAtDep = OrbitalMechanics.resolveWorldState(root, star, departureTime);
            OrbitalMechanics.OrbitalState srcState = OrbitalMechanics
                .resolveWorldState(root, sourceBody, departureTime);
            OrbitalMechanics.OrbitalState dstState = OrbitalMechanics
                .resolveWorldState(root, destinationBody, departureTime + tof);
            OrbitalMechanics.OrbitalState starAtArr = OrbitalMechanics
                .resolveWorldState(root, star, departureTime + tof);
            if (starAtDep == null || srcState == null || dstState == null || starAtArr == null) return null;

            double r1x = srcState.x() - starAtDep.x();
            double r1y = srcState.y() - starAtDep.y();
            double r2x = dstState.x() - starAtArr.x();
            double r2y = dstState.y() - starAtArr.y();

            LambertTransfer.Solution sol = LambertTransfer.between(r1x, r1y, r2x, r2y)
                .mu(mu)
                .timeOfFlight(tof)
                .prograde(true)
                .solve();
            if (!sol.valid()) {
                sol = LambertTransfer.between(r1x, r1y, r2x, r2y)
                    .mu(mu)
                    .timeOfFlight(tof)
                    .prograde(false)
                    .solve();
            }

            double[] trajectoryXs;
            double[] trajectoryYs;
            int trajectoryPointCount;
            if (sol.valid()) {
                trajectoryXs = new double[TRAJECTORY_SAMPLES];
                trajectoryYs = new double[TRAJECTORY_SAMPLES];
                trajectoryPointCount = sampleTransferArcInto(
                    starAtDep.x(),
                    starAtDep.y(),
                    r1x,
                    r1y,
                    sol.dvx1(),
                    sol.dvy1(),
                    tof,
                    mu,
                    trajectoryXs,
                    trajectoryYs,
                    TRAJECTORY_SAMPLES);
            } else {
                // Fallback: linear interpolation
                trajectoryXs = new double[TRAJECTORY_SAMPLES];
                trajectoryYs = new double[TRAJECTORY_SAMPLES];
                trajectoryPointCount = TRAJECTORY_SAMPLES;
                for (int i = 0; i < TRAJECTORY_SAMPLES; i++) {
                    double frac = i / (double) (TRAJECTORY_SAMPLES - 1);
                    trajectoryXs[i] = srcState.x() + (dstState.x() - srcState.x()) * frac;
                    trajectoryYs[i] = srcState.y() + (dstState.y() - srcState.y()) * frac;
                }
            }

            String id = sourceBody.id() + "->" + destinationBody.id() + "@" + Math.round(departureTime * 1000.0);
            String inv = (inventorySummary == null || inventorySummary.isEmpty()) ? "Empty" : inventorySummary;
            return new InterplanetaryTransferJob(
                id,
                transferName,
                inv,
                root,
                sourceBody,
                destinationBody,
                star,
                departureTime,
                departureTime + tof,
                trajectoryXs,
                trajectoryYs,
                trajectoryPointCount,
                TransferPackageKind.HAMMER);
        }

        double getTransferDuration(CelestialObject sourceBody, CelestialObject destinationBody) {
            if (sourceBody == null || destinationBody == null
                || sourceBody.orbitalParams() == null
                || destinationBody.orbitalParams() == null) {
                return DEFAULT_TRANSFER_DURATION;
            }
            double sourceRadius = sourceBody.orbitalParams()
                .semiMajorAxis();
            double destinationRadius = destinationBody.orbitalParams()
                .semiMajorAxis();
            double orbitDistance = Math.abs(sourceRadius - destinationRadius);
            return DEFAULT_TRANSFER_DURATION + orbitDistance * 18.0;
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferRenderer
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferRenderer {

        public interface Callbacks {

            float worldToScreenX(double worldX);

            float worldToScreenY(double worldY);

            double[] getWorldPosition(CelestialObject body);

            double getServerOrbitalTime();
        }

        private static final int PATH_COLOR = EnumColors.MAP_COLOR_TRANSFER_PATH.getColor();
        private static final int PREVIEW_PATH_COLOR = EnumColors.MAP_COLOR_TRANSFER_PREVIEW_PATH.getColor();
        private static final float DOT_HIT_RADIUS = 7.0f;
        private static final float PACKAGE_SPRITE_SIZE = 12.0f;

        private final Callbacks callbacks;
        private final MutableTransferPoint transferPoint = new MutableTransferPoint();

        public OrbitalTransferRenderer(Callbacks callbacks) {
            this.callbacks = callbacks;
        }

        void drawTransferPaths(OrbitalTransferState state, double currentTime, float alpha) {
            if (state.transfers()
                .isEmpty() || alpha <= 0.01f) return;
            state.pruneFinishedTransfers(currentTime, callbacks.getServerOrbitalTime());
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (InterplanetaryTransferJob transfer : state.transfers()) {
                drawTransferPath(transfer, alpha);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        void drawTransferDots(OrbitalTransferState state, double currentTime, float alpha) {
            if (state.transfers()
                .isEmpty() || alpha <= 0.01f) return;
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (InterplanetaryTransferJob transfer : state.transfers()) {
                drawTransferDot(
                    transfer,
                    effectiveTransferTime(transfer, currentTime, callbacks.getServerOrbitalTime()),
                    alpha);
            }
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableDepth();
        }

        void drawPreviewTrajectory(OrbitalTransferSimulatorState state, float alpha) {
            if (state == null || !state.isOpen() || alpha <= 0.01f || !state.hasPreview()) return;

            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            int color = withAlpha(PREVIEW_PATH_COLOR, alpha);
            applyColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i < state.previewPointCount(); i++) {
                GL11.glVertex2f(
                    callbacks.worldToScreenX(state.previewX(i)),
                    callbacks.worldToScreenY(state.previewY(i)));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);

            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        InterplanetaryTransferJob findHoveredTransfer(OrbitalTransferState state, double currentTime, float mouseX,
            float mouseY) {
            for (int i = state.transfers()
                .size() - 1; i >= 0; i--) {
                InterplanetaryTransferJob transfer = state.transfers()
                    .get(i);
                double effectiveTime = effectiveTransferTime(transfer, currentTime, callbacks.getServerOrbitalTime());
                if (!writeCurrentTransferPoint(transfer, effectiveTime, transferPoint) || !transferPoint.valid()) {
                    continue;
                }
                float sx = callbacks.worldToScreenX(transferPoint.worldX());
                float sy = callbacks.worldToScreenY(transferPoint.worldY());
                float dx = mouseX - sx;
                float dy = mouseY - sy;
                if (dx * dx + dy * dy <= DOT_HIT_RADIUS * DOT_HIT_RADIUS) return transfer;
            }
            return null;
        }

        private void drawTransferPath(InterplanetaryTransferJob transfer, float alpha) {
            if (transfer.trajectoryPointCount() <= 0) return;
            int color = withAlpha(PATH_COLOR, alpha);
            applyColor(color);
            GL11.glLineWidth(1.8f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i < transfer.trajectoryPointCount(); i++) {
                GL11.glVertex2f(
                    callbacks.worldToScreenX(transfer.trajectoryXs()[i]),
                    callbacks.worldToScreenY(transfer.trajectoryYs()[i]));
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);
        }

        private void drawTransferDot(InterplanetaryTransferJob transfer, double currentTime, float alpha) {
            if (!writeCurrentTransferPoint(transfer, currentTime, transferPoint) || !transferPoint.valid()) return;
            float sx = callbacks.worldToScreenX(transferPoint.worldX());
            float sy = callbacks.worldToScreenY(transferPoint.worldY());
            TransferPackageIcons.drawCentered(transfer.packageKind(), sx, sy, PACKAGE_SPRITE_SIZE, alpha);
        }

        private void applyColor(int color) {
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            float a = ((color >> 24) & 0xFF) / 255f;
            GlStateManager.color(r, g, b, a);
        }

        private int withAlpha(int color, float alpha) {
            int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
            return (color & 0x00FFFFFF) | (a << 24);
        }
    }

    // -----------------------------------------------------------------------
    // TransferPickMode
    // -----------------------------------------------------------------------

    public enum TransferPickMode {
        NONE,
        ORIGIN,
        DESTINATION
    }

    public enum TransferOptimizationMode {

        MIN_TOF,
        MIN_DV;

        TransferOptimizationMode toggled() {
            return this == MIN_TOF ? MIN_DV : MIN_TOF;
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferSimulatorState
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSimulatorState {

        private boolean open = false;
        private TransferPickMode pickMode = TransferPickMode.NONE;
        private TransferOptimizationMode optimizationMode = TransferOptimizationMode.MIN_TOF;
        private CelestialObject originBody = null;
        private CelestialObject destinationBody = null;
        private int version = 0;

        // New dV fields
        private double maxDv = 5.0;
        private double sliderDv = 0.0;

        // Preview data
        private double[] previewXs = new double[0];
        private double[] previewYs = new double[0];
        private int previewPointCount = 0;
        private double previewTof = 0.0;
        private double previewTotalDv = 0.0;
        private double previewDvDep = 0.0;
        private double previewDvCap = 0.0;

        boolean isOpen() {
            return open;
        }

        TransferPickMode pickMode() {
            return pickMode;
        }

        CelestialObject originBody() {
            return originBody;
        }

        CelestialObject destinationBody() {
            return destinationBody;
        }

        TransferOptimizationMode optimizationMode() {
            return optimizationMode;
        }

        void toggleOptimizationMode() {
            optimizationMode = optimizationMode.toggled();
            clearPreview();
            version++;
        }

        int version() {
            return version;
        }

        boolean isWaitingForPick() {
            return open && pickMode != TransferPickMode.NONE;
        }

        void open() {
            if (open) return;
            open = true;
            pickMode = TransferPickMode.NONE;
            version++;
        }

        void close() {
            if (!open && pickMode == TransferPickMode.NONE && originBody == null && destinationBody == null) return;
            open = false;
            pickMode = TransferPickMode.NONE;
            originBody = null;
            destinationBody = null;
            clearPreview();
            version++;
        }

        void beginPick(TransferPickMode mode) {
            if (!open || mode == null) return;
            pickMode = mode;
            version++;
        }

        void cancelPick() {
            if (pickMode == TransferPickMode.NONE) return;
            pickMode = TransferPickMode.NONE;
            version++;
        }

        void resetSelection() {
            if (pickMode == TransferPickMode.NONE && originBody == null && destinationBody == null) return;
            pickMode = TransferPickMode.NONE;
            originBody = null;
            destinationBody = null;
            clearPreview();
            version++;
        }

        void applyPickedBody(CelestialObject body) {
            if (!open || pickMode == TransferPickMode.NONE || body == null) return;
            if (pickMode == TransferPickMode.ORIGIN) originBody = body;
            else if (pickMode == TransferPickMode.DESTINATION) destinationBody = body;
            pickMode = TransferPickMode.NONE;
            clearPreview();
            version++;
        }

        double maxDv() {
            return maxDv;
        }

        void setMaxDv(double value) {
            this.maxDv = Math.max(0.001, value);
            if (sliderDv > this.maxDv) sliderDv = this.maxDv;
            version++;
        }

        double sliderDv() {
            return sliderDv;
        }

        void setSliderDv(double value) {
            this.sliderDv = Math.max(0.0, Math.min(maxDv, value));
        }

        void ensurePreviewCapacity(int count) {
            int required = Math.max(0, count);
            if (previewXs.length >= required && previewYs.length >= required) return;
            previewXs = new double[required];
            previewYs = new double[required];
        }

        void setPreview(int pointCount, double tof, double totalDv, double dvDep, double dvCap) {
            this.previewPointCount = Math.max(0, pointCount);
            this.previewTof = tof;
            this.previewTotalDv = totalDv;
            this.previewDvDep = dvDep;
            this.previewDvCap = dvCap;
        }

        void clearPreview() {
            this.previewPointCount = 0;
            this.previewTof = 0.0;
            this.previewTotalDv = 0.0;
            this.previewDvDep = 0.0;
            this.previewDvCap = 0.0;
        }

        boolean hasPreview() {
            return previewPointCount > 0;
        }

        int previewPointCount() {
            return previewPointCount;
        }

        double[] previewXs() {
            return previewXs;
        }

        double[] previewYs() {
            return previewYs;
        }

        double previewX(int index) {
            return previewXs[index];
        }

        double previewY(int index) {
            return previewYs[index];
        }

        double previewTof() {
            return previewTof;
        }

        double previewTotalDv() {
            return previewTotalDv;
        }

        double previewDvDep() {
            return previewDvDep;
        }

        double previewDvCap() {
            return previewDvCap;
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferSimulatorWidget
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferSimulatorWidget extends ParentWidget<OrbitalTransferSimulatorWidget> {

        public interface Callbacks {

            int getViewportWidth();

            int getViewportHeight();

            void closeTransferSimulator();

            void beginTransferPick(TransferPickMode pickMode);

            CelestialObject getCurrentSystemBody();

            void onPreviewNeeded();

            void dispatchTransfer();

            void runLambertStressTest();

            double getTimeScale();
        }

        private static final int PANEL_LEFT = 28;
        private static final int PANEL_TOP = 80;
        private static final int PANEL_WIDTH = 300;
        private static final int PANEL_HEIGHT = 260;
        private static final int CONTENT_X = 16;
        private static final int PICK_BUTTON_WIDTH = 96;
        private static final int PICK_BUTTON_HEIGHT = 20;
        private static final int INPUT_FIELD_WIDTH = 80;
        private static final int INPUT_FIELD_HEIGHT = 18;

        private final OrbitalTransferSimulatorState state;
        private final Callbacks callbacks;
        private final TextFieldWidget maxDvField;
        private int panelLeft = PANEL_LEFT;
        private int panelTop = PANEL_TOP;
        private int lastVersion = -1;

        // Track the DoubleValue for the slider
        private DoubleValue sliderValue;

        // Dynamic text widgets replaced with cached strings for IKey.dynamic
        private String cachedDvLabel = "dV: --";
        private String cachedTof = "Time of Flight: --";
        private String cachedDepDv = "Departure dV: --";
        private String cachedCapDv = "Capture dV: --";
        private String cachedTotalDv = "Total dV: --";

        private double lastSliderDv = -1;
        private double lastPreviewTof = -1;
        private double lastPreviewDvDep = -1;
        private double lastPreviewDvCap = -1;
        private double lastPreviewTotalDv = -1;
        private boolean lastHasPreview = false;
        private double lastTimeScale = -1;

        OrbitalTransferSimulatorWidget(OrbitalTransferSimulatorState state, Callbacks callbacks) {
            this.state = state;
            this.callbacks = callbacks;
            this.maxDvField = createInputField("Max dV");
            maxDvField.setText(String.valueOf(state.maxDv()));
            this.sliderValue = new DoubleValue(state.sliderDv());
            setEnabled(false);
            size(0, 0);
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        boolean isPointInPanel(int localX, int localY) {
            return state.isOpen() && localX >= panelLeft
                && localX <= panelLeft + PANEL_WIDTH
                && localY >= panelTop
                && localY <= panelTop + PANEL_HEIGHT;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            if (!state.isOpen()) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                lastVersion = -1;
                setEnabled(false);
                size(0, 0);
                return;
            }
            setEnabled(true);
            size(callbacks.getViewportWidth(), callbacks.getViewportHeight());
            if (state.version() != lastVersion) {
                rebuildChildren();
                lastVersion = state.version();
                lastSliderDv = -1;
                lastPreviewTof = -1;
            }
            // Poll slider value for changes
            if (sliderValue != null) {
                double newVal = sliderValue.getDoubleValue();
                double oldVal = state.sliderDv();
                if (Math.abs(newVal - oldVal) > 1e-9) {
                    state.setSliderDv(newVal);
                    callbacks.onPreviewNeeded();
                }
            }

            // Update dynamic widgets without allocations
            double currentSliderDv = state.sliderDv();
            if (Math.abs(currentSliderDv - lastSliderDv) > 1e-6) {
                cachedDvLabel = "dV: " + formatFixed1(currentSliderDv);
                lastSliderDv = currentSliderDv;
            }

            boolean hasPreview = state.hasPreview();
            if (hasPreview != lastHasPreview) {
                lastHasPreview = hasPreview;
                lastPreviewTof = -1; // force update
            }

            double currentTimeScale = callbacks.getTimeScale();

            if (hasPreview) {
                if (Math.abs(state.previewTof() - lastPreviewTof) > 1e-6
                    || Math.abs(currentTimeScale - lastTimeScale) > 1e-6) {
                    cachedTof = "Time of Flight: " + formatFixed1(state.previewTof() / Math.max(1e-6, currentTimeScale))
                        + "s";
                    lastPreviewTof = state.previewTof();
                    lastTimeScale = currentTimeScale;
                }
                if (Math.abs(state.previewDvDep() - lastPreviewDvDep) > 1e-6) {
                    cachedDepDv = "Departure dV: " + formatFixed1(state.previewDvDep());
                    lastPreviewDvDep = state.previewDvDep();
                }
                if (Math.abs(state.previewDvCap() - lastPreviewDvCap) > 1e-6) {
                    cachedCapDv = "Capture dV: " + formatFixed1(state.previewDvCap());
                    lastPreviewDvCap = state.previewDvCap();
                }
                if (Math.abs(state.previewTotalDv() - lastPreviewTotalDv) > 1e-6) {
                    cachedTotalDv = "Total dV: " + formatFixed1(state.previewTotalDv());
                    lastPreviewTotalDv = state.previewTotalDv();
                }
            } else {
                if (lastPreviewTof != -2) {
                    cachedTof = "Time of Flight: --";
                    cachedDepDv = "Departure dV: --";
                    cachedCapDv = "Capture dV: --";
                    cachedTotalDv = "Total dV: --";
                    lastPreviewTof = -2;
                }
            }
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!state.isOpen()) return;
            super.drawBackground(context, widgetTheme);
        }

        private void rebuildChildren() {
            String dvText = maxDvField.getText();
            removeAll();

            panelLeft = PANEL_LEFT;
            panelTop = PANEL_TOP;
            ParentWidget<?> panel = new ParentWidget<>().pos(panelLeft, panelTop)
                .size(PANEL_WIDTH, PANEL_HEIGHT);

            // Background
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(
                    drawable(
                        (ctx, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_MODAL_BG.getColor())));
            panel.child(backgroundLayer);
            panel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

            // Title
            panel.child(
                new TextWidget<>(IKey.str("Transfer Planner")).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 12));

            // Close button
            panel.child(
                createButton(
                    "X",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::closeTransferSimulator).pos(PANEL_WIDTH - 30, 8)
                        .size(20, 18));

            // System row
            panel.child(
                createInfoRow(
                    "System:",
                    callbacks.getCurrentSystemBody() != null ? callbacks.getCurrentSystemBody()
                        .displayName() : "None",
                    36));

            // Origin row with pick button
            panel.child(createInfoRow("Origin:", formatBodyLabel(state.originBody(), "None"), 58));
            panel.child(
                createButton(
                    state.pickMode() == TransferPickMode.ORIGIN ? "Picking..." : "Pick",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    () -> callbacks.beginTransferPick(TransferPickMode.ORIGIN)).pos(PANEL_WIDTH - 114, 54)
                        .size(PICK_BUTTON_WIDTH, PICK_BUTTON_HEIGHT));

            // Destination row with pick button
            panel.child(createInfoRow("Destination:", formatBodyLabel(state.destinationBody(), "None"), 82));
            panel.child(
                createButton(
                    state.pickMode() == TransferPickMode.DESTINATION ? "Picking..." : "Pick",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    () -> callbacks.beginTransferPick(TransferPickMode.DESTINATION)).pos(PANEL_WIDTH - 114, 78)
                        .size(PICK_BUTTON_WIDTH, PICK_BUTTON_HEIGHT));

            // Separator
            panel.child(createSeparator(106));

            // Ship dV row: label + field + Set button
            panel.child(
                new TextWidget<>(IKey.str("Ship dV:")).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 116));

            // dV field background
            panel.child(
                new PassiveLayer().pos(80, 112)
                    .size(INPUT_FIELD_WIDTH, INPUT_FIELD_HEIGHT)
                    .background(drawable((ctx, x, y, w, h) -> {
                        BorderedRect.draw(
                            x,
                            y,
                            w,
                            h,
                            EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                            EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor());
                    })));

            panel.child(
                createButton(
                    "Set",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    this::applyMaxDv).pos(168, 112)
                        .size(36, 18));

            panel.child(
                createButton(
                    formatOptimizationModeLabel(state.optimizationMode()),
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    this::toggleOptimizationMode).pos(210, 112)
                        .size(74, 18));

            // Slider row
            double maxDvVal = state.maxDv();
            sliderValue = new DoubleValue(Math.max(0.0, Math.min(maxDvVal, state.sliderDv())));
            SliderWidget slider = new SliderWidget().value(sliderValue)
                .bounds(0.0, maxDvVal)
                .pos(CONTENT_X, 138)
                .size(PANEL_WIDTH - CONTENT_X * 2, 14);
            panel.child(slider);

            // dV label (dynamic: show current sliderDv)
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedDvLabel)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 158));

            // Separator before info
            panel.child(createSeparator(172));

            // Preview info rows
            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedTof)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 180));

            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedDepDv)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 194));

            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedCapDv)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 208));

            panel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedTotalDv)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(CONTENT_X, 222));

            // Dispatch button at bottom
            panel.child(
                createButton(
                    "Stress",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::runLambertStressTest).pos(CONTENT_X, 240)
                        .size(72, 16));

            panel.child(
                createButton(
                    "Dispatch Transfer",
                    EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                    EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor(),
                    callbacks::dispatchTransfer).pos(CONTENT_X + 80, 240)
                        .size(PANEL_WIDTH - 32 - 80, 16));

            // Position maxDv text field
            maxDvField.setText(dvText);
            maxDvField.pos(panelLeft + 80 + 4, panelTop + 112 + 3)
                .size(INPUT_FIELD_WIDTH - 8, INPUT_FIELD_HEIGHT - 6);

            child(panel);
            child(maxDvField);
            scheduleResize();

            // Trigger preview if both bodies are selected and dV is set
            if (state.originBody() != null && state.destinationBody() != null && state.sliderDv() > 0.0) {
                callbacks.onPreviewNeeded();
            }
        }

        private void applyMaxDv() {
            String text = maxDvField.getText()
                .trim()
                .replace(',', '.');
            if (text.isEmpty()) return;
            try {
                double val = Double.parseDouble(text);
                if (val > 0.0) {
                    state.setMaxDv(val);
                    // Start slider at half of max dV for immediate feedback
                    if (state.sliderDv() <= 0.0) state.setSliderDv(val * 0.5);
                    callbacks.onPreviewNeeded();
                    rebuildChildren();
                    lastVersion = state.version();
                }
            } catch (NumberFormatException ignored) {}
        }

        private void toggleOptimizationMode() {
            state.toggleOptimizationMode();
            callbacks.onPreviewNeeded();
            rebuildChildren();
            lastVersion = state.version();
        }

        private String formatOptimizationModeLabel(TransferOptimizationMode mode) {
            if (mode == TransferOptimizationMode.MIN_DV) return "Mode: MIN dV";
            return "Mode: MIN TOF";
        }

        private TextFieldWidget createInputField(String hintText) {
            return new TextFieldWidget().setMaxLength(12)
                .setTextColor(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                .hintText(hintText)
                .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .setFocusOnGuiOpen(false);
        }

        private ParentWidget<?> createInfoRow(String label, String value, int y) {
            ParentWidget<?> row = new ParentWidget<>().pos(CONTENT_X, y)
                .size(PANEL_WIDTH - CONTENT_X * 2, 20);
            row.child(
                new TextWidget<>(IKey.str(label)).color(EnumColors.MAP_COLOR_TEXT_SECTION.getColor())
                    .shadow(true)
                    .pos(0, 0));
            row.child(
                new TextWidget<>(IKey.str(value)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(72, 0)
                    .width(PANEL_WIDTH - CONTENT_X * 2 - 72 - PICK_BUTTON_WIDTH - 12));
            return row;
        }

        private PassiveLayer createSeparator(int y) {
            return new PassiveLayer().pos(CONTENT_X, y)
                .size(PANEL_WIDTH - CONTENT_X * 2, 1)
                .background(
                    drawable(
                        (ctx, x, yy, w, h) -> Gui
                            .drawRect(x, yy, x + w, yy + 1, EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor())));
        }

        private ButtonWidget<?> createButton(String label, int backgroundColor, int borderColor, Runnable onClick) {
            return new ButtonWidget<>()
                .background(
                    drawable((ctx, x, y, w, h) -> { BorderedRect.draw(x, y, w, h, backgroundColor, borderColor); }))
                .hoverBackground(
                    drawable(
                        (ctx, x, y, w, h) -> {
                            BorderedRect
                                .draw(x, y, w, h, EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor(), borderColor);
                        }))
                .overlay(drawable((ctx, x, y, w, h) -> {
                    net.minecraft.client.gui.FontRenderer fr = net.minecraft.client.Minecraft
                        .getMinecraft().fontRenderer;
                    int textW = fr.getStringWidth(label);
                    fr.drawStringWithShadow(
                        label,
                        x + (w - textW) / 2,
                        y + (h - fr.FONT_HEIGHT) / 2 + 1,
                        EnumColors.MAP_COLOR_TEXT_BODY.getColor());
                }))
                .onMousePressed(btn -> {
                    if (btn != 0) return false;
                    onClick.run();
                    return true;
                });
        }

        private String formatBodyLabel(CelestialObject body, String fallback) {
            return body == null ? fallback : body.displayName();
        }

        private IDrawable drawable(DrawableCommand cmd) {
            return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
        }
    }

    // -----------------------------------------------------------------------
    // OrbitalTransferTooltipWidget
    // -----------------------------------------------------------------------

    public static final class OrbitalTransferTooltipWidget extends ParentWidget<OrbitalTransferTooltipWidget> {

        public interface Callbacks {

            InterplanetaryTransferJob getHoveredTransfer();

            int getTooltipMouseX();

            int getTooltipMouseY();

            int getViewportWidth();

            int getViewportHeight();

            double getCurrentTime();

            double getTimeScale();

            double getServerOrbitalTime();
        }

        private static final int PANEL_WIDTH = 190;
        private static final int PANEL_HEIGHT = 76;
        private static final int PADDING = 10;

        private final Callbacks callbacks;
        private InterplanetaryTransferJob activeTransfer;
        private ParentWidget<?> rootPanel;

        private String cachedTitle = "";
        private String cachedInventory = "";
        private String cachedProgress = "";
        private String cachedRemaining = "";

        private long lastProgress = -1;
        private long lastRemaining = -1;

        public OrbitalTransferTooltipWidget(Callbacks callbacks) {
            this.callbacks = callbacks;
            setEnabled(false);
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void onUpdate() {
            super.onUpdate();
            InterplanetaryTransferJob transfer = callbacks.getHoveredTransfer();
            if (transfer == null) {
                if (isEnabled()) {
                    removeAll();
                    scheduleResize();
                }
                activeTransfer = null;
                rootPanel = null;
                setEnabled(false);
                return;
            }
            setEnabled(true);
            if (transfer != activeTransfer) {
                cachedTitle = transfer.displayName();
                cachedInventory = "Inventory " + transfer.inventorySummary();
                rebuildChildren(transfer);
                activeTransfer = transfer;
                lastProgress = -1;
                lastRemaining = -1;
            } else {
                updateTooltipPosition();
            }

            if (activeTransfer != null) {
                double currentTime = effectiveTransferTime(
                    activeTransfer,
                    callbacks.getCurrentTime(),
                    callbacks.getServerOrbitalTime());
                double pct = activeTransfer.progress(currentTime) * 100.0;
                long currentProgress = Math.round(pct);
                if (currentProgress != lastProgress) {
                    cachedProgress = "Progress " + currentProgress + "%";
                    lastProgress = currentProgress;
                }

                double timeScale = Math.max(1e-6, effectiveTransferTimeScale(activeTransfer, callbacks.getTimeScale()));
                double remainingSec = Math.max(0.0, activeTransfer.arrivalTime() - currentTime) / timeScale;
                long currentRemaining = Math.round(remainingSec * 10.0);
                if (currentRemaining != lastRemaining) {
                    cachedRemaining = "Remaining " + formatFixed1(remainingSec) + "s";
                    lastRemaining = currentRemaining;
                }
            }
        }

        private void rebuildChildren(InterplanetaryTransferJob transfer) {
            removeAll();
            rootPanel = new ParentWidget<>().size(PANEL_WIDTH, PANEL_HEIGHT);
            PassiveLayer backgroundLayer = new PassiveLayer().pos(0, 0)
                .widthRel(1f)
                .heightRel(1f)
                .background(
                    drawable(
                        (ctx, x, y, w, h) -> Gui
                            .drawRect(x, y, x + w, y + h, EnumColors.MAP_COLOR_TRANSFER_TOOLTIP_BG.getColor())));
            rootPanel.child(backgroundLayer);
            rootPanel.child(WidgetOutline.create(backgroundLayer, 3, EnumColors.MAP_COLOR_MODAL_ACCENT.getColor()));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedTitle)).color(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
                    .shadow(true)
                    .pos(PADDING, 8));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedInventory)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 24));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedProgress)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 40));

            rootPanel.child(
                new TextWidget<>(IKey.dynamic(() -> cachedRemaining)).color(EnumColors.MAP_COLOR_TEXT_BODY.getColor())
                    .shadow(true)
                    .pos(PADDING, 54));

            updateTooltipPosition();
            child(rootPanel);
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry widgetTheme) {
            if (!isEnabled()) return;
            super.drawBackground(context, widgetTheme);
        }

        private String formatProgress(InterplanetaryTransferJob transfer) {
            double pct = transfer.progress(
                effectiveTransferTime(transfer, callbacks.getCurrentTime(), callbacks.getServerOrbitalTime())) * 100.0;
            return Math.round(pct) + "%";
        }

        private String formatRemaining(InterplanetaryTransferJob transfer) {
            double timeScale = Math.max(1e-6, effectiveTransferTimeScale(transfer, callbacks.getTimeScale()));
            double remaining = Math.max(
                0.0,
                transfer.arrivalTime()
                    - effectiveTransferTime(transfer, callbacks.getCurrentTime(), callbacks.getServerOrbitalTime()))
                / timeScale;
            return formatFixed1(remaining) + "s";
        }

        private void updateTooltipPosition() {
            if (rootPanel == null) return;
            int localMouseX = callbacks.getTooltipMouseX();
            int localMouseY = callbacks.getTooltipMouseY();
            int viewportWidth = callbacks.getViewportWidth();
            int viewportHeight = callbacks.getViewportHeight();
            int left = Math.max(8, localMouseX + 12);
            int top = Math.max(8, localMouseY - PANEL_HEIGHT / 2);
            if (left + PANEL_WIDTH > viewportWidth - 8) left = Math.max(8, localMouseX - 12 - PANEL_WIDTH);
            if (top + PANEL_HEIGHT > viewportHeight - 8) top = Math.max(8, viewportHeight - 8 - PANEL_HEIGHT);
            rootPanel.pos(left, top);
        }

        private IDrawable drawable(DrawableCommand cmd) {
            return (ctx, x, y, w, h, theme) -> cmd.draw(ctx, x, y, w, h);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static String formatFixed1(double value) {
        return Long.toString(Math.round(value * 10.0) / 10L) + "." + Math.abs(Math.round(value * 10.0) % 10L);
    }

}
