package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
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

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class AssetSyncPacket implements IMessage {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    public static final byte FULL_SYNC = 0;
    public static final byte MODULE_ADDED = 1;
    public static final byte MODULE_REMOVED = 2;
    public static final byte MODULE_UPDATED = 3;
    public static final byte INVENTORY_UPDATE = 4;
    public static final byte LOGISTICS_CONFIG_UPDATED = 6;
    public static final byte LOGISTICS_CONFIG_REMOVED = 7;
    public static final byte LAYOUT_TILE_UPDATED = 8;
    public static final byte LAYOUT_TILE_REMOVED = 9;
    public static final byte ASSET_REMOVED = 10;
    public static final byte SETTINGS_GROUP_UPDATED = 11;
    public static final byte INVENTORY_BOUND_UPDATE = 12;
    public static final byte FILTER_UPDATED = 13;
    public static final byte FILTER_REMOVED = 14;
    public static final byte CLEAR = 15;
    public static final byte INVENTORY_BOUNDS_SNAPSHOT = 16;

    private static final int MAX_OPERATION_MAP_ENTRIES = 256;
    private static final int MAX_INVENTORY_BOUND_SNAPSHOT_ENTRIES = 4096;
    private static final int MAX_RECIPE_STACKS = 64;
    private static final byte OPERATION_SPEC_TIER = 1;
    private static final byte OPERATION_SPEC_HAMMER = 2;
    private static final byte OPERATION_SPEC_MINER_FOCUS = 3;

    private CelestialAsset.ID assetId;
    private byte syncType;

    private int syncRevision;

    private UUID teamId;
    private CelestialObjectId celestialBodyId;
    private CelestialObjectId systemId;
    private CelestialObjectId planetaryAnchorBodyId;
    private Buildable.Status assetStatus;
    private CelestialAsset.Kind assetKind;
    private String displayName;
    private long energyStored;
    private long stationFeatureSalt;
    private UpkeepSettlement.Credits upkeepCredits = UpkeepSettlement.Credits.empty();

    private List<AssetSyncPacket> fullSyncDeltas;

    private int moduleIndex;
    private ModuleInstance.ID moduleId;
    private ModuleInstance moduleData;

    @Deprecated
    private String resourceKey;
    private InventoryKey resource;
    private long inventoryDelta;
    private BoundKind inventoryBoundKind;
    private boolean inventoryBoundPresent;
    private long inventoryBoundAmount;
    private Map<InventoryKey, InventoryBounds> inventoryBoundSnapshot;
    private LogisticsResourceConfig logConfig;

    private StationTileCoord tileCoord;
    private StationTileState tileState;
    private ModuleInstance.ID tileModuleId;

    private BlockPos stationControllerPos;

    private short settingsGroupId;
    private FacilityModuleKind settingsGroupKind;
    private String settingsGroupName;
    private boolean settingsGroupJoinable;
    private ModuleSettings settingsGroupSettings;

    private boolean filterItem;
    private List<String> filterItems;

    public AssetSyncPacket() {}

    public static AssetSyncPacket fullSync(CelestialAsset state) {
        if (state instanceof AutomatedFacility) {
            return fullSync((AutomatedFacility) state);
        } else if (state instanceof Station) {
            return fullSync((Station) state);
        }
        throw new IllegalStateException("Unexpected value: " + state);
    }

    public static AssetSyncPacket fullSync(Station state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.assetKind = state.kind;
        pkt.syncType = FULL_SYNC;
        pkt.syncRevision = state.getSyncRevision();
        pkt.assetStatus = state.status();

        pkt.celestialBodyId = state.celestialObjectId;
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

    public static AssetSyncPacket fullSync(AutomatedFacility state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.assetKind = state.kind;
        pkt.syncType = FULL_SYNC;
        pkt.syncRevision = state.getSyncRevision();
        pkt.assetStatus = state.status();

        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyId = state.celestialObjectId;
        pkt.systemId = state.systemId;
        pkt.planetaryAnchorBodyId = state.planetaryAnchorBodyId;
        pkt.energyStored = state.getEnergyStored();
        pkt.stationFeatureSalt = state.stationFeatureSalt();
        pkt.upkeepCredits = state.upkeepCredits();
        pkt.fullSyncDeltas = new ArrayList<>();

        state.settingsGroups()
            .groups()
            .values()
            .stream()
            .sorted(java.util.Comparator.comparingInt(SettingsGroup::id))
            .forEach(group -> pkt.fullSyncDeltas.add(settingsGroupUpdated(state.assetId, group)));

        for (Map.Entry<Boolean, List<String>> e : state.filtersSnapshot()
            .entrySet()) {
            pkt.fullSyncDeltas.add(filterUpdated(state.assetId, e.getKey(), e.getValue()));
        }

        List<ModuleInstance> modules = state.modules();
        for (int i = 0; i < modules.size(); i++) {
            pkt.fullSyncDeltas.add(moduleAdded(state.assetId, i, modules.get(i)));
        }

        for (Map.Entry<ItemStackWrapper, Long> e : state.itemSnapshot()
            .entrySet()) {
            pkt.fullSyncDeltas.add(inventoryUpdate(state.assetId, e.getKey(), e.getValue()));
        }
        AssetSyncPacket boundsSnapshot = inventoryBoundsSnapshot(
            state.assetId,
            state.getBounds(true),
            state.getBounds(false));
        if (!boundsSnapshot.inventoryBoundSnapshot.isEmpty()) {
            pkt.fullSyncDeltas.add(boundsSnapshot);
        }

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

        StationLayout layout = state.stationLayout();
        if (layout != null) {
            for (Map.Entry<StationTileCoord, PlacedTile> e : layout.snapshot()
                .entrySet()) {
                pkt.fullSyncDeltas.add(layoutTileUpdated(state.assetId, e.getKey(), e.getValue()));
            }
        }

        return pkt;
    }

    public static AssetSyncPacket clear() {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.syncType = CLEAR;
        return pkt;
    }

    public static AssetSyncPacket assetRemoved(CelestialAsset.ID assetId) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = ASSET_REMOVED;
        return pkt;
    }

    public static AssetSyncPacket moduleAdded(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_ADDED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    public static AssetSyncPacket moduleRemoved(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_REMOVED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        return pkt;
    }

    public static AssetSyncPacket moduleUpdated(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance module) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_UPDATED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    public static AssetSyncPacket inventoryUpdate(CelestialAsset.ID assetId, InventoryKey resource, long delta) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_UPDATE;
        pkt.resource = resource;
        pkt.inventoryDelta = delta;
        return pkt;
    }

    public static AssetSyncPacket inventoryBoundUpdate(CelestialAsset.ID assetId, BoundKind kind, InventoryKey resource,
        boolean present, long amount) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_BOUND_UPDATE;
        pkt.inventoryBoundKind = kind;
        pkt.resource = resource;
        pkt.inventoryBoundPresent = present;
        pkt.inventoryBoundAmount = amount;
        return pkt;
    }

    private static AssetSyncPacket inventoryBoundsSnapshot(CelestialAsset.ID assetId,
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

    public static AssetSyncPacket logisticsConfigUpdated(CelestialAsset.ID assetId, InventoryKey resource,
        int minReserve, int orderSize, boolean importEnabled, boolean supplyEnabled) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_UPDATED;
        pkt.resource = resource;
        pkt.logConfig = new LogisticsResourceConfig(minReserve, orderSize, importEnabled, supplyEnabled);
        return pkt;
    }

    public static AssetSyncPacket logisticsConfigRemoved(CelestialAsset.ID assetId, InventoryKey resource) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_REMOVED;
        pkt.resource = resource;
        return pkt;
    }

    public static AssetSyncPacket settingsGroupUpdated(CelestialAsset.ID assetId, SettingsGroup group) {
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

    public static AssetSyncPacket layoutTileUpdated(CelestialAsset.ID assetId, StationTileCoord coord,
        PlacedTile tile) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LAYOUT_TILE_UPDATED;
        pkt.tileCoord = coord;
        pkt.tileState = tile.state();
        pkt.tileModuleId = tile.module() == null ? null : tile.module().id;
        return pkt;
    }

    public static AssetSyncPacket layoutTileRemoved(CelestialAsset.ID assetId, StationTileCoord coord) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LAYOUT_TILE_REMOVED;
        pkt.tileCoord = coord;
        return pkt;
    }

    public static AssetSyncPacket filterUpdated(CelestialAsset.ID assetId, boolean item, List<String> filters) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = FILTER_UPDATED;
        pkt.filterItem = item;
        pkt.filterItems = filters == null ? List.of() : filters;
        return pkt;
    }

    public static AssetSyncPacket filterRemoved(CelestialAsset.ID assetId, boolean item) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = FILTER_REMOVED;
        pkt.filterItem = item;
        return pkt;
    }

    /**
     * Decides what to sync for the given facility and player. Returns a list of packets
     * (full sync or individual deltas) and updates the facility's dirty/sync state.
     */
    public static List<AssetSyncPacket> figureOutWhatToSend(CelestialAsset asset, UUID playerId) {
        List<AssetSyncPacket> packets = new ArrayList<>();
        if (asset instanceof AutomatedFacility facility) {
            if (facility.needsFullSyncFor(playerId)) {
                packets.add(fullSync(facility));
                facility.markSyncedFor(playerId);
                facility.drainDirtyModules();
                facility.drainRemovedIds();
                facility.drainDirtyInventoryDeltas();
                facility.drainDirtyInventoryBoundDeltas();
                return packets;
            }
            if (!facility.isDirty()) {
                return packets;
            }
            for (ModuleInstance.ID id : facility.drainRemovedIds()) {
                packets.add(
                    moduleRemoved(facility.assetId, facility.moduleIndex(id), id)
                        .withSyncRevision(facility.getSyncRevision()));
            }
            for (ModuleInstance m : facility.drainDirtyModules()) {
                int idx = facility.moduleIndex(m.id);
                packets.add(moduleAdded(facility.assetId, idx, m).withSyncRevision(facility.getSyncRevision()));
            }
            for (Map.Entry<InventoryKey, Long> delta : facility.drainDirtyInventoryDeltas()
                .entrySet()) {
                packets.add(
                    inventoryUpdate(facility.assetId, delta.getKey(), delta.getValue())
                        .withSyncRevision(facility.getSyncRevision()));
            }
            for (CelestialAsset.InventoryBoundDelta delta : facility.drainDirtyInventoryBoundDeltas()) {
                packets.add(
                    inventoryBoundUpdate(
                        facility.assetId,
                        delta.kind(),
                        delta.resource(),
                        delta.present(),
                        delta.amount()).withSyncRevision(facility.getSyncRevision()));
            }
        } else if (asset instanceof Station station) {
            if (station.needsFullSyncFor(playerId)) {
                packets.add(fullSync(station));
                station.markSyncedFor(playerId);
            }
        }
        return packets;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(syncType);
        buf.writeInt(syncRevision);

        if (syncType != CLEAR) {
            PacketUtil.writeId(buf, assetId);
        }
        switch (syncType) {
            case FULL_SYNC -> {
                PacketUtil.writeEnum(buf, assetKind);
                PacketUtil.writeEnum(buf, assetStatus);
                PacketUtil.writeString(buf, displayName == null ? "" : displayName);

                switch (assetKind) {
                    case STATION -> {
                        PacketUtil.writeEnum(buf, celestialBodyId);
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
                        PacketUtil.writeEnum(buf, celestialBodyId);
                        PacketUtil.writeEnum(buf, systemId);
                        PacketUtil.writeEnum(buf, planetaryAnchorBodyId);
                        buf.writeLong(energyStored);
                        buf.writeLong(stationFeatureSalt);
                        writeUpkeepCredits(buf, upkeepCredits);

                        buf.writeInt(fullSyncDeltas.size());
                        for (AssetSyncPacket d : fullSyncDeltas) {
                            buf.writeByte(d.syncType);
                            d.writeDelta(buf);
                        }
                    }
                }
            }
            default -> writeDelta(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        syncType = buf.readByte();
        syncRevision = buf.readInt();

        if (syncType != CLEAR) {
            assetId = PacketUtil.readAssetId(buf);
        }
        switch (syncType) {
            case FULL_SYNC -> {
                assetKind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
                assetStatus = PacketUtil.readEnum(buf, Buildable.Status.class);
                displayName = PacketUtil.readString(buf);

                switch (assetKind) {
                    case STATION -> {
                        celestialBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                        if (assetStatus == Buildable.Status.OPERATIONAL) {
                            stationControllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                        }
                        int count = buf.readInt();
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
                        celestialBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                        systemId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                        planetaryAnchorBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                        energyStored = buf.readLong();
                        stationFeatureSalt = buf.readLong();
                        upkeepCredits = readUpkeepCredits(buf);

                        int count = buf.readInt();
                        fullSyncDeltas = new ArrayList<>(count);

                        for (int i = 0; i < count; i++) {
                            AssetSyncPacket d = new AssetSyncPacket();
                            d.assetId = assetId;
                            d.syncType = buf.readByte();
                            d.readDelta(buf);
                            fullSyncDeltas.add(d);
                        }
                    }
                }
            }
            default -> readDelta(buf);
        }
    }

    private void writeDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                buf.writeInt(moduleIndex);
                writeModule(buf, moduleData);
            }
            case MODULE_REMOVED -> {
                buf.writeInt(moduleIndex);
                PacketUtil.writeId(buf, moduleId);
            }
            case INVENTORY_UPDATE -> {
                PacketUtil.writeInventoryKey(buf, resource);
                buf.writeLong(inventoryDelta);
            }
            case INVENTORY_BOUND_UPDATE -> {
                PacketUtil.writeEnum(buf, inventoryBoundKind);
                PacketUtil.writeInventoryKey(buf, resource);
                buf.writeBoolean(inventoryBoundPresent);
                buf.writeLong(inventoryBoundAmount);
            }
            case INVENTORY_BOUNDS_SNAPSHOT -> writeInventoryBoundsSnapshot(buf, inventoryBoundSnapshot);
            case LOGISTICS_CONFIG_UPDATED -> {
                PacketUtil.writeInventoryKey(buf, resource);
                writeLogisticsConfig(buf, logConfig);
            }
            case LOGISTICS_CONFIG_REMOVED -> PacketUtil.writeInventoryKey(buf, resource);
            case LAYOUT_TILE_UPDATED -> {
                PacketUtil.writeStationTileCoord(buf, tileCoord);
                PacketUtil.writeEnum(buf, tileState);
                boolean hasModule = tileModuleId != null;
                buf.writeBoolean(hasModule);
                if (hasModule) PacketUtil.writeId(buf, tileModuleId);
            }
            case LAYOUT_TILE_REMOVED -> PacketUtil.writeStationTileCoord(buf, tileCoord);
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
            case FILTER_REMOVED -> buf.writeBoolean(filterItem);
        }
    }

    private void readDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                moduleIndex = buf.readInt();
                moduleData = readModule(buf);
            }
            case MODULE_REMOVED -> {
                moduleIndex = buf.readInt();
                moduleId = PacketUtil.readModuleId(buf);
            }
            case INVENTORY_UPDATE -> {
                resource = PacketUtil.readInventoryKey(buf);
                inventoryDelta = buf.readLong();
            }
            case INVENTORY_BOUND_UPDATE -> {
                inventoryBoundKind = PacketUtil.readEnum(buf, BoundKind.class);
                resource = PacketUtil.readInventoryKey(buf);
                inventoryBoundPresent = buf.readBoolean();
                inventoryBoundAmount = buf.readLong();
            }
            case INVENTORY_BOUNDS_SNAPSHOT -> inventoryBoundSnapshot = readInventoryBoundsSnapshot(buf);
            case LOGISTICS_CONFIG_UPDATED -> {
                resource = PacketUtil.readInventoryKey(buf);
                logConfig = readLogisticsConfig(buf);
            }
            case LOGISTICS_CONFIG_REMOVED -> resource = PacketUtil.readInventoryKey(buf);
            case LAYOUT_TILE_UPDATED -> {
                tileCoord = PacketUtil.readStationTileCoord(buf);
                tileState = PacketUtil.readEnum(buf, StationTileState.class);
                tileModuleId = buf.readBoolean() ? PacketUtil.readModuleId(buf) : null;
            }
            case LAYOUT_TILE_REMOVED -> tileCoord = PacketUtil.readStationTileCoord(buf);
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
            case FILTER_REMOVED -> filterItem = buf.readBoolean();
        }
    }

    private static void writeModule(ByteBuf buf, ModuleInstance module) {
        PacketUtil.writeId(buf, module.id);
        PacketUtil.writeEnum(buf, module.kind());
        PacketUtil.writeEnum(buf, module.status());
        PacketUtil.writeEnum(buf, module.tier());
        PacketUtil.writeEnum(buf, module.shape());
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
        ModulePriority modulePriority = PacketUtil.readEnum(buf, ModulePriority.class);
        boolean enabled = buf.readBoolean();
        short groupId = buf.readShort();
        byte parallel = buf.readByte();
        StationTileCoord anchor = buf.readBoolean() ? PacketUtil.readStationTileCoord(buf) : null;

        ModuleInstance module = FacilityModuleRegistry.create(id, kind, anchor, shape, tier);
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

    private static ModuleSettings copySettingsGroupPayload(ModuleSettings settings) {
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

    public AssetSyncPacket withSyncRevision(int rev) {
        this.syncRevision = rev;
        return this;
    }

    // ── Test-support: package-private accessors ──

    byte syncType() {
        return syncType;
    }

    int moduleIndex() {
        return moduleIndex;
    }

    ModuleInstance.ID moduleId() {
        return moduleId;
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

    int syncRevision() {
        return syncRevision;
    }

    public static final class Handler implements IMessageHandler<AssetSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AssetSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> handleClientSync(packet));
            return null;
        }

        public static void handleClientSync(AssetSyncPacket packet) {
            switch (packet.syncType) {
                case CLEAR -> CelestialAssetStore.CLIENT.clearInternal();
                case ASSET_REMOVED -> CelestialAssetStore.CLIENT.destroyAssetInternal(packet.assetId);
                case FULL_SYNC -> handleFull(packet);
                default -> {
                    CelestialAsset asset = CelestialAssetStore.CLIENT.findAssetInternal(packet.assetId);
                    if (asset instanceof AutomatedFacility state) {
                        handleDelta(state, packet);
                        state.setSyncRevision(Math.max(state.getSyncRevision(), packet.syncRevision));
                    } else if (asset instanceof Station station) {
                        if (packet.syncType == LOGISTICS_CONFIG_UPDATED) {
                            if (packet.resource != null) {
                                station.logisticsConfig.set(packet.resource, packet.logConfig);
                            }
                        } else if (packet.syncType == LOGISTICS_CONFIG_REMOVED) {
                            if (packet.resource != null) {
                                station.logisticsConfig.reset(packet.resource);
                            }
                        }
                        station.setSyncRevision(Math.max(station.getSyncRevision(), packet.syncRevision));
                    }
                }
            }
        }

        public static void handleFull(AssetSyncPacket packet) {
            CelestialAsset asset = CelestialAssetStore.CLIENT.findAssetInternal(packet.assetId);
            switch (packet.assetKind) {
                case STATION -> {
                    Station station = asset instanceof Station s ? s : null;
                    if (station == null) {
                        station = new Station(packet.assetId, packet.celestialBodyId, packet.assetStatus);
                        CelestialClient.add(station);
                        asset = station;
                    }
                    station.setController(packet.stationControllerPos);
                    for (AssetSyncPacket d : packet.fullSyncDeltas) {
                        handleDelta(station, d);
                    }
                }
                case AUTOMATED_OUTPOST, AUTOMATED_STATION -> {
                    AutomatedFacility state = asset instanceof AutomatedFacility o ? o : null;
                    if (state == null) {
                        CelestialAsset newAsset = CelestialAsset
                            .create(packet.assetId, packet.celestialBodyId, packet.assetKind, packet.assetStatus);
                        if (!(newAsset instanceof AutomatedFacility newState)) return;
                        state = newState;
                        CelestialAssetStore.CLIENT.registerAssetInternal(packet.teamId, newState);
                        asset = newState;
                    }

                    state.setEnergyStored(packet.energyStored);
                    state.setStationFeatureSalt(packet.stationFeatureSalt);
                    state.loadUpkeepCredits(packet.upkeepCredits);

                    state.clearModules();
                    state.settingsGroups()
                        .clear();
                    state.clear();
                    state.logisticsConfig.clear();
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.loadFromSnapshot(Collections.emptyMap());

                    for (AssetSyncPacket d : packet.fullSyncDeltas) {
                        handleDelta(state, d);
                    }

                }
            }

            if (!packet.displayName.isBlank()) {
                asset.setDisplayName(packet.displayName);
            }
            asset.updateStatus(packet.assetStatus);
            asset.setSyncRevision(packet.syncRevision);
        }

        public static void handleDelta(CelestialAsset asset, AssetSyncPacket packet) {
            switch (packet.syncType) {
                case MODULE_ADDED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .set(packet.moduleIndex, packet.moduleData);
                    } else {
                        state.addModule(packet.moduleData);
                    }
                    // Place layout tiles for the module on the client mirror
                    StationLayout layout = state.stationLayout();
                    ModuleInstance module = packet.moduleData;
                    if (layout != null && module.anchorOrNull() != null) {
                        layout.place(module);
                    }
                    syncModuleGroupMembership(state, module);
                }
                case MODULE_REMOVED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    state.removeModule(packet.moduleId);
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.removeTileForModule(packet.moduleId);
                }
                case MODULE_UPDATED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .set(packet.moduleIndex, packet.moduleData);
                        StationLayout layout = state.stationLayout();
                        if (layout != null && packet.moduleData.anchorOrNull() != null) {
                            layout.place(packet.moduleData);
                        }
                        syncModuleGroupMembership(state, packet.moduleData);
                    }
                }
                case INVENTORY_UPDATE -> {
                    if (packet.resource != null) {
                        asset.updateContents(packet.resource, packet.inventoryDelta);
                    }
                }
                case INVENTORY_BOUND_UPDATE -> {
                    final boolean isLow = packet.inventoryBoundKind == BoundKind.ITEM_LOWER
                        || packet.inventoryBoundKind == BoundKind.FLUID_LOWER;
                    if (packet.inventoryBoundPresent) {
                        if (packet.resource != null) {
                            asset.setBound(packet.resource, packet.inventoryBoundAmount, isLow);
                        }
                    } else {
                        if (packet.resource != null) {
                            asset.clearBound(packet.resource, isLow);
                        }
                    }
                }
                case INVENTORY_BOUNDS_SNAPSHOT -> {
                    if (packet.inventoryBoundSnapshot != null) {
                        for (Map.Entry<InventoryKey, InventoryBounds> e : packet.inventoryBoundSnapshot.entrySet()) {
                            InventoryKey key = e.getKey();
                            InventoryBounds bounds = e.getValue();
                            if (bounds.hasLow()) {
                                asset.setBound(key, bounds.low(), true);
                            }
                            if (bounds.hasUpper()) {
                                asset.setBound(key, bounds.upper(), false);
                            }
                        }
                    }
                }
                case LOGISTICS_CONFIG_UPDATED -> {
                    if (packet.resource != null) {
                        asset.logisticsConfig.set(packet.resource, packet.logConfig);
                    }
                }
                case LOGISTICS_CONFIG_REMOVED -> {
                    if (packet.resource != null) {
                        asset.logisticsConfig.reset(packet.resource);
                    }
                }
                case LAYOUT_TILE_UPDATED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    ModuleInstance module = findModuleById(state, packet.tileModuleId);
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.place(packet.tileCoord, new PlacedTile(module, packet.tileState));
                }
                case LAYOUT_TILE_REMOVED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.remove(packet.tileCoord);
                }
                case SETTINGS_GROUP_UPDATED -> {
                    if (!(asset instanceof AutomatedFacility state)) {
                        throw new IllegalStateException("Wrong delta packet target");
                    }
                    state.settingsGroups()
                        .sync(
                            packet.settingsGroupId,
                            packet.settingsGroupKind,
                            packet.settingsGroupName,
                            packet.settingsGroupJoinable,
                            copySettingsGroupPayload(packet.settingsGroupSettings));
                    state.applySettingsGroupsToModules();
                }
                case FILTER_UPDATED -> {
                    if (asset instanceof AutomatedFacility af) af.setFilters(packet.filterItems, packet.filterItem);
                }
                case FILTER_REMOVED -> {
                    if (asset instanceof AutomatedFacility af) af.clearFilters(packet.filterItem);
                }
            }
        }

        static ModuleInstance findModuleById(AutomatedFacility state, ModuleInstance.ID id) {
            if (id == null) return null;
            for (ModuleInstance m : state.modules()) {
                if (m.id.equals(id)) return m;
            }
            return null;
        }

        private static void syncModuleGroupMembership(AutomatedFacility state, ModuleInstance module) {
            if (module.groupId() == 0 || module.anchorOrNull() == null) return;
            SettingsGroup group = state.settingsGroups()
                .get(module.groupId());
            if (group == null) {
                throw new IllegalStateException(
                    "Client received module " + module.id + " for missing settings group " + module.groupId());
            }
            if (!group.members()
                .contains(module.anchorOrNull())) {
                state.settingsGroups()
                    .addMember(module.groupId(), module.anchor());
            }
        }

    }
}
