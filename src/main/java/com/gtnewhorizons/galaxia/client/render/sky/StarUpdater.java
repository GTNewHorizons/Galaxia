package com.gtnewhorizons.galaxia.client.render.sky;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class StarUpdater {
    @SubscribeEvent
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load event) {
        if (!event.world.isRemote) {
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.renderGlobal != null) {
            jss.notfine.render.RenderStars.reloadStarRenderList(mc.renderGlobal);
        }
    }
}
