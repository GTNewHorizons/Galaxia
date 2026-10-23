package com.gtnewhorizons.galaxia.registry.hazards;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.hasSporeFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.gtnewhorizons.galaxia.registry.android.AndroidControl;
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
     * Applies the "Spore" effect to the body - a random negative effect on body
     *
     * @param def     The dimensional Effect Definition
     * @param body    The body entity
     * @param station
     * @return
     */
    @Override
    public HazardWarnings apply(EffectBuilder def, EntityLivingBase body, Optional<TileStation> station) {
        if (!def.getSpore(body.worldObj)) return HazardWarnings.FINE;
        final boolean hasFilter = hasSporeFilter(body);
        final int harshness = 1;

        if (station.filter(TileStation::hasAirPurifier)
            .isPresent()) {
            return HazardWarnings.FINE;
        }

        if (hasFilter) return HazardWarnings.FINE;

        int effectToAdd = possibleEffects.get(rand.nextInt(possibleEffects.size())).id;
        if (effectToAdd == Potion.hunger.id && AndroidControl.isAndroid(body)) return HazardWarnings.SPORES;
        body.addPotionEffect(new PotionEffect(effectToAdd, BASE_EFFECT_DURATION, harshness));
        return HazardWarnings.SPORES;
    }
}
