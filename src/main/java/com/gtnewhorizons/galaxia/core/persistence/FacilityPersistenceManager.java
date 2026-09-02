package com.gtnewhorizons.galaxia.core.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.gtnewhorizons.galaxia.core.persistence.CelestialObjectKeyJsonCodec.CelestialObjectKeyJson;
import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerTrajectoryLoadTracker;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticSignal;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticStore;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsDelivery;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class FacilityPersistenceManager {

    private static final Logger LOG = LogManager.getLogger(FacilityPersistenceManager.class);

    private static final String DATA_DIR = "galaxiadata";
    private static final String ASSETS_FILE = "_assets.dat";
    private static final String TASKS_FILE = "_tasks.json";
    private static final String KNOWLEDGE_FILE = "_celestial_knowledge.json";
    private static final String DISCOVERY_FILE = "_discovery.json";

    private final Gson gson;
    private File worldSaveDir;
    private final CelestialServerRuntime celestialRuntime;
    private final CelestialKnowledgePersistenceAdapter celestialKnowledge;
    private final CelestialDiscoveryPersistenceAdapter celestialDiscovery;

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
        NBTTagCompound root;
        try (FileInputStream input = new FileInputStream(file)) {
            root = CompressedStreamTools.readCompressed(input);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("[PERSIST] LOAD FAILED: read error " + file + ": " + e.getMessage(), e);
        }
        requireType(root, "version", NBT.TAG_INT, "assets");
        if (root.getInteger("version") != 1) {
            throw new IllegalStateException(
                "[PERSIST] assets.version: unsupported format " + root.getInteger("version"));
        }
        NBTTagList assets = requireList(root, "assets", NBT.TAG_COMPOUND, "assets");
        Map<CelestialAsset.ID, AssetState.Decoded> decoded = new LinkedHashMap<>();
        for (int i = 0; i < assets.tagCount(); i++) {
            AssetState.Decoded replacement;
            try {
                replacement = AssetState.decode(assets.getCompoundTagAt(i));
            } catch (IllegalStateException ex) {
                throw new IllegalStateException("[PERSIST] assets[" + i + "]: " + ex.getMessage(), ex);
            }
            if (decoded.put(replacement.asset().assetId, replacement) != null) {
                throw new IllegalStateException("[PERSIST] assets[" + i + "].id: duplicate asset ID");
            }
        }
        decoded.values()
            .forEach(replacement -> CelestialAssetStore.registerAsset(replacement.teamId(), replacement.asset()));
        LOG.info("[PERSIST] LOAD END: {} asset(s) loaded", decoded.size());
    }

    private void saveAssets(File file) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", 1);
        NBTTagList assets = new NBTTagList();
        for (CelestialAsset asset : CelestialAssetStore.allAssets()) {
            assets.appendTag(AssetState.encode(CelestialAssetStore.getTeamId(asset.assetId), asset));
        }
        root.setTag("assets", assets);
        writeNbt(file, root);
    }

    private void loadTasks(File file) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<TaskJson>>() {}.getType();
            List<TaskJson> list = gson.fromJson(reader, listType);
            if (list == null) return;
            List<LogisticsDelivery> decoded = new ArrayList<>(list.size());
            for (TaskJson task : list) {
                if (task == null || task.taskId == null
                    || task.fromAssetId == null
                    || task.toAssetId == null
                    || task.resourceId == null
                    || task.transportKind == null
                    || task.fromBodyId == null
                    || task.toBodyId == null
                    || task.amount <= 0L) throw new IllegalArgumentException("task entry is incomplete");
                ItemStackWrapper resource = decodeTaskResource(task.resourceId);
                decoded.add(
                    LogisticsDelivery.createWithTrajectory(
                        LogisticsDelivery.ID.from(task.taskId),
                        CelestialAsset.ID.from(task.fromAssetId),
                        CelestialAsset.ID.from(task.toAssetId),
                        resource,
                        task.amount,
                        task.remainingTicks,
                        LogisticSignal.Scope.valueOf(task.transportKind),
                        CelestialObjectKeyJsonCodec.decode(task.fromBodyId),
                        CelestialObjectKeyJsonCodec.decode(task.toBodyId),
                        task.departureOrbitalTime,
                        task.tofOrbitalOsu,
                        decodeTransferRoute(task.transferRoute)));
            }
            LogisticStore.activeDeliveries()
                .addAll(decoded);
        } catch (IOException | JsonParseException | IllegalArgumentException e) {
            LOG.error("[Logistics] Failed to load tasks from {}: {}", file, e.getMessage());
        }
    }

    private static OrbitalTransferPlanner.TransferRoute decodeTransferRoute(TransferRouteJson route) {
        if (route == null) return null;
        OrbitalTransferPlanner.TransferRoute decoded = new OrbitalTransferPlanner.TransferRoute(
            route.tofOsu(),
            route.totalDv(),
            route.departureDv(),
            route.captureDv(),
            CelestialObjectKeyJsonCodec.decode(route.attractorBodyId()),
            route.anchorX(),
            route.anchorY(),
            route.r1x(),
            route.r1y(),
            route.departureVelocityX(),
            route.departureVelocityY(),
            route.prograde());
        if (!decoded.hasTrajectoryGeometry()) throw new IllegalArgumentException("task transfer route is malformed");
        return decoded;
    }

    private void saveTasks(File file) {
        List<TaskJson> list = new ArrayList<>();
        for (LogisticsDelivery delivery : LogisticStore.activeDeliveries()) {
            TaskJson tj = new TaskJson();
            tj.taskId = String.valueOf(delivery.deliveryId);
            tj.fromAssetId = String.valueOf(delivery.data.fromAssetId());
            tj.toAssetId = String.valueOf(delivery.data.toAssetId());
            tj.resourceId = encodeTaskResource(delivery.data.resourceId());
            tj.amount = delivery.data.amount();
            tj.remainingTicks = delivery.getRemainingTicks();
            tj.transportKind = String.valueOf(delivery.data.scope());
            tj.fromBodyId = encodeCelestialObjectKey(delivery.data.fromBodyKey());
            tj.toBodyId = encodeCelestialObjectKey(delivery.data.toBodyKey());
            tj.departureOrbitalTime = delivery.data.departureOrbitalTime();
            tj.tofOrbitalOsu = delivery.data.tofOrbitalOsu();
            tj.transferRoute = encodeTransferRoute(delivery.data.transferRoute());
            list.add(tj);
        }
        AtomicJsonWriter.write(file, gson, list, "logistics tasks");
    }

    private static CelestialObjectKeyJson encodeCelestialObjectKey(CelestialObjectKey key) {
        return CelestialObjectKeyJsonCodec.encode(key);
    }

    private static String encodeTaskResource(ItemStackWrapper resource) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            CompressedStreamTools.writeCompressed(InventoryKeyState.encode(resource), output);
        } catch (IOException e) {
            throw new IllegalStateException("task resource could not be encoded", e);
        }
        return Base64.getEncoder()
            .encodeToString(output.toByteArray());
    }

    private static ItemStackWrapper decodeTaskResource(String encoded) {
        try {
            InventoryKey resource = InventoryKeyState.decode(
                CompressedStreamTools.readCompressed(
                    new ByteArrayInputStream(
                        Base64.getDecoder()
                            .decode(encoded))));
            if (resource instanceof ItemStackWrapper item) return item;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new IllegalArgumentException("task resource is malformed", e);
        }
        throw new IllegalArgumentException("task resource is not an item");
    }

    private static TransferRouteJson encodeTransferRoute(OrbitalTransferPlanner.TransferRoute route) {
        if (route == null) return null;
        return new TransferRouteJson(
            route.tofOsu(),
            route.totalDv(),
            route.departureDv(),
            route.captureDv(),
            encodeCelestialObjectKey(route.attractorBodyKey()),
            route.anchorX(),
            route.anchorY(),
            route.r1x(),
            route.r1y(),
            route.departureVelocityX(),
            route.departureVelocityY(),
            route.prograde());
    }

    private void writeNbt(File file, NBTTagCompound value) {
        File tmp = new File(file.getParent(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(tmp)) {
            CompressedStreamTools.writeCompressed(value, output);
        } catch (IOException e) {
            LOG.error("[PERSIST] Failed to write {}: {}", file, e.getMessage());
            tmp.delete();
            return;
        }
        replace(file, tmp);
    }

    private void replace(File file, File tmp) {
        try {
            Files
                .move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOG.error("[Logistics] Failed to replace {} with {}: {}", file, tmp, e2.getMessage());
            }
        } catch (IOException e) {
            LOG.error("[Logistics] Failed to replace {} with {}: {}", file, tmp, e.getMessage());
        }
    }

    private static NBTTagList requireList(NBTTagCompound source, String key, int elementType, String path) {
        requireType(source, key, NBT.TAG_LIST, path);
        NBTTagList list = source.getTagList(key, elementType);
        if (list.tagCount() > 0 && list.func_150303_d() != elementType) {
            throw new IllegalStateException("[PERSIST] " + path + "." + key + ": wrong list element type");
        }
        return list;
    }

    private static void requireType(NBTTagCompound source, String key, int type, String path) {
        if (source == null || !source.hasKey(key, type)) {
            throw new IllegalStateException("[PERSIST] " + path + "." + key + ": missing or wrong type");
        }
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
        TransferRouteJson transferRoute;
    }

    record TransferRouteJson(double tofOsu, double totalDv, double departureDv, double captureDv,
        CelestialObjectKeyJson attractorBodyId, double anchorX, double anchorY, double r1x, double r1y,
        double departureVelocityX, double departureVelocityY, boolean prograde) {}

}
