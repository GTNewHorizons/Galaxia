package com.gtnewhorizons.galaxia.handlers;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isInGalaxiaDimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.core.network.HazardWarningPacket;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.hazards.HazardOxygen;
import com.gtnewhorizons.galaxia.registry.hazards.HazardPressure;
import com.gtnewhorizons.galaxia.registry.hazards.HazardRadiation;
import com.gtnewhorizons.galaxia.registry.hazards.HazardSpores;
import com.gtnewhorizons.galaxia.registry.hazards.HazardTemperature;
import com.gtnewhorizons.galaxia.registry.hazards.HazardWarnings;
import com.gtnewhorizons.galaxia.registry.hazards.HazardWithering;
import com.gtnewhorizons.galaxia.registry.hazards.HazardZeroG;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * A Handler class to deal with effects of entering a new Galaxia dimension
 */
public class DimensionEventHandler {

    public int counter;

    private List<HazardWarnings> batchedWarnings;

    private static final List<IEnvironmentalHazard> ENVIRONMENTAL_HAZARDS = List.of(
        new HazardTemperature(),
        new HazardSpores(),
        new HazardOxygen(),
        new HazardWithering(),
        new HazardPressure(),
        new HazardZeroG(),
        new HazardRadiation());

    public DimensionEventHandler() {
        this.counter = 0;
        this.batchedWarnings = new ArrayList<>();
    }

    /**
     * Event Handler method that runs every tick, primarily used at the moment to
     * apply dimensional transfer effects
     * USE WITH CAUTION - this method runs every player tick on the server, use
     * guard clauses where possible to not
     * waste computation
     *
     * @param event The player tick event
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (EntityPlayer player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (!isInGalaxiaDimension(player)) continue;
            if (player.ticksExisted % 20 != 0) continue;
            applyEffects(
                SolarSystemRegistry.getById(player.dimension)
                    .effects(),
                player);
        }
    }

    /**
     * Clear warnings when changing dimensions
     *
     * @param event
     */
    @SubscribeEvent
    public void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (GalaxiaAPI.isInGalaxiaDimension(player)) return;
        Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(new ArrayList<>()), player);
    }

    /**
     * Clear warnings when respawning
     *
     * @param event
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (GalaxiaAPI.isInGalaxiaDimension(player)) return;
        Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(new ArrayList<>()), player);
    }

    /**
     * Clear warnings when joining a world
     *
     * @param event
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (GalaxiaAPI.isInGalaxiaDimension(player)) return;
        Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(new ArrayList<>()), player);
    }

    /**
     * Generic function to apply effects to a player based off of dimension
     *
     * @param def    The EffectDef holding all effects of the relevant dimension
     * @param player The player entity
     */
    private void applyEffects(EffectBuilder def, EntityPlayer player) {
        if (player.capabilities.isCreativeMode && !ConfigPlayer.ConfigPlayerGlobal.applyDebuffsInCreative) {
            if (!this.batchedWarnings.isEmpty()) {
                this.batchedWarnings.clear();
                // Clear warnings on the client
                Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(this.batchedWarnings), (EntityPlayerMP) player);
            }

            return;
        }

        this.batchedWarnings.clear();

        Optional<TileStation> station = Optional.ofNullable(
            GalaxiaAPI.getStationAround(
                player.worldObj,
                player.dimension,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ));
        for (IEnvironmentalHazard h : ENVIRONMENTAL_HAZARDS) {
            HazardWarnings w = h.apply(def, player, station);
            if (w != HazardWarnings.FINE) {
                batchedWarnings.add(w);
            }
        }

        if (!player.worldObj.isRemote) {
            Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(this.batchedWarnings), (EntityPlayerMP) player);
        }
    }

}
