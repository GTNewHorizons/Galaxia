package com.gtnewhorizons.galaxia.registry.outpost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.LayoutCacheBundle;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
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
    private final Map<String, Integer> minerVoidChancePercentByOre;

    private long energyStored;

    private final Set<ModuleInstance.ID> dirtyModuleIds = new HashSet<>();
    private final Set<ModuleInstance.ID> dirtyRemovedIds = new HashSet<>();
    private final Set<String> dirtyMinerVoidChanceOreKeys = new HashSet<>();
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
        this.minerVoidChancePercentByOre = new LinkedHashMap<>();
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

    public Map<String, Integer> minerVoidChances() {
        return Collections.unmodifiableMap(minerVoidChancePercentByOre);
    }

    public int minerVoidChancePercent(String oreKey) {
        return minerVoidChancePercentByOre.getOrDefault(requireOreKey(oreKey), 0);
    }

    public void setMinerVoidChancePercent(String oreKey, int percent) {
        String key = requireOreKey(oreKey);
        percent = clampMinerVoidChancePercent(percent);
        Integer oldValue = minerVoidChancePercentByOre.get(key);
        Integer newValue = percent == 0 ? null : percent;
        if (newValue == null) {
            minerVoidChancePercentByOre.remove(key);
        } else {
            minerVoidChancePercentByOre.put(key, newValue);
        }
        if (!Objects.equals(oldValue, newValue)) {
            dirtyMinerVoidChanceOreKeys.add(key);
            bumpSyncRevision();
        }
    }

    public void setMinerVoidChances(@Nonnull Map<String, Integer> chances) {
        minerVoidChancePercentByOre.clear();
        for (Map.Entry<String, Integer> entry : chances.entrySet()) {
            String key = requireOreKey(entry.getKey());
            if (entry.getValue() == null) throw new IllegalArgumentException("Chance has to be present");
            int percent = requireMinerVoidChancePercent(entry.getValue());
            if (percent != 0) minerVoidChancePercentByOre.put(key, percent);
        }
        dirtyMinerVoidChanceOreKeys.clear();
    }

    private static int requireMinerVoidChancePercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Miner void chance percent out of range: " + percent);
        }
        return percent;
    }

    public static int clampMinerVoidChancePercent(int percent) {
        return Math.clamp(percent, 0, 100);
    }

    public void markModuleDirty(ModuleInstance.ID id) {
        dirtyModuleIds.add(id);
        bumpSyncRevision();
    }

    public boolean isDirty() {
        return !dirtyModuleIds.isEmpty() || !dirtyRemovedIds.isEmpty() || !dirtyMinerVoidChanceOreKeys.isEmpty();
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

    public Map<String, Integer> drainDirtyMinerVoidChances() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String oreKey : dirtyMinerVoidChanceOreKeys) {
            result.put(oreKey, minerVoidChancePercent(oreKey));
        }
        dirtyMinerVoidChanceOreKeys.clear();
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

    private static String requireOreKey(@Nonnull String oreKey) {
        if (oreKey.isEmpty()) throw new IllegalArgumentException("oreKey cannot be empty");
        return oreKey;
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
            module.tick(this);
        }

        LogisticStore.updateSignalsForFacility(this);
    }
}
