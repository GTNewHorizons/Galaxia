package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import static com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo.SILO_DEFAULT_X_OFFSET;
import static com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo.SILO_DEFAULT_Y_OFFSET;
import static com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo.SILO_DEFAULT_Z_OFFSET;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

public class SiloRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySilo silo) || !silo.shouldRender || silo.getNumModules() == 0) return;
        final int[] offset = TileEntitySilo
            .getRotatedOffset(SILO_DEFAULT_X_OFFSET, SILO_DEFAULT_Y_OFFSET, SILO_DEFAULT_Z_OFFSET, silo.currentFacing);
        RocketVisualHelper.render(silo.getAssembly(), x + offset[0], y + offset[1], z + offset[2], true);
    }
}
