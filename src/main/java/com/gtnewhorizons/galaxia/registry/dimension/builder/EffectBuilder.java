package com.gtnewhorizons.galaxia.registry.dimension.builder;

import net.minecraft.world.World;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * class to get a list of effects on each planet as required
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EffectBuilder {

    @Builder.Default
    private int baseTemp = 273;
    @Builder.Default
    private boolean withering = false;
    @Builder.Default
    private int oxygenPercent = 100;
    @Builder.Default
    private int radiation = 0;
    @Builder.Default
    private boolean spores = false;
    @Builder.Default
    private int pressure = 1;

    @Builder.Default
    private Modifier<World> tempModifier = null;
    @Builder.Default
    private Modifier<World> oxygenModifier = null;
    @Builder.Default
    private Modifier<World> radiationModifier = null;
    @Builder.Default
    private Modifier<World> pressureModifier = null;

    @FunctionalInterface
    public interface Modifier<T> {

        int apply(T target, int base);
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
}
