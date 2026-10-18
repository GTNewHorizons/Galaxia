package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel;

import java.util.function.Supplier;

import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;

/**
 * A rocket propellant: which fluid it is, how heavy it is, what it takes to store it and the engine that burns it.
 * Every fuel gets a tank core for each frame that can hold it, plus its own engine
 */
public final class RocketFuel {

    public static final String LANG_PREFIX = "galaxia.rocket_fuel.";

    private final String key;
    private final Supplier<Fluid> fluidSupplier;
    private final double density;
    private final FuelContainment containment;
    private final int color;
    private final ModuleRecipe coreRecipe;
    private final EngineSpec engine;
    private Fluid resolvedFluid;

    private RocketFuel(Builder builder) {
        this.key = builder.key;
        this.fluidSupplier = builder.fluid;
        this.density = builder.density;
        this.containment = builder.containment;
        this.color = builder.color;
        this.coreRecipe = builder.coreRecipe;
        this.engine = builder.engine;
    }

    public String key() {
        return key;
    }

    public String name() {
        return StatCollector.translateToLocal(LANG_PREFIX + key);
    }

    /**
     * Resolves the backing fluid lazily, as other mods register theirs after our part registry is built
     *
     * @return The fluid, or null if the providing mod is missing
     */
    public Fluid fluid() {
        if (resolvedFluid == null) {
            try {
                resolvedFluid = fluidSupplier.get();
            } catch (LinkageError | RuntimeException ignored) {
                // Providing mod not installed
            }
        }
        return resolvedFluid;
    }

    public boolean matches(FluidStack stack) {
        Fluid fluid = fluid();
        return fluid != null && stack != null && stack.getFluid() == fluid;
    }

    /** Mass in kg of 1 mB, which is one litre */
    public double density() {
        return density;
    }

    /**
     * Mass of an amount of this fuel
     *
     * @param amount Amount in mB
     *
     * @return Mass in kg
     */
    public double massOf(long amount) {
        return amount * density;
    }

    public FuelContainment containment() {
        return containment;
    }

    /** RGB colour used to tint tank cores */
    public int color() {
        return color;
    }

    /** Cost of the core for one grid cell of tank */
    public ModuleRecipe coreRecipe() {
        return coreRecipe;
    }

    public EngineSpec engine() {
        return engine;
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    /**
     * Stats of the engine that burns a fuel
     *
     * @param thrust      Thrust in kgf, one kgf lifts one kg at Earth gravity
     * @param isp         Specific impulse in seconds
     * @param weight      Dry mass in kg
     * @param width       Width in grid cells
     * @param height      Height in grid cells
     * @param assetFolder Model folder under {@code textures/model/modules}
     * @param recipe      Assembler recipe
     */
    public record EngineSpec(double thrust, double isp, int weight, int width, int height, String assetFolder,
        ModuleRecipe recipe) {}

    public static final class Builder {

        private final String key;
        private Supplier<Fluid> fluid = () -> null;
        private double density = 1.0;
        private FuelContainment containment = FuelContainment.BASIC;
        private int color = 0xFFFFFF;
        private ModuleRecipe coreRecipe = ModuleRecipe.EMPTY;
        private EngineSpec engine;

        private Builder(String key) {
            this.key = key;
        }

        public Builder fluid(Supplier<Fluid> fluid) {
            this.fluid = fluid;
            return this;
        }

        /** Mass in kg of 1 mB, which is one litre */
        public Builder density(double density) {
            this.density = density;
            return this;
        }

        public Builder containment(FuelContainment containment) {
            this.containment = containment;
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder coreRecipe(ModuleRecipe coreRecipe) {
            this.coreRecipe = coreRecipe;
            return this;
        }

        public Builder engine(double thrust, double isp, int weight, ModuleRecipe recipe) {
            this.engine = new EngineSpec(thrust, isp, weight, 3, 4, "engine_1", recipe);
            return this;
        }

        public RocketFuel build() {
            if (engine == null) throw new IllegalStateException("Rocket fuel " + key + " has no engine");
            return new RocketFuel(this);
        }
    }
}
