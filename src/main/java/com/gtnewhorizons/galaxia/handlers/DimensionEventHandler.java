package com.gtnewhorizons.galaxia.handlers;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.isInGalaxiaDimension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingEvent;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.GalaxiaPlayerProperties;
import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;
import com.gtnewhorizons.galaxia.core.network.HazardWarningPacket;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.CelestialDimensionMaterializer;
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

    private static final List<IEnvironmentalHazard> ENVIRONMENTAL_HAZARDS = List.of(
        new HazardTemperature(),
        new HazardSpores(),
        new HazardOxygen(),
        new HazardWithering(),
        new HazardPressure(),
        new HazardZeroG(),
        new HazardRadiation());

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
            applyEffectsIfDue(player);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving instanceof EntityBody body && !body.worldObj.isRemote) applyEffectsIfDue(body);
    }

    private void applyEffectsIfDue(EntityLivingBase body) {
        if (body.isDead || !isInGalaxiaDimension(body) || body.ticksExisted % 20 != 0) return;
        applyEffects(
            CelestialDimensionMaterializer.findDefinitionById(body.dimension)
                .orElseThrow()
                .effects(),
            body);
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
        GalaxiaPlayerProperties.get(event.player).lowOxygenDuration = 0;
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
        GalaxiaPlayerProperties.get(event.player).lowOxygenDuration = 0;
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
        GalaxiaPlayerProperties.get(event.player).lowOxygenDuration = 0;
    }

    /**
     * Applies the shared environmental rules to the physical body in its current dimension.
     *
     * @param def  The EffectDef holding all effects of the relevant dimension
     * @param body The living body
     */
    private void applyEffects(EffectBuilder def, EntityLivingBase body) {
        if (body instanceof EntityPlayer player && player.capabilities.isCreativeMode
            && !ConfigPlayer.ConfigPlayerGlobal.applyDebuffsInCreative) {
            if (player instanceof EntityPlayerMP connected) {
                Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(List.of()), connected);
            }
            return;
        }
        List<HazardWarnings> warnings = new ArrayList<>();
        Optional<TileStation> station = Optional.ofNullable(
            GalaxiaAPI.getStationAround(
                body.worldObj,
                body.dimension,
                MathHelper.floor_double(body.posX),
                MathHelper.floor_double(body.posY),
                MathHelper.floor_double(body.posZ)));
        for (IEnvironmentalHazard h : ENVIRONMENTAL_HAZARDS) {
            HazardWarnings w = h.apply(def, body, station);
            if (w != HazardWarnings.FINE) {
                warnings.add(w);
            }
        }

        if (body instanceof EntityPlayerMP connected && !body.worldObj.isRemote) {
            Galaxia.GALAXIA_NETWORK.sendTo(new HazardWarningPacket(warnings), connected);
        }
    }

}
