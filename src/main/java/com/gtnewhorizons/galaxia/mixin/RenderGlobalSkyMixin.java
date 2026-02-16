package com.gtnewhorizons.galaxia.mixin;

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

    // 1 rotation every cycle + 1 extra rotation every 27.3 days = 1.0366 revolutions per day
    // 1.0366 revolutions per day is 24000/1.0366 = 23152 ticks per revolution
    private static final long MOON_PERIOD = 23151;

    @Inject(
        method = "renderSky",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;",
            opcode = Opcodes.GETSTATIC),
        cancellable = true)
    private void galaxia$replaceSunMoon(float partialTicks, CallbackInfo ci) {
        World world = mc.theWorld;
        Tessellator t = Tessellator.instance;

        float sunAngle = world.getCelestialAngle(partialTicks);

        double worldTime = (double) world.getWorldTime();
        double timeWithPartial = worldTime + (double) partialTicks;
        float moonAngle = (float) ((timeWithPartial % MOON_PERIOD) / (double) MOON_PERIOD);

        GL11.glPopMatrix();
        GL11.glPushMatrix();

        GL11.glRotatef(-90F, 0F, 1F, 0F);

        drawStar(t, locationSunPng, 30F, 100D, 23.44F, sunAngle);
        drawMoon(t, world, moonAngle);

        GL11.glPopMatrix();
        restoreGLState();
        ci.cancel();
    }

    private void drawStar(Tessellator t, ResourceLocation texture, float size, double height, float tilt,
                          float angle) {
        GL11.glPushMatrix();

        GL11.glRotatef(tilt, 0F, 0F, 1F);
        GL11.glRotatef(angle * 360.0F, 1F, 0F, 0F);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);

        t.startDrawingQuads();
        t.addVertexWithUV(-size, height, -size, 0.0D, 0.0D);
        t.addVertexWithUV(size, height, -size, 1.0D, 0.0D);
        t.addVertexWithUV(size, height, size, 1.0D, 1.0D);
        t.addVertexWithUV(-size, height, size, 0.0D, 1.0D);
        t.draw();

        GL11.glPopMatrix();
    }

    private void drawMoon(Tessellator t, World world, float angle) {
        GL11.glPushMatrix();

        GL11.glRotatef(5.14F, 0F, 0F, 1F);
        GL11.glRotatef(angle * 360.0F, 1F, 0F, 0F);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(locationMoonPhasesPng);

        int phase = world.getMoonPhase();
        int u = phase % 4;
        int v = (phase / 4) % 2;

        float u0 = u / 4.0F;
        float v0 = v / 2.0F;
        float u1 = (u + 1) / 4.0F;
        float v1 = (v + 1) / 2.0F;

        float size = 20F;

        t.startDrawingQuads();
        t.addVertexWithUV(-size, -100.0D, size, u1, v1);
        t.addVertexWithUV(size, -100.0D, size, u0, v1);
        t.addVertexWithUV(size, -100.0D, -size, u0, v0);
        t.addVertexWithUV(-size, -100.0D, -size, u1, v0);
        t.draw();

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
