package com.gtnewhorizons.galaxia.registry.celestial;

import net.minecraft.item.ItemStack;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialAssetRequirement(ItemStack stack, long amount) {

    public CelestialAssetRequirement {
        stack = stack == null ? null : stack.copy();
        if (stack != null) {
            stack.stackSize = 1;
        }
    }

    public String displayName() {
        return stack == null ? "Unknown" : stack.getDisplayName();
    }

    public boolean matches(ItemStack other) {
        return stack != null && other != null && stack.isItemEqual(other);
    }

    public CelestialAssetRequirement withAmount(long newAmount) {
        return new CelestialAssetRequirement(stack, newAmount);
    }
}
