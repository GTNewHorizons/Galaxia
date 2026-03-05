package com.gtnewhorizons.galaxia.handlers;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.checkOxygenAndDrain;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getPlayerOxygenLevel;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getPressureProtection;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getRadiationProtection;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.getThermalProtection;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.hasOxygenmask;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.hasSporeFilter;
import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.isInGalaxiaDimension;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;

import com.gtnewhorizons.galaxia.registry.dimension.SolarSystemRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.utility.effects.GalaxiaEffects;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * A Handler class to deal with effects of entering a new Galaxia dimension
 */
public class DimensionEventHandler {

    public int counter;
    public final int BASE_EFFECT_DURATION = 40;
    int lowOxygenDuration = 0; // defines duration of player being low on oxygen in SECONDS

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
     * Generic function to apply effects to a player based off of dimension
     *
     * @param def    The EffectDef holding all effects of the relevant dimension
     * @param player The player entity
     */
    private void applyEffects(EffectBuilder def, EntityPlayer player) {
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
    private void applyWithering(EffectBuilder def, EntityPlayer player) {
        if (!def.getWithering(player)) return;
        if (player.isPotionActive(Potion.wither)) return;
        player.addPotionEffect(new PotionEffect(Potion.wither.id, BASE_EFFECT_DURATION, 1));
    }

    /**
     * Applies the "Spore" effect to the player - a random negative effect on player
     *
     * @param def    The dimensional Effect Definition
     * @param player The player entity
     */
    private void applySpores(EffectBuilder def, EntityPlayer player) {
        if (!def.getSpore(player)) return;
        boolean hasFilter = hasSporeFilter(player);

        if (hasFilter) return;
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
    private void applyTemperature(EffectBuilder def, EntityPlayer player) {
        // Temp until space suit added
        final int DEFAULT_MIN = 268; // -5 Celsius
        final int DEFAULT_MAX = 323; // 50 Celsius

        int temp = def.getTemperature(player);
        int acceptableMax = DEFAULT_MAX;
        int acceptableMin = DEFAULT_MIN;

        int heatProtection = getThermalProtection(player, true);
        int coldProtection = getThermalProtection(player, false);

        acceptableMax += heatProtection;
        acceptableMin -= coldProtection;

        if (temp < acceptableMax && temp > acceptableMin) return;

        int diff;
        if (temp < acceptableMin) {
            diff = acceptableMin - temp;
        } else {
            diff = temp - acceptableMax;
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
    private void applyLowOxygen(EffectBuilder def, EntityPlayer player) {
        int oxygenPercent = def.getOxygenPercent(player);
        if (oxygenPercent == 100) return;

        boolean hasOxygenToDrain = false;
        boolean hasMask = hasOxygenmask(player);
        if (hasMask) {
            hasOxygenToDrain = checkOxygenAndDrain(player, oxygenPercent);
        }

        float oxygenLevel = getPlayerOxygenLevel(player);
        if (oxygenLevel > 0.1 && hasMask) lowOxygenDuration = 0;
        else lowOxygenDuration++;

        // Apply low oxygen effects if oxygen is too low
        // java8 doesn't support switches for stuff like this and i don't want to make
        // it look messy so here it is
        if (oxygenLevel < 0.02)
            player.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, 4));
        else if (oxygenLevel < 0.04)
            player.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, 3));
        else if (oxygenLevel < 0.06)
            player.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, 2));
        else if (oxygenLevel < 0.08)
            player.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, 1));
        else if (oxygenLevel < 0.1)
            player.addPotionEffect(new PotionEffect(GalaxiaEffects.lowOxygen.getId(), BASE_EFFECT_DURATION, 0));

        if (hasOxygenToDrain) return;
        // Apply damage if no tank could be drained (tank is empty or no tanks
        // available)
        // damage scaled linearly so it can't be bypassed long-term by most of armors
        player.attackEntityFrom(this.noOxygen, lowOxygenDuration * 2);
    }

    /**
     * Applies the pressure effects to the player
     *
     * @param def    The EffectDef holding dimensional effects
     * @param player The Player entity
     */
    private void applyPressure(EffectBuilder def, EntityPlayer player) {
        int DEFAULT_MIN = 1;
        int DEFAULT_MAX = 2;
        // Temp until space suit added:
        int acceptableMin = DEFAULT_MIN;
        int acceptableMax = DEFAULT_MAX;
        int pressure = def.getPressure(player);

        acceptableMax += getPressureProtection(player, true);
        acceptableMin -= getPressureProtection(player, false);

        if (pressure <= acceptableMax && pressure >= acceptableMin) return;
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
    private void applyRadiation(EffectBuilder def, EntityPlayer player) {
        int radiation = def.getRadiation(player);
        if (radiation == 0) return;
        final int DEFAULT_MAX = 0;
        int acceptableMax = DEFAULT_MAX;

        acceptableMax += getRadiationProtection(player);
        if (radiation <= acceptableMax) return;
        player.attackEntityFrom(this.radiation, 5.0f);
    }

}
