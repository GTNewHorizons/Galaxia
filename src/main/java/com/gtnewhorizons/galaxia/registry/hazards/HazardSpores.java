package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.hasSporeFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.dimension.builder.EffectBuilder;
import com.gtnewhorizons.galaxia.registry.interfaces.IEnvironmentalHazard;

public class HazardSpores implements IEnvironmentalHazard {

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
     * @param def     The dimensional Effect Definition
     * @param player  The player entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityPlayer player, Optional<TileStation> station) {
        if (!def.getSpore(player.worldObj)) return HazardWarnings.FINE;
        final boolean hasFilter = hasSporeFilter(player);
        final int harshness = 1;

        if (station.filter(TileStation::hasAirPurifier)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        if (hasFilter) return HazardWarnings.FINE;

        int effectToAdd = possibleEffects.get(rand.nextInt(possibleEffects.size() - 1) + 1).id;
        player.addPotionEffect(new PotionEffect(effectToAdd, BASE_EFFECT_DURATION, harshness));
        return HazardWarnings.SPORES;
    }
}
