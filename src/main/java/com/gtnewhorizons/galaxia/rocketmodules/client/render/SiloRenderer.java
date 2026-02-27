package com.gtnewhorizons.galaxia.rocketmodules.client.render;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.rocketmodules.ModuleType;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

/**
 * Class to deal with specifics of rendering Rockets on Silo
 */
public class SiloRenderer extends TileEntitySpecialRenderer {

    /**
     * TE renderer that works on a step-through process to build a solid model matrix and render
     *
     * @param te           Silo tile entity
     * @param x            X position of tile entity
     * @param y            Y position of tile entity
     * @param z            Z position of tile entity
     * @param partialTicks How far through current tick world is
     */
    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntitySilo silo) || !silo.shouldRender || silo.getNumModules() == 0) return;

        RocketVisualHelper.render(silo.getAssembly(), x, y + 1.0, z, true);
    }

    /**
     * Helper function to push a new model onto the matrix and render
     *
     * @param info    ModuleInfo used to get type of module etc.
     * @param x       x position of render
     * @param y       y position of render
     * @param z       z position of render
     * @param offsetX x offset from TE origin
     * @param offsetZ z offset from TE origin
     */
    private void renderModule(ModuleType info, double x, double y, double z, double offsetX, double offsetZ) {
        bindTexture(info.getTexture());
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5 + offsetX, y, z + 0.5 + offsetZ);
        info.getModel()
            .renderAll();
        GL11.glPopMatrix();
    }
}
