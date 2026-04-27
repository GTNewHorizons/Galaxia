package com.gtnewhorizons.galaxia.registry.interfaces;

import java.util.Map;

import net.minecraft.item.ItemStack;

public interface Buildable {

    enum Status {
        CONSTRUCTION_SITE,
        IN_CONSTRUCTION,
        DECONSTRUCTION,
        OPERATIONAL,
        DISABLED,
        DESTROYED
    }

    Map<ItemStack, Long> getRequiredResources();

    Map<ItemStack, Long> getConstructionInventory();

    void clearConsumedResources();

    Status status();

    void updateStatus(Status status);

    default float getConstructionProgress() {
        Map<ItemStack, Long> cost = getRequiredResources();
        Map<ItemStack, Long> inventory = getConstructionInventory();

        if (cost == null || cost.isEmpty()) {
            return 1.0f;
        }

        Status status = status();
        if (status != Status.IN_CONSTRUCTION && status != Status.CONSTRUCTION_SITE) {
            return 1.0f;
        }

        long totalRequired = 0;
        long totalCollected = 0;

        for (Map.Entry<ItemStack, Long> entry : cost.entrySet()) {
            ItemStack requiredItem = entry.getKey();
            long requiredAmount = entry.getValue();
            long collectedAmount = inventory.getOrDefault(requiredItem, 0L);

            totalRequired += requiredAmount;
            totalCollected += Math.min(collectedAmount, requiredAmount);
        }

        if (totalRequired == 0) {
            return 1.0f;
        }

        return (float) totalCollected / totalRequired;
    }

    default boolean isConstructionSatisfied() {
        Map<ItemStack, Long> required = getRequiredResources();
        Map<ItemStack, Long> inventory = getConstructionInventory();

        if (required == null || required.isEmpty()) {
            return true;
        }

        for (Map.Entry<ItemStack, Long> entry : required.entrySet()) {
            long available = inventory.getOrDefault(entry.getKey(), 0L);
            if (available < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    default void updateConstruction() {
        float constructionProgress = getConstructionProgress();
        if (constructionProgress >= 1.0f) {
            completeConstruction();
        }
    }

    default void completeConstruction() {
        updateStatus(Status.OPERATIONAL);
    }

    default boolean isOperational() {
        return status() == Status.OPERATIONAL;
    }

    default boolean isDisabled() {
        return status() == Status.DISABLED;
    }

    default boolean isInConstruction() {
        return status() == Status.IN_CONSTRUCTION || status() == Status.CONSTRUCTION_SITE;
    }

    default boolean isUnderDeconstruction() {
        return status() == Status.DECONSTRUCTION;
    }

    default boolean isManageable() {
        return status() == Status.OPERATIONAL;
    }
}
