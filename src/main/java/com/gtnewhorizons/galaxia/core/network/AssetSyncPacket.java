package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class AssetSyncPacket implements IMessage {

    public static final byte FULL_SYNC = 0;
    public static final byte MODULE_ADDED = 1;
    public static final byte MODULE_REMOVED = 2;
    public static final byte MODULE_UPDATED = 3;
    public static final byte INVENTORY_UPDATE = 4;
    public static final byte LOGISTICS_CONFIG_UPDATED = 6;
    public static final byte LOGISTICS_CONFIG_REMOVED = 7;
    public static final byte LAYOUT_TILE_UPDATED = 8;
    public static final byte LAYOUT_TILE_REMOVED = 9;

    private CelestialAsset.ID assetId;
    private byte syncType;

    private UUID teamId;
    private CelestialObjectId celestialBodyId;
    private CelestialObjectId systemId;
    private CelestialObjectId planetaryAnchorBodyId;
    private Buildable.Status assetStatus;
    private CelestialAsset.Kind assetKind;
    private long energyStored;

    private List<AssetSyncPacket> fullSyncDeltas;

    private int moduleIndex;
    private ModuleInstance.ID moduleId;
    private ModuleInstance moduleData;

    private String resourceKey;
    private long inventoryDelta;
    private LogisticsResourceConfig logConfig;

    private StationTileCoord tileCoord;
    private StationTileState tileState;
    private ModuleInstance.ID tileModuleId;

    public AssetSyncPacket() {}

    public static AssetSyncPacket fullSync(AutomatedFacility state) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = state.assetId;
        pkt.syncType = FULL_SYNC;

        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyId = state.celestialObjectId;
        pkt.systemId = state.systemId;
        pkt.planetaryAnchorBodyId = state.planetaryAnchorBodyId;
        pkt.assetStatus = state.status();
        pkt.assetKind = state.kind;
        pkt.energyStored = state.getEnergyStored();

        pkt.fullSyncDeltas = new ArrayList<>();

        List<ModuleInstance> modules = state.modules();
        for (int i = 0; i < modules.size(); i++) {
            pkt.fullSyncDeltas.add(moduleAdded(state.assetId, i, modules.get(i)));
        }

        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            pkt.fullSyncDeltas.add(
                inventoryUpdate(
                    state.assetId,
                    e.getKey()
                        .toKey(),
                    e.getValue()));
        }

        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            pkt.fullSyncDeltas.add(
                logisticsConfigUpdated(
                    state.assetId,
                    e.getKey()
                        .toKey(),
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

    public static AssetSyncPacket inventoryUpdate(CelestialAsset.ID assetId, String resourceKey, long delta) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_UPDATE;
        pkt.resourceKey = resourceKey;
        pkt.inventoryDelta = delta;
        return pkt;
    }

    public static AssetSyncPacket logisticsConfigUpdated(CelestialAsset.ID assetId, String resourceKey, int minReserve,
        int orderSize, boolean importEnabled, boolean supplyEnabled) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_UPDATED;
        pkt.resourceKey = resourceKey;
        pkt.logConfig = new LogisticsResourceConfig(minReserve, orderSize, importEnabled, supplyEnabled);
        return pkt;
    }

    public static AssetSyncPacket logisticsConfigRemoved(CelestialAsset.ID assetId, String resourceKey) {
        AssetSyncPacket pkt = new AssetSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_REMOVED;
        pkt.resourceKey = resourceKey;
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

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        buf.writeByte(syncType);

        switch (syncType) {
            case FULL_SYNC -> {
                buf.writeLong(teamId.getMostSignificantBits());
                buf.writeLong(teamId.getLeastSignificantBits());
                PacketUtil.writeEnum(buf, celestialBodyId);
                PacketUtil.writeEnum(buf, systemId);
                PacketUtil.writeEnum(buf, planetaryAnchorBodyId);
                PacketUtil.writeEnum(buf, assetStatus);
                PacketUtil.writeEnum(buf, assetKind);
                buf.writeLong(energyStored);

                buf.writeInt(fullSyncDeltas.size());
                for (AssetSyncPacket d : fullSyncDeltas) {
                    buf.writeByte(d.syncType);
                    d.writeDelta(buf);
                }
            }
            default -> writeDelta(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        syncType = buf.readByte();

        switch (syncType) {
            case FULL_SYNC -> {
                teamId = new UUID(buf.readLong(), buf.readLong());
                celestialBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                systemId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                planetaryAnchorBodyId = PacketUtil.readEnum(buf, CelestialObjectId.class);
                assetStatus = PacketUtil.readEnum(buf, Buildable.Status.class);
                assetKind = PacketUtil.readEnum(buf, CelestialAsset.Kind.class);
                energyStored = buf.readLong();

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
                PacketUtil.writeString(buf, resourceKey);
                buf.writeLong(inventoryDelta);
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                PacketUtil.writeString(buf, resourceKey);
                writeLogisticsConfig(buf, logConfig);
            }
            case LOGISTICS_CONFIG_REMOVED -> PacketUtil.writeString(buf, resourceKey);
            case LAYOUT_TILE_UPDATED -> {
                PacketUtil.writeTileCoord(buf, tileCoord);
                PacketUtil.writeEnum(buf, tileState);
                boolean hasModule = tileModuleId != null;
                buf.writeBoolean(hasModule);
                if (hasModule) PacketUtil.writeId(buf, tileModuleId);
            }
            case LAYOUT_TILE_REMOVED -> PacketUtil.writeTileCoord(buf, tileCoord);
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
                resourceKey = PacketUtil.readString(buf);
                inventoryDelta = buf.readLong();
            }
            case LOGISTICS_CONFIG_UPDATED -> {
                resourceKey = PacketUtil.readString(buf);
                logConfig = readLogisticsConfig(buf);
            }
            case LOGISTICS_CONFIG_REMOVED -> resourceKey = PacketUtil.readString(buf);
            case LAYOUT_TILE_UPDATED -> {
                tileCoord = PacketUtil.readTileCoord(buf);
                tileState = PacketUtil.readEnum(buf, StationTileState.class);
                tileModuleId = buf.readBoolean() ? PacketUtil.readModuleId(buf) : null;
            }
            case LAYOUT_TILE_REMOVED -> tileCoord = PacketUtil.readTileCoord(buf);
        }
    }

    private static void writeModule(ByteBuf buf, ModuleInstance module) {
        PacketUtil.writeId(buf, module.id);
        PacketUtil.writeEnum(buf, module.kind());
        PacketUtil.writeEnum(buf, module.status());

        switch (module.kind()) {
            case MINER -> {
                ModuleMiner m = (ModuleMiner) module.component();
                buf.writeInt(
                    m.blacklistedItemKeys()
                        .size());
                for (String k : m.blacklistedItemKeys()) PacketUtil.writeString(buf, k);
                buf.writeBoolean(m.copySettingsToOtherMiners());
            }
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
            }
            case BIG_HAMMER -> {
                ModuleHammer bh = (ModuleHammer) module.component();
                PacketUtil.writeEnum(
                    buf,
                    bh.config()
                        .mode());
                buf.writeDouble(
                    bh.config()
                        .threshold());
                buf.writeBoolean(bh.planetaryHandling());
                PacketUtil.writeEnum(buf, bh.routePriority());
            }
            case POWER -> {}
        }
    }

    private static ModuleInstance readModule(ByteBuf buf) {
        ModuleInstance.ID id = PacketUtil.readModuleId(buf);
        FacilityModuleKind kind = PacketUtil.readEnum(buf, FacilityModuleKind.class);
        Buildable.Status status = PacketUtil.readEnum(buf, Buildable.Status.class);

        ModuleInstance module = kind.createInstance(id);

        switch (kind) {
            case MINER -> {
                int c = buf.readInt();
                List<String> blacklist = new ArrayList<>(c);
                for (int i = 0; i < c; i++) blacklist.add(PacketUtil.readString(buf));
                boolean copySettings = buf.readBoolean();
                module.setComponent(new ModuleMiner(kind, blacklist, copySettings));
            }
            case HAMMER -> {
                AllowShootingConfig cfg = new AllowShootingConfig(
                    PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class),
                    buf.readDouble());
                OrbitalTransferPlanner.RoutePriority priority = PacketUtil
                    .readEnum(buf, OrbitalTransferPlanner.RoutePriority.class);
                module.setComponent(new ModuleHammer(kind, cfg, priority, false, true, false, 64));
            }
            case BIG_HAMMER -> {
                AllowShootingConfig cfg = new AllowShootingConfig(
                    PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class),
                    buf.readDouble());
                boolean planetary = buf.readBoolean();
                OrbitalTransferPlanner.RoutePriority priority = PacketUtil
                    .readEnum(buf, OrbitalTransferPlanner.RoutePriority.class);
                module.setComponent(new ModuleHammer(kind, cfg, priority, false, planetary, true, 128));
            }
            case POWER -> {}
        }

        module.updateStatus(status);
        return module;
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

    public static final class Handler implements IMessageHandler<AssetSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(AssetSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> handle(packet));
            return null;
        }

        private void handle(AssetSyncPacket packet) {
            switch (packet.syncType) {
                case FULL_SYNC -> handleFull(packet);
                default -> {
                    if (CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedFacility state) {
                        handleDelta(state, packet);
                    }
                }
            }
        }

        private void handleFull(AssetSyncPacket packet) {
            AutomatedFacility state = CelestialAssetStore.findAsset(packet.assetId) instanceof AutomatedFacility o ? o
                : null;
            if (state == null) {
                CelestialAsset newAsset = CelestialAsset
                    .create(packet.celestialBodyId, packet.assetKind, packet.assetStatus);
                if (!(newAsset instanceof AutomatedFacility newState)) return;
                state = newState;
                CelestialClient.add(newState);
            }

            state.setEnergyStored(packet.energyStored);

            state.clearModules();
            state.inventory.clear();
            state.logisticsConfig.clear();
            StationLayout layout = state.stationLayout();
            if (layout != null) layout.loadFromSnapshot(java.util.Collections.emptyMap());

            for (AssetSyncPacket d : packet.fullSyncDeltas) {
                handleDelta(state, d);
            }

            state.bumpSyncRevision();
        }

        private void handleDelta(AutomatedFacility state, AssetSyncPacket packet) {
            switch (packet.syncType) {
                case MODULE_ADDED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .set(packet.moduleIndex, packet.moduleData);
                    } else {
                        state.addModule(packet.moduleData);
                    }
                }
                case MODULE_REMOVED -> state.removeModule(packet.moduleId);
                case MODULE_UPDATED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modulesInternal()
                            .get(packet.moduleIndex)
                            .updateStatus(packet.moduleData.status());
                    }
                }
                case INVENTORY_UPDATE -> {
                    ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                    if (r != null) {
                        if (packet.inventoryDelta > 0) {
                            state.inventory.setAmount(r, state.inventory.getAmount(r) + packet.inventoryDelta);
                        } else {
                            state.inventory.setAmount(
                                r,
                                Math.max(0, state.inventory.getAmount(r) - Math.abs(packet.inventoryDelta)));
                        }
                    }
                }
                case LOGISTICS_CONFIG_UPDATED -> {
                    ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                    if (r != null) state.logisticsConfig.set(r, packet.logConfig);
                }
                case LOGISTICS_CONFIG_REMOVED -> {
                    ItemStackWrapper r = ItemStackWrapper.fromKey(packet.resourceKey);
                    if (r != null) state.logisticsConfig.reset(r);
                }
                case LAYOUT_TILE_UPDATED -> {
                    ModuleInstance module = null;
                    if (packet.tileModuleId != null) {
                        for (ModuleInstance m : state.modules()) {
                            if (m.id.equals(packet.tileModuleId)) {
                                module = m;
                                break;
                            }
                        }
                    }
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.place(packet.tileCoord, new PlacedTile(module, packet.tileState));
                }
                case LAYOUT_TILE_REMOVED -> {
                    StationLayout layout = state.stationLayout();
                    if (layout != null) layout.remove(packet.tileCoord);
                }
            }
        }
    }
}
