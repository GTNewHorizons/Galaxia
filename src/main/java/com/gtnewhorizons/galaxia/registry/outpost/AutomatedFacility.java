package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationDefinition;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationTargetSpec;
import com.gtnewhorizons.galaxia.registry.outpost.station.LayoutCacheBundle;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroupRegistry;

public final class AutomatedFacility extends CelestialAsset {

    private static final Logger LOG = LogManager.getLogger(AutomatedFacility.class);

    public final CelestialObjectId systemId;

    public final CelestialObjectId planetaryAnchorBodyId;

    private final List<ModuleInstance> modules;

    public final AutomatedFacilityInventory inventory;

    public final LogisticsConfiguration logisticsConfig;

    private final StationLayout layout;

    private final LayoutCacheBundle layoutCache;

    private final SettingsGroupRegistry settingsGroups;

    private long energyStored;

    private final Set<ModuleInstance.ID> dirtyModuleIds = new HashSet<>();
    private final Set<ModuleInstance.ID> dirtyRemovedIds = new HashSet<>();
    private final Set<UUID> syncedPlayerIds = new HashSet<>();

    public static final long MAX_ENERGY = 8_000_000L;

    public AutomatedFacility(CelestialAsset.ID assetId, CelestialObjectId celestialBodyId, Kind kind, Status status) {
        super(assetId, celestialBodyId, kind, status, null);
        if (kind != Kind.AUTOMATED_OUTPOST && kind != Kind.AUTOMATED_STATION) {
            throw new IllegalArgumentException(
                "AutomatedFacility kind must be AUTOMATED_OUTPOST or AUTOMATED_STATION, got: " + kind);
        }
        this.systemId = GalaxiaCelestialAPI.findStar(celestialBodyId)
            .id();
        this.planetaryAnchorBodyId = GalaxiaCelestialAPI.findPlanetaryAnchor(celestialBodyId)
            .id();
        this.modules = new ArrayList<>();
        this.inventory = new AutomatedFacilityInventory();
        this.logisticsConfig = new LogisticsConfiguration();
        this.layout = ownsStationLayout(kind) ? new StationLayout() : null;
        this.layoutCache = new LayoutCacheBundle(layout);
        this.settingsGroups = new SettingsGroupRegistry();
        this.energyStored = 0;
    }

    public static boolean ownsStationLayout(Kind kind) {
        return kind == Kind.AUTOMATED_OUTPOST || kind == Kind.AUTOMATED_STATION;
    }

    public boolean hasStationLayout() {
        return layout != null;
    }

    public @Nullable StationLayout stationLayout() {
        return layout;
    }

    public SettingsGroupRegistry settingsGroups() {
        return settingsGroups;
    }

    public LayoutCacheBundle layoutCache() {
        return layoutCache;
    }

    public List<ModuleInstance> modules() {
        return Collections.unmodifiableList(modules);
    }

    public void addModule(ModuleInstance module) {
        if (modules.contains(module)) {
            LOG.warn(
                "[PERSIST] addModule: duplicate module {} kind={} id={} (already present)",
                module.kind(),
                module.id,
                System.identityHashCode(module));
            return;
        }
        modules.add(module);
        dirtyModuleIds.add(module.id);
        bumpSyncRevision();
        LOG.debug(
            "[PERSIST] addModule: added {} id={} anchor=({},{}) shape={} status={} (total={})",
            module.kind(),
            module.id,
            (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                .dx() : ModuleInstance.NULL_ANCHOR_LOG_VALUE),
            (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                .dy() : ModuleInstance.NULL_ANCHOR_LOG_VALUE),
            module.shape(),
            module.status(),
            modules.size());
    }

    public void removeModule(int index) {
        ModuleInstance removed = modules.remove(index);
        if (removed != null) {
            dirtyRemovedIds.add(removed.id);
            dirtyModuleIds.remove(removed.id);
            bumpSyncRevision();
            if (layout != null) layout.removeTileForModule(removed.id);
            layoutCache.applyMutation(MutationKind.DECONSTRUCT, removed.kind(), removed);
        }
    }

    public boolean removeModule(ModuleInstance.ID moduleId) {
        int index = moduleIndex(moduleId);
        if (index < 0) return false;
        removeModule(index);
        return true;
    }

    public int moduleIndex(ModuleInstance.ID moduleId) {
        if (moduleId == null) return -1;
        for (int i = 0; i < modules.size(); i++) {
            if (moduleId.equals(modules.get(i).id)) return i;
        }
        return -1;
    }

    public void clearModules() {
        modules.clear();
    }

    public Stream<ModuleInstance> allOperationalModules() {
        return modules.stream()
            .filter(ModuleInstance::isOperational);
    }

    public List<ModuleInstance> modulesInternal() {
        return modules;
    }

    public MinerSettings minerSettings(ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner miner)) {
            throw new IllegalStateException("Miner settings requested for non-miner module " + module.id);
        }
        if (module.groupId() != 0) {
            SettingsGroup group = settingsGroups.require(module.groupId(), FacilityModuleKind.MINER);
            if (!(group.settings() instanceof MinerSettings settings)) {
                throw new IllegalStateException(
                    "Miner settings group " + module.groupId() + " has non-miner settings for module " + module.id);
            }
            return settings;
        }
        return miner.requireLocalSettings();
    }

    public boolean isMinerOreBlacklisted(ModuleInstance module, String oreKey) {
        return minerSettings(module).isOreBlacklisted(oreKey);
    }

    public void setMinerOreBlacklisted(ModuleInstance module, String oreKey, boolean blacklisted) {
        if (minerSettings(module).setOreBlacklisted(oreKey, blacklisted)) {
            if (module.groupId() == 0) {
                markModuleDirty(module.id);
            } else {
                markSettingsGroupMembersDirty(settingsGroups.require(module.groupId(), FacilityModuleKind.MINER));
            }
        }
    }

    public boolean tryReserveOperationMaterials(ModuleInstance module, Map<ItemStack, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        List<OperationMaterial> requested = materialCostToMaterials(materialCost);
        for (OperationMaterial material : requested) {
            if (inventory.getAmount(material.item()) < material.amount()) return false;
        }
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        for (OperationMaterial material : requested) {
            if (!inventory.tryConsume(material.item(), material.amount())) {
                throw new IllegalStateException(
                    "Operation material reservation became inconsistent for module " + module.id
                        + ", item="
                        + material.itemKey());
            }
            deposited.merge(material.itemKey(), material.amount(), Long::sum);
        }
        module.setOperation(operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
        markModuleDirty(module.id);
        return true;
    }

    public boolean tryReserveAvailableOperationMaterials(ModuleInstance module, Map<ItemStack, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        List<OperationMaterial> requested = materialCostToMaterials(materialCost);
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (OperationMaterial material : requested) {
            long alreadyDeposited = operation.depositedResources()
                .getOrDefault(material.itemKey(), 0L);
            long remaining = material.amount() - alreadyDeposited;
            if (remaining <= 0L) continue;
            long available = inventory.getAmount(material.item());
            long reserved = Math.min(available, remaining);
            if (reserved <= 0L) continue;
            if (!inventory.tryConsume(material.item(), reserved)) {
                throw new IllegalStateException(
                    "Operation partial reservation became inconsistent for module " + module.id
                        + ", item="
                        + material.itemKey());
            }
            deposited.merge(material.itemKey(), reserved, Long::sum);
            changed = true;
        }
        if (changed) {
            module.setOperation(
                operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
            markModuleDirty(module.id);
        }
        return operationHasFullDeposit(requireOperation(module), requested);
    }

    public void cancelModuleOperation(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        module.setOperation(operation.cancel());
        markModuleDirty(module.id);
    }

    public void applyCreativeModuleOperation(ModuleInstance module, ModuleOperationPlan plan) {
        if (module == null) {
            throw new IllegalArgumentException("applyCreativeModuleOperation: module must not be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("applyCreativeModuleOperation: plan must not be null for " + module.id);
        }
        ModuleOperationState existingOperation = module.operationOrNull();
        if (existingOperation != null && !existingOperation.phase()
            .isTerminal()) {
            if (!existingOperation.depositedResources()
                .isEmpty()
                || !existingOperation.refundBuffer()
                    .isEmpty()) {
                throw new IllegalStateException(
                    "Creative operation cannot replace active operation with stored items for module " + module.id);
            }
        }
        applyOperationTarget(module, plan.targetSpec());
        module.clearOperation();
        markModuleDirty(module.id);
    }

    public boolean flushModuleOperationRefund(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        if (operation.phase() != ModuleOperationPhase.REFUNDING) return false;
        for (Map.Entry<String, Long> entry : operation.refundBuffer()
            .entrySet()) {
            inventory.add(requireItemKey(entry.getKey(), module), entry.getValue());
        }
        if (isCompletionRefund(operation)) {
            module.clearOperation();
        } else {
            module.setOperation(operation.finishRefunding());
        }
        markModuleDirty(module.id);
        return true;
    }

    public SettingsGroup createSettingsGroupForModule(ModuleInstance module, String displayName) {
        ModuleSettings settings = copySettings(module);
        detachFromSettingsGroup(module);
        SettingsGroup group = settingsGroups.create(module.kind(), displayName, settings);
        attachToSettingsGroup(module, group);
        return group;
    }

    public void assignSettingsGroup(ModuleInstance module, short groupId) {
        if (module.groupId() == groupId) return;
        if (groupId == 0) {
            leaveSettingsGroup(module);
            return;
        }
        SettingsGroup group = settingsGroups.require(groupId, module.kind());
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, group);
    }

    public void leaveSettingsGroup(ModuleInstance module) {
        if (module.groupId() == 0) return;
        ModuleSettings settings = copySettings(module);
        detachFromSettingsGroup(module);
        applyLocalSettings(module, settings);
        markModuleDirty(module.id);
    }

    private ModuleSettings copySettings(ModuleInstance module) {
        if (module.component() instanceof ModuleMiner) {
            return minerSettings(module).copy();
        }
        throw new IllegalStateException("Settings groups are not supported for module kind " + module.kind());
    }

    private void attachToSettingsGroup(ModuleInstance module, SettingsGroup group) {
        settingsGroups.require(group.id(), module.kind());
        settingsGroups.addMember(group.id(), module.anchor());
        module.setGroupId(group.id());
        clearLocalSettings(module);
        markModuleDirty(module.id);
    }

    private void detachFromSettingsGroup(ModuleInstance module) {
        if (module.groupId() == 0) return;
        short oldGroupId = module.groupId();
        settingsGroups.removeMember(oldGroupId, module.anchor());
        module.setGroupId((short) 0);
    }

    private void applyLocalSettings(ModuleInstance module, ModuleSettings settings) {
        if (settings instanceof MinerSettings minerSettings && module.component() instanceof ModuleMiner miner) {
            miner.setLocalSettings(minerSettings);
            return;
        }
        throw new IllegalStateException("Cannot apply settings " + settings + " to module " + module.id);
    }

    private void clearLocalSettings(ModuleInstance module) {
        if (module.component() instanceof ModuleMiner miner) {
            miner.clearLocalSettings();
            return;
        }
        throw new IllegalStateException("Cannot clear local settings for module " + module.id);
    }

    private void markSettingsGroupMembersDirty(SettingsGroup group) {
        for (StationTileCoord coord : group.members()) {
            for (ModuleInstance module : modules) {
                if (coord.equals(module.anchorOrNull())) {
                    markModuleDirty(module.id);
                }
            }
        }
    }

    private ModuleOperationState requireWaitingOperation(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        if (operation.phase() != ModuleOperationPhase.WAITING_FOR_MATERIALS) {
            throw new IllegalStateException(
                "Module " + module.id + " operation must be WAITING_FOR_MATERIALS, got " + operation.phase());
        }
        return operation;
    }

    private ModuleOperationState requireOperation(ModuleInstance module) {
        if (module == null) {
            throw new IllegalArgumentException("Module operation requested for null module");
        }
        ModuleOperationState operation = module.operationOrNull();
        if (operation == null) {
            throw new IllegalStateException("Module " + module.id + " has no active operation");
        }
        return operation;
    }

    private List<OperationMaterial> materialCostToMaterials(Map<ItemStack, Long> materialCost) {
        if (materialCost == null) {
            throw new IllegalArgumentException("Operation material cost must not be null");
        }
        List<OperationMaterial> materials = new ArrayList<>();
        for (Map.Entry<ItemStack, Long> entry : materialCost.entrySet()) {
            ItemStack stack = entry.getKey();
            Long amount = entry.getValue();
            ItemStackWrapper wrapper = ItemStackWrapper.of(stack);
            if (wrapper == null) {
                throw new IllegalArgumentException("Operation material cost contains null/unkeyable stack");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "Operation material cost amount must be > 0 for " + wrapper.toKey() + ", got " + amount);
            }
            materials.add(new OperationMaterial(wrapper.toKey(), wrapper, amount));
        }
        return materials;
    }

    private ItemStackWrapper requireItemKey(String itemKey, ModuleInstance module) {
        ItemStackWrapper item = ItemStackWrapper.fromKey(itemKey);
        if (item == null) {
            throw new IllegalStateException("Module " + module.id + " operation has unresolvable item key " + itemKey);
        }
        return item;
    }

    private static Map<String, Long> mergeAmounts(Map<String, Long> base, Map<String, Long> added) {
        Map<String, Long> merged = new java.util.LinkedHashMap<>(base);
        for (Map.Entry<String, Long> entry : added.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
        return merged;
    }

    private record OperationMaterial(String itemKey, ItemStackWrapper item, long amount) {}

    public void markModuleDirty(ModuleInstance.ID id) {
        dirtyModuleIds.add(id);
        bumpSyncRevision();
    }

    public boolean isDirty() {
        return !dirtyModuleIds.isEmpty() || !dirtyRemovedIds.isEmpty();
    }

    public boolean needsFullSyncFor(UUID playerId) {
        return !syncedPlayerIds.contains(playerId);
    }

    public void markSyncedFor(UUID playerId) {
        syncedPlayerIds.add(playerId);
    }

    public List<ModuleInstance> drainDirtyModules() {
        List<ModuleInstance> result = new ArrayList<>(dirtyModuleIds.size());
        for (ModuleInstance.ID id : dirtyModuleIds) {
            int idx = moduleIndex(id);
            if (idx >= 0) result.add(modules.get(idx));
        }
        dirtyModuleIds.clear();
        return result;
    }

    public List<ModuleInstance.ID> drainRemovedIds() {
        List<ModuleInstance.ID> result = new ArrayList<>(dirtyRemovedIds);
        dirtyRemovedIds.clear();
        return result;
    }

    public long getEnergyStored() {
        return energyStored;
    }

    public void setEnergyStored(long energyStored) {
        this.energyStored = Math.clamp(energyStored, 0, MAX_ENERGY);
    }

    public void addEnergy(long delta) {
        setEnergyStored(energyStored + delta);
    }

    public boolean tryConsumeEnergy(long amount) {
        if (energyStored < amount) return false;
        setEnergyStored(energyStored - amount);
        return true;
    }

    @Override
    public boolean hasMiningCapability() {
        for (ModuleInstance m : modules) {
            if (m.kind() == FacilityModuleKind.MINER && m.isOperational()) return true;
        }
        return false;
    }

    @Override
    public boolean hasProductionCapability() {
        for (ModuleInstance m : modules) {
            FacilityModuleKind k = m.kind();
            if (k == FacilityModuleKind.HAMMER && m.isOperational()) return true;
        }
        return false;
    }

    @Override
    public WarningPriority warningPriority() {
        if (!isOperational()) return WarningPriority.NONE;
        if (energyStored <= 0L) return WarningPriority.NO_POWER;
        for (ModuleInstance m : modules) {
            if (m.isOperational()) return WarningPriority.NONE;
        }
        return WarningPriority.IDLE;
    }

    public void tick() {
        for (ModuleInstance module : modules) {
            tickModuleOperation(module);
            if (!isBuildingOperation(module)) {
                module.tick(this);
            }
        }

        LogisticStore.updateSignalsForFacility(this);
    }

    private void tickModuleOperation(ModuleInstance module) {
        ModuleOperationState operation = module.operationOrNull();
        if (operation == null) return;
        switch (operation.phase()) {
            case WAITING_FOR_MATERIALS -> tryBeginModuleOperation(module, operation);
            case BUILDING -> tickBuildingOperation(module, operation);
            case REFUNDING -> flushModuleOperationRefund(module);
            case COMPLETE -> applyCompletedModuleOperation(module, operation);
            case CANCELLED -> {
                module.clearOperation();
                markModuleDirty(module.id);
            }
        }
    }

    private boolean isBuildingOperation(ModuleInstance module) {
        ModuleOperationState operation = module.operationOrNull();
        return operation != null && operation.phase() == ModuleOperationPhase.BUILDING;
    }

    private void tryBeginModuleOperation(ModuleInstance module, ModuleOperationState operation) {
        ModuleOperationDefinition definition = operationDefinition(
            operation.plan()
                .targetSpec());
        Map<ItemStack, Long> materialCost = definition.materialCost(
            operation.plan()
                .targetSpec());
        boolean hasFullCost = operation.reserveItems() ? tryReserveAvailableOperationMaterials(module, materialCost)
            : tryReserveOperationMaterials(module, materialCost);
        if (!hasFullCost) {
            return;
        }
        module.setOperation(
            module.operationOrNull()
                .beginBuilding());
        markModuleDirty(module.id);
    }

    private void tickBuildingOperation(ModuleInstance module, ModuleOperationState operation) {
        ModuleOperationState next = operation.tickBuilding();
        module.setOperation(next);
        markModuleDirty(module.id);
        if (next.phase() == ModuleOperationPhase.COMPLETE) {
            applyCompletedModuleOperation(module, next);
        }
    }

    private void applyCompletedModuleOperation(ModuleInstance module, ModuleOperationState operation) {
        ModuleOperationTargetSpec target = operation.plan()
            .targetSpec();
        applyOperationTarget(module, target);
        Map<String, Long> completionRefund = completionRefund(operation);
        if (completionRefund.isEmpty()) {
            module.clearOperation();
        } else {
            module.setOperation(operation.refundAfterCompletion(completionRefund));
        }
        markModuleDirty(module.id);
    }

    private void applyOperationTarget(ModuleInstance module, ModuleOperationTargetSpec target) {
        if (target.operationKind() != ModuleOperationKind.UPGRADE_REBUILD) {
            throw new IllegalStateException(
                "Unsupported operation kind " + target.operationKind() + " for " + module.id);
        }
        if (target.sourceModuleKind() != null && target.sourceModuleKind() != module.kind()) {
            throw new IllegalStateException(
                "Operation source kind mismatch for " + module.id
                    + ": expected "
                    + target.sourceModuleKind()
                    + ", got "
                    + module.kind());
        }
        if (target.targetModuleKind() != null && target.targetModuleKind() != module.kind()) {
            throw new IllegalStateException(
                "Operation target kind changes are not implemented for " + module.id
                    + ": "
                    + target.targetModuleKind());
        }
        if (target.sourceTier() != null && target.sourceTier() != module.tier()) {
            throw new IllegalStateException(
                "Operation source tier mismatch for " + module.id
                    + ": expected "
                    + target.sourceTier()
                    + ", got "
                    + module.tier());
        }
        if (module.component() instanceof ModuleHammer) {
            applyHammerOperationTarget(module, target);
        } else if (module.component() instanceof ModuleMiner) {
            applyMinerOperationTarget(module, target);
        } else {
            throw new IllegalStateException("Operation target is unsupported for module " + module.id);
        }
    }

    private void applyHammerOperationTarget(ModuleInstance module, ModuleOperationTargetSpec target) {
        if (!(module.component() instanceof ModuleHammer hammer)) {
            throw new IllegalStateException("HAMMER operation applied to non-hammer module " + module.id);
        }
        if (target.sourceVariantKey() != null && !target.sourceVariantKey()
            .equals(
                hammer.variant()
                    .name())) {
            throw new IllegalStateException(
                "Operation source variant mismatch for " + module.id
                    + ": expected "
                    + target.sourceVariantKey()
                    + ", got "
                    + hammer.variant()
                        .name());
        }
        if (target.targetVariantKey() == null || target.targetTier() == null) {
            throw new IllegalStateException("HAMMER operation target is incomplete for module " + module.id);
        }
        HammerVariant targetVariant = HammerVariant.valueOf(target.targetVariantKey());
        ModuleTier targetTier = target.targetTier();
        ModuleHammer.requireTier(targetVariant, targetTier);
        hammer.setVariant(targetVariant);
        module.setTier(targetTier);
        layoutCache.applyMutation(MutationKind.SET_TIER, module.kind(), module);
    }

    private void applyMinerOperationTarget(ModuleInstance module, ModuleOperationTargetSpec target) {
        if (!(module.component() instanceof ModuleMiner miner)) {
            throw new IllegalStateException("MINER operation applied to non-miner module " + module.id);
        }
        if (target.targetTier() != null && target.targetTier() != module.tier()) {
            throw new IllegalStateException(
                "MINER focus operation cannot change module tier for " + module.id + ": " + target.targetTier());
        }
        if (target.sourceFocusTierKey() != null && !target.sourceFocusTierKey()
            .equals(
                miner.focusTier()
                    .name())) {
            throw new IllegalStateException(
                "Operation source focus tier mismatch for " + module.id
                    + ": expected "
                    + target.sourceFocusTierKey()
                    + ", got "
                    + miner.focusTier()
                        .name());
        }
        if (target.sourceFocusOreKey() != null && !target.sourceFocusOreKey()
            .equals(miner.focusOreKeyOrNull())) {
            throw new IllegalStateException(
                "Operation source focus ore mismatch for " + module.id
                    + ": expected "
                    + target.sourceFocusOreKey()
                    + ", got "
                    + miner.focusOreKeyOrNull());
        }
        if (target.targetFocusTierKey() == null) {
            throw new IllegalStateException("MINER operation target focus tier is missing for module " + module.id);
        }
        MinerFocusTier focusTier = MinerFocusTier.valueOf(target.targetFocusTierKey());
        String focusOreKey = focusTier == MinerFocusTier.NONE ? null : target.targetFocusOreKey();
        miner.setFocus(focusTier, focusOreKey, 0);
    }

    private Map<String, Long> completionRefund(ModuleOperationState operation) {
        if (operation.plan()
            .voidCompletionRefund()) {
            return Map.of();
        }
        int refundPercent = operation.plan()
            .completionRefundPercent();
        if (refundPercent <= 0) return Map.of();
        FacilityModuleKind sourceKind = operation.plan()
            .targetSpec()
            .sourceModuleKind();
        if (sourceKind == null) return Map.of();
        Map<String, Long> refund = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStack, Long> entry : FacilityModuleRegistry.get(sourceKind)
            .constructionCost()
            .entrySet()) {
            long amount = entry.getValue() * refundPercent / 100L;
            if (amount <= 0L) continue;
            ItemStackWrapper wrapper = ItemStackWrapper.of(entry.getKey());
            if (wrapper == null) {
                throw new IllegalStateException("Operation completion refund contains unkeyable stack");
            }
            refund.merge(wrapper.toKey(), amount, Long::sum);
        }
        return refund;
    }

    private ModuleOperationDefinition operationDefinition(ModuleOperationTargetSpec target) {
        FacilityModuleKind targetKind = target.targetModuleKind() != null ? target.targetModuleKind()
            : target.sourceModuleKind();
        if (targetKind == null) {
            throw new IllegalStateException("Operation target and source kind are both null for " + target);
        }
        return FacilityModuleRegistry.get(targetKind)
            .operationDefinition(target.operationKind());
    }

    private static boolean operationHasFullDeposit(ModuleOperationState operation, List<OperationMaterial> requested) {
        for (OperationMaterial material : requested) {
            if (operation.depositedResources()
                .getOrDefault(material.itemKey(), 0L) < material.amount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompletionRefund(ModuleOperationState operation) {
        return operation.plan()
            .targetSpec()
            .operationKind()
            .buildPhaseRequired()
            && operation.elapsedBuildTicks() >= operation.plan()
                .buildTicks();
    }
}
