package com.gtnewhorizons.galaxia.client.render;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.entity.rocket.ModuleData;
import com.gtnewhorizons.galaxia.registry.entity.rocket.ModuleType;
import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;

import java.util.List;

@SideOnly(Side.CLIENT)
public class RocketWorldRenderer {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new RocketWorldRenderer());
        System.err.println("=== GALAXIA RocketWorldRenderer ЗАРЕГИСТРИРОВАН (RenderWorldLastEvent) ===");
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tes = Tessellator.instance;

        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof RocketEntity rocket && !rocket.isDead) {
                List<ModuleData> mods = rocket.getModules();
                if (mods.isEmpty()) continue;
                renderRocket(rocket, event.partialTicks, tes);
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private void renderRocket(RocketEntity rocket, float partialTicks, Tessellator tes) {
        // интерполяция позиции (чтобы не дёргалось)
        double x = rocket.lastTickPosX + (rocket.posX - rocket.lastTickPosX) * partialTicks - Minecraft.getMinecraft().thePlayer.lastTickPosX
            - (Minecraft.getMinecraft().thePlayer.posX - Minecraft.getMinecraft().thePlayer.lastTickPosX) * partialTicks;
        double y = rocket.lastTickPosY + (rocket.posY - rocket.lastTickPosY) * partialTicks - Minecraft.getMinecraft().thePlayer.lastTickPosY
            - (Minecraft.getMinecraft().thePlayer.posY - Minecraft.getMinecraft().thePlayer.lastTickPosY) * partialTicks;
        double z = rocket.lastTickPosZ + (rocket.posZ - rocket.lastTickPosZ) * partialTicks - Minecraft.getMinecraft().thePlayer.lastTickPosZ
            - (Minecraft.getMinecraft().thePlayer.posZ - Minecraft.getMinecraft().thePlayer.lastTickPosZ) * partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        float currentY = 0.0F;
        for (ModuleData mod : rocket.getModules()) {
            ModuleType type = mod.type();
            float h = type.height * 1.0F; // масштаб 1 блок = 1 высота модуля
            drawColoredBox(tes, -0.95, currentY, -0.95, 0.95, currentY + h, 0.95, type.r, type.g, type.b);
            currentY += h;
        }

        GL11.glPopMatrix();
    }

    private void drawColoredBox(Tessellator tes, double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ, float r, float g, float b) {
        GL11.glColor4f(r, g, b, 0.95f);
        tes.startDrawingQuads();

        // Front
        tes.addVertex(minX, minY, minZ); tes.addVertex(minX, maxY, minZ);
        tes.addVertex(maxX, maxY, minZ); tes.addVertex(maxX, minY, minZ);

        // Back
        tes.addVertex(maxX, minY, maxZ); tes.addVertex(maxX, maxY, maxZ);
        tes.addVertex(minX, maxY, maxZ); tes.addVertex(minX, minY, maxZ);

        // Left
        tes.addVertex(minX, minY, maxZ); tes.addVertex(minX, maxY, maxZ);
        tes.addVertex(minX, maxY, minZ); tes.addVertex(minX, minY, minZ);

        // Right
        tes.addVertex(maxX, minY, minZ); tes.addVertex(maxX, maxY, minZ);
        tes.addVertex(maxX, maxY, maxZ); tes.addVertex(maxX, minY, maxZ);

        // Top
        tes.addVertex(minX, maxY, minZ); tes.addVertex(minX, maxY, maxZ);
        tes.addVertex(maxX, maxY, maxZ); tes.addVertex(maxX, maxY, minZ);

        // Bottom
        tes.addVertex(maxX, minY, minZ); tes.addVertex(maxX, minY, maxZ);
        tes.addVertex(minX, minY, maxZ); tes.addVertex(minX, minY, minZ);

        tes.draw();
    }
}
