package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.LocationGalaxia;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renderer to handle gantry blocks, modules, and carriage rendering
 */
@SideOnly(Side.CLIENT)
public class GantryRenderer extends TileEntitySpecialRenderer {

    private static final float MODULE_SCALE = 1;
    private static final float GANTRY_SCALE = 0.34f;

    private static final IModelCustom carriageModel = AdvancedModelLoader
        .loadModel(LocationGalaxia("textures/model/gantry/carriage.obj"));

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (!(tileEntity instanceof TileEntityGantry)) return;

        TileEntityGantry gantry = (TileEntityGantry) tileEntity;
        List<Vec3> dirs = gantry.neighbourDirs;
        if (dirs.isEmpty()) {
            // Render default variant
            renderFullBeam(gantry, x, y, z, Vec3.createVectorHelper(1, 0, 0));
            return;
        }
        if (dirs.size() == 1) {
            Vec3 dir = dirs.get(0);
            if (dir.yCoord != 0) {
                renderUpBeam(gantry, x, y, z, Vec3.createVectorHelper(-dir.xCoord, 0, -dir.zCoord), dir);
            } else renderFullBeam(gantry, x, y, z, dir);

        }

        // RENDER GANTRY VARIANTS
        // Neighbour dirs contains all neighbours to block

        // Flag for error checking
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
                    // If cardinal and has opposite, render full beam
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
                    if (hasOppositeDiagonal(gantry, elev) || hasNoAboveDiagonal(gantry, elev)) {
                        renderUpBeam(gantry, x, y, z, horiz, elev);
                        errorFlag = false;
                    }
                } else if (dirs.size() == 2 && isUPair(a, b)) {
                    renderUBend(gantry, x, y, z, a);
                    errorFlag = false;
                }
            }
        }
        // If no valid render found, render error
        if (errorFlag) renderErrorBeam(gantry, x, y, z);

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
        int moduleId = gantry.clientModuleId;
        if (moduleId == -1) {
            return;
        }
        RocketModule module = ModuleRegistry.fromId(moduleId);
        applyWorldLighting(gantry);
        GL11.glPushMatrix();
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

        GL11.glPopMatrix();

        // Render Carriage

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);

        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(pitch, 1f, 0f, 0f);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(LocationGalaxia("textures/model/gantry/carriage.png"));
        carriageModel.renderAll();
        GL11.glPopMatrix();
    }

    // LERP HELPERS

    /**
     * Linear Interpolater of an angle based on block progress
     *
     * @param a Start angle
     * @param b End angle
     * @param t Progress through block
     *
     * @return Interpolated angle
     */
    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return a + diff * t;
    }

    /**
     * Basic smoothstep function
     *
     * @see <a href="https://en.wikipedia.org/wiki/Smoothstep">Smoothstep
     *      Wikipedia</a>
     *
     * @param t Progress through block
     *
     * @return Smoothstepped progress
     */
    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    /**
     * Applies the world lightmap at the gantry's block position so the model
     * receives ambient light and shadow from its surroundings.
     *
     * @param gantry The gantry whose world position is sampled
     */
    private static void applyWorldLighting(TileEntityGantry gantry) {
        int brightness = gantry.getWorldObj()
            .getLightBrightnessForSkyBlocks(gantry.xCoord, gantry.yCoord, gantry.zCoord, 0);

        int skyLight = (brightness >> 16) & 0xFFFF;
        int blockLight = brightness & 0xFFFF;

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    // DIRECTIONALITY HELPERS
    /**
     * Determines if a direction is a cardinal direction (N, E, S, W)
     *
     * @param dir Vector direction to check
     *
     * @return Boolean : True => is cardinal
     */
    private boolean isCardinal(Vec3 dir) {
        return dir.yCoord == 0
            && ((Math.abs(dir.xCoord) == 1 && dir.zCoord == 0) || (dir.xCoord == 0 && Math.abs(dir.zCoord) == 1));
    }

    /**
     * Determines if two vectors are opposite
     *
     * @param a First vector
     * @param b Second vector
     *
     * @return Boolean : True => are opposite
     */
    private boolean isOpposite(Vec3 a, Vec3 b) {
        return a.xCoord == -b.xCoord && a.yCoord == -b.yCoord && a.zCoord == -b.zCoord;
    }

    /**
     * Determines if two vectors form a valid "up-bend" pair, where one vector is
     * cardinal, and the other is in opposite x/z direction and at different heights
     *
     * @param a First vector
     * @param b Second vector
     *
     * @return Boolean : True => valid up-bend
     */
    private boolean isUpBendPair(Vec3 a, Vec3 b) {
        boolean xzOpposite = (a.xCoord == -b.xCoord) && (a.zCoord == -b.zCoord);
        boolean oneHasY = (a.yCoord != 0) ^ (b.yCoord != 0);
        return xzOpposite && oneHasY;
    }

    /**
     * Determines if two vector directions form a valid "U-Bend" pair, i.e. both are
     * same cardinal direction at the same (non-zero) y-level
     *
     * @param a First vector
     * @param b Second vector
     *
     * @return Boolean : True => valid U-Bend)
     */
    private boolean isUPair(Vec3 a, Vec3 b) {
        boolean xzOpposite = (a.xCoord == -b.xCoord) && (a.zCoord == -b.zCoord);
        boolean sameY = a.yCoord == b.yCoord && a.yCoord != 0;
        return xzOpposite && sameY;
    }

    /**
     * Determines whether there is no block diagonally the same direction but
     * further in Y direction
     *
     * @param gantry  The gantry being checked
     * @param elevDir The direction with y elevation
     *
     * @return Boolean : True => No above diagonal
     */
    private boolean hasNoAboveDiagonal(TileEntityGantry gantry, Vec3 elevDir) {
        int nx = gantry.xCoord + (int) elevDir.xCoord;
        int ny = gantry.yCoord + (int) elevDir.yCoord;
        int nz = gantry.zCoord + (int) elevDir.zCoord;

        TileEntity te = Minecraft.getMinecraft().theWorld.getTileEntity(nx, ny, nz);
        if (!(te instanceof TileEntityGantry)) return false;

        TileEntityGantry diagNeighbour = (TileEntityGantry) te;

        for (Vec3 dir : diagNeighbour.neighbourDirs) {
            if (dir.xCoord == -elevDir.xCoord && dir.yCoord == elevDir.yCoord && dir.zCoord == -elevDir.zCoord)
                return false;
        }

        return true;

    }

    /**
     * Determines whether a gantry has a diagonal in the opposite direction (i.e. a
     * diagonal chain)
     *
     * @param gantry  The gantry being checked
     * @param elevDir The direction vector with elevation
     *
     * @return Boolean : True => has opposite diagonal
     */
    private boolean hasOppositeDiagonal(TileEntityGantry gantry, Vec3 elevDir) {
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
    // Rendes a full beam from block to block horizontally
    private static void renderFullBeam(TileEntityGantry g, double x, double y, double z, Vec3 dir) {
        Vec3 f = dir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        applyWorldLighting(g);
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

    // Renders an "Error Beam", a cross beam with red colour for invalid placements
    private static void renderErrorBeam(TileEntityGantry g, double x, double y, double z) {

        Vec3 f = Vec3.createVectorHelper(1, 0, 0);
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        applyWorldLighting(g);
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

    // Renders a diagonal beam in the direction of a given vector
    private static void renderDiagonalBeam(TileEntityGantry g, double x, double y, double z, Vec3 dir) {
        Vec3 f = dir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));
        applyWorldLighting(g);
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

    // Renders a corner beam between two cardinal directions
    private static void renderCornerBeam(TileEntityGantry g, double x, double y, double z, Vec3 in, Vec3 out) {
        double cx = in.xCoord + out.xCoord;
        double cz = in.zCoord + out.zCoord;
        float facingYaw = (float) Math.toDegrees(Math.atan2(cx, cz));
        applyWorldLighting(g);
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

    // Renders an "up-beam" between a cardinal direction and a diagonal direction
    private static void renderUpBeam(TileEntityGantry g, double x, double y, double z, Vec3 horiz, Vec3 elev) {
        Vec3 f = horiz.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        double yOffset = y + 0.5 + (elev.yCoord * 0.125);
        applyWorldLighting(g);
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

    private static void renderUBend(TileEntityGantry g, double x, double y, double z, Vec3 dir) {
        Vec3 f = dir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        double yOffset = y + 0.5 + (dir.yCoord * 0.5);
        applyWorldLighting(g);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, yOffset, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        if (f.yCoord < 0) {
            GL11.glRotatef(180, 1, 0, 0);
        }
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getUBendTexture());
        g.getUBendModel()
            .renderAll();
        GL11.glPopMatrix();
    }

}
