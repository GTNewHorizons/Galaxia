package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;

import gregtech.api.enums.Materials;
import gtPlusPlus.core.fluids.GTPPFluids;

/**
 * Every rocket fuel in the mod. Adding an entry here is all it takes to get its engine and a tank core for every
 * frame strong enough to hold it - see {@link FuelTankFrame}
 */
public final class RocketFuels {

    private static final List<RocketFuel> FUELS = new ArrayList<>();

    // spotless:off
    public static final RocketFuel NITROFUEL = register(RocketFuel.builder("nitrofuel")
        .fluid(() -> gtFluid(Materials.NitroFuel, "nitrofuel"))
        .density(0.85)
        .containment(FuelContainment.BASIC)
        .color(0xD9C65A)
        .coreRecipe(ModuleRecipe.builder().ore("plateSteel", 1).duration(10).build())
        .engine(25_000, 260, 600, ModuleRecipe.builder()
            .ore("plateSteel", 12).ore("pipeMediumSteel", 4).ore("circuitBasic", 2).duration(300).build()));

    public static final RocketFuel RP1 = register(RocketFuel.builder("rp1")
        .fluid(() -> GTPPFluids.RP1RocketFuel)
        .density(0.82)
        .containment(FuelContainment.BASIC)
        .color(0xD8513F)
        .coreRecipe(ModuleRecipe.builder().ore("plateSteel", 1).ore("plateRubber", 1).duration(10).build())
        .engine(32_000, 300, 700, ModuleRecipe.builder()
            .ore("plateAluminium", 12).ore("pipeMediumStainlessSteel", 4).ore("circuitGood", 2).duration(400).build()));

    public static final RocketFuel LIQUID_HYDROGEN = register(RocketFuel.builder("liquid_hydrogen")
        .fluid(() -> GTPPFluids.LiquidHydrogen)
        .density(0.071)
        .containment(FuelContainment.CRYOGENIC)
        .color(0x7FB2FF)
        .coreRecipe(ModuleRecipe.builder().ore("plateAluminium", 2).ore("foilPlastic", 2).duration(20).build())
        .engine(22_000, 450, 650, ModuleRecipe.builder()
            .ore("plateStainlessSteel", 12).ore("pipeMediumTitanium", 4).ore("circuitAdvanced", 2).duration(500).build()));

    public static final RocketFuel ANTIMATTER = register(RocketFuel.builder("antimatter")
        .fluid(() -> gtFluid(Materials.Antimatter, "antimatter"))
        .density(1.0)
        .containment(FuelContainment.EXOTIC)
        .color(0xC77DFF)
        .coreRecipe(ModuleRecipe.builder().ore("plateNaquadah", 2).ore("circuitElite", 1).duration(40).build())
        .engine(60_000, 6_000, 1_200, ModuleRecipe.builder()
            .ore("plateNaquadahAlloy", 12).ore("circuitMaster", 4).duration(1200).build()));
    // spotless:on

    private RocketFuels() {}

    private static RocketFuel register(RocketFuel.Builder builder) {
        RocketFuel fuel = builder.build();
        FUELS.add(fuel);
        return fuel;
    }

    public static List<RocketFuel> all() {
        return Collections.unmodifiableList(FUELS);
    }

    public static RocketFuel byKey(String key) {
        for (RocketFuel fuel : FUELS) {
            if (fuel.key()
                .equals(key)) return fuel;
        }
        return null;
    }

    /**
     * Finds the fuel a fluid stack belongs to
     *
     * @param stack The fluid to look up
     *
     * @return The matching fuel, or null if the fluid is not a rocket fuel
     */
    public static RocketFuel byFluid(FluidStack stack) {
        for (RocketFuel fuel : FUELS) {
            if (fuel.matches(stack)) return fuel;
        }
        return null;
    }

    private static Fluid gtFluid(Materials material, String fallbackName) {
        FluidStack stack = material.getFluid(1);
        if (stack == null) stack = material.getGas(1);
        return stack != null ? stack.getFluid() : FluidRegistry.getFluid(fallbackName);
    }
}
