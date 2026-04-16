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
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePower;

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
 *   _tasks.json    <- global in-flight LogisticsDelivery list
 * </pre>
 *
 * <p>
 * {@link CelestialAssetStore} is restored from `_assets.json`.
 * {@link CelestialClient} is then reconstructed from the embedded outpost payloads
 * inside those same asset records. There is no second persisted outpost registry.
 */
public final class OutpostPersistenceManager {

    private static final String DATA_DIR = "galaxiadata";
    private static final String ASSETS_FILE = "_assets.json";
    private static final String TASKS_FILE = "_tasks.json";

    private final Gson gson;
    private static final Gson PURE_GSON = new GsonBuilder().create();
    private File worldSaveDir;

    public OutpostPersistenceManager() {
        gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(AutomatedOutpostModule.class, new OutpostModuleDataAdapter())
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

            for (AssetJson json : list) {
                CelestialAsset asset = decodeAsset(json);
                if (asset == null) continue;
                UUID teamId = UUID.fromString(json.teamId);
                decodeOutpostState(asset, json.outpost);
                CelestialAssetStore.add(teamId, asset);
            }
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            Galaxia.LOG.error("[Logistics] Failed to load station registry from {}: {}", file, e.getMessage());
        }
    }

    private void saveAssets(File file) {
        List<AssetJson> list = new ArrayList<>();
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            AssetJson json = encodeAsset(asset);
            CelestialAsset outpost = CelestialAssetStore.findAsset(asset.assetId);
            if (outpost instanceof AutomatedOutpost o) {
                json.outpost = encodeOutpostState(o);
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
            Galaxia.LOG.error("[Logistics] Failed to load tasks from {}: {}", file, e.getMessage());
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
        CelestialAsset asset = CelestialAsset.create(
            json.assetId,
            objectId,
            CelestialAsset.Kind.valueOf(json.kind),
            Buildable.Status.valueOf(json.status));
        asset.setConstructionInventory(decodeRequirements(json.constructionInventory));
        asset.setDisplayName(json.displayName);
        return asset;
    }

    private OutpostStateJson encodeOutpostState(AutomatedOutpost state) {
        OutpostStateJson out = new OutpostStateJson();
        out.celestialBodyId = String.valueOf(state.celestialObjectId);
        out.systemId = String.valueOf(state.systemId);
        out.planetaryAnchorBodyId = String.valueOf(state.planetaryAnchorBodyId);
        out.energyStored = state.getEnergyStored();
        out.modules = new ArrayList<>();
        for (AutomatedOutpostModule m : state.modules()) {
            ModuleJson mj = new ModuleJson();
            mj.kind = m.getKind()
                .name();
            mj.status = m.status()
                .name();
            mj.constructionProgress = m.getConstructionProgress();
            mj.cooldownTicks = m.cooldownTicks;
            mj.energyBuffer = m.energyBuffer;
            JsonObject moduleData = new JsonObject();
            if (m instanceof ModuleMiner miner) {
                moduleData.add("blacklistedItemKeys", PURE_GSON.toJsonTree(miner.blacklistedItemKeys));
            } else if (m instanceof ModuleHammer hammer) {
                moduleData.add("config", PURE_GSON.toJsonTree(hammer.getConfig()));
                moduleData.add("routePriority", PURE_GSON.toJsonTree(hammer.getRoutePriority()));
            } else if (m instanceof ModuleBigHammer bigHammer) {
                moduleData.add("config", PURE_GSON.toJsonTree(bigHammer.getConfig()));
                moduleData.add("routePriority", PURE_GSON.toJsonTree(bigHammer.getRoutePriority()));
                moduleData.addProperty("planetaryHandling", bigHammer.getPlanetaryHandling());
            } else if (m instanceof ModulePower) {
                // No extra data for ModulePower
            }
            mj.data = moduleData;
            mj.consumedResources = new LinkedHashMap<>();
            for (Map.Entry<ItemStack, Long> e : m.getConsumedResources()
                .entrySet()) {
                mj.consumedResources.put(
                    ItemStackWrapper.of(e.getKey())
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

    private AutomatedOutpost decodeOutpostState(CelestialAsset asset, OutpostStateJson json) {
        if (asset == null || json == null || json.systemId == null) return null;
        if (!(asset instanceof AutomatedOutpost state)) return null;
        state.setEnergyStored(json.energyStored);

        if (json.modules != null) {
            for (ModuleJson mj : json.modules) {
                AutomatedOutpostModule module;
                if (mj.kind == null) {
                    continue;
                }
                JsonObject data = mj.data != null ? mj.data.getAsJsonObject() : new JsonObject();
                switch (mj.kind) {
                    case "HAMMER" -> {
                        AllowShootingConfig config = AllowShootingConfig.ALWAYS;
                        OrbitalTransferPlanner.RoutePriority priority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
                        if (data.has("config")) {
                            config = PURE_GSON.fromJson(data.get("config"), AllowShootingConfig.class);
                        }
                        if (data.has("routePriority")) {
                            priority = PURE_GSON
                                .fromJson(data.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class);
                        }
                        module = new ModuleHammer(config, priority);
                    }
                    case "BIG_HAMMER" -> {
                        AllowShootingConfig config = AllowShootingConfig.ALWAYS;
                        OrbitalTransferPlanner.RoutePriority priority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
                        boolean planetaryHandling = false;
                        if (data.has("config")) {
                            config = PURE_GSON.fromJson(data.get("config"), AllowShootingConfig.class);
                        }
                        if (data.has("routePriority")) {
                            priority = PURE_GSON
                                .fromJson(data.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class);
                        }
                        if (data.has("planetaryHandling")) {
                            planetaryHandling = data.get("planetaryHandling")
                                .getAsBoolean();
                        }
                        module = new ModuleBigHammer(planetaryHandling, config, priority);
                    }
                    case "MINER" -> {
                        List<String> blacklist = new ArrayList<>();
                        if (data.has("blacklistedItemKeys")) {
                            blacklist = PURE_GSON.fromJson(
                                data.get("blacklistedItemKeys"),
                                new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
                        }
                        module = new ModuleMiner(blacklist);
                    }
                    case "POWER" -> {
                        module = new ModulePower();
                    }
                    default -> {
                        continue;
                    }
                }
                if (mj.status != null) {
                    module.updateStatus(Buildable.Status.valueOf(mj.status));
                }
                module.setConstructionProgress(mj.constructionProgress);
                module.cooldownTicks = mj.cooldownTicks;
                module.energyBuffer = mj.energyBuffer;
                module.clearConsumedResources();
                if (mj.consumedResources != null) {
                    for (Map.Entry<String, Long> e : mj.consumedResources.entrySet()) {
                        ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                        if (key != null) {
                            module.getConsumedResources()
                                .put(key.toStack(e.getValue()), e.getValue());
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
        OutpostStateJson outpost;
    }

    static final class OutpostStateJson {

        String celestialBodyId;
        String systemId;
        String planetaryAnchorBodyId;
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

    private static final class OutpostModuleDataAdapter
        implements JsonSerializer<AutomatedOutpostModule>, JsonDeserializer<AutomatedOutpostModule> {

        private static final String TYPE_FIELD = "type";

        @Override
        public JsonElement serialize(AutomatedOutpostModule src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty(
                TYPE_FIELD,
                src.getKind()
                    .name());
            if (src instanceof ModuleMiner miner) {
                obj.add("blacklistedItemKeys", PURE_GSON.toJsonTree(miner.blacklistedItemKeys));
            } else if (src instanceof ModuleHammer hammer) {
                obj.add("config", PURE_GSON.toJsonTree(hammer.getConfig()));
                obj.add("routePriority", PURE_GSON.toJsonTree(hammer.getRoutePriority()));
            } else if (src instanceof ModuleBigHammer bigHammer) {
                obj.add("config", PURE_GSON.toJsonTree(bigHammer.getConfig()));
                obj.add("routePriority", PURE_GSON.toJsonTree(bigHammer.getRoutePriority()));
                obj.addProperty("planetaryHandling", bigHammer.getPlanetaryHandling());
            }
            return obj;
        }

        @Override
        public AutomatedOutpostModule deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String type = obj.get(TYPE_FIELD)
                .getAsString();
            return switch (type) {
                case "HAMMER" -> {
                    AllowShootingConfig config = AllowShootingConfig.ALWAYS;
                    OrbitalTransferPlanner.RoutePriority priority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
                    if (obj.has("config")) {
                        config = PURE_GSON.fromJson(obj.get("config"), AllowShootingConfig.class);
                    }
                    if (obj.has("routePriority")) {
                        priority = PURE_GSON
                            .fromJson(obj.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class);
                    }
                    yield new ModuleHammer(config, priority);
                }
                case "BIG_HAMMER" -> {
                    AllowShootingConfig config = AllowShootingConfig.ALWAYS;
                    OrbitalTransferPlanner.RoutePriority priority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
                    boolean planetaryHandling = false;
                    if (obj.has("config")) {
                        config = PURE_GSON.fromJson(obj.get("config"), AllowShootingConfig.class);
                    }
                    if (obj.has("routePriority")) {
                        priority = PURE_GSON
                            .fromJson(obj.get("routePriority"), OrbitalTransferPlanner.RoutePriority.class);
                    }
                    if (obj.has("planetaryHandling")) {
                        planetaryHandling = obj.get("planetaryHandling")
                            .getAsBoolean();
                    }
                    yield new ModuleBigHammer(planetaryHandling, config, priority);
                }
                case "MINER" -> {
                    List<String> blacklist = new ArrayList<>();
                    if (obj.has("blacklistedItemKeys")) {
                        blacklist = PURE_GSON.fromJson(
                            obj.get("blacklistedItemKeys"),
                            new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
                    }
                    yield new ModuleMiner(blacklist);
                }
                case "POWER" -> new ModulePower();
                default -> throw new JsonParseException("Unknown module type: " + type);
            };
        }
    }
}
