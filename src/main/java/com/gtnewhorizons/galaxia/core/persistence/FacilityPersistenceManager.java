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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.core.persistence.CelestialObjectKeyJsonCodec.CelestialObjectKeyJson;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerTrajectoryLoadTracker;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class FacilityPersistenceManager {

    private static final Logger LOG = LogManager.getLogger(FacilityPersistenceManager.class);

    private static final String DATA_DIR = "galaxiadata";
    private static final String ASSETS_FILE = "_assets.json";
    private static final String TASKS_FILE = "_tasks.json";
    private static final String KNOWLEDGE_FILE = "_celestial_knowledge.json";
    private static final String DISCOVERY_FILE = "_discovery.json";

    private final Gson gson;
    private File worldSaveDir;
    private final CelestialServerRuntime celestialRuntime;
    private final CelestialKnowledgePersistenceAdapter celestialKnowledge;
    private final CelestialDiscoveryPersistenceAdapter celestialDiscovery;

    private static final String INVENTORY_KEY_ITEM_PREFIX = "I";
    private static final String INVENTORY_KEY_FLUID_PREFIX = "F";

    public FacilityPersistenceManager(CelestialServerRuntime celestialRuntime) {
        this.celestialRuntime = celestialRuntime;
        this.celestialKnowledge = new CelestialKnowledgePersistenceAdapter();
        this.celestialDiscovery = new CelestialDiscoveryPersistenceAdapter(celestialRuntime.scans());
        gson = new GsonBuilder().setPrettyPrinting()
            .serializeNulls()
            .create();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!(event.world instanceof WorldServer)) return;
        if (event.world.provider.dimensionId != 0) return;
        ISaveHandler saveHandler = event.world.getSaveHandler();
        loadFromSaveDirectory(saveHandler.getWorldDirectory());
    }

    public void loadFromSaveDirectory(File worldSaveDir) {
        this.worldSaveDir = worldSaveDir;
        celestialRuntime.reset();
        LogisticStore.clearDeliveries();
        LogisticStore.clearSignals();
        HammerTrajectoryLoadTracker.reset();
        loadAll();
    }

    void saveToSaveDirectory(File worldSaveDir) {
        this.worldSaveDir = worldSaveDir;
        saveAll();
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
        celestialRuntime.reset();
        LogisticStore.clearDeliveries();
        LogisticStore.clearSignals();
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
        // Content catalog restores before minor-key facts so those keys resolve;
        // shared facts restore before scan progress that references them.
        celestialKnowledge.load(new File(galaxiaRoot, KNOWLEDGE_FILE), gson);
        celestialDiscovery.load(new File(galaxiaRoot, DISCOVERY_FILE), gson);
    }

    private void saveAll() {
        File galaxiaRoot = new File(worldSaveDir, DATA_DIR);
        galaxiaRoot.mkdirs();
        LOG.info("[PERSIST] SAVE START: writing to {}", galaxiaRoot);
        saveAssets(new File(galaxiaRoot, ASSETS_FILE));
        saveTasks(new File(galaxiaRoot, TASKS_FILE));
        celestialKnowledge.save(new File(galaxiaRoot, KNOWLEDGE_FILE), gson);
        celestialDiscovery.save(new File(galaxiaRoot, DISCOVERY_FILE), gson);
    }

    private void loadAssets(File file) {
        if (!file.exists()) {
            LOG.info("[PERSIST] LOAD: no file at {}, skipping", file);
            return;
        }
        AssetRegistryJson registry;
        try (FileReader reader = new FileReader(file)) {
            registry = gson.fromJson(reader, AssetRegistryJson.class);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: read error " + file + ": " + e.getMessage(), e);
        }
        if (registry == null || registry.assets == null) {
            throw new IllegalStateException(
                "[PERSIST] LOAD FAILED: asset registry " + file + " contained no asset list");
        }

        List<AssetJson> list = registry.assets;
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
            FacilityJsonCodec.decode(asset, json.facility);
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
        AssetRegistryJson registry = new AssetRegistryJson();
        registry.assets = new ArrayList<>();
        int totalAssets = 0;
        int totalModules = 0;
        int totalAnchors = 0;
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            totalAssets++;
            AssetJson json = encodeAsset(asset);
            CelestialAsset facility = CelestialAssetStore.findAsset(asset.assetId);
            if (facility instanceof AutomatedFacility o) {
                json.facility = FacilityJsonCodec.encode(o);
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
            registry.assets.add(json);
        }
        LOG.info(
            "[PERSIST] SAVE: {} asset(s) total, {} modules, {} anchor tiles across all assets",
            totalAssets,
            totalModules,
            totalAnchors);
        writeJson(file, registry);
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
                            tj.fromBodyId == null ? null : CelestialObjectKeyJsonCodec.decode(tj.fromBodyId),
                            tj.toBodyId == null ? null : CelestialObjectKeyJsonCodec.decode(tj.toBodyId),
                            tj.departureOrbitalTime,
                            tj.tofOrbitalOsu));
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
            tj.fromBodyId = encodeCelestialObjectKey(delivery.data.fromBodyKey());
            tj.toBodyId = encodeCelestialObjectKey(delivery.data.toBodyKey());
            tj.departureOrbitalTime = delivery.data.departureOrbitalTime();
            tj.tofOrbitalOsu = delivery.data.tofOrbitalOsu();
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
        json.celestialObjectKey = encodeCelestialObjectKey(asset.celestialObjectKey);
        json.displayName = asset.displayName();
        json.kind = asset.kind.name();
        json.location = asset.location.name();
        json.status = asset.status()
            .name();
        if (asset instanceof Satellite satellite) {
            json.satelliteKind = satellite.satelliteKind()
                .name();
        }
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
        return json;
    }

    CelestialAsset decodeAsset(AssetJson json) {
        if (json == null || json.teamId == null
            || json.assetId == null
            || json.kind == null
            || json.location == null
            || json.status == null) {
            return null;
        }
        CelestialObjectKey objectKey = decodeCelestialObjectKey(json);
        if (objectKey == null) return null;
        CelestialAsset.Kind kind = safeValueOf(CelestialAsset.Kind.class, json.kind);
        Buildable.Status status = safeValueOf(Buildable.Status.class, json.status);
        if (kind == null || status == null) return null;
        SatelliteKind satelliteKind = safeValueOf(SatelliteKind.class, json.satelliteKind);
        if (kind == CelestialAsset.Kind.SATELLITE && satelliteKind == null) return null;
        CelestialAsset asset = CelestialAsset.create(json.assetId, objectKey, kind, status, satelliteKind);
        asset.setConstructionInventory(decodeRequirements(json.constructionInventory));
        asset.setDisplayName(json.displayName);
        if (asset instanceof Station station && json.controllerX != null
            && json.controllerY != null
            && json.controllerZ != null) {
            station.setController(new BlockPos(json.controllerX, json.controllerY, json.controllerZ));
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

        return asset;
    }

    private static CelestialObjectKeyJson encodeCelestialObjectKey(CelestialObjectKey key) {
        return CelestialObjectKeyJsonCodec.encode(key);
    }

    private static CelestialObjectKey decodeCelestialObjectKey(AssetJson json) {
        return CelestialObjectKeyJsonCodec.decode(json.celestialObjectKey);
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

    static final class AssetRegistryJson {

        List<AssetJson> assets;
    }

    static final class AssetJson {

        CelestialAsset.ID assetId;
        String teamId;
        CelestialObjectKeyJson celestialObjectKey;
        String systemId;
        String planetaryAnchorBodyId;
        String displayName;
        String kind;
        String satelliteKind;
        String location;
        String status;
        Map<String, Long> requiredResources;
        Map<String, Long> constructionInventory;
        FacilityJsonCodec.FacilityStateJson facility;
        Integer controllerX;
        Integer controllerY;
        Integer controllerZ;
        Map<String, LogisticsConfigJson> logisticsConfig;
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
        CelestialObjectKeyJson fromBodyId;
        CelestialObjectKeyJson toBodyId;
        double departureOrbitalTime;
        double tofOrbitalOsu;
    }

}
