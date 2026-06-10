package com.gtnewhorizons.galaxia.core;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Holds properties tied to the player itself
 */
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
        NBTTagCompound data = (NBTTagCompound) compound.getTag(propertyId);

        lowOxygenDuration = data.getInteger("lowOxygenDuration");
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        // save stuff as a nested nbt tag to avoid potential conflicts
        NBTTagCompound data = new NBTTagCompound();
        compound.setTag(propertyId, data);

        data.setInteger("lowOxygenDuration", lowOxygenDuration);
    }

    // convenience method for getting the instance tied to a player
    public static GalaxiaPlayerProperties get(@Nonnull EntityPlayer p) {
        return (GalaxiaPlayerProperties) p.getExtendedProperties(propertyId);
    }

    /**
     * Copies data that should persist between clones from `oldProperties` to `this`
     *
     * @param oldProperties properties to copy from
     */
    private void copyFrom(@Nonnull GalaxiaPlayerProperties oldProperties) {
        this.lowOxygenDuration = oldProperties.lowOxygenDuration;
    }

    /**
     * Called when the player has died, used to reset any properties that shouldn't persist between
     * respawns.
     */
    private void onDeath() {
        this.lowOxygenDuration = 0;
    }

    public final static class PlayerEventHandler {

        @SubscribeEvent
        public void onClonePlayer(PlayerEvent.Clone p) {
            GalaxiaPlayerProperties oldData = GalaxiaPlayerProperties.get(p.original);
            GalaxiaPlayerProperties newData = GalaxiaPlayerProperties.get(p.entityPlayer);

            newData.copyFrom(oldData);

            if (p.wasDeath) newData.onDeath();
        }

        @SubscribeEvent
        public void entityConstruct(EntityEvent.EntityConstructing e) {
            if (!(e.entity instanceof EntityPlayer)) return;
            if (e.entity.getExtendedProperties(GalaxiaPlayerProperties.propertyId) != null) return;

            e.entity.registerExtendedProperties(GalaxiaPlayerProperties.propertyId, new GalaxiaPlayerProperties());
        }
    }
}
