package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityRocketTrophy;

public class RocketTrophyRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityRocketTrophy trophy)) return;

        RocketVisualHelper.renderTrophy(trophy, x, y, z, partialTicks);
    }
}
