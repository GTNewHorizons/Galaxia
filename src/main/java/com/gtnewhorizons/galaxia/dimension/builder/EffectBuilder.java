package com.gtnewhorizons.galaxia.dimension.builder;

import java.util.function.BiFunction;

import net.minecraft.entity.player.EntityPlayer;

import com.github.bsideup.jabel.Desugar;

/**
 * Record + Builder class to get a list of effects on each planet as required
 *
 * @param baseTemp          The temperature of the planet (in Kelvin)
 * @param withering         Whether withering is enabled on the planet
 * @param oxygenPercent     The relative oxygen level of the planet (Overworld = 100)
 * @param radiation         The relative radiation level of the planet (Overworld = 0)
 * @param spores            Whether fungal spores are present in the atmosphere
 * @param pressure          The relative atmospheric pressure on the planet (Overworld = 1)
 * @param tempModifier      Optional modifier for temperature (can be null)
 * @param oxygenModifier    Optional modifier for oxygen (can be null)
 * @param radiationModifier Optional modifier for radiation (can be null)
 * @param pressureModifier  Optional modifier for pressure (can be null)
 */
@Desugar
public record EffectBuilder(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores,
    int pressure,

    BiFunction<Integer, EntityPlayer, Integer> tempModifier, BiFunction<Integer, EntityPlayer, Integer> oxygenModifier,
    BiFunction<Integer, EntityPlayer, Integer> radiationModifier,
    BiFunction<Integer, EntityPlayer, Integer> pressureModifier) {

    /** Constructor without modifiers */
    public EffectBuilder(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores,
        int pressure) {
        this(baseTemp, withering, oxygenPercent, radiation, spores, pressure, null, null, null, null);
    }

    /** Default constructor without values, defaults to Overworld */
    public EffectBuilder() {
        this(273, false, 100, 0, false, 1, null, null, null, null);
    }

    /** Convenient static builder */
    public static Builder builder() {
        return new Builder();
    }

    private static int apply(BiFunction<Integer, EntityPlayer, Integer> mod, int base, EntityPlayer player) {
        return mod != null ? mod.apply(base, player) : base;
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

    /**
     * Sine Wave example of a modifier.
     *
     * @param freq frequency is a multiplier on the world's clock cycle
     * @param amp  amplitude is the magnitude of the effect
     */
    @Desugar
    public record ModifierSineWave(float freq, int amp) implements BiFunction<Integer, EntityPlayer, Integer> {

        @Override
        public Integer apply(Integer base, EntityPlayer player) {
            float time = player.worldObj.getCelestialAngle(freq);
            return base + (int) (Math.sin(time) * amp);
        }
    }

    // ====================== BUILDER ======================

    public static final class Builder {

        private int baseTemp = 273;
        private boolean withering = false;
        private int oxygenPercent = 100;
        private int radiation = 0;
        private boolean spores = false;
        private int pressure = 1;

        private BiFunction<Integer, EntityPlayer, Integer> tempMod;
        private BiFunction<Integer, EntityPlayer, Integer> oxygenMod;
        private BiFunction<Integer, EntityPlayer, Integer> radiationMod;
        private BiFunction<Integer, EntityPlayer, Integer> pressureMod;

        public Builder baseTemp(int v) {
            baseTemp = v;
            return this;
        }

        public Builder withering(boolean v) {
            withering = v;
            return this;
        }

        public Builder oxygenPercent(int v) {
            oxygenPercent = v;
            return this;
        }

        public Builder radiation(int v) {
            radiation = v;
            return this;
        }

        public Builder spores(boolean v) {
            spores = v;
            return this;
        }

        public Builder pressure(int v) {
            pressure = v;
            return this;
        }

        public Builder tempMod(BiFunction<Integer, EntityPlayer, Integer> m) {
            tempMod = m;
            return this;
        }

        public Builder oxygenMod(BiFunction<Integer, EntityPlayer, Integer> m) {
            oxygenMod = m;
            return this;
        }

        public Builder radiationMod(BiFunction<Integer, EntityPlayer, Integer> m) {
            radiationMod = m;
            return this;
        }

        public Builder pressureMod(BiFunction<Integer, EntityPlayer, Integer> m) {
            pressureMod = m;
            return this;
        }

        public EffectBuilder build() {
            return new EffectBuilder(
                baseTemp,
                withering,
                oxygenPercent,
                radiation,
                spores,
                pressure,
                tempMod,
                oxygenMod,
                radiationMod,
                pressureMod);
        }
    }
}
