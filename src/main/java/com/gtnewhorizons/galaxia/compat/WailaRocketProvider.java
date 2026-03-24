package com.gtnewhorizons.galaxia.compat;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.entities.EntityRocket;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

public class WailaRocketProvider implements IWailaEntityProvider {

    public static final WailaRocketProvider INSTANCE = new WailaRocketProvider();

    public static void register(IWailaRegistrar registrar) {
        registrar.registerHeadProvider(INSTANCE, EntityRocket.class);
    }

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(Entity entity, List<String> tip, IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        if (entity instanceof EntityRocket rocket) {
            tip.clear(); // Remove default name
            if (rocket.isLander()) {
                tip.add(StatCollector.translateToLocal("entity.galaxia.EntityRocket.lander"));
            } else {
                tip.add(StatCollector.translateToLocal("entity.galaxia.EntityRocket.name"));
            }
        }
        return tip;
    }

    @Override
    public List<String> getWailaBody(Entity entity, List<String> tip, IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaTail(Entity entity, List<String> tip, IWailaEntityAccessor accessor,
        IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, Entity entity, NBTTagCompound tag, World world) {
        return tag;
    }
}
