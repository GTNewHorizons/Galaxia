package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.LocationGalaxia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.AdvancedModelLoader;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GantryRenderer extends TileEntitySpecialRenderer {

    private static final float MODULE_SCALE = 1;
    private static final float GANTRY_SCALE = 0.34f;

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (!(tileEntity instanceof TileEntityGantry)) return;

        TileEntityGantry gantry = (TileEntityGantry) tileEntity;
        // Render Gantry
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(90f, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(gantry.getTexture());
        gantry.getModel()
            .renderAll();
        GL11.glPopMatrix();

        // Module
        int moduleId = gantry.clientModuleId;
        if (moduleId == -1) {
            return;
        }
        RocketModule module = ModuleRegistry.fromId(moduleId);

        Vec3 outDir = gantry.getDirection();
        Vec3 inDir = gantry.clientIncomingDirection;
        float progress = gantry.getInterpolatedProgress(partialTicks);

        boolean isCorner = inDir != null && outDir != null
            && (Math.abs(inDir.xCoord - outDir.xCoord) > 0.01 || Math.abs(inDir.yCoord - outDir.yCoord) > 0.01
                || Math.abs(inDir.zCoord - outDir.zCoord) > 0.01);

        float dx, dy, dz, yaw, pitch;

        if (isCorner) {
            float blend = smoothStep(progress);
            dx = (float) (inDir.xCoord * progress * (1f - blend) + outDir.xCoord * progress * blend);
            dy = (float) (inDir.yCoord * progress * (1f - blend) + outDir.yCoord * progress * blend);
            dz = (float) (inDir.zCoord * progress * (1f - blend) + outDir.zCoord * progress * blend);

            Vec3 inNorm = inDir.normalize();
            Vec3 outNorm = outDir.normalize();

            float inYaw = (float) Math.toDegrees(Math.atan2(inNorm.xCoord, inNorm.zCoord));
            float outYaw = (float) Math.toDegrees(Math.atan2(outNorm.xCoord, outNorm.zCoord));
            yaw = lerpAngle(inYaw, outYaw, blend);
            float inPitch = (float) Math.toDegrees(Math.asin(-inNorm.yCoord));
            float outPitch = (float) Math.toDegrees(Math.asin(-outNorm.yCoord));
            pitch = lerpAngle(inPitch, outPitch, blend);

        } else {
            dx = outDir != null ? (float) outDir.xCoord * progress : 0f;
            dy = outDir != null ? (float) outDir.yCoord * progress : 0f;
            dz = outDir != null ? (float) outDir.zCoord * progress : 0f;
            Vec3 norm = outDir != null ? outDir.normalize() : Vec3.createVectorHelper(0, 0, 1);

            yaw = (float) Math.toDegrees(Math.atan2(norm.xCoord, norm.zCoord));
            pitch = (float) Math.toDegrees(Math.asin(-norm.yCoord));
        }

        // Render Module
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslated(x + 0.5 + dx, y + dy - module.getWidth() / 2, z + 0.5 + dz);
        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(pitch + 90f, 1f, 0f, 0f);

        GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(module.getTexture());
        module.getModel()
            .renderAll();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();

        // Render Carriage

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslated(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);

        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(pitch, 1f, 0f, 0f);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(LocationGalaxia("textures/model/gantry/carriage.png"));
        AdvancedModelLoader.loadModel(LocationGalaxia("textures/model/gantry/carriage.obj"))
            .renderAll();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();;
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return a + diff * t;
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

}
