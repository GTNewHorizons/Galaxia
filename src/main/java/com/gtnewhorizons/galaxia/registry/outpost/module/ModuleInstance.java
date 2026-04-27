package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;

public class ModuleInstance implements Buildable {

    public final ID id;
    private final Map<ItemStack, Long> consumedResources = new HashMap<>();
    private final OutpostModuleRegistry.Definition definition;
    private ModuleComponent component;

    private Buildable.Status status = Buildable.Status.IN_CONSTRUCTION;
    private long energyBuffer = 0L;
    private int ticks = 0;

    public void tick(AutomatedFacility outpost) {
        if (this.status() == Buildable.Status.OPERATIONAL) {
            tickOperational(outpost);
        }
    }

    private void tickOperational(AutomatedFacility outpost) {
        long powerDraw = this.powerDrawEuPerTick();

        if (!outpost.tryConsumeEnergy(powerDraw)) {
            ticks = 0;
            return;
        }

        this.ticks += 1;
        if (this.ticks >= this.cooldownTicks()) {
            this.definition.applyBehavior()
                .accept(this, outpost);
            this.setTicks(this.ticks - this.cooldownTicks());
        }
    }

    public ModuleInstance(ID id, OutpostModuleRegistry.Definition definition) {
        this.id = id;
        this.definition = definition;
    }

    public ModuleInstance(OutpostModuleRegistry.Definition definition) {
        this(ID.create(), definition);
    }

    public ModuleComponent component() {
        return component;
    }

    public void setComponent(ModuleComponent component) {
        this.component = component;
    }

    public FacilityModuleKind kind() {
        return definition.kind();
    }

    @Override
    public void clearConsumedResources() {
        consumedResources.clear();
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return definition.constructionCost();
    }

    @Override
    public Map<ItemStack, Long> getConstructionInventory() {
        return consumedResources;
    }

    public Buildable.Status status() {
        return status;
    }

    @Override
    public void updateStatus(Status status) {
        this.status = status;
    }

    public long energyBuffer() {
        return energyBuffer;
    }

    public void setEnergyBuffer(long energyBuffer) {
        this.energyBuffer = energyBuffer;
    }

    public int ticks() {
        return ticks;
    }

    public void setTicks(int ticks) {
        this.ticks = ticks;
    }

    public boolean isOperational() {
        return status == Buildable.Status.OPERATIONAL;
    }

    public void completeConstruction() {
        this.status = Buildable.Status.OPERATIONAL;
        consumedResources.clear();
        energyBuffer = definition.baseEnergyCapacity();
    }

    public long getDisplayedPowerEuPerTick() {
        if (!isOperational()) return 0L;
        return definition.powerDrawEuPerTick();
    }

    public long baseEnergyCapacity() {
        return definition.baseEnergyCapacity();
    }

    public long powerDrawEuPerTick() {
        return definition.powerDrawEuPerTick();
    }

    public int cooldownTicks() {
        return definition.cooldownTicks();
    }

    public Map<ItemStack, Long> getConstructionCost() {
        return definition.constructionCost();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ModuleInstance that = (ModuleInstance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public record ID(UUID id) implements WithUUID {

        public static ID create() {
            return new ID(UUID.randomUUID());
        }

        public static ID from(String value) {
            if (value == null) return null;
            return new ID(UUID.fromString(value));
        }

        public static ID from(UUID value) {
            return value == null ? null : new ID(value);
        }

        public static ID from(CelestialAsset.ID id) {
            if (id == null) return null;
            return new ID(id.id());
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }
}
