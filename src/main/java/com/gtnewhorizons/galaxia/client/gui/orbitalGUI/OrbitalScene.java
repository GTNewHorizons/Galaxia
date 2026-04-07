package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.utils.GlStateManager;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.orbital.Hierarchy.OrbitalParams;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

public class OrbitalScene {

    private static final double[] ZERO_VIEW_ORIGIN = { 0.0, 0.0 };

    static final class ResolvedBodyDrawState {

        private OrbitalCelestialBody body;
        private OrbitalCelestialBody parent;
        private double worldX;
        private double worldY;
        private float screenX;
        private float screenY;
        private float renderedRadius;
        private float bodyAlpha;
        private boolean renderBody;
        private boolean drawLabel;
        private float labelY;
        private int labelColor;

        void set(OrbitalCelestialBody body, OrbitalCelestialBody parent, double worldX, double worldY, float screenX,
            float screenY, float renderedRadius, float bodyAlpha, boolean renderBody, boolean drawLabel, float labelY,
            int labelColor) {
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

        OrbitalCelestialBody body() {
            return body;
        }

        OrbitalCelestialBody parent() {
            return parent;
        }

        double worldX() {
            return worldX;
        }

        double worldY() {
            return worldY;
        }

        float screenX() {
            return screenX;
        }

        float screenY() {
            return screenY;
        }

        float renderedRadius() {
            return renderedRadius;
        }

        float bodyAlpha() {
            return bodyAlpha;
        }

        boolean renderBody() {
            return renderBody;
        }

        boolean drawLabel() {
            return drawLabel;
        }

        float labelY() {
            return labelY;
        }

        int labelColor() {
            return labelColor;
        }
    }

    static final class ScreenBodyBounds {

        private OrbitalCelestialBody body;
        private float centerX;
        private float centerY;
        private float renderedRadius;
        private float interactionRadius;

        void set(OrbitalCelestialBody body, float centerX, float centerY, float renderedRadius,
            float interactionRadius) {
            this.body = body;
            this.centerX = centerX;
            this.centerY = centerY;
            this.renderedRadius = renderedRadius;
            this.interactionRadius = interactionRadius;
        }

        OrbitalCelestialBody body() {
            return body;
        }

        float centerX() {
            return centerX;
        }

        float centerY() {
            return centerY;
        }

        float renderedRadius() {
            return renderedRadius;
        }

        float interactionRadius() {
            return interactionRadius;
        }

        double bodyScore(float x, float y) {
            double dx = x - centerX;
            double dy = y - centerY;
            double absDx = Math.abs(dx);
            double absDy = Math.abs(dy);
            if (absDx > interactionRadius || absDy > interactionRadius) return Double.MAX_VALUE;
            double normalizedDx = absDx / Math.max(1.0, interactionRadius);
            double normalizedDy = absDy / Math.max(1.0, interactionRadius);
            return Math.max(normalizedDx, normalizedDy);
        }
    }

    static final class LabelDrawCall {

        private String text;
        private float x;
        private float y;
        private int color;

        void set(String text, float x, float y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }

        String text() {
            return text;
        }

        float x() {
            return x;
        }

        float y() {
            return y;
        }

        int color() {
            return color;
        }
    }

    static final class MarkerDrawCall {

        private ResourceLocation texture;
        private int x;
        private int y;
        private int size;
        private float alpha;

        void set(ResourceLocation texture, int x, int y, int size, float alpha) {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.size = size;
            this.alpha = alpha;
        }

        ResourceLocation texture() {
            return texture;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        int size() {
            return size;
        }

        float alpha() {
            return alpha;
        }
    }

    public static final class OrbitalSceneFrame {

        final List<ResolvedBodyDrawState> resolvedBodies = new ArrayList<>();
        final IdentityHashMap<OrbitalCelestialBody, ResolvedBodyDrawState> resolvedBodiesByBody = new IdentityHashMap<>();
        final List<ScreenBodyBounds> screenBodies = new ArrayList<>();
        final List<LabelDrawCall> labelDrawCalls = new ArrayList<>();
        final List<MarkerDrawCall> markerDrawCalls = new ArrayList<>();
        private final List<ResolvedBodyDrawState> resolvedBodyPool = new ArrayList<>();
        private final List<ScreenBodyBounds> screenBodyPool = new ArrayList<>();
        private final List<LabelDrawCall> labelPool = new ArrayList<>();
        private final List<MarkerDrawCall> markerPool = new ArrayList<>();
        private int resolvedBodyPoolIndex = 0;
        private int screenBodyPoolIndex = 0;
        private int labelPoolIndex = 0;
        private int markerPoolIndex = 0;

        void resetForReuse() {
            resolvedBodies.clear();
            resolvedBodiesByBody.clear();
            screenBodies.clear();
            labelDrawCalls.clear();
            markerDrawCalls.clear();
            resolvedBodyPoolIndex = 0;
            screenBodyPoolIndex = 0;
            labelPoolIndex = 0;
            markerPoolIndex = 0;
        }

        ResolvedBodyDrawState addResolvedBody(OrbitalCelestialBody body, OrbitalCelestialBody parent, double worldX,
            double worldY, float screenX, float screenY, float renderedRadius, float bodyAlpha, boolean renderBody,
            boolean drawLabel, float labelY, int labelColor) {
            ResolvedBodyDrawState state = resolvedBodyPoolIndex < resolvedBodyPool.size()
                ? resolvedBodyPool.get(resolvedBodyPoolIndex)
                : createResolvedBody();
            resolvedBodyPoolIndex++;
            state.set(
                body,
                parent,
                worldX,
                worldY,
                screenX,
                screenY,
                renderedRadius,
                bodyAlpha,
                renderBody,
                drawLabel,
                labelY,
                labelColor);
            resolvedBodies.add(state);
            resolvedBodiesByBody.put(body, state);
            return state;
        }

        ScreenBodyBounds addScreenBody(OrbitalCelestialBody body, float centerX, float centerY, float renderedRadius,
            float interactionRadius) {
            ScreenBodyBounds bounds = screenBodyPoolIndex < screenBodyPool.size()
                ? screenBodyPool.get(screenBodyPoolIndex)
                : createScreenBody();
            screenBodyPoolIndex++;
            bounds.set(body, centerX, centerY, renderedRadius, interactionRadius);
            screenBodies.add(bounds);
            return bounds;
        }

        LabelDrawCall addLabel(String text, float x, float y, int color) {
            LabelDrawCall call = labelPoolIndex < labelPool.size() ? labelPool.get(labelPoolIndex) : createLabel();
            labelPoolIndex++;
            call.set(text, x, y, color);
            labelDrawCalls.add(call);
            return call;
        }

        MarkerDrawCall addMarker(ResourceLocation texture, int x, int y, int size, float alpha) {
            MarkerDrawCall call = markerPoolIndex < markerPool.size() ? markerPool.get(markerPoolIndex)
                : createMarker();
            markerPoolIndex++;
            call.set(texture, x, y, size, alpha);
            markerDrawCalls.add(call);
            return call;
        }

        private ResolvedBodyDrawState createResolvedBody() {
            ResolvedBodyDrawState state = new ResolvedBodyDrawState();
            resolvedBodyPool.add(state);
            return state;
        }

        private ScreenBodyBounds createScreenBody() {
            ScreenBodyBounds bounds = new ScreenBodyBounds();
            screenBodyPool.add(bounds);
            return bounds;
        }

        private LabelDrawCall createLabel() {
            LabelDrawCall call = new LabelDrawCall();
            labelPool.add(call);
            return call;
        }

        private MarkerDrawCall createMarker() {
            MarkerDrawCall call = new MarkerDrawCall();
            markerPool.add(call);
            return call;
        }
    }

    public static final class OrbitalSceneFrameBuilder {

        interface Callbacks {

            double[] getViewOrigin(OrbitalCelestialBody viewRoot);

            void fillResolvedBodyDrawState(ResolvedBodyDrawState out, OrbitalCelestialBody body,
                OrbitalCelestialBody parent, double worldX, double worldY, float labelAlpha);

            boolean shouldTraverseChildren(OrbitalCelestialBody body);

            float getInteractionRadius(float renderedRadius);

            boolean isOnScreen(float sx, float sy, float radius);
        }

        private final Callbacks callbacks;
        private final CelestialMarkerBase.CelestialMarkerContext markerContext = new CelestialMarkerBase.CelestialMarkerContext();

        OrbitalSceneFrameBuilder(Callbacks callbacks) {
            this.callbacks = callbacks;
        }

        OrbitalSceneFrame buildInto(OrbitalSceneFrame frame, OrbitalCelestialBody viewRoot, double globalTime,
            float labelAlpha) {
            frame.resetForReuse();
            double[] viewOrigin = callbacks.getViewOrigin(viewRoot);
            if (viewOrigin == null) viewOrigin = ZERO_VIEW_ORIGIN;
            collectRecursive(frame, viewRoot, null, viewOrigin[0], viewOrigin[1], globalTime, labelAlpha);
            return frame;
        }

        private void collectRecursive(OrbitalSceneFrame frame, OrbitalCelestialBody body, OrbitalCelestialBody parent,
            double worldX, double worldY, double globalTime, float labelAlpha) {
            ResolvedBodyDrawState state = frame
                .addResolvedBody(body, parent, worldX, worldY, 0f, 0f, 0f, 0f, false, false, 0f, 0);
            callbacks.fillResolvedBodyDrawState(state, body, parent, worldX, worldY, labelAlpha);
            if (state.body()
                .objectClass() != CelestialObjectClass.GALAXY && state.bodyAlpha() > 0.01f
                && state.renderBody()) {
                registerHitboxes(frame, state);
                registerMarkers(frame, state);
            }
            if (state.drawLabel()) {
                frame.addLabel(
                    state.body()
                        .displayName(),
                    state.screenX(),
                    state.labelY(),
                    state.labelColor());
            }
            if (!callbacks.shouldTraverseChildren(body)) return;
            for (OrbitalCelestialBody child : body.children()) {
                OrbitalMechanics.OrbitalState childWorldState = OrbitalView.OrbitalWorldStateCache
                    .resolveChildWorldState(body, child, worldX, worldY, globalTime);
                collectRecursive(frame, child, body, childWorldState.x(), childWorldState.y(), globalTime, labelAlpha);
            }
        }

        private void registerHitboxes(OrbitalSceneFrame frame, ResolvedBodyDrawState state) {
            float interactionRadius = callbacks.getInteractionRadius(state.renderedRadius());
            float maxRadius = Math.max(state.renderedRadius(), interactionRadius);
            if (!callbacks.isOnScreen(state.screenX(), state.screenY(), maxRadius)) return;
            frame.addScreenBody(
                state.body(),
                state.screenX(),
                state.screenY(),
                state.renderedRadius(),
                interactionRadius);
        }

        private void registerMarkers(OrbitalSceneFrame frame, ResolvedBodyDrawState state) {
            CelestialMarkerBase.CelestialMarkerContext context = markerContext.set(
                state.body(),
                CelestialAssetStore.getStateIfPresent(
                    state.body()
                        .id()));
            List<CelestialMarkerBase.CelestialMarker> markers = CelestialMarkerBase.CelestialMarkerRegistry
                .getMarkers(context);
            if (markers.isEmpty()) return;
            int iconSize = Math.max(10, Math.min(15, Math.round(state.renderedRadius() * 0.95f)));
            int gap = 3;
            int startX = Math.round(state.screenX() + state.renderedRadius() + 6f);
            int topY = Math.round(state.screenY() - state.renderedRadius());
            for (int i = 0; i < markers.size(); i++) {
                CelestialMarkerBase.CelestialMarker marker = markers.get(i);
                int markerX = startX + i * (iconSize + gap);
                frame.addMarker(marker.texture(), markerX, topY, iconSize, state.bodyAlpha() * marker.alpha());
            }
        }
    }

    public static final class OrbitalSceneRenderer {

        interface Callbacks {

            double getScale();

            float worldToScreenX(double wx);

            float worldToScreenY(double wy);

            ResourceLocation getRenderTexture(OrbitalCelestialBody body);

            float getDisplaySpriteSize(OrbitalCelestialBody body);

            float getSelectionBoxRadius(ScreenBodyBounds bounds);

            ResourceLocation getAssetIconTexture(CelestialAssetKind kind);
        }

        private static final float MAP_LABEL_SCALE = 0.82f;
        private static final int GALAXY_TITLE_TOP = 10;
        private static final int GALAXY_TITLE_HEIGHT = 21;
        private static final int SOI_FILL_COLOR = 0x3300FF66;
        private final Callbacks callbacks;

        OrbitalSceneRenderer(Callbacks callbacks) {
            this.callbacks = callbacks;
        }

        void drawBodies(OrbitalSceneFrame frame, OrbitalCelestialBody viewRoot) {
            for (ResolvedBodyDrawState state : frame.resolvedBodies) {
                if (state.body()
                    .objectClass() == CelestialObjectClass.GALAXY || state.bodyAlpha() <= 0.01f
                    || !state.renderBody()) continue;
                ResourceLocation texture = callbacks.getRenderTexture(state.body());
                if (texture != null && callbacks.getDisplaySpriteSize(state.body()) > 0.0001f) {
                    drawSprite(texture, state.screenX(), state.screenY(), state.renderedRadius(), state.bodyAlpha());
                } else {
                    int color = getFallbackBodyColor(
                        state.body()
                            .objectClass());
                    float radius = state.body() == viewRoot ? 11f : 7f;
                    drawFilledCircle(state.screenX(), state.screenY(), radius, color, state.bodyAlpha());
                }
            }
        }

        void drawSpheresOfInfluence(OrbitalSceneFrame frame) {
            for (ResolvedBodyDrawState state : frame.resolvedBodies) {
                if (state.parent() == null || state.bodyAlpha() <= 0.01f || !state.renderBody()) continue;
                if (state.body()
                    .objectClass() == CelestialObjectClass.GALAXY
                    || state.body()
                        .objectClass() == CelestialObjectClass.STAR) {
                    continue;
                }
                double soiRadius = OrbitalMechanics.getSphereOfInfluenceRadius(state.parent(), state.body());
                if (soiRadius <= 1e-6) continue;
                float screenRadius = (float) (soiRadius * callbacks.getScale());
                if (screenRadius < 1.0f) continue;
                drawFilledCircle(state.screenX(), state.screenY(), screenRadius, SOI_FILL_COLOR, state.bodyAlpha());
            }
        }

        void drawOrbits(OrbitalSceneFrame frame, float ellipseAlpha) {
            if (ellipseAlpha <= 0.01f) return;
            for (ResolvedBodyDrawState state : frame.resolvedBodies) {
                if (state.parent() == null || !state.renderBody()
                    || OrbitalView.OrbitalWorldStateCache.usesAbsolutePosition(state.parent(), state.body())) continue;
                ResolvedBodyDrawState parentState = frame.resolvedBodiesByBody.get(state.parent());
                if (parentState == null) continue;
                drawEllipse(
                    state.body()
                        .orbitalParams(),
                    parentState.worldX(),
                    parentState.worldY(),
                    ellipseAlpha);
            }
        }

        void drawCollectedLabels(OrbitalSceneFrame frame) {
            for (LabelDrawCall label : frame.labelDrawCalls)
                drawCenteredString(label.text(), label.x(), label.y(), label.color());
        }

        void drawCollectedMarkers(OrbitalSceneFrame frame) {
            for (MarkerDrawCall marker : frame.markerDrawCalls)
                drawUiSprite(marker.texture(), marker.x(), marker.y(), marker.size(), marker.alpha());
        }

        void drawSelectionHighlight(OrbitalCelestialBody body, OrbitalSceneFrame frame) {
            ScreenBodyBounds bounds = findScreenBodyBounds(frame, body);
            if (bounds == null) return;
            float box = callbacks.getSelectionBoxRadius(bounds);
            int labelY = (int) (bounds.centerY() - box - 22);
            drawSelectionOverlay(bounds.centerX(), bounds.centerY(), box, 1.0f);
            drawCenteredString(body.displayName(), bounds.centerX(), labelY, 0xFFFFFFFF);
        }

        void drawHoverHighlight(OrbitalCelestialBody body, OrbitalSceneFrame frame) {
            ScreenBodyBounds bounds = findScreenBodyBounds(frame, body);
            if (bounds == null) return;
            drawSelectionOverlay(bounds.centerX(), bounds.centerY(), callbacks.getSelectionBoxRadius(bounds), 0.45f);
        }

        void drawDebugOverlay(OrbitalSceneFrame frame, int widgetHeight) {
            Minecraft mc = Minecraft.getMinecraft();
            Gui.drawRect(8, widgetHeight - 36, 182, widgetHeight - 8, 0x990B111C);
            mc.fontRenderer.drawStringWithShadow("Debug: body hitzones", 14, widgetHeight - 30, 0xFF7FFFD4);
            mc.fontRenderer.drawStringWithShadow("Toggle: B", 14, widgetHeight - 18, 0xFFB8C7D9);
            for (ScreenBodyBounds bounds : frame.screenBodies) {
                drawSquareOutline(
                    bounds.centerX(),
                    bounds.centerY(),
                    bounds.interactionRadius(),
                    0xFF00E5FF,
                    0.95f,
                    1.5f);
                Gui.drawRect(
                    Math.round(bounds.centerX()) - 1,
                    Math.round(bounds.centerY()) - 1,
                    Math.round(bounds.centerX()) + 1,
                    Math.round(bounds.centerY()) + 1,
                    0xFF9BFF7A);
            }
        }

        void drawViewTitleBanner(OrbitalCelestialBody viewRoot, int widgetWidth) {
            if (viewRoot == null) return;
            String title = viewRoot.objectClass() == CelestialObjectClass.GALAXY ? viewRoot.displayName()
                : viewRoot.objectClass() == CelestialObjectClass.STAR ? viewRoot.displayName() + " System" : null;
            if (title == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            int textWidth = mc.fontRenderer.getStringWidth(title);
            float centerX = widgetWidth / 2f;
            int top = GALAXY_TITLE_TOP;
            int bottom = top + GALAXY_TITLE_HEIGHT;
            float bottomHalfWidth = Math.max(74f, textWidth / 2f + 28f);
            float topHalfWidth = bottomHalfWidth + 8f;
            drawFilledTrapezoid(centerX, top, bottom, topHalfWidth, bottomHalfWidth, 0xEE162133);
            drawTrapezoidOutline(centerX, top, bottom, topHalfWidth, bottomHalfWidth, 0xFF7FB6FF, 1.4f);
            drawCenteredBannerString(title, centerX, top + 7, 0xFFFFFFFF);
        }

        void drawAssetIcon(CelestialAssetKind kind, int x, int y, int size, float alpha) {
            ResourceLocation texture = callbacks.getAssetIconTexture(kind);
            if (texture != null) drawUiSprite(texture, x, y, size, alpha);
        }

        private ScreenBodyBounds findScreenBodyBounds(OrbitalSceneFrame frame, OrbitalCelestialBody body) {
            for (int i = frame.screenBodies.size() - 1; i >= 0; i--) {
                ScreenBodyBounds bounds = frame.screenBodies.get(i);
                if (bounds.body() == body) return bounds;
            }
            return null;
        }

        private void drawSprite(ResourceLocation tex, float x, float y, float radius, float alpha) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(tex);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, alpha);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(x - radius, y + radius, 0, 0, 1);
            tess.addVertexWithUV(x + radius, y + radius, 0, 1, 1);
            tess.addVertexWithUV(x + radius, y - radius, 0, 1, 0);
            tess.addVertexWithUV(x - radius, y - radius, 0, 0, 0);
            tess.draw();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        private void drawUiSprite(ResourceLocation tex, int x, int y, int size, float alpha) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(tex);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, alpha);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(x, y + size, 0, 0, 1);
            tess.addVertexWithUV(x + size, y + size, 0, 1, 1);
            tess.addVertexWithUV(x + size, y, 0, 1, 0);
            tess.addVertexWithUV(x, y, 0, 0, 0);
            tess.draw();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        private void drawFilledCircle(float x, float y, float r, int color, float alpha) {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            float red = ((color >> 16) & 0xFF) / 255f;
            float green = ((color >> 8) & 0xFF) / 255f;
            float blue = (color & 0xFF) / 255f;
            float baseAlpha = ((color >> 24) & 0xFF) / 255f;
            GlStateManager.color(red, green, blue, baseAlpha * alpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(x, y);
            for (int i = 0; i <= 32; i++) {
                double a = i * Math.PI * 2.0 / 32.0;
                GL11.glVertex2f(x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r);
            }
            GL11.glEnd();
            GL11.glEnable(GL11.GL_CULL_FACE);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }

        private void drawSquareOutline(float x, float y, float halfSize, int color, float alpha, float lineWidth) {
            GlStateManager.disableTexture2D();
            float red = ((color >> 16) & 0xFF) / 255f;
            float green = ((color >> 8) & 0xFF) / 255f;
            float blue = (color & 0xFF) / 255f;
            GlStateManager.color(red, green, blue, alpha);
            GL11.glLineWidth(lineWidth);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x - halfSize, y - halfSize);
            GL11.glVertex2f(x + halfSize, y - halfSize);
            GL11.glVertex2f(x + halfSize, y + halfSize);
            GL11.glVertex2f(x - halfSize, y + halfSize);
            GL11.glEnd();
            GL11.glLineWidth(1f);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }

        private void drawCenteredString(String text, float x, float y, int color) {
            Minecraft mc = Minecraft.getMinecraft();
            int w = mc.fontRenderer.getStringWidth(text);
            GlStateManager.pushMatrix();
            GlStateManager.scale(MAP_LABEL_SCALE, MAP_LABEL_SCALE, 1f);
            mc.fontRenderer.drawStringWithShadow(
                text,
                Math.round((x / MAP_LABEL_SCALE) - (w / 2f)),
                Math.round(y / MAP_LABEL_SCALE),
                color);
            GlStateManager.popMatrix();
        }

        private void drawCenteredBannerString(String text, float x, float y, int color) {
            Minecraft mc = Minecraft.getMinecraft();
            int w = mc.fontRenderer.getStringWidth(text);
            mc.fontRenderer.drawStringWithShadow(text, Math.round(x - w / 2f), Math.round(y), color);
        }

        private void drawFilledTrapezoid(float centerX, int top, int bottom, float topHalfWidth, float bottomHalfWidth,
            int color) {
            prepareFilledShapeDraw(color);
            for (int y = top; y < bottom; y++) {
                float t = (y - top) / (float) Math.max(1, bottom - top);
                float halfWidth = topHalfWidth + (bottomHalfWidth - topHalfWidth) * t;
                int left = Math.round(centerX - halfWidth);
                int right = Math.round(centerX + halfWidth);
                Gui.drawRect(left, y, right, y + 1, color);
            }
            finishFilledShapeDraw();
        }

        private void drawTrapezoidOutline(float centerX, int top, int bottom, float topHalfWidth, float bottomHalfWidth,
            int color, float lineWidth) {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float red = ((color >> 16) & 0xFF) / 255f;
            float green = ((color >> 8) & 0xFF) / 255f;
            float blue = (color & 0xFF) / 255f;
            float alpha = ((color >> 24) & 0xFF) / 255f;
            GlStateManager.color(red, green, blue, alpha);
            GL11.glLineWidth(lineWidth);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(centerX - topHalfWidth, top);
            GL11.glVertex2f(centerX + topHalfWidth, top);
            GL11.glVertex2f(centerX + bottomHalfWidth, bottom);
            GL11.glVertex2f(centerX - bottomHalfWidth, bottom);
            GL11.glEnd();
            GL11.glLineWidth(1f);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.enableTexture2D();
        }

        private void prepareFilledShapeDraw(int color) {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            float red = ((color >> 16) & 0xFF) / 255f;
            float green = ((color >> 8) & 0xFF) / 255f;
            float blue = (color & 0xFF) / 255f;
            float alpha = ((color >> 24) & 0xFF) / 255f;
            GlStateManager.color(red, green, blue, alpha);
        }

        private void finishFilledShapeDraw() {
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GlStateManager.enableTexture2D();
        }

        private void drawEllipse(OrbitalParams p, double parentX, double parentY, float alpha) {
            double a = p.semiMajorAxis();
            if (a < 1e-8) return;
            double e = p.eccentricity();
            double b = a * Math.sqrt(Math.max(0.0, 1.0 - e * e));
            double rot = p.argumentOfPeriapsis();
            GlStateManager.disableTexture2D();
            GlStateManager.color(1f, 1f, 1f, alpha * 0.92f);
            GL11.glLineWidth((float) Math.max(1.4, callbacks.getScale() * 0.035));
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i <= 360; i++) {
                double E = i * Math.PI * 2.0 / 360.0;
                double ex = a * (Math.cos(E) - e);
                double ey = b * Math.sin(E);
                double rx = ex * Math.cos(rot) - ey * Math.sin(rot);
                double ry = ex * Math.sin(rot) + ey * Math.cos(rot);
                GL11.glVertex2d(callbacks.worldToScreenX(parentX + rx), callbacks.worldToScreenY(parentY + ry));
            }
            GL11.glEnd();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }

        private void drawSelectionOverlay(float centerX, float centerY, float boxSize, float alpha) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int color = withAlpha(0xFF18C8FF, alpha);
            int thickness = 2;
            int left = Math.round(centerX - boxSize);
            int right = Math.round(centerX + boxSize);
            int top = Math.round(centerY - boxSize);
            int bottom = Math.round(centerY + boxSize);
            int corner = Math.max(5, Math.min(12, Math.round(boxSize * 0.55f)));
            drawCorner(left, top, corner, thickness, true, true, color);
            drawCorner(right, top, corner, thickness, false, true, color);
            drawCorner(left, bottom, corner, thickness, true, false, color);
            drawCorner(right, bottom, corner, thickness, false, false, color);
        }

        private void drawCorner(int x, int y, int length, int thickness, boolean leftAligned, boolean topAligned,
            int color) {
            int horizontalStart = leftAligned ? x : x - length;
            int horizontalEnd = leftAligned ? x + length : x;
            int horizontalTop = topAligned ? y : y - thickness;
            int horizontalBottom = topAligned ? y + thickness : y;
            int verticalLeft = leftAligned ? x : x - thickness;
            int verticalRight = leftAligned ? x + thickness : x;
            int verticalTop = topAligned ? y : y - length;
            int verticalBottom = topAligned ? y + length : y;
            Gui.drawRect(horizontalStart, horizontalTop, horizontalEnd, horizontalBottom, color);
            Gui.drawRect(verticalLeft, verticalTop, verticalRight, verticalBottom, color);
        }

        private int withAlpha(int color, float alpha) {
            int a = Math.max(0, Math.min(255, (int) (((color >> 24) & 0xFF) * alpha)));
            return (color & 0x00FFFFFF) | (a << 24);
        }

        private int getFallbackBodyColor(CelestialObjectClass objectClass) {
            return switch (objectClass) {
                case GALAXY -> 0xFFFFFFFF;
                case BLACK_HOLE -> 0xFF5A4B7A;
                case STAR -> 0xFFFFD36B;
                case GAS_GIANT -> 0xFFD9A066;
                case PLANET -> 0xFF7FC7A6;
                case MOON -> 0xFFD8DCE6;
                case ASTEROID, ASTEROID_BELT -> 0xFF9CA3AF;
                case STATION -> 0xFF89C2FF;
                case COMET -> 0xFFAEE7FF;
            };
        }
    }
}
