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

    private static final float MODULE_SCALE = 0.2f;

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

        float yaw = 0f;
        if (dir != null && (dir.xCoord != 0 || dir.zCoord != 0)) {
            yaw = (float) Math.toDegrees(Math.atan2(-dir.xCoord, dir.zCoord));
        }

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);

        GL11.glTranslated(x + 0.5 + dx, y + dy, z + 0.5 + dz);
        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(90f, 1f, 0f, 0f);
        GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);
        GL11.glTranslatef(0f, -1f, 0f);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(module.getTexture());
        module.getModel()
            .renderAll();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }
}
