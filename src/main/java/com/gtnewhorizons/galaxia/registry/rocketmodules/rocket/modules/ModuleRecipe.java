package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

import gregtech.api.util.GTOreDictUnificator;

/**
 * Items the Module Assembler consumes to build a module, plus how long it takes. Recipes are plain data so they can be
 * combined - a fuel tank is its frame recipe plus the core recipe of its fuel
 */
public final class ModuleRecipe {

    public static final int DEFAULT_DURATION = 200;
    public static final ModuleRecipe EMPTY = new ModuleRecipe(List.of(), DEFAULT_DURATION);

    private final List<Input> inputs;
    private final int duration;

    private ModuleRecipe(List<Input> inputs, int duration) {
        this.inputs = Collections.unmodifiableList(inputs);
        this.duration = duration;
    }

    public List<Input> inputs() {
        return inputs;
    }

    /** Assembly time in ticks */
    public int duration() {
        return duration;
    }

    public boolean isEmpty() {
        return inputs.isEmpty();
    }

    /** Bind authored ore ingredients once, before work is admitted by the existing Module Assembler. */
    public RocketPartDef.WorkBill toWorkBill(long euPerTick) {
        Map<InventoryKey, Long> resources = new LinkedHashMap<>();
        for (Input input : inputs) {
            ItemStack resolved = input.oreName() == null ? input.stack() : GTOreDictUnificator.get(input.oreName(), 1);
            var key = ItemStackWrapper.of(resolved);
            if (key == null) throw new IllegalStateException("Missing rocket recipe input " + input.oreName());
            resources.merge(key, (long) input.amount(), Math::addExact);
        }
        return new RocketPartDef.WorkBill(resources, euPerTick, duration);
    }

    /**
     * Combines two recipes, merging inputs of the same item and adding durations
     *
     * @param other The recipe to add
     *
     * @return A new recipe containing both
     */
    public ModuleRecipe plus(ModuleRecipe other) {
        Map<String, Input> merged = new LinkedHashMap<>();
        for (Input input : inputs) merged.merge(input.mergeKey(), input, Input::add);
        for (Input input : other.inputs) merged.merge(input.mergeKey(), input, Input::add);
        return new ModuleRecipe(new ArrayList<>(merged.values()), duration + other.duration);
    }

    /**
     * Scales every input amount and the duration, rounding up so nothing becomes free
     *
     * @param factor The multiplier
     *
     * @return A new scaled recipe
     */
    public ModuleRecipe times(double factor) {
        List<Input> scaled = new ArrayList<>(inputs.size());
        for (Input input : inputs) {
            scaled.add(input.withAmount((int) Math.ceil(input.amount() * factor)));
        }
        return new ModuleRecipe(scaled, (int) Math.ceil(duration * factor));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A single ingredient, matched either by ore dictionary name or by exact item and damage
     *
     * @param oreName Ore dictionary name, null when matching by stack
     * @param stack   Exact stack to match, null when matching by ore name
     * @param amount  How many are consumed
     */
    public record Input(String oreName, ItemStack stack, int amount) {

        public boolean matches(ItemStack candidate) {
            if (candidate == null) return false;
            if (oreName != null) {
                for (int oreId : OreDictionary.getOreIDs(candidate)) {
                    if (oreName.equals(OreDictionary.getOreName(oreId))) return true;
                }
                return false;
            }
            return candidate.getItem() == stack.getItem() && (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || candidate.getItemDamage() == stack.getItemDamage());
        }

        /** Stacks to show in GUIs, never empty for a valid input */
        public List<ItemStack> displayStacks() {
            if (oreName == null) return List.of(stack);
            // Forge's ore list is a view whose ArrayList internals are empty, copy it before handing it out
            List<ItemStack> ores = OreDictionary.getOres(oreName, false);
            return ores == null ? List.of() : new ArrayList<>(ores);
        }

        private String mergeKey() {
            return oreName != null ? "ore:" + oreName
                : "item:" + stack.getItem()
                    .getUnlocalizedName() + ":" + stack.getItemDamage();
        }

        private Input withAmount(int newAmount) {
            return new Input(oreName, stack, newAmount);
        }

        private Input add(Input other) {
            return withAmount(amount + other.amount);
        }
    }

    public static final class Builder {

        private final List<Input> inputs = new ArrayList<>();
        private int duration = DEFAULT_DURATION;

        private Builder() {}

        public Builder ore(String oreName, int amount) {
            inputs.add(new Input(oreName, null, amount));
            return this;
        }

        public Builder item(ItemStack stack, int amount) {
            inputs.add(new Input(null, stack.copy(), amount));
            return this;
        }

        /** Assembly time in ticks */
        public Builder duration(int ticks) {
            this.duration = ticks;
            return this;
        }

        public ModuleRecipe build() {
            return new ModuleRecipe(new ArrayList<>(inputs), duration);
        }
    }
}
