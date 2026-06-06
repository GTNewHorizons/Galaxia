package com.gtnewhorizons.galaxia.registry.items.tether;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TetherRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        TetherData data = KineticTetherState.getClient();
        if (!data.tetherActive) return;
        if (player.worldObj.provider.dimensionId != data.anchorDim) return;

        double px = player.prevPosX + (player.posX - player.prevPosX) * event.partialTicks;
        double py = player.prevPosY + (player.posY - player.prevPosY) * event.partialTicks;
        double pz = player.prevPosZ + (player.posZ - player.prevPosZ) * event.partialTicks;

        double torsoY = py - player.height * 0.2;

        Tessellator tess = Tessellator.instance;

        GL11.glPushMatrix();
        GL11.glTranslated(
            -RenderManager.instance.viewerPosX,
            -RenderManager.instance.viewerPosY,
            -RenderManager.instance.viewerPosZ);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(5.0F);

        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA(255, 80, 0, 200);

        tess.addVertex(px, torsoY, pz);
        tess.addVertex(data.anchorX, data.anchorY, data.anchorZ);

        tess.draw();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glPopMatrix();
    }
}
