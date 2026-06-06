package com.gtnewhorizons.galaxia.client.render.rockets;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class SiloRenderer extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySilo silo)) return;

        RocketBlueprint blueprint = silo.getBuiltBlueprint();
        if (blueprint == null || blueprint.isEmpty()) return;

        final int[] offset = TileEntitySilo.getRotatedOffset(
            TileEntitySilo.SILO_DEFAULT_X_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Y_OFFSET,
            TileEntitySilo.SILO_DEFAULT_Z_OFFSET,
            silo.currentFacing);

        RocketVisualHelper
            .renderBlueprint(blueprint, x + offset[0], y + offset[1], z + offset[2], 0.0f, partialTicks, true);
    }
}
