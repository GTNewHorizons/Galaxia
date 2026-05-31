package com.gtnewhorizons.galaxia.registry.dimension.builder;

import java.util.function.BiFunction;

import net.minecraft.entity.player.EntityPlayer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * class to get a list of effects on each planet as required
 */
@Data
@Builder
@AllArgsConstructor
public class EffectBuilder {

    private int baseTemp;
    private boolean withering;
    private int oxygenPercent;
    private int radiation;
    private boolean spores;
    private int pressure;

    private BiFunction<Integer, EntityPlayer, Integer> tempModifier;
    private BiFunction<Integer, EntityPlayer, Integer> oxygenModifier;
    private BiFunction<Integer, EntityPlayer, Integer> radiationModifier;
    private BiFunction<Integer, EntityPlayer, Integer> pressureModifier;

    /** Default constructor - Overworld values */
    public EffectBuilder() {
        this(273, false, 100, 0, false, 1, null, null, null, null);
    }

    public int getTemperature(EntityPlayer player) {
        return apply(tempModifier, baseTemp, player);
    }

    public int getOxygenPercent(EntityPlayer player) {
        return apply(oxygenModifier, oxygenPercent, player);
    }

    public int getRadiation(EntityPlayer player) {
        return apply(radiationModifier, radiation, player);
    }

    public int getPressure(EntityPlayer player) {
        return apply(pressureModifier, pressure, player);
    }

    public boolean getSpore(EntityPlayer player) {
        return spores;
    }

    public boolean getWithering(EntityPlayer player) {
        return withering;
    }

    private static int apply(BiFunction<Integer, EntityPlayer, Integer> mod, int base, EntityPlayer player) {
        return mod != null ? mod.apply(base, player) : base;
    }

    /**
     * Sine Wave example of a modifier.
     */
    public record ModifierSineWave(float freq, int amp) implements BiFunction<Integer, EntityPlayer, Integer> {

        @Override
        public Integer apply(Integer base, EntityPlayer player) {
            float time = player.worldObj.getCelestialAngle(freq);
            return base + (int) (Math.sin(time) * amp);
        }
    }

    public static EffectBuilderBuilder builder() {
        return new EffectBuilderBuilder();
    }
}
