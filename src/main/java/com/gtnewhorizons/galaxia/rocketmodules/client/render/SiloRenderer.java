package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class SiloRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySilo silo) || !silo.shouldRender || silo.getNumModules() == 0) return;
        RocketVisualHelper.render(silo.getAssembly(), x, y + 1.0, z, true);
    }
}
