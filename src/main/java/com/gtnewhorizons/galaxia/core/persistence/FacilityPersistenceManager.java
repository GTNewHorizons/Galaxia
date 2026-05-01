package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class FacilityPersistenceManager {

    private static final Logger LOG = LogManager.getLogger(FacilityPersistenceManager.class);

    private static final String DATA_DIR = "galaxiadata";
    private static final String ASSETS_FILE = "_assets.json";
    private static final String TASKS_FILE = "_tasks.json";

    private final Gson gson;
    private static final Gson PURE_GSON = new GsonBuilder().create();
    private File worldSaveDir;

    public FacilityPersistenceManager() {
        gson = new GsonBuilder().setPrettyPrinting()
            .create();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        ISaveHandler saveHandler = event.world.getSaveHandler();
        worldSaveDir = saveHandler.getWorldDirectory();
        CelestialAssetStore.clear();
        LogisticStore.clearDeliveries();
        loadAll();
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        if (worldSaveDir == null) return;
        saveAll();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        if (worldSaveDir != null) saveAll();
        CelestialAssetStore.clear();
        LogisticStore.clearDeliveries();
        worldSaveDir = null;
    }

    private void loadAll() {
        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        if (!galaxiaRoot.exists()) {
            LOG.info("[PERSIST] LOAD START: no galaxiadata dir, skipping load");
            return;
        }
        LOG.info("[PERSIST] LOAD START: reading from {}", galaxiaRoot);
        loadAssets(new File(galaxiaRoot, ASSETS_FILE));
        loadTasks(new File(galaxiaRoot, TASKS_FILE));
    }

    private void saveAll() {
        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        galaxiaRoot.mkdirs();
        LOG.info("[PERSIST] SAVE START: writing to {}", galaxiaRoot);
        saveAssets(new File(galaxiaRoot, ASSETS_FILE));
        saveTasks(new File(galaxiaRoot, TASKS_FILE));
    }

    private void loadAssets(File file) {
        if (!file.exists()) {
            LOG.info("[PERSIST] LOAD: no file at {}, skipping", file);
            return;
        }
        List<AssetJson> list;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<AssetJson>>() {}.getType();
            list = gson.fromJson(reader, listType);
        } catch (IOException | JsonParseException e) {
            LOG.error("[PERSIST] LOAD FAILED: read error {}: {}", file, e.getMessage());
            return;
        }
        if (list == null) {
            LOG.warn("[PERSIST] LOAD: asset registry {} contained no asset list", file);
            return;
        }

        LOG.info("[PERSIST] LOAD: found {} asset(s) in JSON", list.size());
        int loadedCount = 0;
        int skippedCount = 0;
        for (AssetJson json : list) {
            try {
                CelestialAsset asset = decodeAsset(json);
                if (asset == null) {
                    skippedCount++;
                    LOG.warn("[PERSIST] LOAD: skipping malformed asset entry in {}", file);
                    continue;
                }
                UUID teamId = UUID.fromString(json.teamId);
                int moduleCount = (json.facility != null && json.facility.modules != null)
                    ? json.facility.modules.size()
                    : 0;
                int tileCount = (json.facility != null && json.facility.layoutTiles != null)
                    ? json.facility.layoutTiles.size()
                    : 0;
                LOG.info(
                    "[PERSIST] LOAD: decoding asset {} kind={} status={} with {} module(s), {} layout tile(s)",
                    json.assetId,
                    json.kind,
                    json.status,
                    moduleCount,
                    tileCount);
                decodeFacilityState(asset, json.facility);
                CelestialAssetStore.add(teamId, asset);
                loadedCount++;
            } catch (RuntimeException e) {
                skippedCount++;
                LOG.error("[PERSIST] LOAD FAILED: skipping asset entry {}: {}", json.assetId, e.getMessage());
            }
        }
        LOG.info("[PERSIST] LOAD END: {} asset(s) loaded, {} skipped", loadedCount, skippedCount);
    }

    private static <T extends Enum<T>> T safeValueOf(Class<T> cls, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(cls, name);
        } catch (IllegalArgumentException e) {
            LOG.warn("[Logistics] Unknown enum value {} for {}", name, cls.getSimpleName());
            return null;
        }
    }

    private void saveAssets(File file) {
        List<AssetJson> list = new ArrayList<>();
        int totalAssets = 0;
        int totalModules = 0;
        int totalAnchors = 0;
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            totalAssets++;
            AssetJson json = encodeAsset(asset);
            CelestialAsset facility = CelestialAssetStore.findAsset(asset.assetId);
            if (facility instanceof AutomatedFacility o) {
                json.facility = encodeFacilityState(o);
                int mCount = json.facility.modules != null ? json.facility.modules.size() : 0;
                int tCount = json.facility.layoutTiles != null ? json.facility.layoutTiles.size() : 0;
                totalModules += mCount;
                totalAnchors += tCount;
                LOG.info(
                    "[PERSIST] SAVE: asset {} kind={} status={} -> {} module(s), {} anchor tile(s)",
                    asset.assetId,
                    asset.kind,
                    asset.status(),
                    mCount,
                    tCount);
            } else {
                LOG.info(
                    "[PERSIST] SAVE: asset {} kind={} status={} (non-facility, no modules)",
                    asset.assetId,
                    asset.kind,
                    asset.status());
            }
            list.add(json);
        }
        LOG.info(
            "[PERSIST] SAVE: {} asset(s) total, {} modules, {} anchor tiles across all assets",
            totalAssets,
            totalModules,
            totalAnchors);
        writeJson(file, list);
    }

    private void loadTasks(File file) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<TaskJson>>() {}.getType();
            List<TaskJson> list = gson.fromJson(reader, listType);
            if (list == null) return;
            List<LogisticsDelivery> tasks = LogisticStore.activeDeliveries();
            for (TaskJson tj : list) {
                ItemStackWrapper resource = ItemStackWrapper.fromKey(tj.resourceId);
                if (resource != null) {
                    tasks.add(
                        LogisticsDelivery.createWithTrajectory(
                            LogisticsDelivery.ID.from(tj.taskId),
                            CelestialAsset.ID.from(tj.fromAssetId),
                            CelestialAsset.ID.from(tj.toAssetId),
                            resource,
                            tj.amount,
                            tj.remainingTicks,
                            LogisticSignal.Scope.valueOf(tj.transportKind),
                            CelestialObjectId.valueOf(tj.fromBodyId),
                            CelestialObjectId.valueOf(tj.toBodyId),
                            tj.departureOrbitalTime,
                            tj.tofOrbitalSeconds));
                }
            }
        } catch (IOException | JsonParseException e) {
            LOG.error("[Logistics] Failed to load tasks from {}: {}", file, e.getMessage());
        }
    }

    private void saveTasks(File file) {
        List<TaskJson> list = new ArrayList<>();
        for (LogisticsDelivery delivery : LogisticStore.activeDeliveries()) {
            TaskJson tj = new TaskJson();
            tj.taskId = String.valueOf(delivery.deliveryId);
            tj.fromAssetId = String.valueOf(delivery.data.fromAssetId());
            tj.toAssetId = String.valueOf(delivery.data.toAssetId());
            tj.resourceId = delivery.data.resourceId()
                .toKey();
            tj.amount = delivery.data.amount();
            tj.remainingTicks = delivery.getRemainingTicks();
            tj.transportKind = String.valueOf(delivery.data.scope());
            tj.fromBodyId = String.valueOf(delivery.data.fromBodyId());
            tj.toBodyId = String.valueOf(delivery.data.toBodyId());
            tj.departureOrbitalTime = delivery.data.departureOrbitalTime();
            tj.tofOrbitalSeconds = delivery.data.tofOrbitalSeconds();
            list.add(tj);
        }
        writeJson(file, list);
    }

    private void writeJson(File file, Object value) {
        File tmp = new File(file.getParent(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) {
            gson.toJson(value, writer);
        } catch (IOException e) {
            LOG.error("[Logistics] Failed to write {}: {}", file, e.getMessage());
            tmp.delete();
            return;
        }
        try {
            java.nio.file.Files.move(
                tmp.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                java.nio.file.Files
                    .move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOG.error("[Logistics] Failed to replace {} with {}: {}", file, tmp, e2.getMessage());
            }
        } catch (IOException e) {
            LOG.error("[Logistics] Failed to replace {} with {}: {}", file, tmp, e.getMessage());
        }
    }

    private AssetJson encodeAsset(CelestialAsset asset) {
        AssetJson json = new AssetJson();
        json.teamId = String.valueOf(CelestialAssetStore.getTeamId(asset.assetId));
        json.assetId = asset.assetId;
        json.celestialObjectId = asset.celestialObjectId.toString();
        json.displayName = asset.displayName();
        json.kind = asset.kind.name();
        json.location = asset.location.name();
        json.status = asset.status()
            .name();
        json.requiredResources = encodeRequirements(asset.requiredResources());
        json.constructionInventory = encodeRequirements(asset.constructionInventory());
        return json;
    }

    private CelestialAsset decodeAsset(AssetJson json) {
        if (json == null || json.teamId == null
            || json.assetId == null
            || json.celestialObjectId == null
            || json.kind == null
            || json.location == null
            || json.status == null) {
            return null;
        }
        CelestialObjectId objectId = CelestialObjectId.fromString(json.celestialObjectId);
        if (objectId == null) return null;
        CelestialAsset.Kind kind = safeValueOf(CelestialAsset.Kind.class, json.kind);
        Buildable.Status status = safeValueOf(Buildable.Status.class, json.status);
        if (kind == null || status == null) return null;
        CelestialAsset asset = CelestialAsset.create(json.assetId, objectId, kind, status);
        asset.setConstructionInventory(decodeRequirements(json.constructionInventory));
        asset.setDisplayName(json.displayName);
        return asset;
    }

    FacilityStateJson encodeFacilityState(AutomatedFacility state) {
        FacilityStateJson out = new FacilityStateJson();
        out.celestialBodyId = String.valueOf(state.celestialObjectId);
        out.systemId = String.valueOf(state.systemId);
        out.planetaryAnchorBodyId = String.valueOf(state.planetaryAnchorBodyId);
        out.energyStored = state.getEnergyStored();
        out.settingsGroupsNextId = state.settingsGroups()
            .nextGroupId();
        out.modules = new ArrayList<>();
        int moduleCount = 0;
        for (ModuleInstance m : state.modules()) {
            moduleCount++;
            ModuleJson mj = new ModuleJson();
            mj.moduleId = m.id.toString();
            mj.kind = m.kind()
                .name();
            mj.status = m.status()
                .name();
            mj.constructionProgress = 0f;
            mj.cooldownTicks = m.cooldownTicks();
            mj.tier = PacketUtil.enumOrdinal(m.tier());
            mj.priorityOverride = PacketUtil.enumOrdinal(m.priorityOverride());
            mj.enabled = m.enabled();
            mj.groupId = m.groupId();
            mj.shape = PacketUtil.enumOrdinal(m.shape());
            mj.parallel = m.component() instanceof IParallelModule pm ? pm.getParallel() : 1;
            JsonObject moduleData = new JsonObject();
            if (m.component() instanceof ModuleMiner miner) {
                moduleData.add("blacklistedItemKeys", PURE_GSON.toJsonTree(miner.blacklistedItemKeys()));
                moduleData.addProperty("copySettingsToOtherMiners", miner.copySettingsToOtherMiners());
            } else if (m.component() instanceof ModuleHammer hammer) {
                moduleData.add("config", PURE_GSON.toJsonTree(hammer.config()));
                moduleData.add("routePriority", PURE_GSON.toJsonTree(hammer.routePriority()));
                moduleData.addProperty("planetaryHandling", hammer.planetaryHandling());
                moduleData.addProperty("crossPlanetaryCapability", hammer.crossPlanetaryCapability);
            }
            mj.data = moduleData;
            mj.consumedResources = new LinkedHashMap<>();
            for (Map.Entry<ItemStack, Long> e : m.getConstructionInventory()
                .entrySet()) {
                mj.consumedResources.put(
                    ItemStackWrapper.of(e.getKey())
                        .toKey(),
                    e.getValue());
            }
            out.modules.add(mj);
        }
        LOG.info("[PERSIST] SAVE ENCODE: facility {} has {} module(s) in state", state.assetId, moduleCount);

        out.buffer = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            out.buffer.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }
        out.logisticsConfig = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsConfigJson cj = new LogisticsConfigJson();
            cj.minReserve = e.getValue()
                .minReserve();
            cj.orderSize = e.getValue()
                .orderSize();
            cj.isImportEnabled = e.getValue()
                .isImportEnabled();
            cj.isSupplyEnabled = e.getValue()
                .isSupplyEnabled();
            out.logisticsConfig.put(
                e.getKey()
                    .toKey(),
                cj);
        }
        out.layoutTiles = new ArrayList<>();
        StationLayout layout = state.stationLayout();
        int anchorCount = 0;
        if (layout != null) {
            for (Map.Entry<StationTileCoord, PlacedTile> entry : layout.snapshot()
                .entrySet()) {
                StationTileCoord coord = entry.getKey();
                // Save only anchor tiles — children are reconstructed on load
                if (!layout.isAnchorAt(coord)) continue;
                anchorCount++;
                StationTileJson tileJson = new StationTileJson();
                tileJson.dx = coord.dx();
                tileJson.dy = coord.dy();
                tileJson.state = entry.getValue()
                    .state()
                    .name();
                ModuleInstance module = entry.getValue()
                    .module();
                tileJson.moduleId = module == null ? null : module.id.toString();
                out.layoutTiles.add(tileJson);
            }
            LOG.info(
                "[PERSIST] SAVE ENCODE: facility {} layout has {} anchor tile(s) out of {} total tiles",
                state.assetId,
                anchorCount,
                layout.size());
        } else {
            LOG.info("[PERSIST] SAVE ENCODE: facility {} has no layout", state.assetId);
        }
        return out;
    }

    AutomatedFacility decodeFacilityState(CelestialAsset asset, FacilityStateJson json) {
        if (asset == null || json == null || json.systemId == null) return null;
        if (!(asset instanceof AutomatedFacility state)) return null;
        state.setEnergyStored(json.energyStored);
        state.settingsGroups()
            .setNextGroupId(json.settingsGroupsNextId);

        List<PendingTierDowngrade> pendingDowngrades = new ArrayList<>();

        int moduleDecodedCount = 0;
        int moduleSkippedCount = 0;
        if (json.modules != null) {
            for (ModuleJson mj : json.modules) {
                String rawKind = mj.kind;
                FacilityModuleKind kind = safeValueOf(FacilityModuleKind.class, rawKind);
                if (kind == null) {
                    if ("BIG_HAMMER".equals(rawKind)) {
                        kind = FacilityModuleKind.HAMMER;
                        LOG.info("[PERSIST] LOAD DECODE: migrated BIG_HAMMER module {} -> HAMMER", mj.moduleId);
                    } else {
                        moduleSkippedCount++;
                        LOG.warn(
                            "[PERSIST] LOAD DECODE: skipping module {} with unknown kind '{}'",
                            mj.moduleId,
                            rawKind);
                        continue;
                    }
                }
                ModuleInstance.ID moduleId = ModuleInstance.ID.from(mj.moduleId);
                if (moduleId == null && mj.moduleId != null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module from JSON has malformed ID: '" + mj.moduleId + "' of kind " + rawKind);
                }
                if (moduleId == null) {
                    LOG.error(
                        "[PERSIST] Module of kind {} has null/missing moduleId in JSON — generating new ID",
                        rawKind);
                    moduleId = ModuleInstance.ID.create();
                }
                ModuleShape shape = PacketUtil.enumFromByte(mj.shape, ModuleShape.class);
                if (shape == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + moduleId + " has invalid shape ordinal: " + mj.shape);
                }
                ModuleTier tier = PacketUtil.enumFromByte(mj.tier, ModuleTier.class);
                if (tier == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + moduleId + " has invalid tier ordinal: " + mj.tier);
                }
                ModuleTier originalTier = tier;
                if (!kind.allowedTiers()
                    .contains(tier)) {
                    LOG.info(
                        "[PERSIST] LOAD DECODE: module {} kind={} tier {} not allowed, downgrading to {}",
                        mj.moduleId,
                        kind,
                        tier,
                        kind.defaultTier());
                    tier = kind.defaultTier();
                }
                ModuleInstance module = FacilityModuleRegistry.create(moduleId, kind, null, shape, tier);
                if (module == null || module.component() == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Failed to create module " + kind + " (id=" + moduleId + "): component is null");
                }
                LOG.info(
                    "[PERSIST] LOAD DECODE: module {} kind={} shape={} tier={} status={} anchor=({},{})",
                    module.id,
                    kind,
                    shape,
                    tier,
                    mj.status,
                    (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                        .dx() : ModuleInstance.NULL_ANCHOR_LOG_VALUE),
                    (module.anchorOrNull() != null ? (int) module.anchorOrNull()
                        .dy() : ModuleInstance.NULL_ANCHOR_LOG_VALUE));
                if (originalTier != tier) {
                    pendingDowngrades.add(new PendingTierDowngrade(module, kind, originalTier, tier));
                }

                JsonObject data = mj.data != null ? mj.data.getAsJsonObject() : new JsonObject();

                switch (kind) {
                    case HAMMER -> {
                        AllowShootingConfig config = AllowShootingConfig.ALWAYS;
                        OrbitalTransferPlanner.RoutePriority routePriority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
                        boolean planetaryHandling = true;
                        boolean crossPlanetaryCapability = false;
                        if (data.has("config")) {
                            config = PURE_GSON.fromJson(data.get("config"), AllowShootingConfig.class);
                        }
                        if (data.has("routePriority")) {
                            routePriority = PURE_GSON
                                .fromJson(data.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class);
                        }
                        if (data.has("planetaryHandling")) {
                            planetaryHandling = data.get("planetaryHandling")
                                .getAsBoolean();
                        }
                        if (data.has("crossPlanetaryCapability")) {
                            crossPlanetaryCapability = data.get("crossPlanetaryCapability")
                                .getAsBoolean();
                        }
                        module.setComponent(
                            new ModuleHammer(
                                kind,
                                config,
                                routePriority,
                                false,
                                planetaryHandling,
                                crossPlanetaryCapability,
                                64));
                    }
                    case MINER -> {
                        List<String> blacklist = new ArrayList<>();
                        boolean copySettings = false;
                        if (data.has("blacklistedItemKeys")) {
                            blacklist = PURE_GSON.fromJson(
                                data.get("blacklistedItemKeys"),
                                new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
                        }
                        if (data.has("copySettingsToOtherMiners")) {
                            copySettings = data.get("copySettingsToOtherMiners")
                                .getAsBoolean();
                        }
                        module.setComponent(new ModuleMiner(kind, blacklist, copySettings));
                    }
                    case POWER -> {}
                    case STORAGE, TANK, BATTERY, MAINTENANCE_BAY -> {}
                }

                Buildable.Status moduleStatus = safeValueOf(Buildable.Status.class, mj.status);
                if (moduleStatus != null) {
                    module.updateStatus(moduleStatus);
                }
                module.setTicks(mj.cooldownTicks);
                module.setPriorityOverride(PacketUtil.enumFromByte(mj.priorityOverride, ModulePriority.class));
                module.setEnabled(mj.enabled);
                module.setGroupId(mj.groupId);
                if (module.component() instanceof IParallelModule pm) {
                    pm.setParallel(mj.parallel);
                }
                module.clearConsumedResources();
                if (mj.consumedResources != null) {
                    for (Map.Entry<String, Long> e : mj.consumedResources.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            module.getConstructionInventory()
                                .put(key.toStack(e.getValue()), e.getValue());
                        }
                    }
                }
                state.addModule(module);
                moduleDecodedCount++;
            }
        }
        LOG.info(
            "[PERSIST] LOAD DECODE: finished decoding modules: {} decoded, {} skipped",
            moduleDecodedCount,
            moduleSkippedCount);

        if (json.buffer != null) {
            Map<ItemStackWrapper, Long> bufferSnapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : json.buffer.entrySet()) {
                ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                if (key != null) {
                    bufferSnapshot.put(key, e.getValue());
                }
            }
            state.inventory.loadFromSnapshot(bufferSnapshot);
        }

        if (json.logisticsConfig != null) {
            Map<ItemStackWrapper, LogisticsResourceConfig> cfgSnapshot = new LinkedHashMap<>();
            for (Map.Entry<String, LogisticsConfigJson> e : json.logisticsConfig.entrySet()) {
                ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                if (key != null) {
                    LogisticsConfigJson cj = e.getValue();
                    cfgSnapshot.put(
                        key,
                        new LogisticsResourceConfig(
                            cj.minReserve,
                            cj.orderSize,
                            cj.isImportEnabled,
                            cj.isSupplyEnabled));
                }
            }
            state.logisticsConfig.loadFromSnapshot(cfgSnapshot);
        }

        StationLayout layout = state.stationLayout();
        int tilesLoaded = 0;
        int tilesSkipped = 0;
        if (layout != null && json.layoutTiles != null && !json.layoutTiles.isEmpty()) {
            Map<ModuleInstance.ID, ModuleInstance> modulesById = new LinkedHashMap<>();
            for (ModuleInstance m : state.modules()) {
                modulesById.put(m.id, m);
            }
            Map<StationTileCoord, PlacedTile> layoutSnapshot = new LinkedHashMap<>();
            for (StationTileJson tj : json.layoutTiles) {
                if (tj == null) continue;
                StationTileState tileState = safeValueOf(StationTileState.class, tj.state);
                if (tileState == null) continue;
                if (tj.dx < StationTileCoord.MIN || tj.dx > StationTileCoord.MAX
                    || tj.dy < StationTileCoord.MIN
                    || tj.dy > StationTileCoord.MAX) {
                    LOG.warn(
                        "[PERSIST] LOAD LAYOUT: skipping tile out of range: ({}, {}) state={}",
                        tj.dx,
                        tj.dy,
                        tj.state);
                    tilesSkipped++;
                    continue;
                }
                StationTileCoord coord = StationTileCoord.of(tj.dx, tj.dy);
                ModuleInstance module = tj.moduleId == null ? null
                    : modulesById.get(ModuleInstance.ID.from(tj.moduleId));
                if (tj.moduleId != null && module == null) {
                    LOG.info(
                        "[PERSIST] LOAD LAYOUT: skipping orphan tile ({},{}) for missing module {}",
                        (int) tj.dx,
                        (int) tj.dy,
                        tj.moduleId);
                    tilesSkipped++;
                    continue;
                }
                if (module != null) {
                    module.initAnchor(coord);
                }
                layoutSnapshot.put(coord, new PlacedTile(module, tileState));
                tilesLoaded++;
            }
            LOG.info(
                "[PERSIST] LOAD LAYOUT: {} tiles loaded, {} skipped (orphans/out-of-range)",
                tilesLoaded,
                tilesSkipped);
            layout.loadFromSnapshot(layoutSnapshot);
            // Fallback: find anchors for modules whose initAnchor wasn't called during tile loading.
            // Modules may have null anchors if the layout tile's moduleId lookup failed
            // (e.g. UUID format mismatch between JSON and deserialized module).
            int fallbackAnchors = 0;
            for (ModuleInstance m : state.modules()) {
                if (m.anchorOrNull() != null) continue;
                for (Map.Entry<StationTileCoord, PlacedTile> entry : layout.snapshot()
                    .entrySet()) {
                    PlacedTile tile = entry.getValue();
                    if (tile.module() != null && tile.module().id.equals(m.id)) {
                        StationTileCoord coord = entry.getKey();
                        m.initAnchor(coord);
                        LOG.info(
                            "[PERSIST] LOAD LAYOUT: fallback initAnchor for {} id={} at ({},{})",
                            m.kind(),
                            m.id,
                            (int) coord.dx(),
                            (int) coord.dy());
                        fallbackAnchors++;
                        break;
                    }
                }
            }
            if (fallbackAnchors > 0) {
                LOG.warn(
                    "[PERSIST] LOAD LAYOUT: {} module(s) required fallback anchor initialization",
                    fallbackAnchors);
            }
            // Expand each module's full footprint — place() populates child tiles
            int expandedCount = 0;
            for (ModuleInstance m : state.modules()) {
                if (m.anchorOrNull() != null) {
                    layout.place(m);
                    expandedCount++;
                }
            }
            LOG.info(
                "[PERSIST] LOAD LAYOUT: expanded {} module(s) with anchor, layout now has {} tile(s)",
                expandedCount,
                layout.size());
        } else {
            LOG.info(
                "[PERSIST] LOAD LAYOUT: no layout tiles in JSON or no layout (tiles={})",
                json.layoutTiles != null ? json.layoutTiles.size() : 0);
        }

        // Emit deferred tier-downgrade WARN logs
        for (PendingTierDowngrade p : pendingDowngrades) {
            StationTileCoord anchor = p.module.anchorOrNull();
            LOG.warn(
                "[PERSIST] Module {} at {} had unsupported tier {}; downgraded to {}",
                p.kind,
                anchor != null ? anchor : p.module.id,
                p.oldTier,
                p.newTier);
        }

        LOG.info(
            "[PERSIST] LOAD DECODE END: facility {} has {} module(s), layout has {} tile(s)",
            state.assetId,
            state.modules()
                .size(),
            layout != null ? layout.size() : 0);
        return state;
    }

    private static Map<String, Long> encodeRequirements(Map<ItemStack, Long> requirements) {
        Map<String, Long> encoded = new LinkedHashMap<>();
        if (requirements == null) return encoded;
        for (Map.Entry<ItemStack, Long> entry : requirements.entrySet()) {
            ItemStack stack = entry.getKey();
            if (stack == null) continue;
            ItemStackWrapper key = ItemStackWrapper.of(stack);
            if (key == null) continue;
            encoded.put(key.toKey(), entry.getValue());
        }
        return encoded;
    }

    private static Map<ItemStack, Long> decodeRequirements(Map<String, Long> encoded) {
        Map<ItemStack, Long> requirements = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return requirements;
        for (Map.Entry<String, Long> entry : encoded.entrySet()) {
            ItemStackWrapper key = ItemStackWrapper.fromKey(entry.getKey());
            if (key == null) continue;
            requirements.put(key.toStack(1), entry.getValue());
        }
        return requirements;
    }

    static final class AssetJson {

        CelestialAsset.ID assetId;
        String teamId;
        String celestialObjectId;
        String displayName;
        String kind;
        String location;
        String status;
        Map<String, Long> requiredResources;
        Map<String, Long> constructionInventory;
        FacilityStateJson facility;
    }

    static final class FacilityStateJson {

        String celestialBodyId;
        String systemId;
        String planetaryAnchorBodyId;
        long energyStored;
        short settingsGroupsNextId;
        List<ModuleJson> modules;
        Map<String, Long> buffer;
        Map<String, LogisticsConfigJson> logisticsConfig;
        List<StationTileJson> layoutTiles;
    }

    static final class StationTileJson {

        int dx;
        int dy;
        String state;
        String moduleId;
    }

    static final class ModuleJson {

        String moduleId;
        String kind;
        String status;
        float constructionProgress;
        int cooldownTicks;
        byte tier;
        byte priorityOverride;
        boolean enabled;
        short groupId;
        byte shape;
        byte parallel;
        JsonElement data;
        Map<String, Long> consumedResources;
    }

    static final class LogisticsConfigJson {

        int minReserve;
        int orderSize;
        boolean isImportEnabled;
        boolean isSupplyEnabled;
    }

    private record PendingTierDowngrade(ModuleInstance module, FacilityModuleKind kind, ModuleTier oldTier,
        ModuleTier newTier) {}

    static final class TaskJson {

        String taskId;
        String fromAssetId;
        String toAssetId;
        String resourceId;
        long amount;
        int remainingTicks;
        String transportKind;
        String fromBodyId;
        String toBodyId;
        double departureOrbitalTime;
        double tofOrbitalSeconds;
    }
}
