package com.gtnewhorizons.galaxia.mixin;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.registry.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.sky.CelestialBody;
import com.gtnewhorizons.galaxia.registry.dimension.sky.SkyBuilder;

/**
 * Mixin to alter the global sky rendering
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalSkyMixin {

    @Shadow
    private Minecraft mc;

    @Shadow
    @Final
    private static ResourceLocation locationSunPng;

    @Shadow
    @Final
    private static ResourceLocation locationMoonPhasesPng;

    @Unique
    private static final ResourceLocation GALAXIA_EMPTY_SKY = LocationGalaxia("textures/sky/empty.png");

    private static final List<CelestialBody> DEFAULT_OVERWORLD_BODIES = SkyBuilder.builder()
        .addBody(
            b -> b.texture(locationSunPng)
                .size(30f)
                .distance(100.0)
                .inclination(23.44f)
                .period(24000L)
                .phaseOffset(-6000L)
                .mainLightSource())
        .addBody(
            b -> b.texture(locationMoonPhasesPng)
                .size(20f)
                .distance(-100.0)
                .inclination(5.14f)
                .period(23151L)
                .hasPhases()
                .phaseCount(8))
        .build();

    @Unique
    private boolean galaxia$isCustomSkyDim() {
        World world = mc.theWorld;
        if (world == null) return false;
        int dimId = world.provider.dimensionId;
        return dimId == 0 || SolarSystemRegistry.getById(dimId) != null;
    }

    /**
     * Redirects vanilla sun texture to transparent when a custom sky is active.
     * Vanilla still draws its quad; it just becomes invisible.
     */
    @Redirect(
        method = "renderSky",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;",
            opcode = Opcodes.GETSTATIC))
    private ResourceLocation galaxia$redirectSunTexture() {
        return galaxia$isCustomSkyDim() ? GALAXIA_EMPTY_SKY : locationSunPng;
    }

    /**
     * Redirects vanilla moon texture to transparent when a custom sky is active.
     */
    @Redirect(
        method = "renderSky",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;",
            opcode = Opcodes.GETSTATIC))
    private ResourceLocation galaxia$redirectMoonTexture() {
        return galaxia$isCustomSkyDim() ? GALAXIA_EMPTY_SKY : locationMoonPhasesPng;
    }

    /**
     * Draws custom skybox and celestial bodies.
     *
     * <p>
     * Matrix accounting: at this inject point vanilla has already done
     * {@code glPushMatrix()} for the sun/moon block. We pop that push to reach
     * the outer sky matrix, draw everything in a fresh push, then push a dummy
     * matrix so vanilla's {@code glPopMatrix()} after the moon remains balanced.
     * Vanilla's invisible sun/moon quads are drawn inside that dummy push.
     *
     * @param partialTicks How far through the current tick
     * @param ci           Callback info
     */
    @Inject(
        method = "renderSky",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;",
            opcode = Opcodes.GETSTATIC))
    private void galaxia$renderCustomBodies(float partialTicks, CallbackInfo ci) {
        World world = mc.theWorld;
        int dimId = world.provider.dimensionId;
        DimensionDef def = SolarSystemRegistry.getById(dimId);

        if (dimId != 0 && def == null) {
            return; // non-galaxia dim that isn't overworld
        }

        List<CelestialBody> bodies = (dimId == 0) ? DEFAULT_OVERWORLD_BODIES : def.celestialBodies();
        if (bodies.isEmpty()) {
            return;
        }

        Tessellator t = Tessellator.instance;
        double worldTime = world.getWorldTime();
        double timeWithPartial = worldTime + partialTicks;

        // Stack entering inject: base | sky | vanilla-sun-push
        GL11.glPopMatrix(); // base | sky
        GL11.glPushMatrix(); // base | sky | this

        if (def != null && def.skyboxTexture() != null) {
            drawCubemapSkybox(t, def.skyboxTexture());
            // Restore state the skybox disabled
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        GL11.glRotatef(-90F, 0F, 1F, 0F);
        OpenGlHelper.glBlendFunc(775, 1, 1, 0);

        List<Float> angles = new ArrayList<>();
        for (CelestialBody body : bodies) {
            float angle = (float) (((timeWithPartial + body.phaseOffsetTicks()) % body.orbitalPeriodTicks())
                / (double) body.orbitalPeriodTicks());
            angles.add(angle);
        }

        float primarySunAngle = 0.0f;
        for (int i = 0; i < bodies.size(); i++) {
            if (bodies.get(i)
                .isMainLightSource()) {
                primarySunAngle = angles.get(i);
                break;
            }
        }

        // sorting for eclipses
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < bodies.size(); i++) indices.add(i);
        indices.sort(
            (i1, i2) -> Double.compare(
                bodies.get(i2)
                    .distance(),
                bodies.get(i1)
                    .distance()));

        for (int idx : indices) {
            drawCelestialBody(t, bodies.get(idx), angles.get(idx), primarySunAngle);
        }

        GL11.glPopMatrix(); // base | sky
        GL11.glPushMatrix(); // base | sky | dummy
        // Vanilla will glPopMatrix() after drawing its invisible moon quad,
        // consuming this dummy push and leaving the stack correct.
    }

    /**
     * Draws a celestial body given certain parameters
     *
     * @param t               The tesselator to use
     * @param body            The body to be drawn
     * @param angle           The angle in the sky
     * @param primarySunAngle The angle of the primary light source (sun usually) in the sky
     */
    @Unique
    private void drawCelestialBody(Tessellator t, CelestialBody body, float angle, float primarySunAngle) {
        GL11.glPushMatrix();

        GL11.glRotatef(body.inclination(), 0F, 0F, 1F);
        GL11.glRotatef(angle * 360.0F, 1F, 0F, 0F);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(body.texture());

        float size = body.size();
        double height = body.distance();

        if (body.hasPhases()) {
            float delta = angle - primarySunAngle;
            float phaseProgress = delta % 1.0f;
            if (phaseProgress < 0.0f) phaseProgress += 1.0f;

            int phase = (int) (phaseProgress * body.phaseCount()) % body.phaseCount();

            int u = phase % 4;
            int v = (phase / 4) % 2;

            float u0 = u / 4.0F;
            float v0 = v / 2.0F;
            float u1 = (u + 1) / 4.0F;
            float v1 = (v + 1) / 2.0F;

            t.startDrawingQuads();
            t.addVertexWithUV(-size, height, size, u1, v1);
            t.addVertexWithUV(size, height, size, u0, v1);
            t.addVertexWithUV(size, height, -size, u0, v0);
            t.addVertexWithUV(-size, height, -size, u1, v0);
            t.draw();
        } else {
            t.startDrawingQuads();
            t.addVertexWithUV(-size, height, -size, 0.0D, 0.0D);
            t.addVertexWithUV(size, height, -size, 1.0D, 0.0D);
            t.addVertexWithUV(size, height, size, 1.0D, 1.0D);
            t.addVertexWithUV(-size, height, size, 0.0D, 1.0D);
            t.draw();
        }

        GL11.glPopMatrix();
    }

    /**
     * Draws a static cubemap skybox using a single repeating texture.
     * Called before celestial bodies, so bodies render on top.
     */
    @Unique
    private void drawCubemapSkybox(Tessellator t, ResourceLocation[] faces) {
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        float S = 100f;

        // spotless:off
        drawFace(t, faces[0],
            -S,  S,  S,
            S,  S,  S,
            S,  S, -S,
            -S,  S, -S);

        drawFace(t, faces[1],
            -S, -S, -S,
            S, -S, -S,
            S, -S,  S,
            -S, -S,  S);

        drawFace(t, faces[2],
            S,  S,  S,
            -S,  S,  S,
            -S, -S,  S,
            S, -S,  S);

        drawFace(t, faces[3],
            -S,  S, -S,
            S,  S, -S,
            S, -S, -S,
            -S, -S, -S);

        drawFace(t, faces[4],
            S,  S, -S,
            S,  S,  S,
            S, -S,  S,
            S, -S, -S);

        drawFace(t, faces[5],
            -S,  S,  S,
            -S,  S, -S,
            -S, -S, -S,
            -S, -S,  S);
        // spotless:on

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Unique
    private void drawFace(Tessellator t, ResourceLocation texture, float x0, float y0, float z0, float x1, float y1,
        float z1, float x2, float y2, float z2, float x3, float y3, float z3) {

        mc.getTextureManager()
            .bindTexture(texture);
        t.startDrawingQuads();
        t.addVertexWithUV(x0, y0, z0, 0, 0);
        t.addVertexWithUV(x1, y1, z1, 1, 0);
        t.addVertexWithUV(x2, y2, z2, 1, 1);
        t.addVertexWithUV(x3, y3, z3, 0, 1);
        t.draw();
    }
}
