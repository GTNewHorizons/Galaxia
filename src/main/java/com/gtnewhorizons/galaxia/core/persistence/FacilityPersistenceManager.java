package com.gtnewhorizons.galaxia.core.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.Station;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerTrajectoryLoadTracker;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import sun.misc.Unsafe;

public final class FacilityPersistenceManager {

    private static final Logger LOG = LogManager.getLogger(FacilityPersistenceManager.class);

    private static final String DATA_DIR = "galaxiadata";
    private static final String ASSETS_FILE = "_assets.json";
    private static final String TASKS_FILE = "_tasks.json";

    private final Gson gson;
    private static final Gson PURE_GSON = new GsonBuilder().create();
    private File worldSaveDir;

    private static final String INVENTORY_KEY_ITEM_PREFIX = "I";
    private static final String INVENTORY_KEY_FLUID_PREFIX = "F";

    public FacilityPersistenceManager() {
        gson = new GsonBuilder().setPrettyPrinting()
            .serializeNulls()
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
        HammerTrajectoryLoadTracker.reset();
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
        HammerTrajectoryLoadTracker.reset();
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
            throw new IllegalStateException("[PERSIST] LOAD FAILED: read error " + file + ": " + e.getMessage(), e);
        }
        if (list == null) {
            throw new IllegalStateException(
                "[PERSIST] LOAD FAILED: asset registry " + file + " contained no asset list");
        }

        LOG.info("[PERSIST] LOAD: found {} asset(s) in JSON", list.size());
        int loadedCount = 0;
        for (AssetJson json : list) {
            CelestialAsset asset = decodeAsset(json);
            if (asset == null) {
                throw new IllegalStateException("[PERSIST] LOAD FAILED: malformed asset entry in " + file);
            }
            UUID teamId = UUID.fromString(json.teamId);
            int moduleCount = (json.facility != null && json.facility.modules != null) ? json.facility.modules.size()
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
            CelestialAssetStore.registerAsset(teamId, asset);
            loadedCount++;
        }
        LOG.info("[PERSIST] LOAD END: {} asset(s) loaded", loadedCount);
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

    AssetJson encodeAsset(CelestialAsset asset) {
        AssetJson json = new AssetJson();
        json.teamId = String.valueOf(CelestialAssetStore.getTeamId(asset.assetId));
        json.assetId = asset.assetId;
        json.celestialObjectId = String.valueOf(asset.celestialObjectId);
        json.displayName = asset.displayName();
        json.kind = asset.kind.name();
        json.location = asset.location.name();
        json.status = asset.status()
            .name();
        json.requiredResources = encodeRequirements(asset.requiredResources());
        json.constructionInventory = encodeRequirements(asset.constructionInventory());
        if (asset instanceof Station station && station.getController() != null) {
            json.controllerX = station.getController()
                .x();
            json.controllerY = station.getController()
                .y();
            json.controllerZ = station.getController()
                .z();
        }

        json.itemsBounds = encodeBoundsMap(asset.getBounds(true));
        json.fluidsBounds = encodeBoundsMap(asset.getBounds(false));
        json.logisticsConfig = new LinkedHashMap<>();
        for (Map.Entry<InventoryKey, LogisticsResourceConfig> e : asset.logisticsConfig.snapshot()
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
            json.logisticsConfig.put(
                (e.getKey()
                    .isItem() ? INVENTORY_KEY_ITEM_PREFIX : INVENTORY_KEY_FLUID_PREFIX) + e.getKey()
                        .toKey(),
                cj);
        }
        if (asset instanceof AutomatedFacility af) {
            json.filters = new LinkedHashMap<>(af.filtersSnapshot());
        }

        return json;
    }

    CelestialAsset decodeAsset(AssetJson json) {
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
        if (asset instanceof Station station && json.controllerX != null
            && json.controllerY != null
            && json.controllerZ != null) {
            station.setController(new BlockPos(json.controllerX, json.controllerY, json.controllerZ));
        }

        if (json.itemsBounds != null) {
            var boundsMap = decodeBoundsMap(json.itemsBounds, true);
            for (var bound : boundsMap.entrySet()) {
                asset.setBound(
                    bound.getKey(),
                    bound.getValue()
                        .low(),
                    bound.getValue()
                        .upper());
            }
        }
        if (json.fluidsBounds != null) {
            var boundsMap = decodeBoundsMap(json.fluidsBounds, false);
            for (var bound : boundsMap.entrySet()) {
                asset.setBound(
                    bound.getKey(),
                    bound.getValue()
                        .low(),
                    bound.getValue()
                        .upper());
            }
        }
        if (json.logisticsConfig != null) {
            Map<InventoryKey, LogisticsResourceConfig> cfgSnapshot = new LinkedHashMap<>();
            for (Map.Entry<String, LogisticsConfigJson> e : json.logisticsConfig.entrySet()) {
                InventoryKey key = e.getKey()
                    .startsWith(INVENTORY_KEY_ITEM_PREFIX)
                        ? ItemStackWrapper.fromKey(
                            e.getKey()
                                .substring(1))
                        : FluidKey.fromName(
                            e.getKey()
                                .substring(1));
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
            asset.logisticsConfig.loadFromSnapshot(cfgSnapshot);
        }

        if (json.filters != null && asset instanceof AutomatedFacility af) {
            for (Map.Entry<Boolean, List<String>> e : json.filters.entrySet()) {
                af.setFilters(e.getValue(), e.getKey());
            }
        }

        return asset;
    }

    FacilityStateJson encodeFacilityState(AutomatedFacility state) {
        FacilityStateJson out = new FacilityStateJson();
        out.energyStored = state.getEnergyStored();
        out.stationFeatureSalt = state.stationFeatureSalt();
        state.syncRecipeSettingsGroupsFromModules();
        out.settingsGroupsNextId = state.settingsGroups()
            .nextGroupId();
        out.settingsGroups = new ArrayList<>();
        state.settingsGroups()
            .groups()
            .values()
            .stream()
            .sorted(java.util.Comparator.comparingInt(SettingsGroup::id))
            .forEach(group -> out.settingsGroups.add(encodeSettingsGroup(group)));
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
            mj.moduleOperation = encodeModuleOperation(m.kind(), m.operationOrNull());
            JsonObject moduleData = new JsonObject();
            if (m.component() instanceof ModuleHammer hammer) {
                moduleData.add("config", PURE_GSON.toJsonTree(hammer.config()));
                moduleData.add("routePriority", PURE_GSON.toJsonTree(hammer.routePriority()));
                moduleData.addProperty(
                    "variant",
                    hammer.variant()
                        .name());
                moduleData.addProperty("energyStored", hammer.energyStored());
                moduleData.addProperty("shotCooldownTicks", hammer.shotCooldownTicks());
                moduleData.addProperty("routeProbeCooldownTicks", hammer.routeProbeCooldownTicks());
            } else if (m.component() instanceof ModuleMiner miner) {
                moduleData.addProperty(
                    "focusTier",
                    miner.focusTier()
                        .name());
                String focusOreKey = miner.focusOreKeyOrNull();
                moduleData
                    .add("focusOreKey", focusOreKey == null ? JsonNull.INSTANCE : PURE_GSON.toJsonTree(focusOreKey));
                moduleData.addProperty("focusAlignmentProgress", miner.focusAlignmentProgress());
            } else if (m.component() instanceof IRecipeModule recipeModule) {
                RecipeConfig rc = recipeModule.getRecipeConfig();
                if (rc != null) {
                    encodeRecipeConfig(moduleData, rc);
                }
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
        for (Map.Entry<ItemStackWrapper, Long> e : state.itemSnapshot()
            .entrySet()) {
            out.buffer.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }
        out.fluidBuffer = toFluidBuffer(state);

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
        if (asset == null || json == null || asset.systemId == null) return null;
        if (!(asset instanceof AutomatedFacility state)) return null;
        state.setEnergyStored(json.energyStored);
        state.setStationFeatureSalt(json.stationFeatureSalt);
        state.settingsGroups()
            .clear();
        state.settingsGroups()
            .setNextGroupId(json.settingsGroupsNextId);
        List<SettingsGroupJson> settingsGroups = Objects
            .requireNonNull(json.settingsGroups, "[PERSIST] Facility missing settingsGroups");
        for (SettingsGroupJson groupJson : settingsGroups) {
            FacilityModuleKind groupKind = Objects.requireNonNull(
                safeValueOf(FacilityModuleKind.class, groupJson.kind),
                "[PERSIST] Settings group " + groupJson.id + " has invalid kind: " + groupJson.kind);
            state.settingsGroups()
                .restore(
                    groupJson.id,
                    groupKind,
                    groupJson.displayName,
                    groupJson.joinable,
                    decodeSettingsGroupSettings(groupJson));
        }

        int moduleDecodedCount = 0;
        if (json.modules != null) {
            for (ModuleJson mj : json.modules) {
                String rawKind = mj.kind;
                FacilityModuleKind kind = safeValueOf(FacilityModuleKind.class, rawKind);
                if (kind == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + mj.moduleId + " has unknown kind: '" + rawKind + "'");
                }
                ModuleInstance.ID moduleId = ModuleInstance.ID.from(mj.moduleId);
                if (moduleId == null && mj.moduleId != null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module from JSON has malformed ID: '" + mj.moduleId + "' of kind " + rawKind);
                }
                if (moduleId == null) {
                    throw new IllegalStateException(
                        "[PERSIST] Module of kind " + rawKind + " has null/missing moduleId");
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
                if (!kind.allowedTiers()
                    .contains(tier)) {
                    throw new IllegalStateException(
                        "[PERSIST] Module " + moduleId + " kind=" + kind + " has unsupported tier: " + tier);
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
                JsonObject data = mj.data != null ? mj.data.getAsJsonObject() : null;
                module.setGroupId(mj.groupId);

                switch (kind) {
                    case HAMMER -> {
                        JsonObject hammerData = Objects.requireNonNull(data, "[PERSIST] Hammer module missing data");
                        AllowShootingConfig config = Objects.requireNonNull(
                            PURE_GSON.fromJson(hammerData.get("config"), AllowShootingConfig.class),
                            "[PERSIST] Hammer module missing config");
                        OrbitalTransferPlanner.RoutePriority routePriority = Objects.requireNonNull(
                            PURE_GSON
                                .fromJson(hammerData.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class),
                            "[PERSIST] Hammer module missing routePriority");
                        HammerVariant variant = Objects.requireNonNull(
                            PURE_GSON.fromJson(hammerData.get("variant"), HammerVariant.class),
                            "[PERSIST] Hammer module missing variant");
                        long energyStored = Objects
                            .requireNonNull(
                                hammerData.get("energyStored"),
                                "[PERSIST] Hammer module missing energyStored")
                            .getAsLong();
                        ModuleHammer.requireTier(variant, tier);
                        ModuleHammer hammer = new ModuleHammer(kind, config, routePriority, variant, 64, energyStored);
                        hammer.setDispatchCooldowns(
                            optionalInt(hammerData, "shotCooldownTicks", 0),
                            optionalInt(hammerData, "routeProbeCooldownTicks", 0));
                        module.setComponent(hammer);
                    }
                    case MINER -> {
                        if (!(module.component() instanceof ModuleMiner miner)) {
                            throw new IllegalStateException(
                                "[PERSIST] Miner module " + moduleId + " has non-miner data");
                        }
                        if (module.groupId() == 0) {
                            throw new IllegalStateException(
                                "[PERSIST] Miner module " + moduleId + " malformed: has no settings group");
                        }
                        decodeMinerSettings(module, miner, data);
                    }
                    case POWER, GEOTHERMAL_GENERATOR -> {}
                    case STORAGE, TANK, BATTERY, MAINTENANCE_BAY -> {}
                    case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> {
                        if (data.has("recipeMode")) {
                            RecipeConfig rc = decodeRecipeConfig(data);
                            if (rc != null && module.component() instanceof IRecipeModule rm) {
                                rm.setRecipeConfig(rc);
                            }
                        }
                    }
                }

                Buildable.Status moduleStatus = Objects.requireNonNull(
                    safeValueOf(Buildable.Status.class, mj.status),
                    "[PERSIST] Module " + moduleId + " has invalid status: " + mj.status);
                module.updateStatus(moduleStatus);
                module.setTicks(mj.cooldownTicks);
                module.setPriorityOverride(PacketUtil.enumFromByte(mj.priorityOverride, ModulePriority.class));
                module.setEnabled(mj.enabled);
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
                module.setOperation(decodeModuleOperation(mj.moduleOperation, module.id));
                state.addModule(module);
                moduleDecodedCount++;
            }
        }
        LOG.info("[PERSIST] LOAD DECODE: finished decoding modules: {} decoded", moduleDecodedCount);

        if (json.buffer != null) {
            Map<ItemStackWrapper, Long> bufferSnapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : json.buffer.entrySet()) {
                ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                if (key != null) {
                    bufferSnapshot.put(key, e.getValue());
                }
            }
            state.loadFromSnapshot(bufferSnapshot);
        }
        if (json.fluidBuffer != null) {
            state.loadFluidSnapshot(json.fluidBuffer);
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

        for (ModuleInstance module : state.modules()) {
            if (module.groupId() != 0) {
                SettingsGroup group = state.settingsGroups()
                    .require(module.groupId());
                if (!group.members()
                    .contains(module.anchor())) {
                    state.settingsGroups()
                        .addMember(module.groupId(), module.anchor());
                }
            }
        }
        for (SettingsGroup group : state.settingsGroups()
            .groups()
            .values()) {
            if (group.members()
                .isEmpty()) {
                throw new IllegalStateException("[PERSIST] Settings group " + group.id() + " has no member modules");
            }
        }
        state.applySettingsGroupsToModules();

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

    private static Map<String, Map.Entry<Long, Long>> encodeBoundsMap(Map<InventoryKey, InventoryBounds> amounts) {
        Map<String, Map.Entry<Long, Long>> encoded = new LinkedHashMap<>();
        if (amounts == null) return encoded;
        for (Map.Entry<InventoryKey, InventoryBounds> entry : amounts.entrySet()) {
            if (entry.getKey() == null) continue;
            encoded.put(
                entry.getKey()
                    .toKey(),
                Map.entry(
                    entry.getValue()
                        .low(),
                    entry.getValue()
                        .upper()));
        }
        return encoded;
    }

    private static Map<InventoryKey, InventoryBounds> decodeBoundsMap(Map<String, Map.Entry<Long, Long>> encoded,
        boolean items) {
        Map<InventoryKey, InventoryBounds> decoded = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) return decoded;
        for (Map.Entry<String, Map.Entry<Long, Long>> entry : encoded.entrySet()) {
            InventoryKey key = items ? ItemStackWrapper.fromKey(entry.getKey()) : FluidKey.fromName(entry.getKey());
            if (key == null) continue;
            decoded.put(
                key,
                new InventoryBounds(
                    entry.getValue()
                        .getKey(),
                    entry.getValue()
                        .getValue()));
        }
        return decoded;
    }

    private static Map<String, Long> toFluidBuffer(AutomatedFacility state) {
        return new LinkedHashMap<>(state.fluidSnapshot());
    }

    private static Map<String, Long> toFluidBounds(Map<FluidKey, Long> bounds) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<FluidKey, Long> e : bounds.entrySet()) {
            result.put(
                e.getKey()
                    .fluid()
                    .getName(),
                e.getValue());
        }
        return result;
    }

    static final class AssetJson {

        CelestialAsset.ID assetId;
        String teamId;
        String celestialObjectId;
        String systemId;
        String planetaryAnchorBodyId;
        String displayName;
        String kind;
        String location;
        String status;
        Map<String, Long> requiredResources;
        Map<String, Long> constructionInventory;
        FacilityStateJson facility;
        Integer controllerX;
        Integer controllerY;
        Integer controllerZ;
        Map<String, Map.Entry<Long, Long>> itemsBounds;
        Map<String, Map.Entry<Long, Long>> fluidsBounds;
        Map<String, LogisticsConfigJson> logisticsConfig;
        Map<Boolean, List<String>> filters;
    }

    static final class FacilityStateJson {

        long energyStored;
        long stationFeatureSalt;
        short settingsGroupsNextId;
        List<SettingsGroupJson> settingsGroups;
        List<ModuleJson> modules;
        Map<String, Long> buffer;
        Map<String, Long> fluidBuffer;
        List<StationTileJson> layoutTiles;
    }

    static final class StationTileJson {

        int dx;
        int dy;
        String state;
        String moduleId;
    }

    static final class SettingsGroupJson {

        short id;
        String kind;
        String displayName;
        boolean joinable;
        JsonObject data;
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
        ModuleOperationJson moduleOperation;
    }

    static final class ModuleOperationJson {

        String specType;
        String phase;
        String targetModuleKind;
        String targetTier;
        String targetVariantKey;
        String targetFocusTierKey;
        String targetFocusOreKey;
        int buildTicks;
        int completionRefundPercent;
        boolean reserveItems;
        boolean voidCompletionRefund;
        int elapsedBuildTicks;
        Map<String, Long> completionRefundCost;
        Map<String, Long> depositedResources;
        Map<String, Long> refundBuffer;
    }

    static final class LogisticsConfigJson {

        int minReserve;
        int orderSize;
        boolean isImportEnabled;
        boolean isSupplyEnabled;
    }

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

    private static void writeRecipeSnapshot(JsonObject slotObj, RecipeSnapshot snapshot) {
        slotObj.addProperty("duration", snapshot.duration());
        slotObj.addProperty("eut", snapshot.eut());
        writeItemStacks(slotObj, "inputs", snapshot.inputs());
        writeItemStacks(slotObj, "outputs", snapshot.outputs());
        writeIntArray(slotObj, "outputChances", snapshot.outputChances());
        writeFluidStacks(slotObj, "fluidInputs", snapshot.fluidInputs());
        writeFluidStacks(slotObj, "fluidOutputs", snapshot.fluidOutputs());
        writeIntArray(slotObj, "fluidOutputChances", snapshot.fluidOutputChances());
    }

    private static RecipeSnapshot readRecipeSnapshot(JsonObject slotObj, byte recipeMapOrdinal, int recipeIndex,
        long contentHash) {
        if (!slotObj.has("duration") && !slotObj.has("eut")
            && !slotObj.has("inputs")
            && !slotObj.has("outputs")
            && !slotObj.has("outputChances")
            && !slotObj.has("fluidInputs")
            && !slotObj.has("fluidOutputs")
            && !slotObj.has("fluidOutputChances")) {
            return RecipeSnapshot.unresolved(recipeMapOrdinal, recipeIndex, contentHash);
        }
        int duration = slotObj.has("duration") ? slotObj.get("duration")
            .getAsInt() : 0;
        int eut = slotObj.has("eut") ? slotObj.get("eut")
            .getAsInt() : 0;
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            readItemStacks(slotObj, "inputs"),
            readItemStacks(slotObj, "outputs"),
            readFluidStacks(slotObj, "fluidInputs"),
            readFluidStacks(slotObj, "fluidOutputs"),
            readIntArray(slotObj, "outputChances"),
            readIntArray(slotObj, "fluidOutputChances"),
            duration,
            eut);
    }

    private static void writeItemStacks(JsonObject target, String key, ItemStack[] stacks) {
        if (stacks == null) return;
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (ItemStack stack : stacks) {
            ItemStackWrapper wrapper = ItemStackWrapper.of(stack);
            if (wrapper == null) {
                array.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("key", wrapper.toKey());
            obj.addProperty("amount", stack.stackSize);
            array.add(obj);
        }
        target.add(key, array);
    }

    private static ItemStack[] readItemStacks(JsonObject source, String key) {
        if (!source.has(key)) return null;
        com.google.gson.JsonArray array = source.getAsJsonArray(key);
        ItemStack[] stacks = new ItemStack[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) continue;
            JsonObject obj = element.getAsJsonObject();
            ItemStackWrapper wrapper = ItemStackWrapper.fromKey(
                obj.get("key")
                    .getAsString());
            if (wrapper == null) continue;
            int amount = obj.has("amount") ? obj.get("amount")
                .getAsInt() : 1;
            stacks[i] = wrapper.toStack(amount);
        }
        return stacks;
    }

    private static int optionalInt(JsonObject source, String key, int fallback) {
        return source != null && source.has(key) ? source.get(key)
            .getAsInt() : fallback;
    }

    private static void writeIntArray(JsonObject target, String key, int[] values) {
        if (values == null) return;
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (int value : values) {
            array.add(new com.google.gson.JsonPrimitive(value));
        }
        target.add(key, array);
    }

    private static int[] readIntArray(JsonObject source, String key) {
        if (!source.has(key)) return null;
        com.google.gson.JsonArray array = source.getAsJsonArray(key);
        int[] values = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            values[i] = element != null && !element.isJsonNull() ? element.getAsInt() : 0;
        }
        return values;
    }

    private static void writeFluidStacks(JsonObject target, String key, FluidStack[] stacks) {
        if (stacks == null) return;
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (FluidStack stack : stacks) {
            String fluidName = fluidName(stack);
            if (fluidName == null) {
                array.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("fluid", fluidName);
            obj.addProperty("amount", stack.amount);
            array.add(obj);
        }
        target.add(key, array);
    }

    private static FluidStack[] readFluidStacks(JsonObject source, String key) {
        if (!source.has(key)) return null;
        com.google.gson.JsonArray array = source.getAsJsonArray(key);
        FluidStack[] stacks = new FluidStack[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element == null || element.isJsonNull()) continue;
            JsonObject obj = element.getAsJsonObject();
            Fluid fluid = resolveFluid(
                obj.get("fluid")
                    .getAsString());
            if (fluid == null) continue;
            int amount = obj.has("amount") ? obj.get("amount")
                .getAsInt() : 0;
            stacks[i] = createFluidStack(fluid, amount);
        }
        return stacks;
    }

    private static String fluidName(FluidStack stack) {
        if (stack == null) return null;
        Fluid fluid = fluidType(stack);
        return fluid != null ? fluid.getName() : null;
    }

    private static Fluid resolveFluid(String name) {
        try {
            Fluid fluid = FluidRegistry.getFluid(name);
            if (fluid != null) return fluid;
        } catch (Throwable ignored) {}
        return name != null && !name.isEmpty() ? new Fluid(name) : null;
    }

    private static FluidStack createFluidStack(Fluid fluid, int amount) {
        try {
            FluidStack stack = new FluidStack(fluid, amount);
            if (fluidType(stack) != null) return stack;
        } catch (Throwable ignored) {
            // Fall through to the reflective path below.
        }
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            FluidStack stack = (FluidStack) unsafe.allocateInstance(FluidStack.class);
            Field fluidField = FluidStack.class.getDeclaredField("fluid");
            fluidField.setAccessible(true);
            fluidField.set(stack, fluid);
            stack.amount = amount;
            return stack;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Fluid fluidType(FluidStack stack) {
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                Field field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

    private static ModuleOperationJson encodeModuleOperation(FacilityModuleKind moduleKind,
        ModuleOperationState operation) {
        if (operation == null) return null;
        ModuleOperationJson json = new ModuleOperationJson();
        ModuleOperationPlan plan = operation.plan();
        json.phase = operation.phase()
            .name();
        if (plan.spec() instanceof HammerModuleOperation hammerSpec) {
            json.specType = "HAMMER";
            json.targetModuleKind = FacilityModuleKind.HAMMER.name();
            json.targetTier = hammerSpec.targetTier()
                .name();
            json.targetVariantKey = hammerSpec.targetVariantKey();
        } else if (plan.spec() instanceof MinerFocusOperation minerSpec) {
            json.specType = "MINER_FOCUS";
            json.targetModuleKind = FacilityModuleKind.MINER.name();
            json.targetTier = minerSpec.targetTier()
                .name();
            json.targetFocusTierKey = minerSpec.targetFocusTierKey();
            json.targetFocusOreKey = minerSpec.targetFocusOreKey();
        } else if (plan.spec() instanceof ModuleTierOperation tierSpec) {
            json.specType = "MODULE_TIER";
            json.targetModuleKind = moduleKind.name();
            json.targetTier = tierSpec.targetTier()
                .name();
        }
        json.buildTicks = plan.buildTicks();
        json.completionRefundPercent = plan.completionRefundPercent();
        json.completionRefundCost = encodeOperationCost(plan.completionRefundCost());
        json.reserveItems = plan.reserveItems();
        json.voidCompletionRefund = plan.voidCompletionRefund();
        json.elapsedBuildTicks = operation.elapsedBuildTicks();
        json.depositedResources = new LinkedHashMap<>(operation.depositedResources());
        json.refundBuffer = new LinkedHashMap<>(operation.refundBuffer());
        return json;
    }

    private static ModuleOperationState decodeModuleOperation(ModuleOperationJson json, ModuleInstance.ID moduleId) {
        if (json == null) return null;
        ModuleOperationPhase phase = requireEnum(
            ModuleOperationPhase.class,
            json.phase,
            "[PERSIST] Module " + moduleId + " has invalid operation phase: " + json.phase);
        FacilityModuleKind regKind = json.targetModuleKind != null
            ? requireOptionalEnum(
                FacilityModuleKind.class,
                json.targetModuleKind,
                "[PERSIST] Module " + moduleId + " has invalid target kind: " + json.targetModuleKind)
            : null;
        ModuleTier targetTier = requireEnum(
            ModuleTier.class,
            json.targetTier,
            "[PERSIST] Module " + moduleId + " has invalid target tier: " + json.targetTier);
        FacilityModuleKind kindForLookup = regKind != null ? regKind : FacilityModuleKind.HAMMER;
        if (json.buildTicks <= 0) {
            throw new IllegalStateException(
                "[PERSIST] Module " + moduleId + " operation has invalid buildTicks: " + json.buildTicks);
        }
        IModuleOperation spec;
        if ("HAMMER".equals(json.specType)) {
            spec = new HammerModuleOperation(targetTier, json.targetVariantKey);
        } else if ("MINER_FOCUS".equals(json.specType)) {
            spec = new MinerFocusOperation(targetTier, json.targetFocusTierKey, json.targetFocusOreKey);
        } else if ("MODULE_TIER".equals(json.specType)) {
            if (regKind == null) {
                throw new IllegalStateException(
                    "[PERSIST] Module " + moduleId + " tier operation is missing target kind");
            }
            spec = new ModuleTierOperation(targetTier);
        } else {
            throw new IllegalStateException(
                "[PERSIST] Module " + moduleId + " has unknown spec type: " + json.specType);
        }
        Map<ItemStackWrapper, Long> cost = regKind != null ? FacilityModuleRegistry.operationCost(
            FacilityModuleRegistry.get(regKind)
                .getTierData(targetTier)
                .constructionCost())
            : Map.of();
        ModuleOperationPlan plan = new ModuleOperationPlan(
            spec,
            json.buildTicks,
            cost,
            requireOperationCost(json.completionRefundCost, "completionRefundCost", moduleId),
            json.completionRefundPercent,
            json.reserveItems,
            json.voidCompletionRefund);
        return ModuleOperationState.restore(
            plan,
            phase,
            json.elapsedBuildTicks,
            requireOperationAmounts(json.depositedResources, "depositedResources", moduleId),
            requireOperationAmounts(json.refundBuffer, "refundBuffer", moduleId));
    }

    private static Map<String, Long> encodeOperationCost(Map<ItemStackWrapper, Long> cost) {
        Map<String, Long> encoded = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> entry : cost.entrySet()) {
            encoded.merge(
                entry.getKey()
                    .toKey(),
                entry.getValue(),
                Long::sum);
        }
        return encoded;
    }

    private static Map<ItemStackWrapper, Long> requireOperationCost(Map<String, Long> amounts, String fieldName,
        ModuleInstance.ID moduleId) {
        if (amounts == null) {
            throw new IllegalStateException("[PERSIST] Module " + moduleId + " operation missing " + fieldName);
        }
        Map<ItemStackWrapper, Long> cost = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            ItemStackWrapper item = ItemStackWrapper.fromKey(entry.getKey());
            if (item == null) {
                throw new IllegalStateException(
                    "[PERSIST] Module " + moduleId
                        + " operation "
                        + fieldName
                        + " contains unresolvable item: "
                        + entry.getKey());
            }
            cost.merge(item, entry.getValue(), Long::sum);
        }
        return cost;
    }

    private static Map<String, Long> requireOperationAmounts(Map<String, Long> amounts, String fieldName,
        ModuleInstance.ID moduleId) {
        if (amounts == null) {
            throw new IllegalStateException("[PERSIST] Module " + moduleId + " operation missing " + fieldName);
        }
        return amounts;
    }

    private static <T extends Enum<T>> T requireEnum(Class<T> cls, String name, String message) {
        T value = safeValueOf(cls, name);
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static <T extends Enum<T>> T requireOptionalEnum(Class<T> cls, String name, String message) {
        if (name == null) return null;
        T value = safeValueOf(cls, name);
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static SettingsGroupJson encodeSettingsGroup(SettingsGroup group) {
        SettingsGroupJson json = new SettingsGroupJson();
        json.id = group.id();
        json.kind = group.kind()
            .name();
        json.displayName = group.displayName();
        json.joinable = group.isJoinable();
        json.data = encodeSettingsGroupSettings(group.settings());
        return json;
    }

    private static JsonObject encodeSettingsGroupSettings(ModuleSettings settings) {
        JsonObject data = new JsonObject();
        if (settings instanceof MinerSettings minerSettings) {
            data.add("minerSettings", PURE_GSON.toJsonTree(minerSettings));
            return data;
        }
        if (settings instanceof RecipeModuleSettings recipeSettings) {
            JsonObject recipeData = new JsonObject();
            if (recipeSettings.config() != null) {
                encodeRecipeConfig(recipeData, recipeSettings.config());
            }
            data.add("recipeSettings", recipeData);
            return data;
        }
        throw new IllegalStateException("[PERSIST] Unsupported settings group payload " + settings);
    }

    private static ModuleSettings decodeSettingsGroupSettings(SettingsGroupJson groupJson) {
        JsonObject data = Objects
            .requireNonNull(groupJson.data, "[PERSIST] Settings group " + groupJson.id + " missing data");
        FacilityModuleKind kind = Objects.requireNonNull(
            safeValueOf(FacilityModuleKind.class, groupJson.kind),
            "[PERSIST] Settings group " + groupJson.id + " has invalid kind: " + groupJson.kind);
        if (kind == FacilityModuleKind.MINER) {
            if (data.entrySet()
                .size() != 1 || !data.has("minerSettings")) {
                throw new IllegalStateException(
                    "[PERSIST] Miner settings group " + groupJson.id + " has malformed data");
            }
            JsonObject settingsData = data.getAsJsonObject("minerSettings");
            JsonElement keysElement = Objects.requireNonNull(
                settingsData.get("blacklistedOreKeys"),
                "[PERSIST] Miner settings group " + groupJson.id + " missing blacklistedOreKeys");
            Type keySetType = new TypeToken<Set<String>>() {}.getType();
            Set<String> keys = Objects.requireNonNull(
                PURE_GSON.fromJson(keysElement, keySetType),
                "[PERSIST] Miner settings group " + groupJson.id + " has null blacklistedOreKeys");
            return new MinerSettings(keys);
        }
        if (FacilityModuleRegistry.get(kind)
            .settingsGroups()) {
            if (data.entrySet()
                .size() != 1 || !data.has("recipeSettings")) {
                throw new IllegalStateException(
                    "[PERSIST] Recipe settings group " + groupJson.id + " has malformed data");
            }
            JsonObject recipeData = data.getAsJsonObject("recipeSettings");
            return new RecipeModuleSettings(recipeData.has("recipeMode") ? decodeRecipeConfig(recipeData) : null);
        }
        throw new IllegalStateException("[PERSIST] Unsupported settings group kind " + kind);
    }

    private static void encodeRecipeConfig(JsonObject data, RecipeConfig rc) {
        data.addProperty(
            "recipeMode",
            rc.mode()
                .name());
        data.addProperty(
            "recipeNotDoablePolicy",
            rc.notDoablePolicy()
                .name());
        data.addProperty("recipeOrderCursor", rc.orderCursor() & 0xFF);
        data.addProperty("recipeOrderRemaining", rc.orderRemaining() & 0xFF);
        com.google.gson.JsonArray slotsArray = new com.google.gson.JsonArray();
        for (int i = 0; i < SavedRecipeList.MAX_SAVED_RECIPES; i++) {
            SavedRecipe slot = rc.savedRecipes()
                .getOrNull(i);
            if (slot == null) continue;
            com.google.gson.JsonObject slotObj = new com.google.gson.JsonObject();
            slotObj.addProperty(
                "recipeMapOrdinal",
                slot.recipe()
                    .recipeMapOrdinal() & 0xFF);
            slotObj.addProperty(
                "recipeIndex",
                slot.recipe()
                    .recipeIndex());
            slotObj.addProperty(
                "contentHash",
                slot.recipe()
                    .contentHash());
            writeRecipeSnapshot(slotObj, slot.recipe());
            slotObj.addProperty("enabled", slot.enabled());
            slotObj.addProperty("requestAmount", slot.requestAmount());
            slotObj.addProperty("priority", slot.priority() & 0xFF);
            slotObj.addProperty("orderSize", slot.orderSize() & 0xFF);
            if (slot.displayName() != null && !slot.displayName()
                .isBlank()) {
                slotObj.addProperty("displayName", slot.displayName());
            }
            slotObj.addProperty("slotIndex", i);
            slotsArray.add(slotObj);
        }
        data.add("savedRecipes", slotsArray);
    }

    private static void decodeMinerSettings(ModuleInstance module, ModuleMiner miner, JsonObject data) {
        JsonObject minerData = Objects.requireNonNull(data, "[PERSIST] Miner module " + module.id + " missing data");
        if (minerData.entrySet()
            .size() != 3 || !minerData.has("focusTier")
            || !minerData.has("focusOreKey")
            || !minerData.has("focusAlignmentProgress")) {
            throw new IllegalStateException("[PERSIST] Miner module " + module.id + " has malformed settings data");
        }
        decodeMinerFocus(module, miner, minerData);
    }

    private static void decodeMinerFocus(ModuleInstance module, ModuleMiner miner, JsonObject minerData) {
        MinerFocusTier focusTier = requireEnum(
            MinerFocusTier.class,
            minerData.get("focusTier")
                .getAsString(),
            "[PERSIST] Miner module " + module.id + " has invalid focus tier");
        JsonElement focusOreElement = minerData.get("focusOreKey");
        String focusOreKey = focusOreElement == null || focusOreElement.isJsonNull() ? null
            : focusOreElement.getAsString();
        int focusAlignmentProgress = minerData.get("focusAlignmentProgress")
            .getAsInt();
        miner.setFocus(focusTier, focusOreKey, focusAlignmentProgress);
    }

    private static RecipeConfig decodeRecipeConfig(JsonObject data) {
        try {
            RecipeSchedulerMode mode = RecipeSchedulerMode.valueOf(
                data.get("recipeMode")
                    .getAsString());
            NotDoablePolicy policy = NotDoablePolicy.valueOf(
                data.get("recipeNotDoablePolicy")
                    .getAsString());
            byte orderCursor = data.get("recipeOrderCursor")
                .getAsByte();
            byte orderRemaining = data.get("recipeOrderRemaining")
                .getAsByte();
            SavedRecipeList slots = new SavedRecipeList();

            if (data.has("savedRecipes")) {
                com.google.gson.JsonArray slotsArray = data.getAsJsonArray("savedRecipes");
                for (int i = 0; i < slotsArray.size(); i++) {
                    JsonObject slotObj = slotsArray.get(i)
                        .getAsJsonObject();
                    byte recipeMapOrdinal = slotObj.get("recipeMapOrdinal")
                        .getAsByte();
                    int recipeIndex = slotObj.get("recipeIndex")
                        .getAsInt();
                    long contentHash = slotObj.get("contentHash")
                        .getAsLong();
                    boolean enabled = slotObj.get("enabled")
                        .getAsBoolean();
                    long requestAmount = slotObj.has("requestAmount") ? slotObj.get("requestAmount")
                        .getAsLong() : 0L;
                    byte priority = slotObj.get("priority")
                        .getAsByte();
                    byte orderSize = slotObj.get("orderSize")
                        .getAsByte();
                    RecipeSnapshot ref = readRecipeSnapshot(slotObj, recipeMapOrdinal, recipeIndex, contentHash);
                    String displayName = slotObj.has("displayName") ? slotObj.get("displayName")
                        .getAsString() : "";
                    SavedRecipe slot = new SavedRecipe(ref, enabled, requestAmount, priority, orderSize, displayName);
                    int slotIndex = slotObj.has("slotIndex") ? slotObj.get("slotIndex")
                        .getAsInt() : i;
                    slots.setOrAppend(slotIndex, slot);
                }
            }

            return new RecipeConfig(slots, mode, policy, orderCursor, orderRemaining);
        } catch (Exception e) {
            LOG.warn("[PERSIST] Failed to decode RecipeConfig: {}", e.getMessage());
            return null;
        }
    }
}
