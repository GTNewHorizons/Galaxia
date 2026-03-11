package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GantryRenderer extends TileEntitySpecialRenderer {

    private static final float MODULE_SCALE = 0.4f;

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (!(tileEntity instanceof TileEntityGantry)) return;

        // Gantry block
        // GL11.glPushMatrix();
        // GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        // Minecraft.getMinecraft().getTextureManager().bindTexture(gantry.getTexture());
        // gantry.getModel().renderAll();
        // GL11.glPopMatrix();

        // Module
        TileEntityGantry gantry = (TileEntityGantry) tileEntity;
        int moduleId = gantry.clientModuleId;
        if (moduleId == -1) {
            return;
        }
        RocketModule module = ModuleRegistry.fromId(moduleId);

        Vec3 dir = gantry.getDirection();
        float progress = gantry.getInterpolatedProgress(partialTicks);

        float dx = dir != null ? (float) dir.xCoord * progress : 0f;
        float dy = dir != null ? (float) dir.yCoord * progress : 0f;
        float dz = dir != null ? (float) dir.zCoord * progress : 0f;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslated(x + 0.5 + dx, y + dy - 0.5f, z + 0.5 + dz);

        Vec3 forward = Vec3.createVectorHelper(0, 1, 0);
        Vec3 dirNorm = dir.normalize();
        Vec3 axis = forward.crossProduct(dirNorm);
        float angle = (float) Math.toDegrees(Math.acos(forward.dotProduct(dirNorm)));

        if (axis.lengthVector() > 0.0001) {
            GL11.glRotatef(angle, (float) axis.xCoord, (float) axis.yCoord, (float) axis.zCoord);
        }
        GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(module.getTexture());
        module.getModel()
            .renderAll();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

}
