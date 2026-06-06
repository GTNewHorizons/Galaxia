package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketTrophy;

public class RocketTrophyRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityRocketTrophy trophy)) return;

        RocketBlueprint blueprint = trophy.getBlueprint();
        if (blueprint == null || blueprint.isEmpty()) return;

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();

        double dx = x + 0.5 + trophy.getOffsetX();
        double dy = y + 0.5 + trophy.getOffsetY();
        double dz = z + 0.5 + trophy.getOffsetZ();

        GL11.glTranslated(dx, dy, dz);

        GL11.glRotatef(trophy.getYaw(), 0f, 1f, 0f);
        GL11.glRotatef(trophy.getPitch(), 1f, 0f, 0f);
        GL11.glScalef(trophy.getScale(), trophy.getScale(), trophy.getScale());

        RocketVisualHelper.renderBlueprint(blueprint, 0, 0, 0, 0f, partialTicks, false);

        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }
}
