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
        DEFAULT_PRESET.brightStars(64, 0.25f, 0.85f, true)
            .billboardLayer(
                new BillboardLayer(LocationGalaxia("textures/sky/nebula_01.png"), 22, 6.0f, 0.20f, 0.15f, 0.95f))
            .billboardLayer(
                new BillboardLayer(LocationGalaxia("textures/sky/quasar_01.png"), 5, 1.8f, 0.45f, 0.35f, 1.00f))
            .domeLayer(new DomeLayer(LocationGalaxia("textures/sky/milky_way.png"), 1.0f, 0.20f, 0.55f));
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
    public static void renderBakedSkyLayers(World world, float partialTicks) {
        SkyPreset preset = getPreset(world);
        if (preset == null) {
            return;
        }

        if (preset.brightStarCount > 0) {
            renderBrightStars(world, preset, partialTicks);
        }

        for (BillboardLayer layer : preset.billboardLayers) {
            renderBillboardLayer(world, preset, layer, partialTicks);
        }

        for (DomeLayer layer : preset.domeLayers) {
            renderDomeLayer(world, preset, layer, partialTicks);
        }
    }

    /**
     * Bigger stars: same geometry style, larger size and optional color variation.
     *
     * These are meant to look like bright stars, not like nearby objects.
     */
    private static void applySkyFacingTransform(World world, float partialTicks) {
        GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
        float celestialAngle = world.getCelestialAngle(partialTicks);
        GL11.glRotatef(180.0F + celestialAngle * 360.0F, 1.0F, 0.0F, 0.0F);
    }

    private static void renderBrightStars(World world, SkyPreset preset, float partialTicks) {
        float starBrightness = world.getStarBrightness(partialTicks);
        if (starBrightness <= 0.0F) return;

        final RandomXoshiro256StarStar random = new RandomXoshiro256StarStar(BASE_SEED ^ 0xA53D7F11L);
        final Tessellator tessellator = Tessellator.instance;

        GL11.glPushMatrix();
        applySkyFacingTransform(world, partialTicks);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(false);

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

            float size = lerp(preset.brightStarMinSize, preset.brightStarMaxSize, random.nextFloat()) * starBrightness;
            double rotation = random.nextDouble() * Math.PI * 2.0D;
            double sinRot = Math.sin(rotation);
            double cosRot = Math.cos(rotation);

            float r = 1.0f, g = 1.0f, b = 1.0f;
            if (preset.colorfulBrightStars) {
                int tint = random.nextInt(5);
                if (tint == 1) {
                    r = 0.95f;
                    g = 0.98f;
                    b = 1.00f;
                } else if (tint == 2) {
                    r = 1.00f;
                    g = 0.95f;
                    b = 0.88f;
                } else if (tint == 3) {
                    r = 1.00f;
                    g = 0.88f;
                    b = 0.84f;
                } else if (tint == 4) {
                    r = 0.88f;
                    g = 0.92f;
                    b = 1.00f;
                }
            }

            tessellator.setColorRGBA_F(r, g, b, preset.brightStarAlpha * starBrightness);

            for (int corner = 0; corner < 4; ++corner) {
                double offU = (double) ((corner & 2) - 1) * size;
                double offV = (double) ((corner + 1 & 2) - 1) * size;
                double rotU = offU * cosRot - offV * sinRot;
                double rotV = offV * cosRot + offU * sinRot;
                tessellator.addVertex(starX + rotU, starY + rotV, starZ);
            }
        }
        tessellator.draw();

        // Restore state
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glPopMatrix();
    }

    /**
     * Billboard layers (nebulae, quasars, etc.)
     * Each object is a tangent-plane quad on the celestial sphere
     */
    private static void renderBillboardLayer(World world, SkyPreset preset, BillboardLayer layer, float partialTicks) {
        if (layer == null || layer.texture == null || layer.count <= 0) return;

        float dayFactor = computeNightFactor(world, layer.dayVisibilityMin, layer.dayVisibilityMax, partialTicks);
        if (dayFactor <= 0.001f) return;

        RandomXoshiro256StarStar random = new RandomXoshiro256StarStar(BASE_SEED ^ layer.seedSalt);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(layer.texture);

        GL11.glPushMatrix();
        applySkyFacingTransform(world, partialTicks);
        setupTexturedSkyBlend(true);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        for (int i = 0; i < layer.count; ++i) {
            Vector3 dir = randomUnitSphere(random);
            if (dir == null) continue;

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
     * Full-sky dome overlay (Milky Way, etc.).
     */
    private static void renderDomeLayer(World world, SkyPreset preset, DomeLayer layer, float partialTicks) {
        if (layer == null || layer.texture == null || layer.opacity <= 0.001f) return;

        float dayFactor = computeNightFactor(world, 0.0f, 1.0f, partialTicks);
        if (dayFactor <= 0.001f && !layer.allowDayVisible) return;

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(layer.texture);

        GL11.glPushMatrix();
        applySkyFacingTransform(world, partialTicks);
        setupTexturedSkyBlend(false);
        GL11.glColor4f(1f, 1f, 1f, layer.opacity * lerp(layer.minVisibility, layer.maxVisibility, dayFactor));

        drawTexturedDome(layer.radius, layer.segmentsLon, layer.segmentsLat, layer.textureVOffset);

        restoreSkyState();
        GL11.glPopMatrix();
    }

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
            float sinA = MathHelper.sin(thetaA), cosA = MathHelper.cos(thetaA);
            float sinB = MathHelper.sin(thetaB), cosB = MathHelper.cos(thetaB);

            for (int x = 0; x < lon; ++x) {
                float uA = (float) x / (float) lon;
                float uB = (float) (x + 1) / (float) lon;
                float phiA = (float) (uA * Math.PI * 2.0D);
                float phiB = (float) (uB * Math.PI * 2.0D);
                float sinPhiA = MathHelper.sin(phiA), cosPhiA = MathHelper.cos(phiA);
                float sinPhiB = MathHelper.sin(phiB), cosPhiB = MathHelper.cos(phiB);

                double x1 = -cosA * sinPhiA * radius, y1 = sinA * radius, z1 = cosA * cosPhiA * radius;
                double x2 = -cosA * sinPhiB * radius, y2 = sinA * radius, z2 = cosA * cosPhiB * radius;
                double x3 = -cosB * sinPhiB * radius, y3 = sinB * radius, z3 = cosB * cosPhiB * radius;
                double x4 = -cosB * sinPhiA * radius, y4 = sinB * radius, z4 = cosB * cosPhiA * radius;

                float vv0 = 1.0f - vA + textureVOffset;
                float vv1 = 1.0f - vB + textureVOffset;
                t.addVertexWithUV(x1, y1, z1, uA, vv0);
                t.addVertexWithUV(x2, y2, z2, uB, vv0);
                t.addVertexWithUV(x3, y3, z3, uB, vv1);
                t.addVertexWithUV(x4, y4, z4, uA, vv1);
            }
        }
        t.draw();
    }

    private static void setupTexturedSkyBlend(boolean additive) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
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
     * Night factor: 0 = broad daylight, 1 = full night.
     * minVisible / maxVisible control how much of the layer survives in daylight.
     */
    private static float computeNightFactor(World world, float minVisible, float maxVisible, float partialTicks) {
        if (world == null) return 1.0f;
        float celestial = world.getCelestialAngle(partialTicks);
        float dayNight = 1.0f - (MathHelper.cos(celestial * (float) Math.PI * 2.0F) * 2.0F + 0.2F);
        dayNight = MathHelper.clamp_float(dayNight, 0.0F, 1.0F);
        dayNight = 1.0f - dayNight;
        return MathHelper.clamp_float(minVisible + (maxVisible - minVisible) * (1.0f - dayNight), 0.0F, 1.0F);
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

    public static final class SkyPreset {

        private final String name;
        int brightStarCount = 0;
        float brightStarMinSize = 0.25f;
        float brightStarMaxSize = 0.85f;
        float brightStarAlpha = 1.0f;
        boolean colorfulBrightStars = true;
        final List<BillboardLayer> billboardLayers = new ArrayList<>();
        final List<DomeLayer> domeLayers = new ArrayList<>();

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

    public static final class BillboardLayer {

        final ResourceLocation texture;
        final int count;
        final float minSize;
        final float maxSize;
        final float alpha;
        final float dayVisibilityMin;
        final float dayVisibilityMax;
        final boolean jitterRotation;
        final long seedSalt;

        public BillboardLayer(ResourceLocation texture, int count, float minSize, float maxSize, float dayVisibilityMin,
            float dayVisibilityMax) {
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

    public static final class DomeLayer {

        final ResourceLocation texture;
        final float opacity;
        final float minVisibility;
        final float maxVisibility;
        final float radius;
        final int segmentsLon;
        final int segmentsLat;
        final float textureVOffset;
        final boolean allowDayVisible;

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

    private record Vector3(float x, float y, float z) {

        Vector3 normalize() {
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            return len <= 0.0001f ? new Vector3(0f, 1f, 0f) : new Vector3(x / len, y / len, z / len);
        }

        Vector3 cross(Vector3 o) {
            return new Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
        }

        Vector3 scale(float s) {
            return new Vector3(x * s, y * s, z * s);
        }

        Vector3 add(Vector3 o) {
            return new Vector3(x + o.x, y + o.y, z + o.z);
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
