package com.gtnewhorizons.galaxia.registry.outpost.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;

public record MiningFeatureEffects(List<ItemStack> candidates, int bonusRolls, int outputMultiplierPercent,
    List<ChanceStack> replacementRolls) {

    public MiningFeatureEffects {
        candidates = candidates.isEmpty() ? List.of() : List.copyOf(candidates);
        replacementRolls = replacementRolls.isEmpty() ? List.of() : List.copyOf(replacementRolls);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ItemStack rollReplacement(Random random) {
        for (ChanceStack roll : replacementRolls) {
            if (roll.chancePercent() > 0 && random.nextInt(100) < roll.chancePercent()) {
                return roll.stack()
                    .copy();
            }
        }
        return null;
    }

    public boolean shouldKeepOutput(Random random) {
        if (outputMultiplierPercent >= 100) return true;
        if (outputMultiplierPercent <= 0) return false;
        return random.nextInt(100) < outputMultiplierPercent;
    }

    public record ChanceStack(int chancePercent, ItemStack stack) {

        public ChanceStack {
            if (chancePercent < 0 || chancePercent > 100) {
                throw new IllegalArgumentException("Invalid replacement roll chance: " + chancePercent);
            }
            if (stack == null) {
                throw new IllegalArgumentException("Replacement stack must not be null");
            }
        }
    }

    public static final class Builder {

        private final List<ItemStack> candidates = new ArrayList<>();
        private final List<ChanceStack> replacementRolls = new ArrayList<>();
        private int bonusRolls;
        private int outputMultiplierPercent = 100;

        public void addCandidates(Collection<ItemStack> stacks, int repeats) {
            if (repeats <= 0 || stacks.isEmpty()) return;
            for (int i = 0; i < repeats; i++) {
                for (ItemStack stack : stacks) {
                    ItemStack copy = stack.copy();
                    copy.stackSize = 1;
                    candidates.add(copy);
                }
            }
        }

        public void addBonusRolls(int rolls) {
            if (rolls > 0) bonusRolls += rolls;
        }

        public void multiplyOutputMultiplierPercent(int multiplierPercent) {
            outputMultiplierPercent = outputMultiplierPercent * Math.clamp(multiplierPercent, 0, 100) / 100;
        }

        public void addReplacementRoll(int chancePercent, ItemStack stack) {
            replacementRolls.add(new ChanceStack(chancePercent, stack));
        }

        public MiningFeatureEffects build() {
            return new MiningFeatureEffects(candidates, bonusRolls, outputMultiplierPercent, replacementRolls);
        }
    }
}
