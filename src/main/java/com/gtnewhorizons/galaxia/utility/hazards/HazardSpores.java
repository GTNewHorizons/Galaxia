package com.gtnewhorizons.galaxia.utility.hazards;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.hasSporeFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;

public class HazardSpores extends EnvironmentalHazard {

    public static final List<Potion> possibleEffects = Arrays.asList(
        Potion.moveSlowdown,
        Potion.digSlowdown,
        Potion.blindness,
        Potion.hunger,
        Potion.weakness,
        Potion.poison,
        Potion.wither);

    private static final Random rand = new Random();

    /**
     * Applies the "Spore" effect to the player - a random negative effect on player
     *
     * @param def    The dimensional Effect Definition
     * @param player The player entity
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player) {
        if (!def.getSpore(player)) return HazardWarnings.FINE;
        final boolean hasFilter = hasSporeFilter(player);
        final int harshness = 1;

        if (hasFilter) return HazardWarnings.FINE;

        int effectToAdd = possibleEffects.get(rand.nextInt(possibleEffects.size() - 1) + 1).id;
        player.addPotionEffect(new PotionEffect(effectToAdd, BASE_EFFECT_DURATION, harshness));
        return HazardWarnings.SPORES;
    }
}
