package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.function.DoubleUnaryOperator;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidStarmapProjection;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryCapability;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryClientState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryScanSnapshot;

final class AsteroidStarmapScenePresentation {

    private static final int BELT_BAND_COLOR = 0x226E7480;
    private static final int BELT_BAND_SEGMENTS = 192;

    private AsteroidStarmapScenePresentation() {}

    static boolean isBeltContainer(CelestialObject body) {
        return body != null && body.isAsteroidBelt();
    }

    /** Only asteroids gate their label on discovery; every other body labels by default. */
    static boolean drawsDefaultBodyLabel(CelestialObject body) {
        if (body == null) return false;
        if (!isAsteroid(body)) return true;
        return CelestialClient.asteroidProjection(body)
            .map(AsteroidStarmapProjection::drawDefaultLabel)
            .orElse(false);
    }

    static void drawProspectingScanRanges(OrbitalScene.OrbitalSceneFrame frame, double scale) {
        for (OrbitalScene.ResolvedBodyDrawState state : frame.resolvedBodies) {
            if (!isAsteroid(state.body()) || !state.renderBody()) continue;
            double radius = CelestialDiscoveryClientState.scan(
                state.body()
                    .key(),
                CelestialDiscoveryCapability.PROSPECTING)
                .filter(snapshot -> snapshot.status() == CelestialDiscoveryScanSnapshot.Status.ACTIVE)
                .map(CelestialDiscoveryScanSnapshot::radius)
                .orElse(0.0);
            float screenRadius = (float) (radius * scale);
            if (screenRadius < 1.0f) continue;
            drawCircleOutline(
                state.screenX(),
                state.screenY(),
                screenRadius,
                EnumColors.MAP_COLOR_DEBUG_HITBOX.getColor(),
                0.45f,
                1.2f);
        }
    }

    static void drawBeltBand(AsteroidFieldProfile profile, double parentX, double parentY, float alpha, double scale,
        DoubleUnaryOperator worldToScreenX, DoubleUnaryOperator worldToScreenY) {
        if (profile == null || alpha <= 0.01f) return;
        double innerRadius = profile.innerOrbitalRadius();
        double outerRadius = profile.outerOrbitalRadius();
        if (outerRadius <= innerRadius || innerRadius <= 0.0) return;
        if (outerRadius * scale < 1.0 || (outerRadius - innerRadius) * scale < 0.5) return;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int color = StarmapColor.withAlpha(BELT_BAND_COLOR, alpha);
        GlStateManager.color(
            ((color >> 16) & 0xFF) / 255f,
            ((color >> 8) & 0xFF) / 255f,
            (color & 0xFF) / 255f,
            ((color >> 24) & 0xFF) / 255f);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= BELT_BAND_SEGMENTS; i++) {
            double angle = i * Math.PI * 2.0 / BELT_BAND_SEGMENTS;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            GL11.glVertex2d(
                worldToScreenX.applyAsDouble(parentX + outerRadius * cos),
                worldToScreenY.applyAsDouble(parentY + outerRadius * sin));
            GL11.glVertex2d(
                worldToScreenX.applyAsDouble(parentX + innerRadius * cos),
                worldToScreenY.applyAsDouble(parentY + innerRadius * sin));
        }
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
    }

    private static boolean isAsteroid(CelestialObject body) {
        return body != null && body.isAsteroid();
    }

    private static void drawCircleOutline(float x, float y, float radius, int color, float alpha, float lineWidth) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, alpha);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI * 2.0 / 64.0;
            GL11.glVertex2f(x + (float) Math.cos(angle) * radius, y + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

}
