package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.CapacityCluster;
import com.gtnewhorizons.galaxia.registry.outpost.station.LayoutCacheBundle;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;
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
    private final Map<ItemStackWrapper, Long> dirtyInventoryDeltas = new LinkedHashMap<>();
    private final List<InventoryBoundDelta> dirtyInventoryBoundDeltas = new ArrayList<>();
    private final Set<UUID> syncedPlayerIds = new HashSet<>();
    private final Set<String> dirtyMinerVoidChanceOreKeys = new HashSet<>();

    public static final long MAX_ENERGY = 8_000_000L;
    public static final long BASE_ITEM_CAPACITY = 1000L;

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

    public void applySettingsGroupsToModules() {
        for (SettingsGroup group : settingsGroups.groups()
            .values()) {
            applyRecipeSettingsToGroup(group);
        }
    }

    public void syncRecipeSettingsGroupsFromModules() {
        for (SettingsGroup group : settingsGroups.groups()
            .values()) {
            if (!(group.settings() instanceof RecipeModuleSettings recipeSettings)) continue;
            for (StationTileCoord coord : group.members()) {
                ModuleInstance module = moduleAtAnchor(coord);
                if (module != null && module.component() instanceof IRecipeModule recipeModule) {
                    recipeSettings.setConfig(recipeModule.getRecipeConfig());
                    break;
                }
            }
        }
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
        if (FacilityModuleRegistry.get(module.kind())
            .settingsGroups() && module.groupId() == 0) {
            attachToSettingsGroup(module, settingsGroups.create(module.kind(), privateSettingsFor(module)));
        }
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

        markDirty();
    }

    public void removeModule(int index) {
        ModuleInstance removed = modules.remove(index);
        if (removed != null) {
            detachFromSettingsGroup(removed);
            dirtyRemovedIds.add(removed.id);
            dirtyModuleIds.remove(removed.id);
            bumpSyncRevision();
            if (layout != null) layout.removeTileForModule(removed.id);
            layoutCache.applyMutation(MutationKind.DECONSTRUCT, removed.kind(), removed);
            markDirty();
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

    private @Nullable ModuleInstance moduleAtAnchor(StationTileCoord coord) {
        if (coord == null) return null;
        for (ModuleInstance module : modules) {
            if (coord.equals(module.anchorOrNull())) return module;
        }
        return null;
    }

    public void clearModules() {
        modules.clear();
        markDirty();
    }

    public Stream<ModuleInstance> allOperationalModules() {
        return modules.stream()
            .filter(ModuleInstance::isOperational);
    }

    public List<ModuleInstance> modulesInternal() {
        return modules;
    }

    public MinerSettings minerSettings(ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("Miner settings requested for non-miner module " + module.id);
        }
        if (module.groupId() == 0) {
            throw new IllegalStateException("Miner module " + module.id + " has no settings group");
        }
        SettingsGroup group = settingsGroups.require(module.groupId(), FacilityModuleKind.MINER);
        if (!(group.settings() instanceof MinerSettings settings)) {
            throw new IllegalStateException(
                "Miner settings group " + module.groupId() + " has non-miner settings for module " + module.id);
        }
        return settings;
    }

    public boolean isMinerOreBlacklisted(ModuleInstance module, String oreKey) {
        return minerSettings(module).isOreBlacklisted(oreKey);
    }

    public void setMinerOreBlacklisted(ModuleInstance module, String oreKey, boolean blacklisted) {
        if (minerSettings(module).setOreBlacklisted(oreKey, blacklisted)) {
            markSettingsGroupMembersDirty(settingsGroups.require(module.groupId(), FacilityModuleKind.MINER));
        }
    }

    public RecipeConfig recipeConfig(ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) {
            throw new IllegalStateException("Recipe config requested for non-recipe module " + module.id);
        }
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            RecipeConfig config = recipeModule.getRecipeConfig();
            return config != null ? config : RecipeConfig.empty();
        }
        if (module.groupId() == 0) {
            throw new IllegalStateException("Recipe module " + module.id + " has no settings group");
        }
        SettingsGroup group = settingsGroups.require(module.groupId(), module.kind());
        if (!(group.settings() instanceof RecipeModuleSettings settings)) {
            throw new IllegalStateException(
                "Recipe settings group " + module.groupId() + " has non-recipe settings for module " + module.id);
        }
        RecipeConfig config = settings.config();
        return config != null ? config : RecipeConfig.empty();
    }

    public void setRecipeConfig(ModuleInstance module, RecipeConfig config) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) {
            throw new IllegalStateException("Recipe config update requested for non-recipe module " + module.id);
        }
        RecipeConfig normalized = RecipeModuleSettings.copyConfig(config);
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            recipeModule.setRecipeConfig(normalized);
            markModuleDirty(module.id);
            return;
        }
        if (module.groupId() == 0) {
            attachToSettingsGroup(module, settingsGroups.create(module.kind(), new RecipeModuleSettings(normalized)));
        }
        SettingsGroup group = settingsGroups.require(module.groupId(), module.kind());
        if (!(group.settings() instanceof RecipeModuleSettings settings)) {
            throw new IllegalStateException(
                "Recipe settings group " + module.groupId() + " has non-recipe settings for module " + module.id);
        }
        settings.setConfig(normalized);
        applyRecipeSettingsToGroup(group);
        markSettingsGroupMembersDirty(group);
    }

    public void copyMinerRuntimeSettings(ModuleInstance source, ModuleInstance target) {
        if (!(source.component() instanceof ModuleMiner sourceMiner)) {
            throw new IllegalStateException("Miner settings copy source is not a miner: " + source.id);
        }
        if (!(target.component() instanceof ModuleMiner targetMiner)) {
            throw new IllegalStateException("Miner settings copy target is not a miner: " + target.id);
        }
        if (source.id.equals(target.id)) {
            throw new IllegalStateException("Miner settings copy target must be different from source: " + source.id);
        }
        SettingsGroup sourceGroup = settingsGroups.require(source.groupId(), FacilityModuleKind.MINER);
        String sourceFocusOreKey = sourceMiner.focusOreKeyOrNull();
        if (sourceFocusOreKey != null && targetMiner.focusTier() == MinerFocusTier.NONE) {
            throw new IllegalStateException(
                "Miner settings copy target " + target.id + " has no focus tier for ore " + sourceFocusOreKey);
        }
        if (sourceGroup.isJoinable()) {
            assignSettingsGroup(target, sourceGroup.id());
        } else {
            setPrivateMinerSettings(target, ((MinerSettings) sourceGroup.settings()).copy());
        }
        targetMiner.setFocusOre(sourceFocusOreKey);
        markModuleDirty(target.id);
    }

    public boolean tryReserveOperationMaterials(ModuleInstance module, Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            if (inventory.getAmount(material.getKey()) < material.getValue()) return false;
        }
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            if (!tryConsumeInventory(material.getKey(), material.getValue())) {
                throw new IllegalStateException(
                    "Operation material reservation became inconsistent for module " + module.id
                        + ", item="
                        + material.getKey()
                            .toKey());
            }
            deposited.merge(
                material.getKey()
                    .toKey(),
                material.getValue(),
                Long::sum);
        }
        module.setOperation(operation.withDepositedResources(mergeAmounts(operation.depositedResources(), deposited)));
        markModuleDirty(module.id);
        return true;
    }

    public long itemInventoryCapacity() {
        long capacity = BASE_ITEM_CAPACITY;
        for (CapacityCluster cluster : layoutCache.getCapacityClusters(FacilityModuleKind.STORAGE)) {
            capacity += cluster.effectiveCapacity();
        }
        return capacity;
    }

    public long usedItemInventoryCapacity() {
        return inventory.totalItems();
    }

    public long remainingItemInventoryCapacity() {
        return Math.max(0L, itemInventoryCapacity() - usedItemInventoryCapacity());
    }

    public boolean isItemInventoryFull() {
        return remainingItemInventoryCapacity() <= 0L;
    }

    public long insertInventory(ItemStackWrapper item, long amount) {
        return insertInventory(item, amount, true);
    }

    public long insertInventoryWithoutSync(ItemStackWrapper item, long amount) {
        return insertInventory(item, amount, false);
    }

    private long insertInventory(ItemStackWrapper item, long amount, boolean trackSync) {
        if (item == null || amount <= 0L) return 0L;
        long accepted = Math.min(amount, remainingItemInventoryCapacity());
        if (accepted <= 0L) return 0L;
        inventory.add(item, accepted);
        if (trackSync) markInventoryDelta(item, accepted);
        return accepted;
    }

    public long addInventory(ItemStackWrapper item, long delta) {
        return addInventory(item, delta, true);
    }

    public long addInventoryWithoutSync(ItemStackWrapper item, long delta) {
        return addInventory(item, delta, false);
    }

    private long addInventory(ItemStackWrapper item, long delta, boolean trackSync) {
        if (item == null || delta == 0L) return 0L;
        long actual = inventory.add(item, delta);
        if (actual != 0L && trackSync) markInventoryDelta(item, actual);
        return actual;
    }

    public boolean tryConsumeInventory(ItemStackWrapper item, long amount) {
        if (item == null) return false;
        if (amount <= 0L) return true;
        if (!inventory.tryConsume(item, amount)) return false;
        markInventoryDelta(item, -amount);
        return true;
    }

    public boolean tryReserveAvailableOperationMaterials(ModuleInstance module,
        Map<ItemStackWrapper, Long> materialCost) {
        ModuleOperationState operation = requireWaitingOperation(module);
        Map<ItemStackWrapper, Long> requested = requireMaterialCost(materialCost);
        Map<String, Long> deposited = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            String itemKey = material.getKey()
                .toKey();
            long alreadyDeposited = operation.depositedResources()
                .getOrDefault(itemKey, 0L);
            long remaining = material.getValue() - alreadyDeposited;
            if (remaining <= 0L) continue;
            long available = inventory.getAmount(material.getKey());
            long reserved = Math.min(available, remaining);
            if (reserved <= 0L) continue;
            if (!tryConsumeInventory(material.getKey(), reserved)) {
                throw new IllegalStateException(
                    "Operation partial reservation became inconsistent for module " + module.id + ", item=" + itemKey);
            }
            deposited.merge(itemKey, reserved, Long::sum);
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
        applyOperationTarget(module, plan);
        module.clearOperation();
        markModuleDirty(module.id);
    }

    public boolean flushModuleOperationRefund(ModuleInstance module) {
        ModuleOperationState operation = requireOperation(module);
        if (operation.phase() != ModuleOperationPhase.REFUNDING) return false;
        Map<String, Long> remaining = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Long> entry : operation.refundBuffer()
            .entrySet()) {
            ItemStackWrapper item = requireItemKey(entry.getKey(), module);
            long accepted = insertInventory(item, entry.getValue());
            if (accepted > 0L) changed = true;
            long leftover = entry.getValue() - accepted;
            if (leftover > 0L) remaining.put(entry.getKey(), leftover);
        }
        if (!changed) return false;
        if (!remaining.isEmpty()) {
            module.setOperation(operation.withRefundBuffer(remaining));
        } else if (isCompletionRefund(operation)) {
            module.clearOperation();
        } else {
            module.setOperation(operation.finishRefunding());
        }
        markModuleDirty(module.id);
        return true;
    }

    public SettingsGroup createSettingsGroupForModule(ModuleInstance module, String displayName) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroups.require(module.groupId(), module.kind());
            if (current.members()
                .size() == 1) {
                if (displayName != null) {
                    current.setDisplayName(displayName);
                } else if (current.hasDefaultPrivateDisplayName()) {
                    current.setDisplayName(current.defaultJoinableDisplayName());
                }
                current.setJoinable(true);
                markModuleDirty(module.id);
                return current;
            }
        }
        ModuleSettings settings = copySettings(module);
        detachFromSettingsGroup(module);
        SettingsGroup group = settingsGroups.create(module.kind(), displayName, true, settings);
        attachToSettingsGroup(module, group);
        return group;
    }

    public void renameSettingsGroupForModule(ModuleInstance module, short groupId, String displayName) {
        if (module == null) {
            throw new IllegalArgumentException("renameSettingsGroupForModule: module must not be null");
        }
        requireSettingsGroupsSupported(module);
        SettingsGroup group = settingsGroups.require(groupId, module.kind());
        if (!group.isJoinable()) {
            throw new IllegalStateException("Settings group " + groupId + " is private and cannot be renamed");
        }
        group.setDisplayName(displayName);
        markSettingsGroupMembersDirty(group);
        if (group.members()
            .isEmpty()) {
            bumpSyncRevision();
            markDirty();
        }
    }

    public void assignSettingsGroup(ModuleInstance module, short groupId) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() == groupId) return;
        if (groupId == 0) {
            leaveSettingsGroup(module);
            return;
        }
        SettingsGroup group = settingsGroups.require(groupId, module.kind());
        if (!group.isJoinable()) {
            throw new IllegalStateException("Settings group " + groupId + " is private and cannot be joined");
        }
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, group);
    }

    public void leaveSettingsGroup(ModuleInstance module) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroups.require(module.groupId(), module.kind());
            if (current.members()
                .size() == 1) {
                current.setJoinable(false);
                markModuleDirty(module.id);
                return;
            }
        }
        ModuleSettings settings = copySettings(module);
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, settingsGroups.create(module.kind(), settings));
        markModuleDirty(module.id);
    }

    private void setPrivateMinerSettings(ModuleInstance module, MinerSettings settings) {
        if (module.groupId() != 0) {
            SettingsGroup current = settingsGroups.require(module.groupId(), module.kind());
            if (!current.isJoinable() && current.members()
                .size() == 1) {
                current.setSettings(settings);
                markModuleDirty(module.id);
                return;
            }
        }
        detachFromSettingsGroup(module);
        attachToSettingsGroup(module, settingsGroups.create(FacilityModuleKind.MINER, settings));
    }

    private ModuleSettings copySettings(ModuleInstance module) {
        requireSettingsGroupsSupported(module);
        if (module.groupId() != 0) {
            SettingsGroup group = settingsGroups.require(module.groupId(), module.kind());
            return module.component()
                .copySettings(module, group.settings());
        }
        return module.component()
            .createPrivateSettings(module);
    }

    private ModuleSettings privateSettingsFor(ModuleInstance module) {
        requireSettingsGroupsSupported(module);
        return module.component()
            .createPrivateSettings(module);
    }

    private static void requireSettingsGroupsSupported(ModuleInstance module) {
        if (module == null) {
            throw new IllegalArgumentException("Settings group module must not be null");
        }
        if (!FacilityModuleRegistry.get(module.kind())
            .settingsGroups()) {
            throw new IllegalStateException("Settings groups are not supported for module kind " + module.kind());
        }
    }

    private void attachToSettingsGroup(ModuleInstance module, SettingsGroup group) {
        settingsGroups.require(group.id(), module.kind());
        settingsGroups.addMember(group.id(), module.anchor());
        module.setGroupId(group.id());
        applySettingsToModule(group.settings(), module);
        markModuleDirty(module.id);
    }

    private void detachFromSettingsGroup(ModuleInstance module) {
        if (module.groupId() == 0) return;
        short oldGroupId = module.groupId();
        settingsGroups.removeMember(oldGroupId, module.anchor());
        module.setGroupId((short) 0);
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

    private void applyRecipeSettingsToGroup(SettingsGroup group) {
        for (StationTileCoord coord : group.members()) {
            for (ModuleInstance module : modules) {
                if (coord.equals(module.anchorOrNull())) {
                    applySettingsToModule(group.settings(), module);
                }
            }
        }
    }

    private static void applySettingsToModule(ModuleSettings settings, ModuleInstance module) {
        module.component()
            .applySettings(module, settings);
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

    private Map<ItemStackWrapper, Long> requireMaterialCost(Map<ItemStackWrapper, Long> materialCost) {
        if (materialCost == null) {
            throw new IllegalArgumentException("Operation material cost must not be null");
        }
        for (Map.Entry<ItemStackWrapper, Long> entry : materialCost.entrySet()) {
            ItemStackWrapper item = entry.getKey();
            Long amount = entry.getValue();
            if (item == null) {
                throw new IllegalArgumentException("Operation material cost contains null item");
            }
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException(
                    "Operation material cost amount must be > 0 for " + item.toKey() + ", got " + amount);
            }
        }
        return materialCost;
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

    public void markModuleDirty(ModuleInstance.ID id) {
        dirtyModuleIds.add(id);
        bumpSyncRevision();
        markDirty();
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || !dirtyModuleIds.isEmpty()
            || !dirtyRemovedIds.isEmpty()
            || !dirtyInventoryDeltas.isEmpty()
            || !dirtyInventoryBoundDeltas.isEmpty();
    }

    @Override
    public boolean needsFullSyncFor(UUID playerId) {
        return !syncedPlayerIds.contains(playerId);
    }

    @Override
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

    public Map<ItemStackWrapper, Long> drainDirtyInventoryDeltas() {
        Map<ItemStackWrapper, Long> result = new LinkedHashMap<>(dirtyInventoryDeltas);
        dirtyInventoryDeltas.clear();
        return result;
    }

    public List<InventoryBoundDelta> drainDirtyInventoryBoundDeltas() {
        List<InventoryBoundDelta> result = new ArrayList<>(dirtyInventoryBoundDeltas);
        dirtyInventoryBoundDeltas.clear();
        return result;
    }

    public List<ModuleInstance.ID> drainRemovedIds() {
        List<ModuleInstance.ID> result = new ArrayList<>(dirtyRemovedIds);
        dirtyRemovedIds.clear();
        return result;
    }

    private void markInventoryDelta(ItemStackWrapper item, long delta) {
        if (item == null || delta == 0L) return;
        dirtyInventoryDeltas.merge(item, delta, Long::sum);
        if (dirtyInventoryDeltas.getOrDefault(item, 0L) == 0L) {
            dirtyInventoryDeltas.remove(item);
        }
        bumpSyncRevision();
    }

    public void markInventoryBoundDelta(AutomatedFacilityInventory.BoundKind kind, String resourceKey, boolean present,
        long amount) {
        if (kind == null || resourceKey == null || resourceKey.isEmpty()) return;
        dirtyInventoryBoundDeltas.add(new InventoryBoundDelta(kind, resourceKey, present, amount));
        bumpSyncRevision();
        markDirty();
    }

    public record InventoryBoundDelta(AutomatedFacilityInventory.BoundKind kind, String resourceKey, boolean present,
        long amount) {}

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
            boolean moduleTickBlocked = tickModuleOperation(module);
            if (!moduleTickBlocked) {
                module.tick(this);
            }
        }

        LogisticStore.updateSignalsForFacility(this);
    }

    private boolean tickModuleOperation(ModuleInstance module) {
        ModuleOperationState operation = module.operationOrNull();
        if (operation == null) return false;
        return switch (operation.phase()) {
            case WAITING_FOR_MATERIALS -> tryBeginModuleOperation(module, operation);
            case BUILDING -> {
                tickBuildingOperation(module, operation);
                yield true;
            }
            case REFUNDING -> {
                flushModuleOperationRefund(module);
                yield true;
            }
            case COMPLETE -> {
                applyCompletedModuleOperation(module, operation);
                yield false;
            }
            case CANCELLED -> {
                module.clearOperation();
                markModuleDirty(module.id);
                yield false;
            }
        };
    }

    private boolean tryBeginModuleOperation(ModuleInstance module, ModuleOperationState operation) {
        Map<ItemStackWrapper, Long> materialCost = operation.plan()
            .materialCost();
        boolean hasFullCost = operation.reserveItems() ? tryReserveAvailableOperationMaterials(module, materialCost)
            : tryReserveOperationMaterials(module, materialCost);
        if (!hasFullCost) {
            return false;
        }
        module.setOperation(
            module.operationOrNull()
                .beginBuilding());
        markModuleDirty(module.id);
        return true;
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
        ModuleOperationPlan plan = operation.plan();
        applyOperationTarget(module, plan);
        Map<String, Long> completionRefund = completionRefund(module, operation);
        if (completionRefund.isEmpty()) {
            module.clearOperation();
        } else {
            module.setOperation(operation.refundAfterCompletion(completionRefund));
        }
        markModuleDirty(module.id);
    }

    private void applyOperationTarget(ModuleInstance module, ModuleOperationPlan plan) {
        ModuleTier oldTier = module.tier();
        module.component()
            .applyOperationTarget(plan.spec(), module);
        if (module.tier() != oldTier) {
            layoutCache.applyMutation(MutationKind.SET_TIER, module.kind(), module);
        }
    }

    private Map<String, Long> completionRefund(ModuleInstance module, ModuleOperationState operation) {
        if (operation.plan()
            .voidCompletionRefund()) {
            return Map.of();
        }
        int refundPercent = operation.plan()
            .completionRefundPercent();
        if (refundPercent <= 0) return Map.of();
        Map<String, Long> refund = new java.util.LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : operation.plan()
            .completionRefundCost()
            .entrySet()) {
            long amount = entry.getValue() * refundPercent / 100L;
            if (amount <= 0L) continue;
            refund.merge(
                entry.getKey()
                    .toKey(),
                amount,
                Long::sum);
        }
        return refund;
    }

    private static boolean operationHasFullDeposit(ModuleOperationState operation,
        Map<ItemStackWrapper, Long> requested) {
        for (Map.Entry<ItemStackWrapper, Long> material : requested.entrySet()) {
            if (operation.depositedResources()
                .getOrDefault(
                    material.getKey()
                        .toKey(),
                    0L)
                < material.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompletionRefund(ModuleOperationState operation) {
        return operation.elapsedBuildTicks() >= operation.plan()
            .buildTicks();
    }
}
