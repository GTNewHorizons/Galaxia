package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;

/**
 * Shapes a fuel tank can have. The frame is shared between every fuel, only the core inside changes, so each
 * frame/fuel pair that passes {@link #canHold(RocketFuel)} becomes its own module
 * <p>
 * To add a frame: add an entry here and a model under {@code textures/model/modules/<assetFolder>}
 */
public enum FuelTankFrame {

    // spotless:off
    SLIM_3("1x3", 1, 3, 180, FuelContainment.BASIC, "fuel_tank_1x3",
        ModuleRecipe.builder().ore("plateSteel", 6).ore("ringSteel", 2).duration(160).build()),
    SLIM_5("1x5", 1, 5, 260, FuelContainment.CRYOGENIC, "fuel_tank_1x5",
        ModuleRecipe.builder().ore("plateAluminium", 10).ore("ringAluminium", 2).duration(200).build()),
    MEDIUM_3("3x3", 3, 3, 450, FuelContainment.CRYOGENIC, "fuel_tank_3x3",
        ModuleRecipe.builder().ore("plateAluminium", 14).ore("ringAluminium", 3).duration(240).build()),
    MEDIUM_5("3x5", 3, 5, 650, FuelContainment.CRYOGENIC, "fuel_tank_3x5",
        ModuleRecipe.builder().ore("plateAluminium", 22).ore("ringAluminium", 4).duration(280).build()),
    WIDE_3("5x3", 5, 3, 900, FuelContainment.EXOTIC, "fuel_tank_5x3",
        ModuleRecipe.builder().ore("plateStainlessSteel", 24).ore("frameGtStainlessSteel", 4).duration(300).build()),
    WIDE_5("5x5", 5, 5, 1400, FuelContainment.EXOTIC, "fuel_tank_5x5",
        ModuleRecipe.builder().ore("plateStainlessSteel", 40).ore("frameGtStainlessSteel", 6).duration(400).build());
    // spotless:on

    /** Fuel volume in mB per grid cell the frame covers */
    public static final int VOLUME_PER_CELL = 1_000;

    public final String label;
    public final int width;
    public final int height;
    /** Dry mass of the frame in kg, the core adds its own mass on top */
    public final int weight;
    public final FuelContainment containment;
    public final String assetFolder;
    public final ModuleRecipe recipe;

    FuelTankFrame(String label, int width, int height, int weight, FuelContainment containment, String assetFolder,
        ModuleRecipe recipe) {
        this.label = label;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.containment = containment;
        this.assetFolder = assetFolder;
        this.recipe = recipe;
    }

    public int cells() {
        return width * height;
    }

    /** Fuel capacity in mB */
    public int capacity() {
        return cells() * VOLUME_PER_CELL;
    }

    /**
     * Checks whether this frame is strong enough for a fuel
     *
     * @param fuel The fuel to check
     *
     * @return Boolean : True => a core for this fuel fits in this frame
     */
    public boolean canHold(RocketFuel fuel) {
        return containment.canHold(fuel.containment());
    }
}
