package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shows a wireframe of the rail piece a held Gantry Shaft would become, connections included
 */
@SideOnly(Side.CLIENT)
public class GantryPlacementPreviewHandler {

    private static final float GHOST_ALPHA = 0.65f;

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        EntityPlayer player = event.player;
        ItemStack held = player.getHeldItem();

        if (held == null || Block.getBlockFromItem(held.getItem()) != GalaxiaBlocksEnum.GANTRY.get()) return;

        MovingObjectPosition mop = event.target;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        World world = player.worldObj;
        int x = mop.blockX, y = mop.blockY, z = mop.blockZ;
        // Like vanilla placement, replaceable blocks are placed into, anything else gets the block on its face
        if (!world.getBlock(x, y, z)
            .isReplaceable(world, x, y, z)) {
            x += Facing.offsetsXForSide[mop.sideHit];
            y += Facing.offsetsYForSide[mop.sideHit];
            z += Facing.offsetsZForSide[mop.sideHit];
            if (!world.getBlock(x, y, z)
                .isReplaceable(world, x, y, z)) return;
        }

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        setGhostGLState();
        GL11.glPushMatrix();
        GL11.glTranslated(x - px + 0.5, y - py + 0.5, z - pz + 0.5);
        GantryRailModels.render(world, x, y, z, TileEntityGantry.connectionMask(world, x, y, z));
        GL11.glPopMatrix();
        restoreGLState();
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
}
