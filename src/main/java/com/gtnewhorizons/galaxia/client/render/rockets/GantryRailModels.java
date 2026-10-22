package com.gtnewhorizons.galaxia.client.render.rockets;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Picks and draws the rail piece of a gantry block from its connections. All pieces sweep the same I-beam, so they
 * join seamlessly:
 * <ul>
 * <li>one or two opposite connections: a straight beam</li>
 * <li>two flat connections at a right angle: a rounded corner</li>
 * <li>a flat and a vertical connection: a rounded bend</li>
 * <li>three or more: a straight beam through the block with branches welded on, their webs reaching into it, or a
 * hub with arms when no two connections line up</li>
 * </ul>
 * A vertical shaft faces the bend at its bottom. When the bend at its top turns the other way, that bend twists the
 * beam a quarter round while it bends, so the flanges never jump between two blocks
 */
@SideOnly(Side.CLIENT)
public final class GantryRailModels {

    public static final ResourceLocation TEXTURE = LocationGalaxia("textures/model/gantry/rail.png");
    public static final ResourceLocation STRAIGHT = LocationGalaxia("textures/model/gantry/rail_straight.obj");
    private static final ResourceLocation ARM = LocationGalaxia("textures/model/gantry/rail_arm.obj");
    private static final ResourceLocation BRANCH = LocationGalaxia("textures/model/gantry/rail_branch.obj");
    private static final ResourceLocation CORNER = LocationGalaxia("textures/model/gantry/rail_corner.obj");
    private static final ResourceLocation BEND = LocationGalaxia("textures/model/gantry/rail_bend.obj");
    private static final ResourceLocation BEND_TWIST = LocationGalaxia("textures/model/gantry/rail_bend_twist.obj");
    private static final ResourceLocation HUB = LocationGalaxia("textures/model/gantry/rail_hub.obj");

    /** How far a vertical beam looks along its shaft for the bend that decides its orientation */
    private static final int SHAFT_SEARCH = 64;
    private static final ForgeDirection[] THROUGH_AXES = { ForgeDirection.EAST, ForgeDirection.SOUTH,
        ForgeDirection.UP };
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private GantryRailModels() {}

    /**
     * Draws the rail of a block centred on the current GL origin
     *
     * @param world The world, used to orient vertical shafts
     * @param x     X position of the block
     * @param y     Y position of the block
     * @param z     Z position of the block
     * @param mask  Connected sides, bit per {@link ForgeDirection} ordinal
     */
    public static void render(IBlockAccess world, int x, int y, int z, int mask) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);

        List<ForgeDirection> arms = new ArrayList<>(6);
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (has(mask, side)) arms.add(side);
        }

        if (arms.isEmpty()) {
            draw(STRAIGHT, ForgeDirection.EAST, ForgeDirection.UP);
            return;
        }
        if (arms.size() == 1) {
            ForgeDirection arm = arms.getFirst();
            draw(STRAIGHT, arm, up(world, x, y, z, arm));
            return;
        }
        if (arms.size() == 2) {
            ForgeDirection a = arms.get(0), b = arms.get(1);
            if (a == b.getOpposite()) {
                draw(STRAIGHT, a, up(world, x, y, z, a));
            } else if (a.offsetY == 0 && b.offsetY == 0) {
                drawCorner(a, b);
            } else {
                // Bend from the flat arm up or down into the vertical one
                ForgeDirection flat = a.offsetY == 0 ? a : b;
                ForgeDirection vertical = a.offsetY == 0 ? b : a;
                draw(twists(world, x, y, z, flat, vertical) ? BEND_TWIST : BEND, flat.getOpposite(), vertical);
            }
            return;
        }

        ForgeDirection through = null;
        for (ForgeDirection axis : THROUGH_AXES) {
            if (has(mask, axis) && has(mask, axis.getOpposite())) {
                through = axis;
                break;
            }
        }
        if (through != null) {
            draw(STRAIGHT, through, up(world, x, y, z, through));
        } else {
            ModelCache.get(HUB)
                .renderAll();
        }
        for (ForgeDirection arm : arms) {
            if (through != null && (arm == through || arm == through.getOpposite())) continue;
            draw(through != null ? BRANCH : ARM, arm, up(world, x, y, z, arm));
        }
    }

    /**
     * Turns the flat corner piece, which joins the -X and +Z faces, so it joins the two given faces
     */
    private static void drawCorner(ForgeDirection a, ForgeDirection b) {
        // Swapping the arms keeps the turn a rotation, a mirrored model would light inside out
        if (cross(a.getOpposite(), ForgeDirection.UP) != b) {
            ForgeDirection swap = a;
            a = b;
            b = swap;
        }
        draw(CORNER, a.getOpposite(), ForgeDirection.UP);
    }

    /**
     * Which way the flanges of a beam along the given axis face
     */
    private static ForgeDirection up(IBlockAccess world, int x, int y, int z, ForgeDirection axis) {
        return axis.offsetY == 0 ? ForgeDirection.UP : shaftSide(world, x, y, z);
    }

    /**
     * Whether a bend at the top of a shaft has to twist, because the bend at the bottom set the shaft square to it
     */
    private static boolean twists(IBlockAccess world, int x, int y, int z, ForgeDirection flat,
        ForgeDirection vertical) {
        if (vertical != ForgeDirection.DOWN) return false;
        ForgeDirection bottom = bottomFlat(world, x, y - 1, z);
        return bottom != null && bottom.offsetX == 0 != (flat.offsetX == 0);
    }

    /**
     * Flat direction a vertical shaft faces: the bend at its bottom, or the one at its top if the shaft has no bend
     * below. Every beam of a shaft gets the same answer, so the whole shaft faces the same way
     */
    private static ForgeDirection shaftSide(IBlockAccess world, int x, int y, int z) {
        ForgeDirection bottom = bottomFlat(world, x, y, z);
        if (bottom != null) return bottom;
        int cy = y;
        for (int i = 0; i < SHAFT_SEARCH; i++) {
            int mask = TileEntityGantry.connectionMask(world, x, cy, z);
            ForgeDirection flat = firstFlat(mask);
            if (flat != null) return flat;
            if (!has(mask, ForgeDirection.UP)) break;
            cy++;
        }
        return ForgeDirection.EAST;
    }

    /**
     * Flat arm of the first block down a shaft that has one, null when the shaft ends without a bend
     */
    private static ForgeDirection bottomFlat(IBlockAccess world, int x, int y, int z) {
        int cy = y;
        for (int i = 0; i < SHAFT_SEARCH; i++) {
            int mask = TileEntityGantry.connectionMask(world, x, cy, z);
            ForgeDirection flat = firstFlat(mask);
            if (flat != null) return flat;
            if (!has(mask, ForgeDirection.DOWN)) return null;
            cy--;
        }
        return null;
    }

    private static ForgeDirection firstFlat(int mask) {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (side.offsetY == 0 && has(mask, side)) return side;
        }
        return null;
    }

    /**
     * Draws a model with its X axis turned to {@code xAxis} and its Y axis to {@code yAxis}
     */
    private static void draw(ResourceLocation model, ForgeDirection xAxis, ForgeDirection yAxis) {
        GL11.glPushMatrix();
        multiply(xAxis.offsetX, xAxis.offsetY, xAxis.offsetZ, yAxis.offsetX, yAxis.offsetY, yAxis.offsetZ);
        IModelCustom custom = ModelCache.get(model);
        custom.renderAll();
        GL11.glPopMatrix();
    }

    /**
     * Multiplies the GL matrix by the rotation that takes the model X and Y axes to the given vectors, Z follows as
     * their cross product. Both vectors must be unit length and perpendicular
     */
    static void multiply(double xx, double xy, double xz, double yx, double yy, double yz) {
        double zx = xy * yz - xz * yy;
        double zy = xz * yx - xx * yz;
        double zz = xx * yy - xy * yx;
        MATRIX.clear();
        MATRIX.put(
            new float[] { (float) xx, (float) xy, (float) xz, 0, (float) yx, (float) yy, (float) yz, 0, (float) zx,
                (float) zy, (float) zz, 0, 0, 0, 0, 1 });
        MATRIX.flip();
        GL11.glMultMatrix(MATRIX);
    }

    private static ForgeDirection cross(ForgeDirection a, ForgeDirection b) {
        int x = a.offsetY * b.offsetZ - a.offsetZ * b.offsetY;
        int y = a.offsetZ * b.offsetX - a.offsetX * b.offsetZ;
        int z = a.offsetX * b.offsetY - a.offsetY * b.offsetX;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (side.offsetX == x && side.offsetY == y && side.offsetZ == z) return side;
        }
        return ForgeDirection.UNKNOWN;
    }

    private static boolean has(int mask, ForgeDirection side) {
        return (mask & (1 << side.ordinal())) != 0;
    }
}
