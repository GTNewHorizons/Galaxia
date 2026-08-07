package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkClientState;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkGraph;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkState;

/**
 * Draws the satellite communication network on top of a rendered {@link OrbitalScene.OrbitalSceneFrame}: the link
 * threads between bodies, and the packets travelling along them.
 * <p>
 * The overlay owns its own per-frame caches and reads nothing from the map widget, so link geometry, packet timing and
 * bandwidth styling can be exercised without a GL context.
 */
final class SatelliteNetworkOverlay {

    private static final double SIGNAL_WORLD_UNITS_PER_SECOND = 4_700.0D;
    private static final double SIGNAL_MIN_SEGMENT_PIXELS = 16.0D;
    private static final double SIGNAL_SEGMENT_PIXELS_RANGE = 8.0D;
    private static final double SIGNAL_BASE_SECONDS_PER_PACKET = 6.0D;
    private static final double SIGNAL_KBPS_PER_RATE_STEP = 50.0D;
    private static final double SIGNAL_PURPLE_TAIL_PIXELS = 14.0D;
    private static final int SIGNAL_PURPLE_TAIL_STEPS = 5;
    private static final double MAX_FRAME_SECONDS = 0.25D;

    static final SignalStyle KB_STYLE = new SignalStyle(3.0f, 3.0f, 0xFF80D7FF, 0xCC76BFFF);
    static final SignalStyle MB_STYLE = new SignalStyle(4.0f, 4.0f, 0xFF9BAAFF, 0xCC8E98F2);
    static final SignalStyle GB_STYLE = new SignalStyle(5.0f, 5.0f, 0xFFB986FF, 0xCCA578EA);
    static final SignalStyle TB_STYLE = new SignalStyle(6.0f, 6.0f, 0xFFD579FF, 0xCCB86BDD);

    record Endpoint(float centerX, float centerY, float renderedRadius) {}

    record SignalKey(SatelliteNetworkGraph.Edge edge, int direction, boolean keepAlive) {}

    record SignalStyle(float headLength, float headWidth, int headColor, int tailColor) {}

    static final class SignalState {

        private double cooldownSeconds;
        private boolean returning;
        private int packetSequence;
        private final List<SignalPacket> packets = new ArrayList<>();
    }

    static final class SignalPacket {

        private double distanceWorldUnits;
        private final int seed;
        private final SignalStyle style;

        private SignalPacket(double distanceWorldUnits, int seed, SignalStyle style) {
            this.distanceWorldUnits = distanceWorldUnits;
            this.seed = seed;
            this.style = style;
        }
    }

    private final List<SatelliteNetworkGraph.Edge> visibleEdges = new ArrayList<>();
    private final Set<CelestialObjectKey> visibleNodeIds = new HashSet<>();
    private final Map<CelestialObjectKey, Endpoint> endpoints = new HashMap<>();
    private final Map<CelestialObjectKey, OrbitalScene.ResolvedBodyDrawState> worldStates = new HashMap<>();
    private final Map<SignalKey, SignalState> signalStates = new HashMap<>();
    private final float[] threadEndpointScratch = new float[4];
    private long lastFrameMs = System.currentTimeMillis();

    void draw(OrbitalScene.OrbitalSceneFrame frame, float alpha) {
        UUID teamId = GTTeamsCompat.getTeam();
        if (teamId == null || alpha <= 0.01f) return;
        SatelliteNetworkState networkState = SatelliteNetworkClientState.current();
        if (!teamId.equals(networkState.teamId())) {
            updateVisibleEdges(List.of(), Set.of());
            return;
        }

        List<SatelliteNetworkGraph.Edge> snapshotEdges = networkState.links()
            .stream()
            .map(SatelliteNetworkState.Link::asEdge)
            .toList();
        Set<CelestialObjectKey> snapshotBodyIds = networkState.bodies()
            .keySet();
        updateVisibleEdges(snapshotEdges, snapshotBodyIds);
        if (visibleEdges.isEmpty()) return;

        endpoints.clear();
        worldStates.clear();
        for (OrbitalScene.ResolvedBodyDrawState state : frame.resolvedBodies) {
            CelestialObject body = state.body();
            if (body == null || body.objectClass() == CelestialObject.Class.GALAXY
                || body.objectClass() == CelestialObject.Class.STAR
                || !isRenderable(state)
                || !snapshotBodyIds.contains(body.key())) {
                continue;
            }
            worldStates.put(body.key(), state);
            endpoints.put(body.key(), endpointOf(state));
        }

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawThreads(networkState, alpha);
        drawSignals(networkState, alpha);
        GL11.glLineWidth(1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }

    private static boolean isRenderable(OrbitalScene.ResolvedBodyDrawState state) {
        return state.renderBody() && state.bodyAlpha() > 0.01f;
    }

    private static Endpoint endpointOf(OrbitalScene.ResolvedBodyDrawState state) {
        return new Endpoint(state.screenX(), state.screenY(), state.renderedRadius());
    }

    private void updateVisibleEdges(List<SatelliteNetworkGraph.Edge> candidateEdges,
        Set<CelestialObjectKey> candidateNodeIds) {
        if (candidateEdges.isEmpty()) {
            visibleEdges.clear();
            visibleNodeIds.clear();
            return;
        }
        if (!visibleNodeIds.equals(candidateNodeIds) || !visibleEdges.equals(candidateEdges))
            replaceVisibleEdges(candidateEdges, candidateNodeIds);
    }

    private void replaceVisibleEdges(List<SatelliteNetworkGraph.Edge> edges, Collection<CelestialObjectKey> nodeIds) {
        visibleEdges.clear();
        visibleEdges.addAll(edges);
        visibleNodeIds.clear();
        visibleNodeIds.addAll(nodeIds);
    }

    // ── Link threads ──

    private void drawThreads(SatelliteNetworkState networkState, float alpha) {
        drawThreadPass(networkState, alpha, 5.0f, 0.10f);
        drawThreadPass(networkState, alpha, 2.0f, 0.26f);
    }

    private void drawThreadPass(SatelliteNetworkState networkState, float alpha, float width, float opacity) {
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        for (SatelliteNetworkGraph.Edge edge : visibleEdges) {
            drawThread(networkState, edge, alpha * opacity);
        }
        GL11.glEnd();
    }

    private static SatelliteNetworkState.Link linkFor(SatelliteNetworkState networkState,
        SatelliteNetworkGraph.Edge edge) {
        for (SatelliteNetworkState.Link link : networkState.links()) {
            if (link.asEdge()
                .equals(edge)) return link;
        }
        return null;
    }

    private static int bodyColor(SatelliteNetworkState networkState, CelestialObjectKey bodyKey) {
        return SatelliteNetworkLinkColor.forLoad(networkState.usedKbps(bodyKey), networkState.capacityKbps(bodyKey));
    }

    private void drawThread(SatelliteNetworkState networkState, SatelliteNetworkGraph.Edge edge, float alpha) {
        Endpoint from = endpoints.get(edge.from());
        Endpoint to = endpoints.get(edge.to());
        if (from == null || to == null) return;
        float[] ends = threadEndpoints(from, to);
        SatelliteNetworkState.Link link = linkFor(networkState, edge);
        int fromColor = bodyColor(networkState, edge.from());
        int toColor = bodyColor(networkState, edge.to());
        int linkColor = link == null ? SatelliteNetworkLinkColor.GREEN
            : SatelliteNetworkLinkColor.forLoad(link.usedKbps(), link.capacityKbps());

        drawThreadSegment(ends, 0.0D, 0.20D, fromColor, fromColor, alpha);
        drawThreadSegment(ends, 0.20D, 0.30D, fromColor, linkColor, alpha);
        drawThreadSegment(ends, 0.30D, 0.70D, linkColor, linkColor, alpha);
        drawThreadSegment(ends, 0.70D, 0.80D, linkColor, toColor, alpha);
        drawThreadSegment(ends, 0.80D, 1.0D, toColor, toColor, alpha);
    }

    private static void drawThreadSegment(float[] ends, double startProgress, double endProgress, int startColor,
        int endColor, float alpha) {
        StarmapColor.apply(StarmapColor.withAlpha(startColor, alpha));
        GL11.glVertex2f((float) lerp(ends[0], ends[2], startProgress), (float) lerp(ends[1], ends[3], startProgress));
        StarmapColor.apply(StarmapColor.withAlpha(endColor, alpha));
        GL11.glVertex2f((float) lerp(ends[0], ends[2], endProgress), (float) lerp(ends[1], ends[3], endProgress));
    }

    // ── Travelling packets ──

    private void drawSignals(SatelliteNetworkState networkState, float alpha) {
        double elapsedSeconds = frameSeconds();
        Set<SignalKey> activeSignals = new HashSet<>();
        for (SatelliteNetworkGraph.Edge edge : visibleEdges) {
            Endpoint from = endpoints.get(edge.from());
            Endpoint to = endpoints.get(edge.to());
            OrbitalScene.ResolvedBodyDrawState fromState = worldStates.get(edge.from());
            OrbitalScene.ResolvedBodyDrawState toState = worldStates.get(edge.to());
            if (from == null || to == null || fromState == null || toState == null) continue;
            double worldLength = worldLength(fromState, toState);
            long forwardUsage = linkUsage(networkState, edge, edge.from(), edge.to());
            long reverseUsage = linkUsage(networkState, edge, edge.to(), edge.from());
            boolean keepAliveLink = forwardUsage <= 0L && reverseUsage <= 0L;
            if (keepAliveLink) {
                drawKeepAliveSignal(edge, from, to, worldLength, alpha, elapsedSeconds, activeSignals);
                continue;
            }
            drawDataSignalPackets(edge, 0, from, to, worldLength, forwardUsage, alpha, elapsedSeconds, activeSignals);
            drawDataSignalPackets(edge, 1, to, from, worldLength, reverseUsage, alpha, elapsedSeconds, activeSignals);
        }
        signalStates.keySet()
            .removeIf(key -> !activeSignals.contains(key));
    }

    private static long linkUsage(SatelliteNetworkState networkState, SatelliteNetworkGraph.Edge edge,
        CelestialObjectKey source, CelestialObjectKey destination) {
        for (SatelliteNetworkState.Link link : networkState.links()) {
            if (link.asEdge()
                .equals(edge)) return link.usedKbps(source, destination);
        }
        return 0L;
    }

    /** Packet appearance scales with the directional traffic on the link. */
    static SignalStyle signalStyle(long directionalUsageKbps) {
        if (directionalUsageKbps >= 1_000_000_000L) return TB_STYLE;
        if (directionalUsageKbps >= 1_000_000L) return GB_STYLE;
        if (directionalUsageKbps >= 1_000L) return MB_STYLE;
        return KB_STYLE;
    }

    private void drawDataSignalPackets(SatelliteNetworkGraph.Edge edge, int direction, Endpoint from, Endpoint to,
        double linkLengthWorldUnits, long directionalUsage, float alpha, double elapsedSeconds,
        Set<SignalKey> activeSignals) {
        if (directionalUsage <= 0L || linkLengthWorldUnits <= 0.001D) return;
        SignalStyle style = signalStyle(directionalUsage);
        SignalState state = signalState(new SignalKey(edge, direction, false), activeSignals);
        state.cooldownSeconds -= elapsedSeconds;
        if (state.cooldownSeconds <= 0.0D) {
            state.packets
                .add(new SignalPacket(0.0D, signalSeed(edge, direction, state.packetSequence++, false), style));
            state.cooldownSeconds = cooldownSeconds(directionalUsage);
        }

        for (int packetIndex = state.packets.size() - 1; packetIndex >= 0; packetIndex--) {
            SignalPacket packet = state.packets.get(packetIndex);
            /*
             * Store real packet distance instead of deriving packet positions from one stream phase. Satellite
             * links move and change length every frame, so shared phase/spacing makes the whole stream jump when
             * traffic or link length changes. Independent packets keep progress monotonic and expire at the target.
             */
            packet.distanceWorldUnits += elapsedSeconds * SIGNAL_WORLD_UNITS_PER_SECOND;
            if (packet.distanceWorldUnits >= linkLengthWorldUnits) {
                state.packets.remove(packetIndex);
                continue;
            }
            drawSignalPacket(
                from,
                to,
                linkLengthWorldUnits,
                packet.distanceWorldUnits / linkLengthWorldUnits,
                packet.seed,
                alpha,
                packet.style);
        }
    }

    private void drawKeepAliveSignal(SatelliteNetworkGraph.Edge edge, Endpoint from, Endpoint to,
        double linkLengthWorldUnits, float alpha, double elapsedSeconds, Set<SignalKey> activeSignals) {
        if (linkLengthWorldUnits <= 0.001D) return;
        int seed = signalSeed(edge, 0, 0, true);
        SignalState state = signalState(new SignalKey(edge, 0, true), activeSignals);
        if (state.packets.isEmpty()) {
            state.packets.add(new SignalPacket(signalUnit(seed, 8) * linkLengthWorldUnits, seed, KB_STYLE));
        }
        SignalPacket packet = state.packets.get(0);
        packet.distanceWorldUnits += elapsedSeconds * SIGNAL_WORLD_UNITS_PER_SECOND;
        while (packet.distanceWorldUnits >= linkLengthWorldUnits) {
            packet.distanceWorldUnits -= linkLengthWorldUnits;
            state.returning = !state.returning;
        }
        double headProgress = packet.distanceWorldUnits / linkLengthWorldUnits;
        if (state.returning) {
            drawSignalPacket(to, from, linkLengthWorldUnits, headProgress, seed, alpha, KB_STYLE);
            return;
        }
        drawSignalPacket(from, to, linkLengthWorldUnits, headProgress, seed, alpha, KB_STYLE);
    }

    private SignalState signalState(SignalKey key, Set<SignalKey> activeSignals) {
        activeSignals.add(key);
        return signalStates.computeIfAbsent(key, ignored -> new SignalState());
    }

    /** Busier links emit packets more often, but the rate grows logarithmically so a fast link stays readable. */
    static double cooldownSeconds(long directionalUsage) {
        return SIGNAL_BASE_SECONDS_PER_PACKET / (1.0D + Math.log1p(directionalUsage / SIGNAL_KBPS_PER_RATE_STEP));
    }

    private void drawSignalPacket(Endpoint from, Endpoint to, double linkLengthWorldUnits, double headProgress,
        int seed, float alpha, SignalStyle style) {
        float[] ends = threadEndpoints(from, to);
        double dx = ends[2] - ends[0];
        double dy = ends[3] - ends[1];
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001D || linkLengthWorldUnits <= 0.001D) return;
        double segmentLengthPixels = signalSegmentLength(seed, 16);
        double tailStartProgress = Math.max(0.0D, headProgress - segmentLengthPixels / length);
        double ex = lerp(ends[0], ends[2], headProgress);
        double ey = lerp(ends[1], ends[3], headProgress);
        drawPurpleSignalTail(ends, length, tailStartProgress, headProgress, alpha, true, style);
        drawSignalHead(ends, length, ex, ey, alpha, style);
    }

    private static void drawPurpleSignalTail(float[] ends, double length, double startProgress, double endProgress,
        float alpha, boolean reserveHeadGap, SignalStyle style) {
        float headLength = style.headLength();
        double headGap = reserveHeadGap ? (headLength * 0.6D) / length : 0.0D;
        double tailEndProgress = Math.max(startProgress, endProgress - headGap);
        double tailProgress = Math.min(SIGNAL_PURPLE_TAIL_PIXELS, (tailEndProgress - startProgress) * length) / length;
        if (tailProgress <= 0.0D) return;
        float widthScale = style.headWidth() / KB_STYLE.headWidth();
        for (int i = 0; i < SIGNAL_PURPLE_TAIL_STEPS; i++) {
            double stepEnd = tailEndProgress - tailProgress * i / SIGNAL_PURPLE_TAIL_STEPS;
            double stepStart = tailEndProgress - tailProgress * (i + 1) / SIGNAL_PURPLE_TAIL_STEPS;
            stepStart = Math.max(startProgress, stepStart);
            if (stepEnd <= stepStart) continue;
            double sx = lerp(ends[0], ends[2], stepStart);
            double sy = lerp(ends[1], ends[3], stepStart);
            double ex = lerp(ends[0], ends[2], stepEnd);
            double ey = lerp(ends[1], ends[3], stepEnd);
            drawSegmentQuad(
                sx,
                sy,
                ex,
                ey,
                (3.2f - 0.4f * i) * widthScale,
                style.tailColor(),
                alpha,
                0.72f - 0.13f * i);
        }
    }

    private static void drawSegmentQuad(double sx, double sy, double ex, double ey, float width, int color, float alpha,
        float opacity) {
        double dx = ex - sx;
        double dy = ey - sy;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001D) return;
        double px = -dy / length * width / 2.0D;
        double py = dx / length * width / 2.0D;
        StarmapColor.apply(StarmapColor.withAlpha(color, alpha * opacity));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f((float) (sx + px), (float) (sy + py));
        GL11.glVertex2f((float) (ex + px), (float) (ey + py));
        GL11.glVertex2f((float) (ex - px), (float) (ey - py));
        GL11.glVertex2f((float) (sx - px), (float) (sy - py));
        GL11.glEnd();
    }

    private static void drawSignalHead(float[] ends, double length, double x, double y, float alpha,
        SignalStyle style) {
        double ux = (ends[2] - ends[0]) / length;
        double uy = (ends[3] - ends[1]) / length;
        drawHeadQuad(x, y, ux, uy, style.headLength(), style.headWidth(), style.headColor(), alpha, 1.0f);
    }

    private static void drawHeadQuad(double x, double y, double ux, double uy, float length, float width, int color,
        float alpha, float opacity) {
        double halfLength = length / 2.0D;
        double halfWidth = width / 2.0D;
        double px = -uy * halfWidth;
        double py = ux * halfWidth;
        double hx = ux * halfLength;
        double hy = uy * halfLength;
        StarmapColor.apply(StarmapColor.withAlpha(color, alpha * opacity));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f((float) (x - hx + px), (float) (y - hy + py));
        GL11.glVertex2f((float) (x + hx + px), (float) (y + hy + py));
        GL11.glVertex2f((float) (x + hx - px), (float) (y + hy - py));
        GL11.glVertex2f((float) (x - hx - px), (float) (y - hy - py));
        GL11.glEnd();
    }

    // ── Geometry and determinism ──

    private double frameSeconds() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = Math.max(0.0D, (now - lastFrameMs) / 1000.0D);
        lastFrameMs = now;
        return Math.min(elapsedSeconds, MAX_FRAME_SECONDS);
    }

    private static double worldLength(OrbitalScene.ResolvedBodyDrawState from, OrbitalScene.ResolvedBodyDrawState to) {
        double dx = to.worldX() - from.worldX();
        double dy = to.worldY() - from.worldY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Every packet's jitter is derived from its link, direction and sequence, so it stays stable across frames. */
    static int signalSeed(SatelliteNetworkGraph.Edge edge, int direction, int packetIndex, boolean keepAlive) {
        int seed = edge.from()
            .hashCode() * 73471
            ^ edge.to()
                .hashCode() * 19349663
            ^ direction * 0x7f4a7c15
            ^ packetIndex * 0x9e3779b9
            ^ (keepAlive ? 0x45d9f3b : 0);
        return mixSeed(seed);
    }

    private static int mixSeed(int seed) {
        seed ^= seed >>> 16;
        seed *= 0x7feb352d;
        seed ^= seed >>> 15;
        return seed;
    }

    static double signalUnit(int seed, int shift) {
        return ((seed >>> shift) & 0xFF) / 255.0D;
    }

    static double signalSegmentLength(int seed, int shift) {
        return SIGNAL_MIN_SEGMENT_PIXELS + signalUnit(seed, shift) * SIGNAL_SEGMENT_PIXELS_RANGE;
    }

    /**
     * Trims both ends of the link so the thread starts at each body's edge rather than its centre. The returned array
     * is reused between calls and must not be retained.
     */
    float[] threadEndpoints(Endpoint from, Endpoint to) {
        float dx = to.centerX() - from.centerX();
        float dy = to.centerY() - from.centerY();
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.001f) {
            threadEndpointScratch[0] = from.centerX();
            threadEndpointScratch[1] = from.centerY();
            threadEndpointScratch[2] = to.centerX();
            threadEndpointScratch[3] = to.centerY();
            return threadEndpointScratch;
        }
        float nx = dx / len;
        float ny = dy / len;
        threadEndpointScratch[0] = from.centerX() + nx * Math.max(0f, from.renderedRadius() + 2f);
        threadEndpointScratch[1] = from.centerY() + ny * Math.max(0f, from.renderedRadius() + 2f);
        threadEndpointScratch[2] = to.centerX() - nx * Math.max(0f, to.renderedRadius() + 2f);
        threadEndpointScratch[3] = to.centerY() - ny * Math.max(0f, to.renderedRadius() + 2f);
        return threadEndpointScratch;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
