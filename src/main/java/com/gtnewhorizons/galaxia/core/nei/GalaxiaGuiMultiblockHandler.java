package com.gtnewhorizons.galaxia.core.nei;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaMultiblockBase;

import blockrenderer6343.api.utils.CreativeItemSource;
import blockrenderer6343.client.utils.BRUtil;
import blockrenderer6343.integration.nei.GuiMultiblockHandler;

public class GalaxiaGuiMultiblockHandler extends GuiMultiblockHandler {

    @Override
    protected void placeMultiblock() {
        if (!(renderingController instanceof GalaxiaMultiblockBase<?>dummy)) return;

        renderer.world.setBlock(MB_PLACE_POS.x, MB_PLACE_POS.y, MB_PLACE_POS.z, dummy.getControllerBlock(), 0, 3);

        TileEntity te = renderer.world.getTileEntity(MB_PLACE_POS.x, MB_PLACE_POS.y, MB_PLACE_POS.z);

        if (!(te instanceof GalaxiaMultiblockBase<?>live)) return;

        renderingController = live;
        this.trigger = DEFAULT_TRIGGER;

        int iterations = 0;
        while (renderer.world.hasChanged() && iterations < MAX_PLACE_ROUNDS) {
            live.survivalConstruct(
                getBuildTriggerStack(),
                Integer.MAX_VALUE,
                ISurvivalBuildEnvironment.create(CreativeItemSource.instance, BRUtil.FAKE_PLAYER));
            iterations++;
        }
    }
}
