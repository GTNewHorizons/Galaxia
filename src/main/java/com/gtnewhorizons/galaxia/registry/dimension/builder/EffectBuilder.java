package com.gtnewhorizons.galaxia.registry.dimension.builder;

import net.minecraft.world.World;

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

    private Modifier<World> tempModifier;
    private Modifier<World> oxygenModifier;
    private Modifier<World> radiationModifier;
    private Modifier<World> pressureModifier;

    @FunctionalInterface
    public interface Modifier<T> {

        int apply(T target, int base);
    }

    /** Default constructor - Overworld values */
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
     */
    public record ModifierSineWave(float freq, int amp) implements Modifier<World> {

        @Override
        public int apply(World world, int base) {
            float time = world.getCelestialAngle(freq);
            return base + (int) (Math.sin(time) * amp);
        }
    }

    public static EffectBuilderBuilder builder() {
        return new EffectBuilderBuilder();
    }
}
