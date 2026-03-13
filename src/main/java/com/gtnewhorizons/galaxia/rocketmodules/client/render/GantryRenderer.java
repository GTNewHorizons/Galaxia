package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.LocationGalaxia;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.AdvancedModelLoader;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renderer to handle gantry blocks, modules, and carriage rendering
 */
@SideOnly(Side.CLIENT)
public class GantryRenderer extends TileEntitySpecialRenderer {

    private static final float MODULE_SCALE = 1;
    private static final float GANTRY_SCALE = 0.34f;

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (!(tileEntity instanceof TileEntityGantry)) return;

        TileEntityGantry gantry = (TileEntityGantry) tileEntity;
        List<Vec3> dirs = gantry.neighbourDirs;
        addAssemblerAndSiloIfRequired(gantry, dirs);
        if (dirs.isEmpty()) {
            // Render default variant
            renderFullBeam(gantry, x, y, z, Vec3.createVectorHelper(1, 0, 0));
            return;
        }
        if (dirs.size() == 1) {
            Vec3 dir = dirs.get(0);
            renderFullBeam(gantry, x, y, z, dir);

        }

        // RENDER GANTRY VARIANTS
        // Neighbour dirs contains all neighbours to block

        // First pass, straight beams:
        boolean errorFlag = true;
        if (dirs.size() < 2) {
            errorFlag = false;
        }
        for (int i = 0; i < dirs.size(); i++) {
            for (int j = i + 1; j < dirs.size(); j++) {
                Vec3 a = dirs.get(i);
                Vec3 b = dirs.get(j);
                boolean opp = isOpposite(a, b);
                if (opp && isCardinal(a)) {
                    // If cardinal and has posite, render full beam
                    renderFullBeam(gantry, x, y, z, a);
                    errorFlag = false;
                } else if (opp && !isCardinal(a)) {
                    // If not cardinal and has opposite, render diagonal beam
                    Vec3 upDir = (a.yCoord >= 0) ? a : b;
                    renderDiagonalBeam(gantry, x, y, z, upDir);
                    errorFlag = false;
                } else if (!opp && isCardinal(a) && isCardinal(b)) {
                    // If cardinal and not opposite, render corner beam
                    renderCornerBeam(gantry, x, y, z, a, b);
                    errorFlag = false;
                } else if (!opp && isCardinal(a) != isCardinal(b) && isUpBendPair(a, b)) {
                    // If not cardinal, and not opposite, render up bend
                    Vec3 horiz = isCardinal(a) ? a : b;
                    Vec3 elev = isCardinal(a) ? b : a;
                    if (hasDiagonalChain(gantry, elev)) {
                        renderUpBeam(gantry, x, y, z, horiz, elev);
                        errorFlag = false;
                    }
                }
            }
        }
        if (errorFlag) renderErrorBeam(gantry, x, y, z);

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
        GL11.glTranslated(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);
        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(pitch, 1f, 0f, 0f);
        GL11.glTranslatef(0f, (float) -module.getWidth() / 2, 0f);
        GL11.glRotatef(90f, 1, 0, 0);

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
        GL11.glPopMatrix();
    }

    // LERP HELPERS
    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return a + diff * t;
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    // DIRECTIONALITY HELPERS
    private boolean isCardinal(Vec3 dir) {
        return dir.yCoord == 0
            && ((Math.abs(dir.xCoord) == 1 && dir.zCoord == 0) || (dir.xCoord == 0 && Math.abs(dir.zCoord) == 1));
    }

    private boolean isOpposite(Vec3 a, Vec3 b) {
        return a.xCoord == -b.xCoord && a.yCoord == -b.yCoord && a.zCoord == -b.zCoord;
    }

    private boolean isUpBendPair(Vec3 a, Vec3 b) {
        boolean xzOpposite = (a.xCoord == -b.xCoord) && (a.zCoord == -b.zCoord);
        boolean oneHasY = (a.yCoord != 0) ^ (b.yCoord != 0);
        return xzOpposite && oneHasY;
    }

    private boolean hasDiagonalChain(TileEntityGantry gantry, Vec3 elevDir) {
        int nx = gantry.xCoord + (int) elevDir.xCoord;
        int ny = gantry.yCoord + (int) elevDir.yCoord;
        int nz = gantry.zCoord + (int) elevDir.zCoord;

        TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(nx, ny, nz);
        if (!(te instanceof TileEntityGantry)) return false;

        TileEntityGantry diagNeighbour = (TileEntityGantry) te;

        for (Vec3 dir : diagNeighbour.neighbourDirs) {
            if (dir.xCoord == elevDir.xCoord && dir.yCoord == elevDir.yCoord && dir.zCoord == elevDir.zCoord)
                return true;
        }
        return false;
    }

    // BEAM RENDERERS
    private static void renderFullBeam(TileEntityGantry g, double x, double y, double z, Vec3 dir) {
        Vec3 f = dir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getTexture());
        g.getModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    private static void renderErrorBeam(TileEntityGantry g, double x, double y, double z) {

        Vec3 f = Vec3.createVectorHelper(1, 0, 0);
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getErrorTexture());
        g.getModel()
            .renderAll();
        GL11.glPopMatrix();

        f = Vec3.createVectorHelper(0, 0, 1);
        facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getErrorTexture());
        g.getModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    private static void renderDiagonalBeam(TileEntityGantry g, double x, double y, double z, Vec3 dir) {
        Vec3 f = dir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.425, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getDiagonalTexture());
        g.getDiagonalModel()
            .renderAll();
        GL11.glPopMatrix();

    }

    private static void renderCornerBeam(TileEntityGantry g, double x, double y, double z, Vec3 in, Vec3 out) {
        double cx = in.xCoord + out.xCoord;
        double cz = in.zCoord + out.zCoord;
        float facingYaw = (float) Math.toDegrees(Math.atan2(cx, cz));
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(225, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getCornerTexture());
        g.getCornerModel()
            .renderAll();
        GL11.glPopMatrix();

    }

    private static void renderUpBeam(TileEntityGantry g, double x, double y, double z, Vec3 horiz, Vec3 elev) {
        Vec3 f = horiz.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        double yOffset = y + 0.5 + (elev.yCoord * 0.125);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, yOffset, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        if (elev.yCoord < 0) {
            GL11.glRotatef(180, 1, 0, 0);
        }
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getUpBendTexture());
        g.getUpBendModel()
            .renderAll();
        GL11.glPopMatrix();

    }

    public void addAssemblerAndSiloIfRequired(TileEntityGantry gantry, List<Vec3> dirs) {
        if (!(gantry instanceof TileEntityGantryTerminal)) return;
        TileEntityGantryTerminal tegt = (TileEntityGantryTerminal) gantry;
        Vec3 dirToCheck;
        if (tegt.getAssembler() != null) {
            TileEntityModuleAssembler ma = tegt.getAssembler();
            dirToCheck = Vec3
                .createVectorHelper(ma.xCoord - gantry.xCoord, ma.yCoord - gantry.yCoord, ma.zCoord - gantry.zCoord);
        } else if (tegt.getSilo() != null) {
            TileEntitySilo s = tegt.getSilo();
            dirToCheck = Vec3
                .createVectorHelper(s.xCoord - gantry.xCoord, s.yCoord - gantry.yCoord, s.zCoord - gantry.zCoord);
        } else {
            return;
        }

        for (Vec3 dir : dirs) {
            if (dir.xCoord == dirToCheck.xCoord && dir.yCoord == dirToCheck.yCoord && dir.zCoord == dirToCheck.zCoord) {
                return;
            }
        }
        dirs.add(dirToCheck);
    }
}
