package com.gtnewhorizons.galaxia.registry.outpost.module;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.interfaces.WithUUID;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepDemand;

public class ModuleInstance implements Buildable {

    public final ID id;
    private final FacilityModuleRegistry.Definition definition;
    private IModuleComponent component;
    private @Nullable AutomatedFacility facilityOwner;

    private Buildable.Status status = Buildable.Status.IN_CONSTRUCTION;
    private int ticks = 0;

    private StationTileCoord anchor;
    private final ModuleShape shape;
    private int rotation = 0;
    private ModuleTier tier;
    private byte parallel = 1;
    private ModulePriority priorityOverride = ModulePriority.NORMAL;
    private boolean enabled = true;
    private ModuleState state = ModuleState.IDLE;
    private BlockingReason blocking = BlockingReason.NONE;
    private ModuleOperationState operation;
    private @Nullable SettingsBinding settingsBinding;

    private ModuleTierData currentTierData() {
        return definition.getTierData(this.tier);
    }

    public void tick(CelestialAsset outpost) {
        if (this.status() == Buildable.Status.OPERATIONAL) {
            tickOperational(outpost);
        }
    }

    private void tickOperational(CelestialAsset asset) {
        long powerDraw = asset instanceof AutomatedFacility facility ? facility.effectivePowerDrawEuPerTick(this)
            : this.powerDrawEuPerTick();

        if (!asset.tryConsumeEnergy(powerDraw)) {
            ticks = 0;
            return;
        }

        IModuleComponent component = this.component;
        if (component != null) {
            component.tickOperational(this, asset);
        }

        this.ticks += 1;
        if (this.ticks >= this.cooldownTicks()) {
            if (component != null) {
                component.runCycle(this, asset);
            }
            this.setTicks(this.ticks - this.cooldownTicks());
        }
    }

    public ModuleInstance(ID id, FacilityModuleRegistry.Definition definition, StationTileCoord anchor,
        ModuleShape shape, ModuleTier tier) {
        this.id = id;
        this.definition = definition;
        this.anchor = anchor;
        this.shape = shape;
        this.tier = tier;
    }

    public IModuleComponent component() {
        return component;
    }

    public void setComponent(IModuleComponent component) {
        if (this.component == component) return;
        this.component = component;
        configurationChanged();
    }

    public void setFacilityOwner(@Nullable AutomatedFacility facilityOwner) {
        this.facilityOwner = facilityOwner;
    }

    private void configurationChanged() {
        if (facilityOwner != null) facilityOwner.moduleConfigurationChanged();
    }

    public @Nullable SettingsBinding settingsBinding() {
        return settingsBinding;
    }

    public void setSettingsBinding(SettingsBinding settingsBinding) {
        if (settingsBinding == null) throw new IllegalArgumentException("Module settings binding must not be null");
        this.settingsBinding = settingsBinding;
    }

    public FacilityModuleKind kind() {
        return definition.kind();
    }

    public @Nullable FacilityModuleRegistry.Definition.Recipe recipe() {
        return definition.recipe();
    }

    public Map<ModuleTier, ModuleTierData> allTierData() {
        return definition.tierData();
    }

    public java.util.List<ModuleAreaEffect> areaEffects() {
        return definition.areaEffects();
    }

    @Override
    public Map<ItemStack, Long> getRequiredResources() {
        return currentTierData().constructionCost();
    }

    public Buildable.Status status() {
        return status;
    }

    @Override
    public void updateStatus(Status status) {
        if (this.status == status) return;
        this.status = status;
        configurationChanged();
    }

    public int ticks() {
        return ticks;
    }

    public void setTicks(int ticks) {
        this.ticks = ticks;
    }

    public static final int NULL_ANCHOR_LOG_VALUE = -999;

    public StationTileCoord anchor() {
        if (anchor == null) {
            throw new IllegalStateException(
                "Module " + kind() + " (id=" + id + "): anchor is null — module was not placed on layout");
        }
        return anchor;
    }

    public StationTileCoord anchorOrNull() {
        return anchor;
    }

    public void initAnchor(StationTileCoord anchor) {
        if (this.anchor != null) return;
        this.anchor = anchor;
        configurationChanged();
    }

    public ModuleShape shape() {
        return shape;
    }

    public int rotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        int normalized = ModuleShape.normalizeRotation(rotation);
        if (this.rotation == normalized) return;
        this.rotation = normalized;
        configurationChanged();
    }

    public StationTileCoord[] tiles() {
        return shape.tiles(anchor(), rotation);
    }

    public ModuleTier tier() {
        return tier;
    }

    public void setTier(ModuleTier tier) {
        if (this.tier == tier) return;
        this.tier = tier;
        configurationChanged();
    }

    public ModulePriority priorityOverride() {
        return priorityOverride;
    }

    public void setPriorityOverride(ModulePriority priorityOverride) {
        if (this.priorityOverride == priorityOverride) return;
        this.priorityOverride = priorityOverride;
        configurationChanged();
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        configurationChanged();
    }

    public ModuleState state() {
        return state;
    }

    public void setState(ModuleState state) {
        this.state = state;
    }

    public BlockingReason blocking() {
        return blocking;
    }

    public void setBlocking(BlockingReason blocking) {
        this.blocking = blocking;
    }

    public ModuleOperationState operationOrNull() {
        return operation;
    }

    public void setOperation(ModuleOperationState operation) {
        this.operation = operation;
    }

    public void clearOperation() {
        this.operation = null;
    }

    public boolean canStartOperation() {
        return operation == null || operation.phase()
            .isTerminal()
            && operation.depositedResources()
                .isEmpty()
            && operation.refundBuffer()
                .isEmpty();
    }

    public boolean isOperational() {
        return status == Buildable.Status.OPERATIONAL;
    }

    public long getDisplayedPowerEuPerTick() {
        if (!isOperational()) return 0L;
        return currentTierData().powerDrawEuPerTick();
    }

    public long baseCapacity() {
        ModuleTierData data = currentTierData();
        return data.hasCapacity() ? data.capacity() : 0L;
    }

    public long powerDrawEuPerTick() {
        return currentTierData().powerDrawEuPerTick();
    }

    public byte parallel() {
        return parallel;
    }

    public void setParallel(byte parallel) {
        if (!kind().supportsParallel()) {
            throw new IllegalStateException(kind() + " does not support parallel execution");
        }
        if (parallel < 1) {
            throw new IllegalArgumentException("parallel must be at least 1");
        }
        this.parallel = parallel;
    }

    public ModuleTier nextTier() {
        ModuleTier[] available = definition.tierData()
            .keySet()
            .toArray(new ModuleTier[0]);
        Arrays.sort(available);
        for (int i = 0; i < available.length; i++) {
            if (available[i] == this.tier) {
                return available[Math.min(i + 1, available.length - 1)];
            }
        }
        return available[0];
    }

    public int cooldownTicks() {
        ModuleTierData data = currentTierData();
        IModuleComponent component = this.component;
        if (component != null) {
            return component.cooldownTicks(this, data);
        }
        return data.cooldownTicks();
    }

    public Map<ItemStack, Long> getConstructionCost() {
        return currentTierData().constructionCost();
    }

    public UpkeepDemand currentTierUpkeepDemand() {
        return currentTierData().upkeepDemand();
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

    public sealed interface SettingsBinding {

        record Private(ModuleSettings settings) implements SettingsBinding {

            public Private {
                if (settings == null) throw new IllegalArgumentException("Private module settings must not be null");
            }
        }

        record Shared(SettingsGroup.ID groupId) implements SettingsBinding {

            public Shared {
                if (groupId == null) throw new IllegalArgumentException("Shared settings group ID must not be null");
            }
        }
    }
}
