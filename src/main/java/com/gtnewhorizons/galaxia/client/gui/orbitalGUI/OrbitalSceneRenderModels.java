package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

final class OrbitalSceneFrame {

    final List<ResolvedBodyDrawState> resolvedBodies = new ArrayList<>();
    final IdentityHashMap<OrbitalCelestialBody, ResolvedBodyDrawState> resolvedBodiesByBody = new IdentityHashMap<>();
    final List<ScreenBodyBounds> screenBodies = new ArrayList<>();
    final List<LabelDrawCall> labelDrawCalls = new ArrayList<>();
    final List<MarkerDrawCall> markerDrawCalls = new ArrayList<>();
}

final class ScreenBodyBounds {

    final OrbitalCelestialBody body;
    final float centerX;
    final float centerY;
    final float renderedRadius;
    final float interactionRadius;

    ScreenBodyBounds(OrbitalCelestialBody body, float centerX, float centerY, float renderedRadius,
        float interactionRadius) {
        this.body = body;
        this.centerX = centerX;
        this.centerY = centerY;
        this.renderedRadius = renderedRadius;
        this.interactionRadius = interactionRadius;
    }

    double bodyScore(float x, float y) {
        double dx = x - centerX;
        double dy = y - centerY;
        double absDx = Math.abs(dx);
        double absDy = Math.abs(dy);
        if (absDx > interactionRadius || absDy > interactionRadius) {
            return Double.MAX_VALUE;
        }
        double normalizedDx = absDx / Math.max(1.0, interactionRadius);
        double normalizedDy = absDy / Math.max(1.0, interactionRadius);
        return Math.max(normalizedDx, normalizedDy);
    }
}

final class ResolvedBodyDrawState {

    final OrbitalCelestialBody body;
    final OrbitalCelestialBody parent;
    final double worldX;
    final double worldY;
    final float screenX;
    final float screenY;
    final float renderedRadius;
    final float bodyAlpha;
    final boolean renderBody;
    final boolean drawLabel;
    final float labelY;
    final int labelColor;

    ResolvedBodyDrawState(OrbitalCelestialBody body, OrbitalCelestialBody parent, double worldX, double worldY,
        float screenX, float screenY, float renderedRadius, float bodyAlpha, boolean renderBody, boolean drawLabel,
        float labelY, int labelColor) {
        this.body = body;
        this.parent = parent;
        this.worldX = worldX;
        this.worldY = worldY;
        this.screenX = screenX;
        this.screenY = screenY;
        this.renderedRadius = renderedRadius;
        this.bodyAlpha = bodyAlpha;
        this.renderBody = renderBody;
        this.drawLabel = drawLabel;
        this.labelY = labelY;
        this.labelColor = labelColor;
    }
}

final class LabelDrawCall {

    final String text;
    final float x;
    final float y;
    final int color;

    LabelDrawCall(String text, float x, float y, int color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }
}

final class MarkerDrawCall {

    final ResourceLocation texture;
    final int x;
    final int y;
    final int size;
    final float alpha;

    MarkerDrawCall(ResourceLocation texture, int x, int y, int size, float alpha) {
        this.texture = texture;
        this.x = x;
        this.y = y;
        this.size = size;
        this.alpha = alpha;
    }
}
