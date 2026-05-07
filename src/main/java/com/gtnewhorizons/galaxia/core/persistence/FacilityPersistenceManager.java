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
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

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
        out.minerVoidChances = new LinkedHashMap<>(state.minerVoidChances());
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
            if (m.component() instanceof ModuleHammer hammer) {
                moduleData.add("config", PURE_GSON.toJsonTree(hammer.config()));
                moduleData.add("routePriority", PURE_GSON.toJsonTree(hammer.routePriority()));
                moduleData.addProperty(
                    "variant",
                    hammer.variant()
                        .name());
            } else if (m.component() instanceof IRecipeModule recipeModule) {
                RecipeConfig rc = recipeModule.getRecipeConfig();
                if (rc != null) {
                    moduleData.addProperty(
                        "recipeMode",
                        rc.mode()
                            .name());
                    moduleData.addProperty(
                        "recipeNotDoablePolicy",
                        rc.notDoablePolicy()
                            .name());
                    moduleData.addProperty("recipeOrderCursor", rc.orderCursor() & 0xFF);
                    moduleData.addProperty("recipeOrderRemaining", rc.orderRemaining() & 0xFF);
                    com.google.gson.JsonArray slotsArray = new com.google.gson.JsonArray();
                    for (int i = 0; i < RecipeSlotList.MAX_RECIPE_SLOTS; i++) {
                        RecipeSlot slot = rc.slots()
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
                        slotObj.addProperty("inputGuard", slot.inputGuard());
                        slotObj.addProperty("outputGuard", slot.outputGuard());
                        slotObj.addProperty("priority", slot.priority() & 0xFF);
                        slotObj.addProperty("orderSize", slot.orderSize() & 0xFF);
                        slotObj.addProperty("slotIndex", i);
                        slotsArray.add(slotObj);
                    }
                    moduleData.add("recipeSlots", slotsArray);
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
        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            out.buffer.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }
        out.fluidBuffer = new LinkedHashMap<>(state.inventory.fluidSnapshot());
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
        state.setMinerVoidChances(
            Objects.requireNonNull(json.minerVoidChances, "[PERSIST] Facility missing minerVoidChances"));

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
                        ModuleHammer.requireTier(variant, tier);
                        module.setComponent(new ModuleHammer(kind, config, routePriority, false, variant, 64));
                    }
                    case MINER -> {
                        if (data != null && !data.entrySet()
                            .isEmpty()) {
                            throw new IllegalStateException(
                                "[PERSIST] Miner module " + moduleId + " has obsolete data");
                        }
                    }
                    case POWER -> {}
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
        LOG.info("[PERSIST] LOAD DECODE: finished decoding modules: {} decoded", moduleDecodedCount);

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
        if (json.fluidBuffer != null) {
            state.inventory.loadFluidSnapshot(json.fluidBuffer);
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
        Map<String, Integer> minerVoidChances = new LinkedHashMap<>();
        List<ModuleJson> modules;
        Map<String, Long> buffer;
        Map<String, Long> fluidBuffer;
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
            RecipeSlotList slots = new RecipeSlotList();

            if (data.has("recipeSlots")) {
                com.google.gson.JsonArray slotsArray = data.getAsJsonArray("recipeSlots");
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
                    int inputGuard = slotObj.get("inputGuard")
                        .getAsInt();
                    int outputGuard = slotObj.get("outputGuard")
                        .getAsInt();
                    byte priority = slotObj.get("priority")
                        .getAsByte();
                    byte orderSize = slotObj.get("orderSize")
                        .getAsByte();
                    RecipeSnapshot ref = readRecipeSnapshot(slotObj, recipeMapOrdinal, recipeIndex, contentHash);
                    RecipeSlot slot = new RecipeSlot(ref, enabled, inputGuard, outputGuard, priority, orderSize);
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
