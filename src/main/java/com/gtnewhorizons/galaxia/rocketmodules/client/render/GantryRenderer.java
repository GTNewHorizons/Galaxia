package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.core.Galaxia.LOG;
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

        Vec3 dir = gantry.getDirection();
        float progress = gantry.getInterpolatedProgress(partialTicks);

        float dx = dir != null ? (float) dir.xCoord * progress : 0f;
        float dy = dir != null ? (float) dir.yCoord * progress : 0f;
        float dz = dir != null ? (float) dir.zCoord * progress : 0f;

        Vec3 dirNorm = dir.normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-dirNorm.xCoord, dirNorm.zCoord));
        float pitch = (float) Math.toDegrees(Math.asin(-dirNorm.yCoord)) + 90f;

        // Render Module
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslated(x + 0.5 + dx, y + dy - module.getWidth() / 2, z + 0.5 + dz);
        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(pitch, 1f, 0f, 0f);

        GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(module.getTexture());
        module.getModel()
            .renderAll();

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();

        // Render Carriage
        Vec3 dirNormCar = dir.normalize();

        float carYaw = (float) Math.toDegrees(Math.atan2(-dirNormCar.xCoord, dirNormCar.zCoord));
        float carPitch = (float) Math.toDegrees(Math.asin(-dirNormCar.yCoord));

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glTranslated(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);

        GL11.glRotatef(carYaw, 0f, 1f, 0f);
        GL11.glRotatef(carPitch, 1f, 0f, 0f);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        LOG.info(dirNormCar.zCoord);
        Vec3 offsets = getOffsets(dirNormCar);
        GL11.glTranslated(offsets.xCoord, offsets.yCoord, offsets.zCoord);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(LocationGalaxia("textures/model/gantry/carriage.png"));
        AdvancedModelLoader.loadModel(LocationGalaxia("textures/model/gantry/carriage.obj"))
            .renderAll();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();;
    }

    public Vec3 getOffsets(Vec3 dir) {
        if (dir.yCoord == 0) {
            return Vec3.createVectorHelper(0, 0, 0);
        }
        double x, y, z;
        if (dir.xCoord == 0) {

            x = 0;
            y = Math.abs(dir.yCoord);
            z = -dir.yCoord * 2;
        } else {
            x = -Math.abs(dir.yCoord) * 2;
            y = Math.abs(dir.yCoord);
            z = 0;
        }
        return Vec3.createVectorHelper(x, y, z);

    }
}
