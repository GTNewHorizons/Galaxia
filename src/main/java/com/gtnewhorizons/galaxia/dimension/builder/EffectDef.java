package com.gtnewhorizons.galaxia.dimension.builder;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Record to hold effects of the dimension (effectively a posh dataclass)
 * Constructs a definition based on the given parameters
 * *
 * * @param baseTemp The temperature of the planet
 * * @param withering Whether withering is enabled on the planet
 * * @param oxygenPercent The relative oxygen level of the planet
 * * @param radiation The relative radiation level of the planet
 * * @param spores Whether spores are enabled on the planet
 * * @param pressure The relative pressure on the planet
 */
public class EffectDef {

    int baseTemp = 273;
    boolean withering = false;
    int oxygenPercent = 100;
    int radiation = 0;
    boolean spores = false;
    int pressure = 1;

    public EffectDef(int temp, boolean withering, int oxygenPercent, int radiation, boolean spores, int pressure) {
        baseTemp = temp;
        this.withering = withering;
        this.oxygenPercent = oxygenPercent;
        this.radiation = radiation;
        this.spores = spores;
        this.pressure = pressure;
    }

    public EffectDef() {
        this(273, false, 100, 0, false, 1);
    }

    /**
     * Lambda function to override the value of an effect based on the playerentity.
     */
    public interface IEffectModifier {

        public int apply(int base, EntityPlayer player);
    }

    /**
     * Sine Wave example of IEffectModifier.
     * float Frequency is a multiplier on the world's clock cycle
     * int amplitude is the magnitude of the effect
     */
    public static class ModifierSineWave implements EffectDef.IEffectModifier {

        float freq;
        int amp;

        public ModifierSineWave(float frequency, int amplitude) {
            freq = frequency;
            amp = amplitude;
        }

        @Override
        public int apply(int base, EntityPlayer player) {
            float time = player.worldObj.getCelestialAngle(freq);
            return base + (int) (Math.sin(time) * amp);
        }
    }

    // Stored Modifiers
    public IEffectModifier tempModifier;
    public IEffectModifier oxygenModifier;
    public IEffectModifier radiationModifier;
    public IEffectModifier pressureModifier;

    /**
     * @param player Player to check effects of.
     * @return Temperature of surroundings of player.
     */
    public int getTemperature(EntityPlayer player) {
        if (tempModifier != null) {
            return tempModifier.apply(baseTemp, player);
        } else {
            return baseTemp;
        }
    }

    /**
     * @param player Player to check effects of.
     * @return oxygen levels around player as a percentage
     */
    public int getOxygenPercent(EntityPlayer player) {
        if (oxygenModifier != null) {
            return oxygenModifier.apply(oxygenPercent, player);
        } else {
            return oxygenPercent;
        }
    }

    /**
     * @param player Player to check effects of.
     * @return radiation value of player relative to overworld
     */
    public int getRadiation(EntityPlayer player) {
        if (radiationModifier != null) {
            return radiationModifier.apply(radiation, player);
        } else {
            return radiation;
        }
    }

    /**
     * @param player Player to check effects of.
     * @return Pressure value of player.
     */
    public int getPressure(EntityPlayer player) {
        if (pressureModifier != null) {
            return pressureModifier.apply(pressure, player);
        } else {
            return pressure;
        }
    }

    public boolean getSpore(EntityPlayer player) {
        return spores;
    }

    public boolean getWithering(EntityPlayer player) {
        return withering;
    }
}
