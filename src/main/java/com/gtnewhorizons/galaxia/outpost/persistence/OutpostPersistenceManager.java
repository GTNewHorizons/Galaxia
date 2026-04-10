package com.gtnewhorizons.galaxia.outpost.persistence;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.event.world.WorldEvent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.logistics.LogisticsTask;
import com.gtnewhorizons.galaxia.outpost.logistics.OutpostLogisticsEngine;
import com.gtnewhorizons.galaxia.outpost.module.BigHammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData;
import com.gtnewhorizons.galaxia.outpost.module.PowerModuleData;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Handles JSON persistence for station and outpost state.
 *
 * <p>
 * There is one canonical persisted station registry:
 *
 * <pre>
 * world/galaxiadata/
 *   _assets.json   <- all celestial assets, with embedded outpost state where applicable
 *   _tasks.json    <- global in-flight LogisticsTask list
 * </pre>
 *
 * <p>
 * {@link CelestialAssetStore} is restored from `_assets.json`.
 * {@link OutpostDataStore} is then reconstructed from the embedded outpost payloads
 * inside those same asset records. There is no second persisted outpost registry.
 */
public final class OutpostPersistenceManager {

    private static final String DATA_DIR = "galaxiadata";
    private static final String ASSETS_FILE = "_assets.json";
    private static final String TASKS_FILE = "_tasks.json";

    private final Gson gson;
    private File worldSaveDir;

    public OutpostPersistenceManager() {
        gson = new GsonBuilder().registerTypeHierarchyAdapter(OutpostModuleData.class, new OutpostModuleDataAdapter())
            .setPrettyPrinting()
            .create();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        ISaveHandler saveHandler = event.world.getSaveHandler();
        worldSaveDir = saveHandler.getWorldDirectory();
        CelestialAssetStore.clear();
        OutpostDataStore.get()
            .clear();
        OutpostLogisticsEngine.get()
            .activeTasksInternal()
            .clear();
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
        OutpostDataStore.get()
            .clear();
        OutpostLogisticsEngine.get()
            .activeTasksInternal()
            .clear();
        worldSaveDir = null;
    }

    private void loadAll() {
        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        if (!galaxiaRoot.exists()) return;
        loadAssets(new File(galaxiaRoot, ASSETS_FILE));
        loadTasks(new File(galaxiaRoot, TASKS_FILE));
    }

    private void saveAll() {
        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        galaxiaRoot.mkdirs();
        saveAssets(new File(galaxiaRoot, ASSETS_FILE));
        saveTasks(new File(galaxiaRoot, TASKS_FILE));
    }

    private void loadAssets(File file) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<AssetJson>>() {}.getType();
            List<AssetJson> list = gson.fromJson(reader, listType);
            if (list == null) return;

            List<CelestialManagedAsset> assets = new ArrayList<>();
            for (AssetJson json : list) {
                CelestialManagedAsset asset = decodeAsset(json);
                if (asset == null) continue;
                assets.add(asset);

                AutomatedOutpostState outpost = decodeOutpostState(asset, json.outpost);
                if (outpost != null) {
                    OutpostDataStore.get()
                        .put(outpost);
                }
            }
            CelestialAssetStore.loadAssets(assets);
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            Galaxia.LOG.error("[Logistics] Failed to load station registry from {}: {}", file, e.getMessage());
        }
    }

    private void saveAssets(File file) {
        List<AssetJson> list = new ArrayList<>();
        for (CelestialManagedAsset asset : CelestialAssetStore.allAssets()) {
            AssetJson json = encodeAsset(asset);
            AutomatedOutpostState outpost = OutpostDataStore.get()
                .getByAssetId(asset.assetId());
            if (outpost != null) {
                json.outpost = encodeOutpostState(outpost);
            }
            list.add(json);
        }
        writeJson(file, list);
    }

    private void loadTasks(File file) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<TaskJson>>() {}.getType();
            List<TaskJson> list = gson.fromJson(reader, listType);
            if (list == null) return;
            List<LogisticsTask> tasks = OutpostLogisticsEngine.get()
                .activeTasksInternal();
            for (TaskJson tj : list) {
                ItemStackWrapper resource = ItemStackWrapper.fromKey(tj.resourceId);
                if (resource != null) {
                    tasks.add(
                        new LogisticsTask(
                            tj.taskId,
                            tj.fromAssetId,
                            tj.toAssetId,
                            resource,
                            tj.amount,
                            tj.remainingTicks,
                            tj.transportKind,
                            tj.fromBodyId != null ? tj.fromBodyId : "",
                            tj.toBodyId != null ? tj.toBodyId : "",
                            tj.departureOrbitalTime,
                            tj.tofOrbitalSeconds));
                }
            }
        } catch (IOException | JsonParseException e) {
            Galaxia.LOG.error("[Logistics] Failed to load tasks from {}: {}", file, e.getMessage());
        }
    }

    private void saveTasks(File file) {
        List<TaskJson> list = new ArrayList<>();
        for (LogisticsTask task : OutpostLogisticsEngine.get()
            .activeTasksInternal()) {
            TaskJson tj = new TaskJson();
            tj.taskId = task.taskId();
            tj.fromAssetId = task.fromAssetId();
            tj.toAssetId = task.toAssetId();
            tj.resourceId = task.resourceId()
                .toKey();
            tj.amount = task.amount();
            tj.remainingTicks = task.remainingTicks();
            tj.transportKind = task.transportKind();
            tj.fromBodyId = task.fromBodyId();
            tj.toBodyId = task.toBodyId();
            tj.departureOrbitalTime = task.departureOrbitalTime();
            tj.tofOrbitalSeconds = task.tofOrbitalSeconds();
            list.add(tj);
        }
        writeJson(file, list);
    }

    private void writeJson(File file, Object value) {
        File tmp = new File(file.getParent(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) {
            gson.toJson(value, writer);
        } catch (IOException e) {
            Galaxia.LOG.error("[Logistics] Failed to write {}: {}", file, e.getMessage());
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
                Galaxia.LOG.error("[Logistics] Failed to replace {} with {}: {}", file, tmp, e2.getMessage());
            }
        } catch (IOException e) {
            Galaxia.LOG.error("[Logistics] Failed to replace {} with {}: {}", file, tmp, e.getMessage());
        }
    }

    private AssetJson encodeAsset(CelestialManagedAsset asset) {
        AssetJson json = new AssetJson();
        json.assetId = asset.assetId();
        json.celestialObjectId = asset.celestialObjectId();
        json.displayName = asset.displayName();
        json.kind = asset.kind()
            .name();
        json.location = asset.location()
            .name();
        json.status = asset.status()
            .name();
        json.requiredResources = encodeRequirements(asset.requiredResources());
        json.constructionInventory = encodeRequirements(asset.constructionInventory());
        return json;
    }

    private CelestialManagedAsset decodeAsset(AssetJson json) {
        if (json == null || json.assetId == null
            || json.celestialObjectId == null
            || json.kind == null
            || json.location == null
            || json.status == null) {
            return null;
        }
        return new CelestialManagedAsset(
            json.assetId,
            json.celestialObjectId,
            json.displayName == null ? json.assetId : json.displayName,
            CelestialAssetKind.valueOf(json.kind),
            CelestialAssetLocation.valueOf(json.location),
            CelestialAssetStatus.valueOf(json.status),
            decodeRequirements(json.requiredResources),
            decodeRequirements(json.constructionInventory));
    }

    private OutpostStateJson encodeOutpostState(AutomatedOutpostState state) {
        OutpostStateJson out = new OutpostStateJson();
        out.teamId = state.teamId.toString();
        out.celestialBodyId = state.celestialBodyId;
        out.systemId = state.systemId;
        out.energyStored = state.getEnergyStored();
        out.modules = new ArrayList<>();
        for (AutomatedOutpostModule m : state.modules()) {
            ModuleJson mj = new ModuleJson();
            mj.kind = m.kind.name();
            mj.status = m.getStatus()
                .name();
            mj.constructionProgress = m.getConstructionProgress();
            mj.cooldownTicks = m.cooldownTicks;
            mj.energyBuffer = m.energyBuffer;
            mj.data = gson.toJsonTree(m.getData());
            mj.consumedResources = new LinkedHashMap<>();
            for (Map.Entry<ItemStackWrapper, Integer> e : m.getConsumedResources()
                .entrySet()) {
                mj.consumedResources.put(
                    e.getKey()
                        .toKey(),
                    e.getValue());
            }
            out.modules.add(mj);
        }
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
        return out;
    }

    private AutomatedOutpostState decodeOutpostState(CelestialManagedAsset asset, OutpostStateJson json) {
        if (asset == null || json == null || json.teamId == null || json.systemId == null) return null;
        AutomatedOutpostState state = new AutomatedOutpostState(
            asset.assetId(),
            UUID.fromString(json.teamId),
            json.celestialBodyId != null ? json.celestialBodyId : asset.celestialObjectId(),
            json.systemId);
        state.setEnergyStored(json.energyStored);

        if (json.modules != null) {
            for (ModuleJson mj : json.modules) {
                OutpostModuleKind kind = OutpostModuleKind.valueOf(mj.kind);
                OutpostModuleData data = gson.fromJson(mj.data, OutpostModuleData.class);
                AutomatedOutpostModule module = new AutomatedOutpostModule(kind, data);
                if (mj.status != null) {
                    module.setStatus(AutomatedOutpostModule.Status.valueOf(mj.status));
                }
                module.setConstructionProgress(mj.constructionProgress);
                module.cooldownTicks = mj.cooldownTicks;
                module.energyBuffer = mj.energyBuffer;
                module.clearConsumedResources();
                if (mj.consumedResources != null) {
                    for (Map.Entry<String, Integer> e : mj.consumedResources.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            module.getConsumedResources()
                                .put(key, e.getValue());
                        }
                    }
                }
                state.addModule(module);
            }
        }

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

        return state;
    }

    private static Map<String, Long> encodeRequirements(List<CelestialAssetRequirement> requirements) {
        Map<String, Long> encoded = new LinkedHashMap<>();
        if (requirements == null) return encoded;
        for (CelestialAssetRequirement requirement : requirements) {
            if (requirement == null || requirement.stack() == null) continue;
            ItemStackWrapper key = ItemStackWrapper.of(requirement.stack());
            if (key == null) continue;
            encoded.put(key.toKey(), requirement.amount());
        }
        return encoded;
    }

    private static List<CelestialAssetRequirement> decodeRequirements(Map<String, Long> encoded) {
        List<CelestialAssetRequirement> requirements = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return requirements;
        for (Map.Entry<String, Long> entry : encoded.entrySet()) {
            ItemStackWrapper key = ItemStackWrapper.fromKey(entry.getKey());
            if (key == null) continue;
            requirements.add(new CelestialAssetRequirement(key.toStack(1), entry.getValue()));
        }
        return requirements;
    }

    static final class AssetJson {

        String assetId;
        String celestialObjectId;
        String displayName;
        String kind;
        String location;
        String status;
        Map<String, Long> requiredResources;
        Map<String, Long> constructionInventory;
        OutpostStateJson outpost;
    }

    static final class OutpostStateJson {

        String teamId;
        String celestialBodyId;
        String systemId;
        long energyStored;
        List<ModuleJson> modules;
        Map<String, Long> buffer;
        Map<String, LogisticsConfigJson> logisticsConfig;
    }

    static final class ModuleJson {

        String kind;
        String status;
        float constructionProgress;
        int cooldownTicks;
        long energyBuffer;
        JsonElement data;
        Map<String, Integer> consumedResources;
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

    private static final class OutpostModuleDataAdapter
        implements JsonSerializer<OutpostModuleData>, JsonDeserializer<OutpostModuleData> {

        private static final String TYPE_FIELD = "type";
        private static final Map<String, Class<? extends OutpostModuleData>> TYPE_MAP = new HashMap<>();
        private static final Map<Class<? extends OutpostModuleData>, String> TYPE_NAME_MAP = new HashMap<>();
        private static final Gson PURE_GSON = new GsonBuilder().create();

        static {
            TYPE_MAP.put("HAMMER", HammerModuleData.class);
            TYPE_MAP.put("BIG_HAMMER", BigHammerModuleData.class);
            TYPE_MAP.put("MINER", MinerModuleData.class);
            TYPE_MAP.put("POWER", PowerModuleData.class);
            for (Map.Entry<String, Class<? extends OutpostModuleData>> e : TYPE_MAP.entrySet()) {
                TYPE_NAME_MAP.put(e.getValue(), e.getKey());
            }
        }

        @Override
        public JsonElement serialize(OutpostModuleData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty(TYPE_FIELD, resolveTypeName(src.getClass()));
            JsonElement fields = PURE_GSON.toJsonTree(src, src.getClass());
            if (fields.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : fields.getAsJsonObject()
                    .entrySet()) {
                    obj.add(entry.getKey(), entry.getValue());
                }
            }
            return obj;
        }

        @Override
        public OutpostModuleData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get(TYPE_FIELD)
                .getAsString();
            Class<? extends OutpostModuleData> clazz = TYPE_MAP.get(type);
            if (clazz == null) throw new JsonParseException("Unknown OutpostModuleData type: " + type);
            return PURE_GSON.fromJson(obj, clazz);
        }

        private static String resolveTypeName(Class<?> clazz) {
            String name = TYPE_NAME_MAP.get(clazz);
            if (name == null)
                throw new IllegalArgumentException("Unregistered OutpostModuleData class: " + clazz.getName());
            return name;
        }
    }
}
