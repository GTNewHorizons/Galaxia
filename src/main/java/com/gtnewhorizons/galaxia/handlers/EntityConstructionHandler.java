package com.gtnewhorizons.galaxia.handlers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;

import com.gtnewhorizons.galaxia.core.GalaxiaPlayerProperties;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class EntityConstructionHandler {

    @SubscribeEvent
    public void entityConstruct(EntityEvent.EntityConstructing e) {
        if (!(e.entity instanceof EntityPlayer)) return;
        if (e.entity.getExtendedProperties(GalaxiaPlayerProperties.propertyId) != null) return;

        e.entity.registerExtendedProperties(GalaxiaPlayerProperties.propertyId, new GalaxiaPlayerProperties());
    }
}
