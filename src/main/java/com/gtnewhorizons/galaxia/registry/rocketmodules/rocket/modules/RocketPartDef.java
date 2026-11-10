package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;

/**
 * Shared state of every rocket module. Subclasses only add what is specific to their type, everything else (size,
 * mass, model, recipe) lives here
 */
public abstract class RocketPartDef implements IRocketPartDef {

    public static final String LANG_PREFIX = "galaxia.rocket_part.";

    private final String key;
    private final int width;
    private final int height;
    private final int weight;
    private final String assetFolder;
    private ModuleRecipe recipe = ModuleRecipe.EMPTY;
    private int id = -1;

    protected RocketPartDef(String key, int width, int height, int weight, String assetFolder) {
        this.key = key;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.assetFolder = assetFolder;
    }

    /**
     * Sets the recipe the Module Assembler uses for this module
     *
     * @param recipe The recipe to use
     *
     * @return This definition, for chaining during registration
     */
    public RocketPartDef withRecipe(ModuleRecipe recipe) {
        this.recipe = recipe == null ? ModuleRecipe.EMPTY : recipe;
        return this;
    }

    /** Called once by {@code RocketPartRegistry}, ids follow registration order */
    public void assignId(int id) {
        if (this.id != -1) throw new IllegalStateException("Rocket part " + key + " registered twice");
        this.id = id;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String name() {
        return StatCollector.translateToLocal(LANG_PREFIX + key);
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public String assetFolder() {
        return assetFolder;
    }

    @Override
    public ModuleRecipe recipe() {
        return recipe;
    }

    @Override
    public abstract RocketPartCategory category();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + key + "]";
    }
}
