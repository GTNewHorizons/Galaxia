package com.gtnewhorizons.galaxia.handlers;

import com.gtnewhorizons.galaxia.registry.items.special.ItemKineticTether;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class TetherEventHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        ItemKineticTether.onPlayerTick(event.player);
    }
}
