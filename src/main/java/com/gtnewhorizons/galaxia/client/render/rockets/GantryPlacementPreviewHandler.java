package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.BlockGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.GantryAPI;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GantryPlacementPreviewHandler {

    /** Shared dummy TE used solely to access lazily-loaded OBJ models / textures. */
    private static TileEntityGantry ghostDummy = null;

    private static final float GHOST_ALPHA = 0.65f;

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        EntityPlayer player = event.player;
        ItemStack held = player.getHeldItem();

        if (held == null || !(Block.getBlockFromItem(held.getItem()) instanceof BlockGantry)) return;

        MovingObjectPosition mop = event.target;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        // Candidate placement position (the face the player is looking at)
        int x = mop.blockX + Facing.offsetsXForSide[mop.sideHit];
        int y = mop.blockY + Facing.offsetsYForSide[mop.sideHit];
        int z = mop.blockZ + Facing.offsetsZForSide[mop.sideHit];

        World world = player.worldObj;

        TileEntityGantry conflictGantry = null;
        TileEntity below = world.getTileEntity(x, y - 1, z);
        TileEntity above = world.getTileEntity(x, y + 1, z);
        if (below instanceof TileEntityGantry g) conflictGantry = g;
        else if (above instanceof TileEntityGantry g) conflictGantry = g;

        int finalX = x, finalY = y, finalZ = z;
        boolean redirected = false;

        if (conflictGantry != null) {
            Vec3 redirect = BlockGantry.getLineEndDirection(conflictGantry);
            finalX = x + (redirect != null ? (int) redirect.xCoord : 1);
            finalZ = z + (redirect != null ? (int) redirect.zCoord : 0);
            redirected = true;
        }

        // Don't preview if the final position is already occupied
        if (!world.getBlock(finalX, finalY, finalZ)
            .isReplaceable(world, finalX, finalY, finalZ)) return;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        double rx = finalX - px;
        double ry = finalY - py;
        double rz = finalZ - pz;

        // Don't preview if where we want to place isn't legal
        if (!(new BlockGantry().canPlaceBlockAt(world, x, y, z))) return;

        renderGhostModel(world, finalX, finalY, finalZ, rx, ry, rz);

        if (redirected) {
            event.setCanceled(true);
        }
    }

    /**
     * Renders a ghost of the gantry at the final position
     */
    private static void renderGhostModel(World world, int finalX, int finalY, int finalZ, double rx, double ry,
        double rz) {

        ensureGhostDummy();

        Vec3 connectDir = null;
        boolean isDiagonal = false;
        for (Vec3 offset : GantryAPI.CHECK_OFFSETS) {
            int cx = finalX + (int) offset.xCoord;
            int cy = finalY + (int) offset.yCoord;
            int cz = finalZ + (int) offset.zCoord;
            if (world.getTileEntity(cx, cy, cz) instanceof TileEntityGantry) {
                connectDir = offset;
                isDiagonal = (offset.yCoord != 0);
                break;
            }
        }

        if (connectDir == null) connectDir = Vec3.createVectorHelper(1, 0, 0);

        setGhostGLState();

        if (isDiagonal) {
            renderGhostDiagonal(connectDir, rx, ry, rz);
        } else {
            renderGhostStraight(connectDir, rx, ry, rz);
        }

        restoreGLState();
    }

    private static void renderGhostStraight(Vec3 dir, double rx, double ry, double rz) {
        Vec3 f = dir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        GL11.glPushMatrix();
        GL11.glTranslated(rx + 0.5, ry + 0.5, rz + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glScalef(1f, 1f, 1f);

        Minecraft.getMinecraft().renderEngine.bindTexture(ghostDummy.getStraightTexture());
        ghostDummy.getStraightModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    private static void renderGhostDiagonal(Vec3 dir, double rx, double ry, double rz) {
        Vec3 ascDir = (dir.yCoord >= 0) ? dir : Vec3.createVectorHelper(-dir.xCoord, -dir.yCoord, -dir.zCoord);
        Vec3 f = ascDir.normalize();
        float facingYaw = (float) Math.toDegrees(Math.atan2(f.xCoord, f.zCoord));

        GL11.glPushMatrix();
        GL11.glTranslated(rx + 0.5, ry + 0.5, rz + 0.5);
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glRotatef(facingYaw, 0, 1, 0);
        GL11.glRotatef(180, 0, 1, 0);
        GL11.glScalef(1f, 1f, 1f);

        Minecraft.getMinecraft().renderEngine.bindTexture(ghostDummy.getDiagonalTexture());
        ghostDummy.getDiagonalModel()
            .renderAll();
        GL11.glPopMatrix();
    }

    private static void setGhostGLState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2.0F);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, GHOST_ALPHA);
    }

    private static void restoreGLState() {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.0F);

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static void ensureGhostDummy() {
        if (ghostDummy == null) ghostDummy = new TileEntityGantry();
    }
}
