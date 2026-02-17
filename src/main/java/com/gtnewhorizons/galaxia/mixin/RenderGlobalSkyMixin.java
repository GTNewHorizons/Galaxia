package com.gtnewhorizons.galaxia.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.dimension.sky.CelestialBody;

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

    private static final List<CelestialBody> DEFAULT_OVERWORLD_BODIES;

    static {
        List<CelestialBody> list = new ArrayList<>();
        list.add(new CelestialBody(locationSunPng, null, 30f, 100.0, 23.44f, 24000L, true, false, 0));
        list.add(
            new CelestialBody(
                locationMoonPhasesPng,
                locationMoonPhasesPng,
                20f,
                -100.0,
                5.14f,
                23151L,
                false,
                true,
                8));
        DEFAULT_OVERWORLD_BODIES = Collections.unmodifiableList(list);
    }

    @Inject(
        method = "renderSky",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;",
            opcode = Opcodes.GETSTATIC),
        cancellable = true)
    private void galaxia$replaceSunMoon(float partialTicks, CallbackInfo ci) {
        World world = mc.theWorld;
        int dimId = world.provider.dimensionId;

        DimensionDef def = SolarSystemRegistry.getById(dimId);
        boolean isGalaxiaDim = def != null;

        if (dimId != 0 && !isGalaxiaDim) {
            return; // vanilla sky (nether, end, other mods)
        }

        Tessellator t = Tessellator.instance;

        List<CelestialBody> bodies = (dimId == 0) ? DEFAULT_OVERWORLD_BODIES : def.celestialBodies;

        if (bodies.isEmpty()) {
            return;
        }

        double worldTime = world.getWorldTime();
        double timeWithPartial = worldTime + partialTicks;

        // angles
        List<Float> angles = new ArrayList<>();
        for (CelestialBody body : bodies) {
            float angle = (float) ((timeWithPartial % body.orbitalPeriodTicks) / (double) body.orbitalPeriodTicks);
            angles.add(angle);
        }

        float primarySunAngle = 0.0f;
        for (int i = 0; i < bodies.size(); i++) {
            if (bodies.get(i).emissive) {
                primarySunAngle = angles.get(i);
                break;
            }
        }

        // sorting for eclipses
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < bodies.size(); i++) indices.add(i);
        indices.sort((i1, i2) -> Double.compare(bodies.get(i2).distance, bodies.get(i1).distance));

        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glRotatef(-90F, 0F, 1F, 0F);

        for (int idx : indices) {
            CelestialBody body = bodies.get(idx);
            float angle = angles.get(idx);
            drawCelestialBody(t, body, angle, primarySunAngle);
        }

        GL11.glPopMatrix();
        restoreGLState();
        ci.cancel();
    }

    private void drawCelestialBody(Tessellator t, CelestialBody body, float angle, float primarySunAngle) {
        GL11.glPushMatrix();

        GL11.glRotatef(body.inclination, 0F, 0F, 1F);
        GL11.glRotatef(angle * 360.0F, 1F, 0F, 0F);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(body.texture);

        float size = body.size;
        double height = body.distance;

        if (body.hasPhases) {
            float delta = angle - primarySunAngle;
            float phaseProgress = delta % 1.0f;
            if (phaseProgress < 0.0f) phaseProgress += 1.0f;

            int phase = (int) (phaseProgress * body.phaseCount) % body.phaseCount;

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

    private void restoreGLState() {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_FOG);
    }
}
