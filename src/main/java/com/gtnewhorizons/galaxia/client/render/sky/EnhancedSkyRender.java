package com.gtnewhorizons.galaxia.client.render.sky;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import jss.util.RandomXoshiro256StarStar;

public final class EnhancedSkyRender {

    private EnhancedSkyRender() {}

    private static World currentWorld;

    public static void setCurrentWorld(World world) {
        currentWorld = world;
    }

    public static World getCurrentWorld() {
        return currentWorld;
    }

    public static void clearCurrentWorld() {
        currentWorld = null;
    }

    /**
     * One global preset used for now. Later this can become dimension-specific.
     */
    public static final SkyPreset DEFAULT_PRESET = new SkyPreset("default");

    /**
     * Optional per-dimension/per-planet presets.
     */
    private static final Map<Integer, SkyPreset> PRESETS_BY_DIMENSION = new LinkedHashMap<>();

    /**
     * Fallback seed so generated sky content stays stable.
     */
    private static final long BASE_SEED = 10842L;

    static {
        // Default baseline: no fancy assumptions about art direction.
        // Designers can replace textures later.
        DEFAULT_PRESET.brightStars(64, 0.25f, 0.85f, true)
            .billboardLayer(
                new BillboardLayer(LocationGalaxia("textures/sky/nebula_01.png"), 22, 6.0f, 0.20f, 0.95f, 0.15f))
            .billboardLayer(
                new BillboardLayer(LocationGalaxia("textures/sky/quasar_01.png"), 5, 1.8f, 0.45f, 1.00f, 0.35f))
            .domeLayer(new DomeLayer(LocationGalaxia("textures/sky/milky_way.png"), 1.0f, 0.55f, 0.20f));
    }

    /**
     * Registers a preset for a specific dimension id.
     * Call this during init.
     */
    public static void registerPreset(int dimensionId, SkyPreset preset) {
        PRESETS_BY_DIMENSION.put(dimensionId, preset);
    }

    public static void registerPreset(SkyPreset preset, int... dimensionIds) {
        for (int dimId : dimensionIds) {
            registerPreset(dimId, preset);
        }
    }

    /**
     * Returns the active preset for the dimension, or the default preset.
     */
    public static SkyPreset getPreset(World world) {
        if (world == null || world.provider == null) {
            return null;
        }
        SkyPreset preset = PRESETS_BY_DIMENSION.get(world.provider.dimensionId);
        return preset != null ? preset : DEFAULT_PRESET;
    }

    /**
     * Public wrapper for Angelica's baked star-list path.
     */
    public static void renderBakedSkyLayers(World world) {
        renderPrebakedGlobalLayers(world, true);
    }

    /**
     * Renders additional sky layers: bright stars, billboard objects, and dome overlays.
     *
     * @param world                The world whose sky should be rendered.
     * @param bakedIntoDisplayList True if we are baking into a list; false if rendering every frame.
     */
    private static void renderPrebakedGlobalLayers(World world, boolean bakedIntoDisplayList) {
        SkyPreset preset = getPreset(world);
        if (preset == null) {
            return;
        }

        if (preset.brightStarCount > 0) {
            renderBrightStars(world, preset, bakedIntoDisplayList);
        }

        for (BillboardLayer layer : preset.billboardLayers) {
            renderBillboardLayer(world, preset, layer, bakedIntoDisplayList);
        }

        for (DomeLayer layer : preset.domeLayers) {
            renderDomeLayer(world, preset, layer, bakedIntoDisplayList);
        }
    }

    /**
     * Bigger stars: same geometry style, larger size and optional color variation.
     *
     * These are meant to look like bright stars, not like nearby objects.
     */
    private static void renderBrightStars(World world, SkyPreset preset, boolean bakedIntoDisplayList) {
        if (preset.brightStarCount <= 0) {
            return;
        }

        final RandomXoshiro256StarStar random = new RandomXoshiro256StarStar(BASE_SEED ^ 0xA53D7F11L);
        final Tessellator tessellator = Tessellator.instance;
        final float intensity = 1.0f;

        // These are rendered as extra quads in the same celestial sphere space.
        tessellator.startDrawingQuads();
        for (int i = 0; i < preset.brightStarCount; ++i) {
            Vector3 dir = randomUnitSphere(random);
            if (dir == null) {
                continue;
            }

            // Rarer and more prominent than vanilla stars.
            double radius = 100.0D;
            double starX = dir.x * radius;
            double starY = dir.y * radius;
            double starZ = dir.z * radius;

            float size = lerp(preset.brightStarMinSize, preset.brightStarMaxSize, random.nextFloat()) * intensity;
            double rotation = random.nextDouble() * Math.PI * 2.0D;
            double sinRot = Math.sin(rotation);
            double cosRot = Math.cos(rotation);

            // Slight color variation, but keep it stylistic and not too literal.
            float r = 1.0f;
            float g = 1.0f;
            float b = 1.0f;
            if (preset.colorfulBrightStars) {
                int tint = random.nextInt(5);
                if (tint == 1) {
                    r = 0.95f;
                    g = 0.98f;
                    b = 1.0f;
                } else if (tint == 2) {
                    r = 1.0f;
                    g = 0.95f;
                    b = 0.88f;
                } else if (tint == 3) {
                    r = 1.0f;
                    g = 0.88f;
                    b = 0.84f;
                } else if (tint == 4) {
                    r = 0.88f;
                    g = 0.92f;
                    b = 1.0f;
                }
            }

            GL11.glColor4f(r, g, b, preset.brightStarAlpha);
            for (int corner = 0; corner < 4; ++corner) {
                double offU = (double) ((corner & 2) - 1) * size;
                double offV = (double) ((corner + 1 & 2) - 1) * size;
                double rotU = offU * cosRot - offV * sinRot;
                double rotV = offV * cosRot + offU * sinRot;
                tessellator.addVertex(starX + rotU, starY + rotV, starZ);
            }
        }
        tessellator.draw();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /**
     * Renders billboard layers such as nebulae or distant galaxies.
     *
     * The object is placed on the celestial sphere and optionally made visible even in daylight.
     */
    private static void renderBillboardLayer(World world, SkyPreset preset, BillboardLayer layer, boolean bakedIntoDisplayList) {
        if (layer == null || layer.texture == null || layer.count <= 0) {
            return;
        }

        float dayFactor = computeNightFactor(world, layer.dayVisibilityMin, layer.dayVisibilityMax);
        if (dayFactor <= 0.001f) {
            return;
        }

        RandomXoshiro256StarStar random = new RandomXoshiro256StarStar(BASE_SEED ^ layer.seedSalt);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(layer.texture);

        GL11.glPushMatrix();
        applySkyFacingTransform(world);
        setupTexturedSkyBlend(true);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        for (int i = 0; i < layer.count; ++i) {
            Vector3 dir = randomUnitSphere(random);
            if (dir == null) {
                continue;
            }

            float size = lerp(layer.minSize, layer.maxSize, random.nextFloat());
            double depth = 100.0D;
            double cx = dir.x * depth;
            double cy = dir.y * depth;
            double cz = dir.z * depth;

            OrthoBasis basis = OrthoBasis.fromDirection(dir);
            float alpha = layer.alpha * dayFactor;

            if (layer.jitterRotation) {
                double rot = random.nextDouble() * Math.PI * 2.0D;
                basis = basis.rotatedAroundForward(rot);
            }

            addTexturedBillboardQuad(t, cx, cy, cz, basis, size, size, 0.0f, 0.0f, 1.0f, 1.0f, alpha);
        }

        t.draw();
        restoreSkyState();
        GL11.glPopMatrix();
    }

    /**
     * Renders a full-sky dome overlay.
     *
     * This is ideal for the Milky Way because it should feel like a texture wrapped around the heavens.
     * The texture should be equirectangular.
     */
    private static void renderDomeLayer(World world, SkyPreset preset, DomeLayer layer, boolean bakedIntoDisplayList) {
        if (layer == null || layer.texture == null || layer.opacity <= 0.001f) {
            return;
        }

        float dayFactor = computeNightFactor(world, 0.0f, 1.0f);
        if (dayFactor <= 0.001f && !layer.allowDayVisible) {
            return;
        }

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(layer.texture);

        GL11.glPushMatrix();
        applySkyFacingTransform(world);
        setupTexturedSkyBlend(false);
        GL11.glColor4f(1f, 1f, 1f, layer.opacity * lerp(layer.minVisibility, layer.maxVisibility, dayFactor));

        drawTexturedDome(layer.radius, layer.segmentsLon, layer.segmentsLat, layer.textureVOffset);

        restoreSkyState();
        GL11.glPopMatrix();
    }

    /**
     * Applies the same sky-space direction as the vanilla RenderGlobal star list.
     * We keep it simple: the caller is expected to be in the sky render context already.
     */
    private static void applySkyFacingTransform(World world) {
        // Vanilla sky list usually runs after a -90 degree yaw in RenderGlobal.
        // We avoid the ground-facing orientation so all layers share the same distant-space feel.
        GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Sets up additive blending for bright sky elements.
     */
    private static void setupAdditiveSkyBlend(BillboardLayer layer) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
    }

    /**
     * Restores a sane baseline after additive sky drawing.
     */
    private static void restoreSkyBlend() {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /**
     * Draws one billboard quad using a basis aligned to the object direction.
     */
    private static void addTexturedBillboardQuad(Tessellator t, double cx, double cy, double cz, OrthoBasis basis,
                                                 float width, float height, float u0, float v0, float u1, float v1, float alpha) {
        double hx = basis.right.x * width;
        double hy = basis.right.y * width;
        double hz = basis.right.z * width;

        double vx = basis.up.x * height;
        double vy = basis.up.y * height;
        double vz = basis.up.z * height;

        GL11.glColor4f(1f, 1f, 1f, alpha);
        t.addVertexWithUV(cx - hx - vx, cy - hy - vy, cz - hz - vz, u1, v1);
        t.addVertexWithUV(cx + hx - vx, cy + hy - vy, cz + hz - vz, u0, v1);
        t.addVertexWithUV(cx + hx + vx, cy + hy + vy, cz + hz + vz, u0, v0);
        t.addVertexWithUV(cx - hx + vx, cy - hy + vy, cz - hz + vz, u1, v0);
    }

    /**
     * Simple low-cost dome mesh for equirectangular textures.
     * This is static content, so a modest tessellation is enough.
     */
    private static void drawTexturedDome(float radius, int segmentsLon, int segmentsLat, float textureVOffset) {
        final int lon = Math.max(8, segmentsLon);
        final int lat = Math.max(4, segmentsLat);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        for (int y = 0; y < lat; ++y) {
            float vA = (float) y / (float) lat;
            float vB = (float) (y + 1) / (float) lat;
            float thetaA = (float) (vA * Math.PI * 0.5D);
            float thetaB = (float) (vB * Math.PI * 0.5D);

            float sinA = MathHelper.sin(thetaA);
            float cosA = MathHelper.cos(thetaA);
            float sinB = MathHelper.sin(thetaB);
            float cosB = MathHelper.cos(thetaB);

            for (int x = 0; x < lon; ++x) {
                float uA = (float) x / (float) lon;
                float uB = (float) (x + 1) / (float) lon;
                float phiA = (float) (uA * Math.PI * 2.0D);
                float phiB = (float) (uB * Math.PI * 2.0D);

                float sinPhiA = MathHelper.sin(phiA);
                float cosPhiA = MathHelper.cos(phiA);
                float sinPhiB = MathHelper.sin(phiB);
                float cosPhiB = MathHelper.cos(phiB);

                // Inside-facing dome: camera is in the center looking outward.
                double x1 = -cosA * sinPhiA * radius;
                double y1 = sinA * radius;
                double z1 = cosA * cosPhiA * radius;

                double x2 = -cosA * sinPhiB * radius;
                double y2 = sinA * radius;
                double z2 = cosA * cosPhiB * radius;

                double x3 = -cosB * sinPhiB * radius;
                double y3 = sinB * radius;
                double z3 = cosB * cosPhiB * radius;

                double x4 = -cosB * sinPhiA * radius;
                double y4 = sinB * radius;
                double z4 = cosB * cosPhiA * radius;

                // Simple equirectangular mapping.
                float uu0 = uA;
                float uu1 = uB;
                float vv0 = 1.0f - vA + textureVOffset;
                float vv1 = 1.0f - vB + textureVOffset;

                t.addVertexWithUV(x1, y1, z1, uu0, vv0);
                t.addVertexWithUV(x2, y2, z2, uu1, vv0);
                t.addVertexWithUV(x3, y3, z3, uu1, vv1);
                t.addVertexWithUV(x4, y4, z4, uu0, vv1);
            }
        }

        t.draw();
    }

    /**
     * Computes how visible a layer should be given the time of day.
     *
     * minVisible and maxVisible define the fade range against daylight.
     * 0 = fully hidden in day, 1 = fully visible always.
     */
    private static float computeNightFactor(World world, float minVisible, float maxVisible) {
        if (world == null) {
            return 1.0f;
        }

        float celestial = world.getCelestialAngle(1.0F);
        float dayNight = 1.0f - (MathHelper.cos(celestial * (float) Math.PI * 2.0F) * 2.0F + 0.2F);
        dayNight = MathHelper.clamp_float(dayNight, 0.0F, 1.0F);
        dayNight = 1.0f - dayNight;

        float result = minVisible + (maxVisible - minVisible) * (1.0f - dayNight);
        return MathHelper.clamp_float(result, 0.0F, 1.0F);
    }

    /**
     * Helper for a unit sphere vector.
     */
    private static Vector3 randomUnitSphere(RandomXoshiro256StarStar random) {
        for (int attempts = 0; attempts < 8; ++attempts) {
            float x = random.nextFloat() * 2.0F - 1.0F;
            float y = random.nextFloat() * 2.0F - 1.0F;
            float z = random.nextFloat() * 2.0F - 1.0F;
            float lenSq = x * x + y * y + z * z;
            if (lenSq > 0.01f && lenSq < 1.0f) {
                float inv = 1.0f / (float) Math.sqrt(lenSq);
                return new Vector3(x * inv, y * inv, z * inv);
            }
        }
        return null;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Data model for a per-dimension sky preset.
     * Use chainable helpers to keep registration readable.
     */
    public static final class SkyPreset {

        private final String name;
        private int brightStarCount = 0;
        private float brightStarMinSize = 0.25f;
        private float brightStarMaxSize = 0.85f;
        private float brightStarAlpha = 1.0f;
        private boolean colorfulBrightStars = true;
        private final List<BillboardLayer> billboardLayers = new ArrayList<>();
        private final List<DomeLayer> domeLayers = new ArrayList<>();

        public SkyPreset(String name) {
            this.name = name;
        }

        public SkyPreset brightStars(int count, float minSize, float maxSize, boolean colorful) {
            this.brightStarCount = count;
            this.brightStarMinSize = minSize;
            this.brightStarMaxSize = maxSize;
            this.colorfulBrightStars = colorful;
            return this;
        }

        public SkyPreset brightStarAlpha(float alpha) {
            this.brightStarAlpha = alpha;
            return this;
        }

        public SkyPreset billboardLayer(BillboardLayer layer) {
            this.billboardLayers.add(layer);
            return this;
        }

        public SkyPreset domeLayer(DomeLayer layer) {
            this.domeLayers.add(layer);
            return this;
        }

        public String name() {
            return name;
        }
    }

    /**
     * Tiny factory helpers so registrations stay one-liners.
     */
    public static SkyPreset preset(String name) {
        return new SkyPreset(name);
    }

    public static BillboardLayer billboard(ResourceLocation texture, int count, float minSize, float maxSize,
                                           float alpha, float dayMin, float dayMax) {
        return new BillboardLayer(texture, count, minSize, maxSize, alpha, dayMin, dayMax);
    }

    public static DomeLayer dome(ResourceLocation texture, float opacity, float minVisibility, float maxVisibility) {
        return new DomeLayer(texture, opacity, minVisibility, maxVisibility);
    }

    public static DomeLayer dome(ResourceLocation texture, float opacity, float minVisibility, float maxVisibility,
                                 float radius, int segmentsLon, int segmentsLat, float textureVOffset, boolean allowDayVisible) {
        return new DomeLayer(
            texture,
            opacity,
            minVisibility,
            maxVisibility,
            radius,
            segmentsLon,
            segmentsLat,
            textureVOffset,
            allowDayVisible);
    }

    private static void setupTexturedSkyBlend(boolean additive) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);

        if (additive) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        GL11.glDepthMask(false);
    }

    private static void restoreSkyState() {
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /**
     * A texture-based object placed on the sky sphere as a billboard quad.
     */
    public static final class BillboardLayer {

        private final ResourceLocation texture;
        private final int count;
        private final float minSize;
        private final float maxSize;
        private final float alpha;
        private final float dayVisibilityMin;
        private final float dayVisibilityMax;
        private final boolean jitterRotation;
        private final long seedSalt;

        public BillboardLayer(ResourceLocation texture, int count, float minSize, float maxSize,
                              float dayVisibilityMin, float dayVisibilityMax) {
            this(texture, count, minSize, maxSize, 1.0f, dayVisibilityMin, dayVisibilityMax, true, 0x515A7E11L);
        }

        public BillboardLayer(ResourceLocation texture, int count, float minSize, float maxSize, float alpha,
                              float dayVisibilityMin, float dayVisibilityMax) {
            this(texture, count, minSize, maxSize, alpha, dayVisibilityMin, dayVisibilityMax, true, 0x515A7E11L);
        }

        public BillboardLayer(ResourceLocation texture, int count, float minSize, float maxSize, float alpha,
                              float dayVisibilityMin, float dayVisibilityMax, boolean jitterRotation, long seedSalt) {
            this.texture = texture;
            this.count = count;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.alpha = alpha;
            this.dayVisibilityMin = dayVisibilityMin;
            this.dayVisibilityMax = dayVisibilityMax;
            this.jitterRotation = jitterRotation;
            this.seedSalt = seedSalt;
        }
    }

    /**
     * A full-sky layer, usually for a Milky Way style overlay.
     */
    public static final class DomeLayer {

        private final ResourceLocation texture;
        private final float opacity;
        private final float minVisibility;
        private final float maxVisibility;
        private final float radius;
        private final int segmentsLon;
        private final int segmentsLat;
        private final float textureVOffset;
        private final boolean allowDayVisible;

        public DomeLayer(ResourceLocation texture, float opacity, float minVisibility, float maxVisibility) {
            this(texture, opacity, minVisibility, maxVisibility, 120.0f, 48, 24, 0.0f, false);
        }

        public DomeLayer(ResourceLocation texture, float opacity, float minVisibility, float maxVisibility,
                         float radius, int segmentsLon, int segmentsLat, float textureVOffset, boolean allowDayVisible) {
            this.texture = texture;
            this.opacity = opacity;
            this.minVisibility = minVisibility;
            this.maxVisibility = maxVisibility;
            this.radius = radius;
            this.segmentsLon = segmentsLon;
            this.segmentsLat = segmentsLat;
            this.textureVOffset = textureVOffset;
            this.allowDayVisible = allowDayVisible;
        }
    }

    /**
     * Minimal vector class for sky basis construction.
     */
    private record Vector3(float x, float y, float z) {

        Vector3 normalize() {
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len <= 0.0001f) {
                return new Vector3(0f, 1f, 0f);
            }
            return new Vector3(x / len, y / len, z / len);
        }

        Vector3 cross(Vector3 other) {
            return new Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }

        Vector3 scale(float s) {
            return new Vector3(x * s, y * s, z * s);
        }

        Vector3 add(Vector3 other) {
            return new Vector3(x + other.x, y + other.y, z + other.z);
        }
    }

    private record OrthoBasis(Vector3 right, Vector3 up, Vector3 forward) {

        static OrthoBasis fromDirection(Vector3 dir) {
            Vector3 forward = dir.normalize();
            Vector3 helper = Math.abs(forward.y) > 0.95f ? new Vector3(1f, 0f, 0f) : new Vector3(0f, 1f, 0f);
            Vector3 right = helper.cross(forward)
                .normalize();
            Vector3 up = forward.cross(right)
                .normalize();
            return new OrthoBasis(right, up, forward);
        }

        OrthoBasis rotatedAroundForward(double angle) {
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);

            Vector3 newRight = right.scale((float) cos)
                .add(up.scale((float) sin))
                .normalize();
            Vector3 newUp = forward.cross(newRight)
                .normalize();
            return new OrthoBasis(newRight, newUp, forward);
        }
    }
}
