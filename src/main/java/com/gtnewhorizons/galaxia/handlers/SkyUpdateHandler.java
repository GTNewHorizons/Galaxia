package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import jss.notfine.render.RenderStars;

public class SkyUpdateHandler {

    private static Integer lastDim = null;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderGlobal == null) {
            lastDim = null;
            return;
        }

        int dim = mc.theWorld.provider.dimensionId;
        if (lastDim == null || lastDim != dim) {
            lastDim = dim;
            RenderStars.reloadStarRenderList(mc.renderGlobal);
        }
    }
}
