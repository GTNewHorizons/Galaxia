package com.gtnewhorizons.galaxia.core;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

public class GalaxiaPlayerProperties implements IExtendedEntityProperties {

    public static final String propertyId = "GalaxiaPlayerProperties";

    /**
     * How long the player has been with low oxygen in seconds
     */
    public int lowOxygenDuration = 0;

    @Override
    public void init(Entity entity, World world) {
        // noop
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        // todo
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        // todo
    }

    // convenience method for getting the instance tied to a player
    public static GalaxiaPlayerProperties get(@Nonnull EntityPlayer p) {
        return (GalaxiaPlayerProperties) p.getExtendedProperties(propertyId);
    }
}
