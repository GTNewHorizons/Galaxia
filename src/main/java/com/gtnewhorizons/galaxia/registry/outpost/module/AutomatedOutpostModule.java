package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;

/**
 * A single module instance installed in an {@link AutomatedOutpost}.
 */
public abstract class AutomatedOutpostModule implements Buildable {

    // spotless:off
    public final static Map<ItemStack, Long> defaultConstructionCost = new HashMap<>() {{
        put(new ItemStack(Items.diamond), 8L);
        put(new ItemStack(Items.gold_ingot), 64L);
    }};

    // spotless:on
    public long energyBuffer;
    public final long baseEnergyCapacity;
    public int cooldownTicks;

    private int ticks = 0;
    private final long powerDrawEuPerTick;
    private Status status;
    private Map<ItemStack, Long> constructionResources;

    public AutomatedOutpostModule(long baseEnergyCapacity, long powerDrawEuPerTick, int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
        this.constructionResources = new HashMap<>();
        this.status = Buildable.Status.IN_CONSTRUCTION;
        this.energyBuffer = 0L;

        this.baseEnergyCapacity = baseEnergyCapacity;
        this.powerDrawEuPerTick = powerDrawEuPerTick;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public void updateStatus(Buildable.Status status) {
        this.status = status;
    }

    public void setConstructionProgress(float progress) {}

    public Map<ItemStack, Long> getConsumedResources() {
        return constructionResources;
    }

    public void clearConsumedResources() {
        this.constructionResources.clear();
    }

    public Map<ItemStack, Long> getConstructionCost() {
        return defaultConstructionCost;
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return getConstructionCost();
    }

    @Override
    public Map<ItemStack, Long> getConstructionInventory() {
        return constructionResources;
    }

    public abstract OutpostModuleKind getKind();

    @Override
    public void completeConstruction() {
        updateStatus(Status.OPERATIONAL);
        constructionResources.clear();
        energyBuffer = baseEnergyCapacity;
    }

    public void tick(AutomatedOutpost outpost) {
        if (status == Buildable.Status.IN_CONSTRUCTION) {
            return;
        } else if (status == Buildable.Status.OPERATIONAL) {
            updateOperational(outpost);
        }
    }

    public long getDisplayedPowerEuPerTick() {
        if (status != Buildable.Status.OPERATIONAL) return 0L;
        return powerDrawEuPerTick;
    }

    private void updateOperational(AutomatedOutpost outpost) {
        if (powerDrawEuPerTick > 0 && !outpost.tryConsumeEnergy(powerDrawEuPerTick)) {
            // Lose progress
            this.ticks = 0;
            return;
        }

        this.ticks += 1;
        if (this.ticks >= this.cooldownTicks) {
            this.apply(outpost);
            this.ticks -= this.cooldownTicks;
        }
    }

    protected abstract void apply(AutomatedOutpost outpost);
}
