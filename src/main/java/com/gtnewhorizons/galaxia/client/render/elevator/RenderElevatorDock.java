package com.gtnewhorizons.galaxia.client.render.elevator;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.registry.block.special.TileElevatorDock;

/**
 * Draws the elevator cable hanging down from a dock while an elevator below is connected
 */
public class RenderElevatorDock extends TileEntitySpecialRenderer {

    @Override
    public void renderTileEntityAt(TileEntity tile, double x, double y, double z, float partialTicks) {
        if (!(tile instanceof TileElevatorDock dock) || !dock.isLinked()) return;
        RenderElevatorCable.renderCable(tile.getWorldObj(), x, y - RenderElevatorCable.CABLE_HEIGHT, z, partialTicks);
    }
}
