package com.gtnewhorizons.galaxia.registry.dimension.builder;

import java.util.function.BiFunction;

import lombok.Builder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * Record + Builder class to get a list of effects on each planet as required
 *
 * @param baseTemp          The temperature of the planet (in Kelvin)
 * @param withering         Whether withering is enabled on the planet
 * @param oxygenPercent     The relative oxygen level of the planet (Overworld =
 *                          100)
 * @param radiation         The relative radiation level of the planet
 *                          (Overworld = 0)
 * @param spores            Whether fungal spores are present in the atmosphere
 * @param pressure          The relative atmospheric pressure on the planet
 *                          (Overworld = 1)
 * @param tempModifier      Optional modifier for temperature (can be null)
 * @param oxygenModifier    Optional modifier for oxygen (can be null)
 * @param radiationModifier Optional modifier for radiation (can be null)
 * @param pressureModifier  Optional modifier for pressure (can be null)
 */
public record EffectBuilder(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores,
                            int pressure,

                            Modifier<World> tempModifier, Modifier<World> oxygenModifier,
                            Modifier<World> radiationModifier,
                            Modifier<World> pressureModifier) {

    @FunctionalInterface
    public interface Modifier<T> {
        int apply(T target, int base);
    }

    @Builder
    public EffectBuilder {
        if (baseTemp == 0) baseTemp = 273;
        if (oxygenPercent == 0) oxygenPercent = 100;
        if (pressure == 0) pressure = 1;
    }

    /** Constructor without modifiers */
    public EffectBuilder(int baseTemp, boolean withering, int oxygenPercent, int radiation, boolean spores,
        int pressure) {
        this(baseTemp, withering, oxygenPercent, radiation, spores, pressure, null, null, null, null);
    }

    /** Default constructor without values, defaults to Overworld */
    public EffectBuilder() {
        this(273, false, 100, 0, false, 1, null, null, null, null);
    }

    private static <T> int apply(Modifier<T> mod, int base, T target) {
        return mod != null ? mod.apply(target, base) : base;
    }

    public int getTemperature(World world) {
        return apply(tempModifier, baseTemp, world);
    }

    public int getOxygenPercent(World world) {
        return apply(oxygenModifier, oxygenPercent, world);
    }

    public int getRadiation(World world) {
        return apply(radiationModifier, radiation, world);
    }

    public int getPressure(World world) {
        return apply(pressureModifier, pressure, world);
    }

    public boolean getSpore(World world) {
        return spores;
    }

    public boolean getWithering(World world) {
        return withering;
    }

    /**
     * Sine Wave example of a modifier.
     *
     * @param freq frequency is a multiplier on the world's clock cycle
     * @param amp  amplitude is the magnitude of the effect
     */

    public record ModifierSineWave(float freq, int amp) implements Modifier<World> {

        @Override
        public int apply(World world, int base) {
            float time = world.getCelestialAngle(freq);
            return base + (int) (Math.sin(time) * amp);
        }
    }
}
