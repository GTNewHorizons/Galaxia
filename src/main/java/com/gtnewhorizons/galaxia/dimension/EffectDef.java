package com.gtnewhorizons.galaxia.dimension;

import net.minecraft.entity.player.EntityPlayer;

public class EffectDef {

    int baseTemp = 273;
    boolean withering = false;
    int oxygenPercent = 100;
    int radiation = 0;
    boolean spores = false;
    int pressure = 1;

    public EffectDef(int temp, boolean withering, int oxygen, int rads, boolean spores, int pressure) {

    }

    public EffectDef() {
        this(273, false, 100, 0, false, 1);
    }


    public interface IEffectModifier {
        public int apply(int base, EntityPlayer player);
    }

    public static class ModifierSineWave implements IEffectModifier {

        float freq;
        int amp;

        public ModifierSineWave(float frequency, int amplitude) {
            freq = frequency;
            amp = amplitude;
        }

        @Override
        public int apply(int base, EntityPlayer player) {
            float time = player.worldObj.getCelestialAngle(freq);
            return base + (int)(Math.sin(time) * amp);
        }
    }

    public IEffectModifier tempModifier;
    public IEffectModifier oxygenModifier;
    public IEffectModifier radiationModifier;
    public IEffectModifier pressureModifier;

    public int getTemperature(EntityPlayer player) {
        if (tempModifier != null) {
            return tempModifier.apply(baseTemp, player);
        } else {
            return baseTemp;
        }
    }

    public int getOxygenPercent(EntityPlayer player) {
        if (oxygenModifier != null) {
            return oxygenModifier.apply(oxygenPercent, player);
        } else {
            return oxygenPercent;
        }
    }


    public int getRadiation(EntityPlayer player) {
        if (radiationModifier != null) {
            return radiationModifier.apply(radiation, player);
        } else {
            return radiation;
        }
    }

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
