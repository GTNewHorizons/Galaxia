package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryBounds;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IParallelModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
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
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepAmount;
import com.gtnewhorizons.galaxia.registry.outpost.upkeep.UpkeepSettlement;
import com.gtnewhorizons.galaxia.registry.satellite.Satellite;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;

import io.netty.buffer.ByteBuf;

final class AssetSyncPacket {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    public static final byte FULL_SYNC = 0;
    public static final byte MODULE_ADDED = 1;
    public static final byte INVENTORY_UPDATE = 4;
    public static final byte LOGISTICS_CONFIG_UPDATED = 6;
    public static final byte LAYOUT_TILE_UPDATED = 8;
    public static final byte ASSET_REMOVED = 10;
    public static final byte SETTINGS_GROUP_UPDATED = 11;
    public static final byte FILTER_UPDATED = 13;
    public static final byte CLEAR = 15;
    public static final byte INVENTORY_BOUNDS_SNAPSHOT = 16;
    public static final byte STATE_REPLACEMENT = 17;

    private static final int MAX_OPERATION_MAP_ENTRIES = 256;
    private static final int MAX_INVENTORY_BOUND_SNAPSHOT_ENTRIES = 4096;
    private static final int MAX_RECIPE_STACKS = 64;
    static final int MAX_FULL_SYNC_DELTAS = 65_536;
    private static final byte OPERATION_SPEC_TIER = 1;
    private static final byte OPERATION_SPEC_HAMMER = 2;
    private static final byte OPERATION_SPEC_MINER_FOCUS = 3;

    CelestialAsset.ID assetId;
    byte syncType;

    int stateRevision;
    long basePublishedRevision;
    long publishedRevision;

    UUID teamId;
    CelestialObjectKey celestialBodyKey;
    CelestialObjectKey systemKey;
    CelestialObjectKey planetaryAnchorBodyKey;
    Buildable.Status assetStatus;
    CelestialAsset.Kind assetKind;
    String displayName;
    long energyStored;
    long stationFeatureSalt;
    UpkeepSettlement.Credits upkeepCredits = UpkeepSettlement.Credits.empty();
    SatelliteKind satelliteKind;

    List<AssetSyncPacket> fullSyncDeltas;

    int moduleIndex;
    ModuleInstance moduleData;

    @Deprecated
    private String resourceKey;
    InventoryKey resource;
    long inventoryDelta;
    Map<InventoryKey, InventoryBounds> inventoryBoundSnapshot;
    LogisticsResourceConfig logConfig;

    StationTileCoord tileCoord;
    StationTileState tileState;
    ModuleInstance.ID tileModuleId;

    BlockPos stationControllerPos;

    short settingsGroupId;
    FacilityModuleKind settingsGroupKind;
    String settingsGroupName;
    boolean settingsGroupJoinable;
    ModuleSettings settingsGroupSettings;

    boolean filterItem;
    List<String> filterItems;

    public AssetSyncPacket() {}

    static AssetSyncPacket fullSync(CelestialAsset state) {
        if (state instanceof AutomatedFacility) {
            return fullSync((AutomatedFacility) state);
        } else if (state instanceof Station) {
            return fullSync((Station) state);
        } else if (state instanceof Satellite) {
            return fullSync((Satellite) state);
        }
        throw new IllegalStateException("Unexpected value: " + state);
    }

    static AssetSyncPacket stateReplacement(CelestialAsset state) {
        AssetSyncPacket packet = fullSync(state);
        packet.syncType = STATE_REPLACEMENT;
        return packet;
    }

    static AssetSyncPacket fullSync(Station state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.assetKind = state.kind;
        pkt.syncType = FULL_SYNC;
        pkt.stateRevision = state.getStateRevision();
        pkt.assetStatus = state.status();
        pkt.displayName = state.displayName();

        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyKey = state.celestialObjectKey;
        pkt.stationControllerPos = state.getController();

        pkt.fullSyncDeltas = new ArrayList<>();
        for (Map.Entry<InventoryKey, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            pkt.fullSyncDeltas.add(
                logisticsConfigUpdated(
                    state.assetId,
                    e.getKey(),
                    cfg.minReserve(),
                    cfg.orderSize(),
                    cfg.isImportEnabled(),
                    cfg.isSupplyEnabled()));
        }

        return pkt;
    }

    static AssetSyncPacket fullSync(AutomatedFacility state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.assetKind = state.kind;
        pkt.syncType = FULL_SYNC;
        pkt.stateRevision = state.getStateRevision();
        pkt.assetStatus = state.status();
        pkt.displayName = state.displayName();

        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyKey = state.celestialObjectKey;
        pkt.systemKey = state.systemKey;
        pkt.planetaryAnchorBodyKey = state.planetaryAnchorBodyKey;
        pkt.energyStored = state.getEnergyStored();
        pkt.stationFeatureSalt = state.stationFeatureSalt();
        pkt.upkeepCredits = state.upkeepCredits();
        pkt.fullSyncDeltas = automatedFacilityDeltas(state);

        return pkt;
    }

    static AssetSyncPacket fullSync(Satellite state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.assetKind = state.kind;
        pkt.syncType = FULL_SYNC;
        pkt.stateRevision = state.getStateRevision();
        pkt.assetStatus = state.status();
        pkt.displayName = state.displayName();
        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyKey = state.celestialObjectKey;
        pkt.satelliteKind = state.satelliteKind();

        return pkt;
    }

    private static List<AssetSyncPacket> automatedFacilityDeltas(AutomatedFacility state) {
        List<AssetSyncPacket> deltas = new ArrayList<>();

        state.settingsGroups()
            .groups()
            .values()
            .stream()
            .sorted(Comparator.comparingInt(SettingsGroup::id))
            .forEach(group -> deltas.add(settingsGroupUpdated(state.assetId, group)));

        List<ModuleInstance> modules = state.modules();
        for (int i = 0; i < modules.size(); i++) {
            deltas.add(moduleAdded(state.assetId, i, modules.get(i)));
        }

        for (Map.Entry<ItemStackWrapper, Long> e : state.itemSnapshot()
            .entrySet()) {
            deltas.add(inventoryUpdate(state.assetId, e.getKey(), e.getValue()));
        }

        for (Map.Entry<Boolean, List<String>> e : state.filtersSnapshot()
            .entrySet()) {
            deltas.add(filterUpdated(state.assetId, e.getKey(), e.getValue()));
        }

        Map<? extends InventoryKey, InventoryBounds> itemBounds = state.getBounds(true);
        Map<? extends InventoryKey, InventoryBounds> fluidBounds = state.getBounds(false);
        if (!itemBounds.isEmpty() || !fluidBounds.isEmpty()) {
            deltas.add(inventoryBoundsSnapshot(state.assetId, itemBounds, fluidBounds));
        }

        for (Map.Entry<InventoryKey, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            deltas.add(
                logisticsConfigUpdated(
                    state.assetId,
                    e.getKey(),
                    cfg.minReserve(),
                    cfg.orderSize(),
                    cfg.isImportEnabled(),
                    cfg.isSupplyEnabled()));
        }

        StationLayout layout = state.stationLayout();
        if (layout != null) {
            for (Map.Entry<StationTileCoord, PlacedTile> e : layout.snapshot()
                .entrySet()) {
                deltas.add(layoutTileUpdated(state.assetId, e.getKey(), e.getValue()));
            }
        }

        return deltas;
    }

    static AssetSyncPacket clear() {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.syncType = CLEAR;
        return pkt;
    }

    static AssetSyncPacket assetRemoved(CelestialAsset.ID assetId) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = ASSET_REMOVED;
        return pkt;
    }

    static AssetSyncPacket moduleAdded(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_ADDED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    static AssetSyncPacket inventoryUpdate(CelestialAsset.ID assetId, InventoryKey resource, long delta) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_UPDATE;
        pkt.resource = resource;
        pkt.inventoryDelta = delta;
        return pkt;
    }

    static AssetSyncPacket inventoryBoundsSnapshot(CelestialAsset.ID assetId,
        Map<? extends InventoryKey, InventoryBounds> itemBounds,
        Map<? extends InventoryKey, InventoryBounds> fluidBounds) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_BOUNDS_SNAPSHOT;
        pkt.inventoryBoundSnapshot = new LinkedHashMap<>();
        copyInventoryBounds(pkt.inventoryBoundSnapshot, itemBounds);
        copyInventoryBounds(pkt.inventoryBoundSnapshot, fluidBounds);
        return pkt;
    }

    private static void copyInventoryBounds(Map<InventoryKey, InventoryBounds> target,
        Map<? extends InventoryKey, InventoryBounds> source) {
        for (Map.Entry<? extends InventoryKey, InventoryBounds> e : source.entrySet()) {
            InventoryBounds bounds = e.getValue();
            target.put(e.getKey(), new InventoryBounds(bounds.low(), bounds.upper()));
        }
    }

    static AssetSyncPacket logisticsConfigUpdated(CelestialAsset.ID assetId, InventoryKey resource, int minReserve,
        int orderSize, boolean importEnabled, boolean supplyEnabled) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_UPDATED;
        pkt.resource = resource;
        pkt.logConfig = new LogisticsResourceConfig(minReserve, orderSize, importEnabled, supplyEnabled);
        return pkt;
    }

    static AssetSyncPacket settingsGroupUpdated(CelestialAsset.ID assetId, SettingsGroup group) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = SETTINGS_GROUP_UPDATED;
        pkt.settingsGroupId = group.id();
        pkt.settingsGroupKind = group.kind();
        pkt.settingsGroupName = group.displayName();
        pkt.settingsGroupJoinable = group.isJoinable();
        if (group.settings() instanceof MinerSettings settings) {
            pkt.settingsGroupSettings = settings.copy();
        } else if (group.settings() instanceof RecipeModuleSettings settings) {
            pkt.settingsGroupSettings = settings.copy();
        } else {
            throw new IllegalStateException("Unsupported settings group payload " + group.settings());
        }
        return pkt;
    }

    static AssetSyncPacket layoutTileUpdated(CelestialAsset.ID assetId, StationTileCoord coord, PlacedTile tile) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LAYOUT_TILE_UPDATED;
        pkt.tileCoord = coord;
        pkt.tileState = tile.state();
        pkt.tileModuleId = tile.module() == null ? null : tile.module().id;
        return pkt;
    }

    static AssetSyncPacket filterUpdated(CelestialAsset.ID assetId, boolean item, List<String> filters) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = FILTER_UPDATED;
        pkt.filterItem = item;
        pkt.filterItems = filters == null ? List.of() : filters;
        return pkt;
    }

    public void toBytes(ByteBuf buf) {
        buf.writeByte(syncType);
        buf.writeInt(stateRevision);
        buf.writeLong(basePublishedRevision);
        buf.writeLong(publishedRevision);

        if (syncType != CLEAR) {
            PacketUtil.writeId(buf, assetId);
        }
        switch (syncType) {
            case FULL_SYNC, STATE_REPLACEMENT -> {
                PacketUtil.writeEnum(buf, assetKind);
                PacketUtil.writeEnum(buf, assetStatus);
                PacketUtil.writeString(buf, displayName == null ? "" : displayName);

                switch (assetKind) {
                    case STATION -> {
                        PacketUtil.writeCelestialObjectKey(buf, celestialBodyKey);
                        if (assetStatus == Buildable.Status.OPERATIONAL) {
                            buf.writeInt(stationControllerPos.x());
                            buf.writeInt(stationControllerPos.y());
                            buf.writeInt(stationControllerPos.z());
                        }
                        buf.writeInt(fullSyncDeltas.size());
                        for (AssetSyncPacket d : fullSyncDeltas) {
                            buf.writeByte(d.syncType);
                            d.writeDelta(buf);
                        }
                    }
                    case AUTOMATED_OUTPOST, AUTOMATED_STATION -> {
                        buf.writeLong(teamId.getMostSignificantBits());
                        buf.writeLong(teamId.getLeastSignificantBits());
                        PacketUtil.writeCelestialObjectKey(buf, celestialBodyKey);
                        PacketUtil.writeCelestialObjectKey(buf, systemKey);
                        PacketUtil.writeCelestialObjectKey(buf, planetaryAnchorBodyKey);
                        buf.writeLong(energyStored);
                        buf.writeLong(stationFeatureSalt);
                        writeUpkeepCredits(buf, upkeepCredits);

                        buf.writeInt(fullSyncDeltas.size());
                        for (AssetSyncPacket d : fullSyncDeltas) {
                            buf.writeByte(d.syncType);
                            d.writeDelta(buf);
                        }
                    }
                    case SATELLITE -> {
                        buf.writeLong(teamId.getMostSignificantBits());
                        buf.writeLong(teamId.getLeastSignificantBits());
                        PacketUtil.writeCelestialObjectKey(buf, celestialBodyKey);
                        PacketUtil.writeEnum(buf, satelliteKind);
                    }
                }
            }
            case CLEAR, ASSET_REMOVED -> {}
            default -> throw new IllegalStateException("Unsupported top-level asset update type: " + syncType);
        }
    }

    public void fromBytes(ByteBuf buf) {
        syncType = buf.readByte();
        stateRevision = buf.readInt();
        basePublishedRevision = buf.readLong();
        publishedRevision = buf.readLong();

        if (syncType != CLEAR) {
            assetId = PacketUtil.readAssetId(buf);
        }
        switch (syncType) {
            case FULL_SYNC, STATE_REPLACEMENT -> {
                assetKind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
                assetStatus = PacketUtil.readEnum(buf, Buildable.Status.class);
                displayName = PacketUtil.readString(buf);

                switch (assetKind) {
                    case STATION -> {
                        celestialBodyKey = PacketUtil.readCelestialObjectKey(buf);
                        if (assetStatus == Buildable.Status.OPERATIONAL) {
                            stationControllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                        }
                        int count = readFullSyncDeltaCount(buf);
                        fullSyncDeltas = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            AssetSyncPacket d = new AssetSyncPacket();
                            d.assetId = assetId;
                            d.syncType = buf.readByte();
                            d.readDelta(buf);
                            fullSyncDeltas.add(d);
                        }
                    }
                    case AUTOMATED_OUTPOST, AUTOMATED_STATION -> {
                        teamId = new UUID(buf.readLong(), buf.readLong());
                        celestialBodyKey = PacketUtil.readCelestialObjectKey(buf);
                        systemKey = PacketUtil.readCelestialObjectKey(buf);
                        planetaryAnchorBodyKey = PacketUtil.readCelestialObjectKey(buf);
                        energyStored = buf.readLong();
                        stationFeatureSalt = buf.readLong();
                        upkeepCredits = readUpkeepCredits(buf);

                        int count = readFullSyncDeltaCount(buf);
                        fullSyncDeltas = new ArrayList<>(count);

                        for (int i = 0; i < count; i++) {
                            AssetSyncPacket d = new AssetSyncPacket();
                            d.assetId = assetId;
                            d.syncType = buf.readByte();
                            d.readDelta(buf);
                            fullSyncDeltas.add(d);
                        }
                    }
                    case SATELLITE -> {
                        teamId = new UUID(buf.readLong(), buf.readLong());
                        celestialBodyKey = PacketUtil.readCelestialObjectKey(buf);
                        satelliteKind = PacketUtil.readEnum(buf, SatelliteKind.class);
                    }
                }
            }
            case CLEAR, ASSET_REMOVED -> {}
            default -> throw new IllegalArgumentException("Unsupported top-level asset update type: " + syncType);
        }
    }

    private static int readFullSyncDeltaCount(ByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_FULL_SYNC_DELTAS || count > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid full asset state delta count: " + count);
        }
        return count;
    }

    private void writeDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED -> {
                buf.writeInt(moduleIndex);
                writeModule(buf, moduleData);
            }
            case INVENTORY_UPDATE -> {
                PacketUtil.writeInventoryKey(buf, resource);
                buf.writeLong(inventoryDelta);
            }
            case INVENTORY_BOUNDS_SNAPSHOT -> writeInventoryBoundsSnapshot(buf, inventoryBoundSnapshot);
            case LOGISTICS_CONFIG_UPDATED -> {
                PacketUtil.writeInventoryKey(buf, resource);
                writeLogisticsConfig(buf, logConfig);
            }
            case LAYOUT_TILE_UPDATED -> {
                PacketUtil.writeStationTileCoord(buf, tileCoord);
                PacketUtil.writeEnum(buf, tileState);
                boolean hasModule = tileModuleId != null;
                buf.writeBoolean(hasModule);
                if (hasModule) PacketUtil.writeId(buf, tileModuleId);
            }
            case SETTINGS_GROUP_UPDATED -> {
                buf.writeShort(settingsGroupId);
                PacketUtil.writeEnum(buf, settingsGroupKind);
                PacketUtil.writeString(buf, settingsGroupName);
                buf.writeBoolean(settingsGroupJoinable);
                writeSettingsGroupPayload(buf, settingsGroupKind, settingsGroupSettings);
            }
            case FILTER_UPDATED -> {
                buf.writeBoolean(filterItem);
                buf.writeShort(filterItems.size());
                for (String key : filterItems) {
                    PacketUtil.writeString(buf, key);
                }
            }
            default -> throw new IllegalStateException("Unsupported full asset state delta type: " + syncType);
        }
    }

    private void readDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED -> {
                moduleIndex = buf.readInt();
                moduleData = readModule(buf);
            }
            case INVENTORY_UPDATE -> {
                resource = PacketUtil.readInventoryKey(buf);
                inventoryDelta = buf.readLong();
            }
            case INVENTORY_BOUNDS_SNAPSHOT -> inventoryBoundSnapshot = readInventoryBoundsSnapshot(buf);
            case LOGISTICS_CONFIG_UPDATED -> {
                resource = PacketUtil.readInventoryKey(buf);
                logConfig = readLogisticsConfig(buf);
            }
            case LAYOUT_TILE_UPDATED -> {
                tileCoord = PacketUtil.readStationTileCoord(buf);
                tileState = PacketUtil.readEnum(buf, StationTileState.class);
                tileModuleId = buf.readBoolean() ? PacketUtil.readModuleId(buf) : null;
            }
            case SETTINGS_GROUP_UPDATED -> {
                settingsGroupId = buf.readShort();
                settingsGroupKind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
                settingsGroupName = PacketUtil.readString(buf);
                settingsGroupJoinable = buf.readBoolean();
                settingsGroupSettings = readSettingsGroupPayload(
                    buf,
                    settingsGroupKind,
                    "settingsGroup=" + settingsGroupId);
            }
            case FILTER_UPDATED -> {
                filterItem = buf.readBoolean();
                int count = buf.readShort();
                filterItems = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    filterItems.add(PacketUtil.readString(buf));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported full asset state delta type: " + syncType);
        }
    }

    private static void writeModule(ByteBuf buf, ModuleInstance module) {
        PacketUtil.writeId(buf, module.id);
        PacketUtil.writeEnum(buf, module.kind());
        PacketUtil.writeEnum(buf, module.status());
        PacketUtil.writeEnum(buf, module.tier());
        PacketUtil.writeEnum(buf, module.shape());
        buf.writeByte(module.rotation());
        PacketUtil.writeEnum(buf, module.priorityOverride());
        buf.writeBoolean(module.enabled());
        buf.writeShort(module.groupId());
        buf.writeByte(module.component() instanceof IParallelModule pm ? pm.getParallel() : 1);

        StationTileCoord anchor = module.anchorOrNull();
        buf.writeBoolean(anchor != null);
        if (anchor != null) PacketUtil.writeStationTileCoord(buf, anchor);

        switch (module.kind()) {
            case MINER -> {}
            case HAMMER -> {
                ModuleHammer h = (ModuleHammer) module.component();
                PacketUtil.writeEnum(
                    buf,
                    h.config()
                        .mode());
                buf.writeDouble(
                    h.config()
                        .threshold());
                PacketUtil.writeEnum(buf, h.routePriority());
                PacketUtil.writeEnum(buf, h.variant());
                buf.writeLong(h.energyStored());
            }
            case POWER, GEOTHERMAL_GENERATOR -> {}
            case STORAGE, TANK, BATTERY -> {}
            case DEBUG_DATA_GENERATOR -> writeDebugDataGenerator(buf, module);
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> writeRecipeConfig(
                buf,
                module);
            default -> {}
        }
        writeModuleOperation(buf, module.operationOrNull());
    }

    private static ModuleInstance readModule(ByteBuf buf) {
        ModuleInstance.ID id = PacketUtil.readModuleId(buf);
        FacilityModuleKind kind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        Buildable.Status status = PacketUtil.readEnum(buf, Buildable.Status.class);
        ModuleTier tier = PacketUtil.readEnum(buf, ModuleTier.class);
        ModuleShape shape = PacketUtil.readEnum(buf, ModuleShape.class);
        int rotation = buf.readByte();
        ModulePriority modulePriority = PacketUtil.readEnum(buf, ModulePriority.class);
        boolean enabled = buf.readBoolean();
        short groupId = buf.readShort();
        byte parallel = buf.readByte();
        StationTileCoord anchor = buf.readBoolean() ? PacketUtil.readStationTileCoord(buf) : null;

        ModuleInstance module = FacilityModuleRegistry.create(id, kind, anchor, shape, tier);
        module.setRotation(rotation);
        module.setPriorityOverride(modulePriority);
        module.setEnabled(enabled);
        module.setGroupId(groupId);

        switch (kind) {
            case MINER -> {}
            case HAMMER -> {
                AllowShootingConfig cfg = new AllowShootingConfig(
                    PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class),
                    buf.readDouble());
                OrbitalTransferPlanner.RoutePriority routePriority = PacketUtil
                    .readEnum(buf, OrbitalTransferPlanner.RoutePriority.class);
                HammerVariant variant = PacketUtil.readEnum(buf, HammerVariant.class);
                long energyStored = buf.readLong();
                ModuleHammer.requireTier(variant, tier);
                module.setComponent(new ModuleHammer(kind, cfg, routePriority, variant, 64, energyStored));
            }
            case POWER, GEOTHERMAL_GENERATOR -> {}
            case STORAGE, TANK, BATTERY -> {}
            case DEBUG_DATA_GENERATOR -> readDebugDataGenerator(buf, module);
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> readRecipeConfig(
                buf,
                module);
            default -> {}
        }

        module.setOperation(readModuleOperation(buf));
        if (module.component() instanceof IParallelModule pm) {
            pm.setParallel(parallel);
        }
        module.updateStatus(status);
        return module;
    }

    private static void writeModuleOperation(ByteBuf buf, ModuleOperationState operation) {
        buf.writeBoolean(operation != null);
        if (operation == null) return;
        ModuleOperationPlan plan = operation.plan();
        writeOperationSpec(buf, plan.spec());
        PacketUtil.writeEnum(buf, operation.phase());
        buf.writeInt(operation.elapsedBuildTicks());
        buf.writeInt(plan.buildTicks());
        writeItemAmountMap(buf, plan.materialCost());
        writeItemAmountMap(buf, plan.completionRefundCost());
        buf.writeInt(plan.completionRefundPercent());
        buf.writeBoolean(plan.reserveItems());
        buf.writeBoolean(plan.voidCompletionRefund());
        writeStringAmountMap(buf, operation.depositedResources());
        writeStringAmountMap(buf, operation.refundBuffer());
    }

    private static ModuleOperationState readModuleOperation(ByteBuf buf) {
        if (!buf.readBoolean()) return null;
        IModuleOperation spec = readOperationSpec(buf);
        ModuleOperationPhase phase = PacketUtil.readEnum(buf, ModuleOperationPhase.class);
        int elapsedBuildTicks = buf.readInt();
        int buildTicks = buf.readInt();
        Map<ItemStackWrapper, Long> materialCost = readItemAmountMap(buf);
        Map<ItemStackWrapper, Long> completionRefundCost = readItemAmountMap(buf);
        int completionRefundPercent = buf.readInt();
        boolean reserveItems = buf.readBoolean();
        boolean voidCompletionRefund = buf.readBoolean();
        Map<String, Long> depositedResources = readStringAmountMap(buf);
        Map<String, Long> refundBuffer = readStringAmountMap(buf);
        ModuleOperationPlan plan = new ModuleOperationPlan(
            spec,
            buildTicks,
            materialCost,
            completionRefundCost,
            completionRefundPercent,
            reserveItems,
            voidCompletionRefund);
        return ModuleOperationState.restore(plan, phase, elapsedBuildTicks, depositedResources, refundBuffer);
    }

    private static void writeOperationSpec(ByteBuf buf, IModuleOperation spec) {
        if (spec instanceof HammerModuleOperation hammerSpec) {
            buf.writeByte(OPERATION_SPEC_HAMMER);
            PacketUtil.writeEnum(buf, hammerSpec.targetTier());
            PacketUtil.writeString(buf, hammerSpec.targetVariantKey());
            return;
        }
        if (spec instanceof MinerFocusOperation minerSpec) {
            buf.writeByte(OPERATION_SPEC_MINER_FOCUS);
            PacketUtil.writeEnum(buf, minerSpec.targetTier());
            PacketUtil.writeString(buf, minerSpec.targetFocusTierKey());
            buf.writeBoolean(minerSpec.targetFocusOreKey() != null);
            if (minerSpec.targetFocusOreKey() != null) PacketUtil.writeString(buf, minerSpec.targetFocusOreKey());
            return;
        }
        if (spec instanceof ModuleTierOperation tierSpec) {
            buf.writeByte(OPERATION_SPEC_TIER);
            PacketUtil.writeEnum(buf, tierSpec.targetTier());
            return;
        }
        throw new IllegalStateException(
            "Unsupported module operation spec: " + spec.getClass()
                .getName());
    }

    private static IModuleOperation readOperationSpec(ByteBuf buf) {
        int type = buf.readUnsignedByte();
        ModuleTier targetTier = PacketUtil.readEnum(buf, ModuleTier.class);
        return switch (type) {
            case OPERATION_SPEC_HAMMER -> new HammerModuleOperation(targetTier, PacketUtil.readString(buf));
            case OPERATION_SPEC_MINER_FOCUS -> {
                String focusTierKey = PacketUtil.readString(buf);
                String focusOreKey = buf.readBoolean() ? PacketUtil.readString(buf) : null;
                yield new MinerFocusOperation(targetTier, focusTierKey, focusOreKey);
            }
            case OPERATION_SPEC_TIER -> new ModuleTierOperation(targetTier);
            default -> throw new IllegalStateException("Unknown module operation spec type: " + type);
        };
    }

    private static void writeItemAmountMap(ByteBuf buf, Map<ItemStackWrapper, Long> amounts) {
        buf.writeInt(amounts.size());
        for (Map.Entry<ItemStackWrapper, Long> entry : amounts.entrySet()) {
            PacketUtil.writeString(
                buf,
                entry.getKey()
                    .toKey());
            buf.writeLong(entry.getValue());
        }
    }

    private static Map<ItemStackWrapper, Long> readItemAmountMap(ByteBuf buf) {
        int size = readOperationMapSize(buf);
        Map<ItemStackWrapper, Long> amounts = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            ItemStackWrapper item = ItemStackWrapper.fromKey(PacketUtil.readString(buf));
            long amount = buf.readLong();
            if (item != null && amount > 0L) amounts.put(item, amount);
        }
        return amounts;
    }

    private static void writeStringAmountMap(ByteBuf buf, Map<String, Long> amounts) {
        buf.writeInt(amounts.size());
        for (Map.Entry<String, Long> entry : amounts.entrySet()) {
            PacketUtil.writeString(buf, entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    private static Map<String, Long> readStringAmountMap(ByteBuf buf) {
        int size = readOperationMapSize(buf);
        Map<String, Long> amounts = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String key = PacketUtil.readString(buf);
            long amount = buf.readLong();
            if (!key.isBlank() && amount > 0L) amounts.put(key, amount);
        }
        return amounts;
    }

    private static int readOperationMapSize(ByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MAX_OPERATION_MAP_ENTRIES) {
            throw new IllegalStateException("Invalid module operation map size: " + size);
        }
        return size;
    }

    private static void writeRecipeSnapshot(ByteBuf buf, RecipeSnapshot snapshot) {
        buf.writeInt(snapshot.duration());
        buf.writeInt(snapshot.eut());
        writeItemStacks(buf, snapshot.inputs());
        writeItemStacks(buf, snapshot.outputs());
        writeIntArray(buf, snapshot.outputChances());
        writeFluidStacks(buf, snapshot.fluidInputs());
        writeFluidStacks(buf, snapshot.fluidOutputs());
        writeIntArray(buf, snapshot.fluidOutputChances());
    }

    private static RecipeSnapshot readRecipeSnapshot(ByteBuf buf, byte recipeMapOrdinal, int recipeIndex,
        long contentHash) {
        int duration = buf.readInt();
        int eut = buf.readInt();
        ItemStack[] inputs = readItemStacks(buf);
        ItemStack[] outputs = readItemStacks(buf);
        int[] outputChances = readIntArray(buf);
        FluidStack[] fluidInputs = readFluidStacks(buf);
        FluidStack[] fluidOutputs = readFluidStacks(buf);
        int[] fluidOutputChances = readIntArray(buf);
        return new RecipeSnapshot(
            recipeMapOrdinal,
            recipeIndex,
            contentHash,
            inputs,
            outputs,
            fluidInputs,
            fluidOutputs,
            outputChances,
            fluidOutputChances,
            duration,
            eut);
    }

    private static void writeItemStacks(ByteBuf buf, ItemStack[] stacks) {
        if (stacks == null) {
            buf.writeInt(-1);
            return;
        }
        buf.writeInt(stacks.length);
        for (ItemStack stack : stacks) {
            buf.writeBoolean(stack != null);
            if (stack == null) continue;
            buf.writeInt(Item.getIdFromItem(stack.getItem()));
            buf.writeInt(stack.getItemDamage());
            buf.writeInt(stack.stackSize);
        }
    }

    private static ItemStack[] readItemStacks(ByteBuf buf) {
        int len = readRecipeArrayLength(buf);
        if (len == -1) return null;
        ItemStack[] stacks = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            if (!buf.readBoolean()) continue;
            Item item = Item.getItemById(buf.readInt());
            int damage = buf.readInt();
            int size = buf.readInt();
            stacks[i] = item != null ? new ItemStack(item, size, damage) : null;
        }
        return stacks;
    }

    private static void writeFluidStacks(ByteBuf buf, FluidStack[] stacks) {
        if (stacks == null) {
            buf.writeInt(-1);
            return;
        }
        buf.writeInt(stacks.length);
        for (FluidStack stack : stacks) {
            buf.writeBoolean(stack != null);
            if (stack == null) continue;
            PacketUtil.writeString(buf, fluidName(stack));
            buf.writeInt(stack.amount);
        }
    }

    private static FluidStack[] readFluidStacks(ByteBuf buf) {
        int len = readRecipeArrayLength(buf);
        if (len == -1) return null;
        FluidStack[] stacks = new FluidStack[len];
        for (int i = 0; i < len; i++) {
            if (!buf.readBoolean()) continue;
            String fluidName = PacketUtil.readString(buf);
            int amount = buf.readInt();
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid != null) stacks[i] = new FluidStack(fluid, amount);
        }
        return stacks;
    }

    private static void writeIntArray(ByteBuf buf, int[] values) {
        if (values == null) {
            buf.writeInt(-1);
            return;
        }
        buf.writeInt(values.length);
        for (int value : values) {
            buf.writeInt(value);
        }
    }

    private static int[] readIntArray(ByteBuf buf) {
        int len = readRecipeArrayLength(buf);
        if (len == -1) return null;
        int[] values = new int[len];
        for (int i = 0; i < len; i++) {
            values[i] = buf.readInt();
        }
        return values;
    }

    private static int readRecipeArrayLength(ByteBuf buf) {
        int len = buf.readInt();
        if (len < -1 || len > MAX_RECIPE_STACKS) {
            throw new IllegalStateException("Invalid recipe array length: " + len);
        }
        return len;
    }

    private static String fluidName(FluidStack stack) {
        try {
            Fluid fluid = stack.getFluid();
            return fluid != null ? fluid.getName() : "";
        } catch (RuntimeException e) {
            LOG.warn("[Network] Failed to resolve fluid name for synced FluidStack {}", stack, e);
            return "";
        }
    }

    private static void writeMinerSettingsPayload(ByteBuf buf, MinerSettings settings) {
        buf.writeInt(
            settings.blacklistedOreKeys()
                .size());
        for (String oreKey : settings.blacklistedOreKeys()) {
            PacketUtil.writeString(buf, oreKey);
        }
    }

    private static MinerSettings readMinerSettingsPayload(ByteBuf buf, String context) {
        int count = buf.readInt();
        if (count < 0 || count > 4096) {
            throw new IllegalStateException(
                "Network decoded invalid miner blacklist count " + count + " for " + context);
        }
        MinerSettings settings = new MinerSettings();
        for (int i = 0; i < count; i++) {
            settings.setOreBlacklisted(PacketUtil.readString(buf), true);
        }
        return settings;
    }

    private static void writeSettingsGroupPayload(ByteBuf buf, FacilityModuleKind kind, ModuleSettings settings) {
        if (kind == null) throw new IllegalStateException("Settings group kind must not be null");
        if (kind == FacilityModuleKind.MINER && settings instanceof MinerSettings minerSettings) {
            writeMinerSettingsPayload(buf, minerSettings);
            return;
        }
        if (FacilityModuleRegistry.get(kind)
            .settingsGroups() && settings instanceof RecipeModuleSettings recipeSettings) {
            writeRecipeConfigPayload(buf, recipeSettings.config());
            return;
        }
        throw new IllegalStateException("Unsupported settings group payload " + settings + " for kind " + kind);
    }

    private static ModuleSettings readSettingsGroupPayload(ByteBuf buf, FacilityModuleKind kind, String context) {
        if (kind == null) throw new IllegalStateException("Settings group kind must not be null for " + context);
        if (kind == FacilityModuleKind.MINER) {
            return readMinerSettingsPayload(buf, context);
        }
        if (FacilityModuleRegistry.get(kind)
            .settingsGroups()) {
            return new RecipeModuleSettings(readRecipeConfigPayload(buf));
        }
        throw new IllegalStateException("Unsupported settings group kind " + kind + " for " + context);
    }

    static ModuleSettings copySettingsGroupPayload(ModuleSettings settings) {
        if (settings instanceof MinerSettings minerSettings) {
            return minerSettings.copy();
        }
        if (settings instanceof RecipeModuleSettings recipeSettings) {
            return recipeSettings.copy();
        }
        throw new IllegalStateException("Unsupported settings group payload " + settings);
    }

    private static void writeLogisticsConfig(ByteBuf buf, LogisticsResourceConfig cfg) {
        buf.writeInt(cfg.minReserve());
        buf.writeInt(cfg.orderSize());
        buf.writeBoolean(cfg.isImportEnabled());
        buf.writeBoolean(cfg.isSupplyEnabled());
    }

    private static LogisticsResourceConfig readLogisticsConfig(ByteBuf buf) {
        return new LogisticsResourceConfig(buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean());
    }

    private static void writeUpkeepCredits(ByteBuf buf, UpkeepSettlement.Credits credits) {
        UpkeepSettlement.Credits safeCredits = credits == null ? UpkeepSettlement.Credits.empty() : credits;
        writeUpkeepCreditMap(buf, safeCredits.itemCredits());
        writeUpkeepCreditMap(buf, safeCredits.fluidCredits());
    }

    private static <T extends InventoryKey> void writeUpkeepCreditMap(ByteBuf buf, Map<T, UpkeepAmount> credits) {
        buf.writeInt(credits.size());
        for (Map.Entry<T, UpkeepAmount> entry : credits.entrySet()) {
            PacketUtil.writeInventoryKey(buf, entry.getKey());
            buf.writeLong(
                entry.getValue()
                    .microUnitsPerMinute());
        }
    }

    private static UpkeepSettlement.Credits readUpkeepCredits(ByteBuf buf) {
        Map<ItemStackWrapper, UpkeepAmount> itemCredits = new LinkedHashMap<>();
        int itemCount = buf.readInt();
        for (int i = 0; i < itemCount; i++) {
            InventoryKey key = PacketUtil.readInventoryKey(buf);
            UpkeepAmount amount = UpkeepAmount.ofMicroUnits(buf.readLong());
            if (key instanceof ItemStackWrapper item) {
                itemCredits.put(item, amount);
            }
        }

        Map<FluidKey, UpkeepAmount> fluidCredits = new LinkedHashMap<>();
        int fluidCount = buf.readInt();
        for (int i = 0; i < fluidCount; i++) {
            InventoryKey key = PacketUtil.readInventoryKey(buf);
            UpkeepAmount amount = UpkeepAmount.ofMicroUnits(buf.readLong());
            if (key instanceof FluidKey fluid) {
                fluidCredits.put(fluid, amount);
            }
        }
        return new UpkeepSettlement.Credits(itemCredits, fluidCredits);
    }

    private static void writeInventoryBoundsSnapshot(ByteBuf buf, Map<InventoryKey, InventoryBounds> bounds) {
        int size = bounds == null ? 0 : bounds.size();
        buf.writeInt(size);
        if (bounds == null) return;
        for (Map.Entry<InventoryKey, InventoryBounds> e : bounds.entrySet()) {
            InventoryBounds value = e.getValue();
            PacketUtil.writeInventoryKey(buf, e.getKey());
            buf.writeLong(value.low());
            buf.writeLong(value.upper());
        }
    }

    private static Map<InventoryKey, InventoryBounds> readInventoryBoundsSnapshot(ByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_INVENTORY_BOUND_SNAPSHOT_ENTRIES) {
            throw new IllegalArgumentException("Invalid inventory bounds snapshot size: " + count);
        }
        Map<InventoryKey, InventoryBounds> bounds = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            InventoryKey key = PacketUtil.readInventoryKey(buf);
            long low = buf.readLong();
            long upper = buf.readLong();
            if (key != null) {
                bounds.put(key, new InventoryBounds(low, upper));
            }
        }
        return bounds;
    }

    private static void writeRecipeConfig(ByteBuf buf, ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) {
            buf.writeBoolean(false);
            return;
        }
        writeRecipeConfigPayload(buf, recipeModule.getRecipeConfig());
    }

    private static void writeDebugDataGenerator(ByteBuf buf, ModuleInstance module) {
        ModuleDebugDataGenerator debugGenerator = (ModuleDebugDataGenerator) module.component();
        ModuleDebugDataGenerator.Config config = debugGenerator.config();
        PacketUtil.writeEnum(buf, config.mode());
        buf.writeBoolean(config.enabled());
        PacketUtil.writeEnum(buf, config.dataType());
        buf.writeLong(config.amountKb());
        buf.writeInt(config.durationTicks());
        CelestialObjectKey originBodyKey = config.originBodyKey();
        buf.writeBoolean(originBodyKey != null);
        if (originBodyKey != null) PacketUtil.writeCelestialObjectKey(buf, originBodyKey);
        buf.writeInt(debugGenerator.jobProgressTicks());
        buf.writeLong(debugGenerator.consumedDeciKb());
        CelestialObjectKey detectedCounterpartBodyKey = debugGenerator.detectedCounterpartBodyKey();
        buf.writeBoolean(detectedCounterpartBodyKey != null);
        if (detectedCounterpartBodyKey != null) PacketUtil.writeCelestialObjectKey(buf, detectedCounterpartBodyKey);
    }

    private static void readDebugDataGenerator(ByteBuf buf, ModuleInstance module) {
        if (!(module.component() instanceof ModuleDebugDataGenerator debugGenerator)) return;
        ModuleDebugDataGenerator.Mode mode = PacketUtil.readEnum(buf, ModuleDebugDataGenerator.Mode.class);
        boolean enabled = buf.readBoolean();
        SatelliteDataType dataType = PacketUtil.readEnum(buf, SatelliteDataType.class);
        long amountKb = buf.readLong();
        int durationTicks = buf.readInt();
        CelestialObjectKey originBodyKey = buf.readBoolean() ? PacketUtil.readCelestialObjectKey(buf) : null;
        int jobProgressTicks = buf.readInt();
        long consumedDeciKb = buf.readLong();
        CelestialObjectKey detectedCounterpartBodyKey = buf.readBoolean() ? PacketUtil.readCelestialObjectKey(buf)
            : null;
        debugGenerator.restore(
            new ModuleDebugDataGenerator.Config(mode, enabled, dataType, amountKb, durationTicks, originBodyKey),
            jobProgressTicks,
            consumedDeciKb,
            detectedCounterpartBodyKey);
    }

    private static void writeRecipeConfigPayload(ByteBuf buf, RecipeConfig config) {
        if (config == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeByte(
            config.mode()
                .ordinal());
        buf.writeByte(
            config.notDoablePolicy()
                .ordinal());
        buf.writeByte(config.orderCursor());
        buf.writeByte(config.orderRemaining());

        List<SavedRecipe> slots = config.savedRecipes()
            .toList();
        Map<RecipeSnapshotRef, Integer> snapshotIndexes = new LinkedHashMap<>();
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        for (SavedRecipe slot : slots) {
            RecipeSnapshot snap = slot.recipe();
            RecipeSnapshotRef ref = RecipeSnapshotRef.of(snap);
            if (!snapshotIndexes.containsKey(ref)) {
                snapshotIndexes.put(ref, snapshots.size());
                snapshots.add(snap);
            }
        }
        buf.writeByte(slots.size());
        buf.writeByte(snapshots.size());
        for (RecipeSnapshot snap : snapshots) {
            buf.writeByte(snap.recipeMapOrdinal());
            buf.writeInt(snap.recipeIndex());
            buf.writeLong(snap.contentHash());
            writeRecipeSnapshot(buf, snap);
        }
        for (SavedRecipe slot : slots) {
            RecipeSnapshot snap = slot.recipe();
            buf.writeByte(snapshotIndexes.get(RecipeSnapshotRef.of(snap)));
            buf.writeBoolean(slot.enabled());
            buf.writeLong(slot.requestAmount());
            buf.writeByte(slot.priority());
            buf.writeByte(slot.orderSize());
            PacketUtil.writeString(buf, slot.displayName());
        }
    }

    private static void readRecipeConfig(ByteBuf buf, ModuleInstance module) {
        RecipeConfig config = readRecipeConfigPayload(buf);
        if (config == null) return;
        if (module.component() instanceof IRecipeModule recipeModule) {
            recipeModule.setRecipeConfig(config);
        }
    }

    private static RecipeConfig readRecipeConfigPayload(ByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int modeOrd = Byte.toUnsignedInt(buf.readByte());
        int policyOrd = Byte.toUnsignedInt(buf.readByte());
        byte orderCursor = buf.readByte();
        byte orderRemaining = buf.readByte();

        RecipeSchedulerMode[] modes = RecipeSchedulerMode.values();
        if (modeOrd >= modes.length) return null;
        RecipeSchedulerMode mode = modes[modeOrd];

        NotDoablePolicy[] policies = NotDoablePolicy.values();
        if (policyOrd >= policies.length) return null;
        NotDoablePolicy policy = policies[policyOrd];

        int slotCount = Byte.toUnsignedInt(buf.readByte());
        if (slotCount < 0 || slotCount > SavedRecipeList.MAX_SAVED_RECIPES) return null;

        int snapshotCount = Byte.toUnsignedInt(buf.readByte());
        if (snapshotCount < 0 || snapshotCount > SavedRecipeList.MAX_SAVED_RECIPES) return null;

        List<RecipeSnapshot> snapshots = new ArrayList<>(snapshotCount);
        for (int i = 0; i < snapshotCount; i++) {
            byte mapOrdinal = buf.readByte();
            int recipeIndex = buf.readInt();
            long contentHash = buf.readLong();
            snapshots.add(readRecipeSnapshot(buf, mapOrdinal, recipeIndex, contentHash));
        }

        RecipeConfig config = new RecipeConfig(new SavedRecipeList(), mode, policy, orderCursor, orderRemaining);

        for (int i = 0; i < slotCount; i++) {
            int snapshotIndex = Byte.toUnsignedInt(buf.readByte());
            if (snapshotIndex >= snapshots.size()) return null;
            RecipeSnapshot snapshot = snapshots.get(snapshotIndex);
            boolean enabled = buf.readBoolean();
            long requestAmount = buf.readLong();
            byte priority = buf.readByte();
            byte orderSize = buf.readByte();
            String displayName = PacketUtil.readString(buf);

            SavedRecipe slot = new SavedRecipe(snapshot, enabled, requestAmount, priority, orderSize, displayName);
            config.savedRecipes()
                .add(slot);
        }

        return config;
    }

    private record RecipeSnapshotRef(byte recipeMapOrdinal, int recipeIndex, long contentHash) {

        private static RecipeSnapshotRef of(RecipeSnapshot snapshot) {
            return new RecipeSnapshotRef(snapshot.recipeMapOrdinal(), snapshot.recipeIndex(), snapshot.contentHash());
        }
    }

    AssetSyncPacket withPublishedRevision(long baseRevision, long revision) {
        this.basePublishedRevision = baseRevision;
        this.publishedRevision = revision;
        return this;
    }

    // ── Test-support: package-private accessors ──

    byte syncType() {
        return syncType;
    }

    int moduleIndex() {
        return moduleIndex;
    }

    ModuleInstance moduleData() {
        return moduleData;
    }

    StationTileCoord tileCoord() {
        return tileCoord;
    }

    StationTileState tileState() {
        return tileState;
    }

    ModuleInstance.ID tileModuleId() {
        return tileModuleId;
    }

    List<AssetSyncPacket> fullSyncDeltas() {
        return fullSyncDeltas;
    }

    int stateRevision() {
        return stateRevision;
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    long basePublishedRevision() {
        return basePublishedRevision;
    }

    long publishedRevision() {
        return publishedRevision;
    }

}
