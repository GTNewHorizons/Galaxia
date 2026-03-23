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
    private static final float GANTRY_SCALE = 1;

    private static final IModelCustom carriageModel = AdvancedModelLoader
        .loadModel(LocationGalaxia("textures/model/gantry/carriage.obj"));

    // spotless:off
    private static final Vec3[] CARDINAL_DIRECTIONS = {
        Vec3.createVectorHelper(1, 0, 0),
        Vec3.createVectorHelper(-1, 0, 0),
        Vec3.createVectorHelper(0, 0, 1),
        Vec3.createVectorHelper(0, 0, -1)
    };
    //spotless:on

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (!(tileEntity instanceof TileEntityGantry gantry)) return;

        // Invalid gantry (stacked directly on another) renders error and doesnt affect anything else
        if (gantry.isInvalid()) {
            renderErrorBeam(gantry, x, y, z);
            return;
        }

        List<Vec3> dirs = gantry.neighbourDirs;

        renderGantryPath(gantry, x, y, z, dirs);

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

        int moduleId = gantry.clientModuleId;
        if (moduleId == -1) return;
        RocketModule module = ModuleRegistry.fromId(moduleId);
        applyWorldLighting(gantry);
        GL11.glPushMatrix();
        applyGantryOrientation(x, y, z, dx, dy, dz, yaw, pitch);
        GL11.glTranslatef(0f, (float) -module.getWidth() / 2, 0f);
        GL11.glRotatef(90f, 1, 0, 0);
        GL11.glScalef(MODULE_SCALE, MODULE_SCALE, MODULE_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(module.getTexture());
        module.getModel()
            .renderAll();

        GL11.glPopMatrix();

        GL11.glPushMatrix();
        applyGantryOrientation(x, y, z, dx, dy, dz, yaw, pitch);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(LocationGalaxia("textures/model/gantry/carriage.png"));
        carriageModel.renderAll();
        GL11.glPopMatrix();
    }

    /**
     * Renders the gantry path using dedicated models for straights, corners,
     * T-junctions, crosses, diagonals, and mixed ramp transitions.
     */
    private void renderGantryPath(TileEntityGantry gantry, double x, double y, double z, List<Vec3> dirs) {
        if (dirs.isEmpty()) {
            renderFullBeam(gantry, x, y, z, Vec3.createVectorHelper(1, 0, 0));
            return;
        }

        if (dirs.size() == 1) {
            Vec3 dir = dirs.get(0);
            if (dir.yCoord != 0) {
                renderDiagonalBeam(gantry, x, y, z, dir);
            } else {
                renderFullBeam(gantry, x, y, z, dir);
            }
            return;
        }

        int cardinalCount = 0;
        Vec3 firstCard = null;
        Vec3 secondCard = null;
        for (Vec3 d : dirs) {
            if (isCardinal(d)) {
                cardinalCount++;
                if (firstCard == null) firstCard = d;
                else if (secondCard == null) secondCard = d;
            }
        }

        if (cardinalCount == 0) {
            renderDiagonalBeam(gantry, x, y, z, dirs.get(0));
            return;
        }

        int uncovered = getUncoveredCount(dirs);
        if (uncovered == 0) {
            renderPlusBeam(gantry, x, y, z);
            return;
        } else if (uncovered == 1) {
            Vec3 missing = getUncoveredMissingDirection(dirs);
            renderTBeam(gantry, x, y, z, missing);
            return;
        }

        if (cardinalCount == 4) {
            renderPlusBeam(gantry, x, y, z);
            return;
        }

        if (cardinalCount == 3) {
            Vec3 missing = findMissingCardinalDirection(dirs);
            if (missing != null) {
                if (hasDiagonalCoveringDirection(dirs, missing)) {
                    renderPlusBeam(gantry, x, y, z);
                } else {
                    renderTBeam(gantry, x, y, z, missing);
                }
            }
            return;
        }

        if (cardinalCount == 2) {
            if (isOpposite(firstCard, secondCard)) {
                renderFullBeam(gantry, x, y, z, firstCard);
            } else {
                renderCornerBeam(gantry, x, y, z, firstCard, secondCard);
            }
            return;
        }

        Vec3 cardDir = null;
        for (Vec3 d : dirs) {
            if (isCardinal(d)) {
                cardDir = d;
                break;
            }
        }

        for (Vec3 d : dirs) {
            if (!isCardinal(d)) {
                Vec3 horiz = Vec3.createVectorHelper(d.xCoord, 0, d.zCoord);
                if (isOpposite(cardDir, horiz)) {
                    if (d.yCoord > 0) {
                        renderDiagonalBeam(gantry, x, y, z, d);
                    } else {
                        renderFullBeam(gantry, x, y, z, cardDir);
                    }
                } else {
                    renderCornerBeam(gantry, x, y, z, cardDir, horiz);
                }
                return;
            }
        }

        renderErrorBeam(gantry, x, y, z);
    }

    /**
     * Checks if a diagonal ramp neighbour covers the missing cardinal direction
     * for a cross instead of a T-shape. When the ramp is below us (y < 0),
     * we are at its upper exit, so we use the raw horizontal component of d.
     */
    private boolean hasDiagonalCoveringDirection(List<Vec3> dirs, Vec3 horizDir) {
        for (Vec3 d : dirs) {
            if (d.yCoord != 0) {
                Vec3 diagHoriz = Vec3.createVectorHelper(d.xCoord, 0, d.zCoord);
                if (d.yCoord > 0) {
                    diagHoriz = Vec3.createVectorHelper(-d.xCoord, 0, -d.zCoord);
                }
                if (approxEqualHorizontal(diagHoriz, horizDir)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Counts how many of the 4 cardinal directions are neither a direct
     * cardinal neighbour nor covered by any diagonal ramp.
     */
    private int getUncoveredCount(List<Vec3> dirs) {
        int count = 0;
        for (Vec3 cand : CARDINAL_DIRECTIONS) {
            if (!isCovered(cand, dirs)) count++;
        }
        return count;
    }

    /**
     * Returns the single uncovered cardinal direction when exactly one remains.
     * Returns null otherwise.
     */
    private Vec3 getUncoveredMissingDirection(List<Vec3> dirs) {
        for (Vec3 cand : CARDINAL_DIRECTIONS) {
            if (!isCovered(cand, dirs)) return cand;
        }
        return null;
    }

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

    /**
     * Applies the base position and yaw/pitch rotation used by both the module
     * and the carriage.
     */
    private void applyGantryOrientation(double x, double y, double z, float dx, float dy, float dz, float yaw,
        float pitch) {
        GL11.glTranslated(x + 0.5 + dx, y + 0.5 + dy, z + 0.5 + dz);
        GL11.glRotatef(yaw, 0f, 1f, 0f);
        GL11.glRotatef(pitch, 1f, 0f, 0f);
    }

    private boolean isCardinal(Vec3 dir) {
        return dir.yCoord == 0
            && ((Math.abs(dir.xCoord) == 1 && dir.zCoord == 0) || (dir.xCoord == 0 && Math.abs(dir.zCoord) == 1));
    }

    private boolean isOpposite(Vec3 a, Vec3 b) {
        return Math.abs(a.xCoord + b.xCoord) < 0.01 && Math.abs(a.yCoord + b.yCoord) < 0.01
            && Math.abs(a.zCoord + b.zCoord) < 0.01;
    }

    /**
     * Compares the horizontal x and z components of two vectors.
     */
    private boolean approxEqualHorizontal(Vec3 a, Vec3 b) {
        return Math.abs(a.xCoord - b.xCoord) < 0.01 && Math.abs(a.zCoord - b.zCoord) < 0.01;
    }

    /**
     * Returns true if the given cardinal direction has a direct cardinal neighbour in the list.
     */
    private boolean hasDirectCardinal(Vec3 candidate, List<Vec3> dirs) {
        for (Vec3 d : dirs) {
            if (isCardinal(d) && approxEqualHorizontal(d, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the cardinal direction is covered by either a direct cardinal or a diagonal ramp.
     */
    private boolean isCovered(Vec3 candidate, List<Vec3> dirs) {
        return hasDirectCardinal(candidate, dirs) || hasDiagonalCoveringDirection(dirs, candidate);
    }

    /**
     * Finds the single missing cardinal direction (for T-shape).
     */
    private Vec3 findMissingCardinalDirection(List<Vec3> dirs) {
        for (Vec3 cand : CARDINAL_DIRECTIONS) {
            if (!hasDirectCardinal(cand, dirs)) return cand;
        }
        return null;
    }

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
            .bindTexture(g.getStraightTexture());
        g.getStraightModel()
            .renderAll();
        GL11.glPopMatrix();
    }

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
        g.getStraightModel()
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
        g.getStraightModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    private static void renderDiagonalBeam(TileEntityGantry g, double x, double y, double z, Vec3 dir) {
        Vec3 ascDir = (dir.yCoord >= 0) ? dir : Vec3.createVectorHelper(-dir.xCoord, -dir.yCoord, -dir.zCoord);
        Vec3 f = ascDir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        applyWorldLighting(g);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
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

    /**
     * Renders a T-shape using a dedicated model.
     * missingDir is the open side of the T.
     */
    private static void renderTBeam(TileEntityGantry g, double x, double y, double z, Vec3 missingDir) {
        Vec3 f = missingDir.normalize();
        float missingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        applyWorldLighting(g);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(missingYaw + 180f, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getSemiCrossTexture());
        g.getSemiCrossModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    private static void renderPlusBeam(TileEntityGantry g, double x, double y, double z) {
        applyWorldLighting(g);
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glScalef(GANTRY_SCALE, GANTRY_SCALE, GANTRY_SCALE);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(g.getCrossTexture());
        g.getCrossModel()
            .renderAll();
        GL11.glPopMatrix();
    }
}
