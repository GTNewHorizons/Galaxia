package com.gtnewhorizons.galaxia.utility;

import static com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry.GALAXIA_DIMENSIONS;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.dimension.EffectDef;
import com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * A Handler class to deal with effects of entering a new Galaxia dimension
 */
public class DimensionEventHandler {

    public int counter;
    public final int BASE_EFFECT_DURATION = 40;

    DamageSource temperature = new DamageSource("galaxia.temperature").setDamageBypassesArmor()
        .setMagicDamage();
    DamageSource noOxygen = new DamageSource("galaxia.noOxygen").setDamageBypassesArmor()
        .setMagicDamage();
    DamageSource radiation = new DamageSource("galaxia.radiation").setDamageBypassesArmor()
        .setMagicDamage();

    public DimensionEventHandler() {
        this.counter = 0;
    }

    /**
     * Event Handler method that runs every tick, primarily used at the moment to apply dimensional transfer effects
     * USE WITH CAUTION - this method runs every player tick on the server, use guard clauses where possible to not
     * waste computation
     *
     * @param event The player tick event
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (EntityPlayer player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            if (!GALAXIA_DIMENSIONS.contains(player.dimension)) continue;
            if (player.ticksExisted % 20 != 0) continue;
            applyEffects(SolarSystemRegistry.getById(player.dimension).effects, player);
        }
    }

    /**
     * Generic function to apply effects to a player based off of dimension
     *
     * @param def    The EffectDef holding all effects of the relevant dimension
     * @param player The player entity
     */
    private void applyEffects(EffectDef def, EntityPlayer player) {
        // TODO: Implement equipment - currently assumes base player with no inventory
        // Temperature Handling
        applyTemperature(def, player);

        // Pressure Handling
        applyPressure(def, player);

        // Oxygen Handling
        applyLowOxygen(def, player);

        // Radiation Handling
        applyRadiation(def, player);

        // Wither Handling
        applyWithering(def, player);

        // Spores Handling
        applySpores(def, player);
    }

    /**
     * Applies wither where needed
     *
     * @param def    The EffectDef holding the dimensional effects
     * @param player The player entity
     */
    private void applyWithering(EffectDef def, EntityPlayer player) {
        if (!def.withering) return;
        if (player.isPotionActive(Potion.wither)) return;
        player.addPotionEffect(new PotionEffect(Potion.wither.id, BASE_EFFECT_DURATION, 1));
    }

    /**
     * Applies the "Spore" effect to the player - a random negative effect on player
     *
     * @param def    The dimensional Effect Definition
     * @param player The player entity
     */
    private void applySpores(EffectDef def, EntityPlayer player) {
        if (!def.spores) return;
        List<Integer> possibleEffects = Arrays.asList(2, 4, 15, 17, 18, 19, 20);
        /*
         * 2 = Slowness
         * 4 = Fatigue
         * 15 = Blindness
         * 17 = Hunger
         * 18 = Weakness
         * 19 = Poison
         * 20 = Wither
         */

        // Check if one of above conditions already applied
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (possibleEffects.contains(effect.getPotionID())) {
                return;
            }
        }

        // Add a random effect from list
        Random random = new Random();

        int effectToAdd = possibleEffects.get(random.nextInt(possibleEffects.size() - 1) + 1);
        player.addPotionEffect(new PotionEffect(effectToAdd, BASE_EFFECT_DURATION, 1));
    }

    /**
     * Applies the effects of Temperature for the player:
     * Diff < 20K = Slowness/Fatigue 1
     * 20K <= Diff <= 50K = Slowness/Fatigue 2
     * Diff > 50K = Slowness/Fatigue 3 and 1 heart per second damange
     *
     * @param def    The EffectDef of the dimension
     * @param player The player entity
     */
    private void applyTemperature(EffectDef def, EntityPlayer player) {
        // Temp until space suit added
        int acceptableMin = 268; // -5 Celsius
        int acceptableMax = 323; // 50 Celsius

        if (def.baseTemp < acceptableMax && def.baseTemp > acceptableMin) return;

        int diff;
        if (def.baseTemp < acceptableMin) {
            diff = acceptableMin - def.baseTemp;
        } else {
            diff = def.baseTemp - acceptableMax;
        }

        if (diff < 20) {
            player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION, 0));
            player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION, 0));
        } else if (diff <= 50) {

            player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION, 1));
            player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION, 1));
        } else {
            player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION, 2));
            player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION, 2));
            player.attackEntityFrom(this.temperature, 2.0f);
        }

    }

    /**
     * Applies the effects of low oxygen to the player
     *
     * @param def    The EffectDef of the dimension
     * @param player The player entity
     */
    private void applyLowOxygen(EffectDef def, EntityPlayer player) {
        if (def.oxygenPercent == 100) return;
        // Temp until oxygen gear added
        boolean hasOxygenGear = false;
        if (hasOxygenGear) return;
        player.attackEntityFrom(this.noOxygen, 3.0f);
    }

    /**
     * Applies the pressure effects to the player
     *
     * @param def    The EffectDef holding dimensional effects
     * @param player The Player entity
     */
    private void applyPressure(EffectDef def, EntityPlayer player) {
        // Temp until space suit added:
        int acceptableMin = 1;
        int acceptableMax = 2;
        if (def.pressure <= acceptableMax && def.pressure >= acceptableMin) return;
        if (player.isPotionActive(Potion.moveSlowdown)) return;
        if (player.isPotionActive(Potion.digSlowdown)) return;
        player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, BASE_EFFECT_DURATION, 1));
        player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BASE_EFFECT_DURATION, 1));

    }

    /**
     * Applies the effects of radiation to the player
     *
     * @param def    The EffectDef holding dimensional effects
     * @param player The player entity
     */
    private void applyRadiation(EffectDef def, EntityPlayer player) {
        if (def.radiation == 0) return;
        // Temp until radiation suit added
        boolean hasRadSuit = false;
        if (hasRadSuit) return;
        player.attackEntityFrom(this.radiation, 5.0f);
    }

}
