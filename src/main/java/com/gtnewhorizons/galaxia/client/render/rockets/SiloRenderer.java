package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class SiloRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySilo silo)) return;

        RocketVisualHelper.renderSilo(silo, x, y, z, partialTicks);
    }
}
