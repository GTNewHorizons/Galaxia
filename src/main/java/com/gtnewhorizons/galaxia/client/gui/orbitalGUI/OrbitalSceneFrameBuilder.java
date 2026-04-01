package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.List;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;

final class OrbitalSceneFrameBuilder {

    interface Callbacks {

        double[] getViewOrigin(OrbitalCelestialBody viewRoot);

        ResolvedBodyDrawState resolveBodyDrawState(OrbitalCelestialBody body, OrbitalCelestialBody parent,
            double worldX, double worldY, float labelAlpha);

        boolean shouldTraverseChildren(OrbitalCelestialBody body);

        float getInteractionRadius(float renderedRadius);

        boolean isOnScreen(float sx, float sy, float radius);
    }

    private final Callbacks callbacks;

    OrbitalSceneFrameBuilder(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    OrbitalSceneFrame build(OrbitalCelestialBody viewRoot, double globalTime, float labelAlpha) {
        OrbitalSceneFrame frame = new OrbitalSceneFrame();
        double[] viewOrigin = callbacks.getViewOrigin(viewRoot);
        if (viewOrigin == null) {
            viewOrigin = new double[] { 0.0, 0.0 };
        }
        collectRecursive(frame, viewRoot, null, viewOrigin[0], viewOrigin[1], globalTime, labelAlpha);
        return frame;
    }

    private void collectRecursive(OrbitalSceneFrame frame, OrbitalCelestialBody body, OrbitalCelestialBody parent,
        double worldX, double worldY, double globalTime, float labelAlpha) {
        ResolvedBodyDrawState state = callbacks.resolveBodyDrawState(body, parent, worldX, worldY, labelAlpha);
        frame.resolvedBodies.add(state);
        frame.resolvedBodiesByBody.put(body, state);

        if (state.body.objectClass() != com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass.GALAXY
            && state.bodyAlpha > 0.01f && state.renderBody) {
            registerHitboxes(frame, state);
            registerMarkers(frame, state);
        }
        if (state.drawLabel) {
            frame.labelDrawCalls.add(new LabelDrawCall(state.body.displayName(), state.screenX, state.labelY, state.labelColor));
        }

        if (!callbacks.shouldTraverseChildren(body)) {
            return;
        }

        for (OrbitalCelestialBody child : body.children()) {
            double[] childWorldPos = OrbitalWorldStateCache.resolveChildWorldPos(body, child, worldX, worldY, globalTime);
            collectRecursive(frame, child, body, childWorldPos[0], childWorldPos[1], globalTime, labelAlpha);
        }
    }

    private void registerHitboxes(OrbitalSceneFrame frame, ResolvedBodyDrawState state) {
        float interactionRadius = callbacks.getInteractionRadius(state.renderedRadius);
        float maxRadius = Math.max(state.renderedRadius, interactionRadius);
        if (!callbacks.isOnScreen(state.screenX, state.screenY, maxRadius)) {
            return;
        }
        frame.screenBodies.add(
            new ScreenBodyBounds(
                state.body,
                state.screenX,
                state.screenY,
                state.renderedRadius,
                interactionRadius));
    }

    private void registerMarkers(OrbitalSceneFrame frame, ResolvedBodyDrawState state) {
        CelestialMarkerContext context = new CelestialMarkerContext(
            state.body,
            CelestialAssetStore.getStateIfPresent(state.body.id()));
        List<CelestialMarker> markers = CelestialMarkerRegistry.getMarkers(context);
        if (markers.isEmpty()) {
            return;
        }

        int iconSize = Math.max(10, Math.min(15, Math.round(state.renderedRadius * 0.95f)));
        int gap = 3;
        int startX = Math.round(state.screenX + state.renderedRadius + 6f);
        int topY = Math.round(state.screenY - state.renderedRadius);

        for (int i = 0; i < markers.size(); i++) {
            CelestialMarker marker = markers.get(i);
            int markerX = startX + i * (iconSize + gap);
            frame.markerDrawCalls.add(
                new MarkerDrawCall(marker.texture(), markerX, topY, iconSize, state.bodyAlpha * marker.alpha()));
        }
    }
}
