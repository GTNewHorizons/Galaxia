package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePower;
import com.gtnewhorizons.galaxia.registry.outpost.module.OutpostModuleKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

public final class OutpostSyncPacket implements IMessage {

    public static final byte FULL_SYNC = 0;
    public static final byte MODULE_ADDED = 1;
    public static final byte MODULE_REMOVED = 2;
    public static final byte MODULE_UPDATED = 3;
    public static final byte INVENTORY_UPDATE = 4;
    public static final byte LOGISTICS_CONFIG_UPDATED = 6;
    public static final byte LOGISTICS_CONFIG_REMOVED = 7;

    private CelestialAsset.ID assetId;
    private byte syncType;

    // FULL SYNC HEADER
    private UUID teamId;
    private CelestialObjectId celestialBodyId;
    private CelestialObjectId systemId;
    private CelestialObjectId planetaryAnchorBodyId;
    private Buildable.Status assetStatus;
    private long energyStored;

    // FULL SYNC AS DELTAS
    private List<OutpostSyncPacket> fullSyncDeltas;

    // MODULE DELTAS
    private int moduleIndex;
    private AutomatedOutpostModule moduleData;

    // INVENTORY / LOGISTICS DELTAS
    private String resourceKey;
    private long inventoryDelta;
    private LogisticsResourceConfig logConfig;

    public OutpostSyncPacket() {}

    public static OutpostSyncPacket fullSync(AutomatedOutpost state) {
        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = state.assetId;
        pkt.syncType = FULL_SYNC;

        pkt.teamId = CelestialAssetStore.getTeamId(state.assetId);
        pkt.celestialBodyId = state.celestialObjectId;
        pkt.systemId = state.systemId;
        pkt.planetaryAnchorBodyId = state.planetaryAnchorBodyId;
        pkt.assetStatus = state.status();
        pkt.energyStored = state.getEnergyStored();

        pkt.fullSyncDeltas = new ArrayList<>();

        List<AutomatedOutpostModule> modules = state.modules();
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

        return pkt;
    }

    public static OutpostSyncPacket moduleAdded(CelestialAsset.ID assetId, int moduleIndex,
        AutomatedOutpostModule module) {
        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_ADDED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    public static OutpostSyncPacket moduleRemoved(CelestialAsset.ID assetId, int moduleIndex) {
        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_REMOVED;
        pkt.moduleIndex = moduleIndex;
        return pkt;
    }

    public static OutpostSyncPacket moduleUpdated(CelestialAsset.ID assetId, int moduleIndex,
        AutomatedOutpostModule module) {
        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = MODULE_UPDATED;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleData = module;
        return pkt;
    }

    public static OutpostSyncPacket inventoryUpdate(CelestialAsset.ID assetId, String resourceKey, long delta) {
        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = INVENTORY_UPDATE;
        pkt.resourceKey = resourceKey;
        pkt.inventoryDelta = delta;
        return pkt;
    }

    public static OutpostSyncPacket logisticsConfigUpdated(CelestialAsset.ID assetId, String resourceKey,
        int minReserve, int orderSize, boolean importEnabled, boolean supplyEnabled) {

        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_UPDATED;
        pkt.resourceKey = resourceKey;
        pkt.logConfig = new LogisticsResourceConfig(minReserve, orderSize, importEnabled, supplyEnabled);
        return pkt;
    }

    public static OutpostSyncPacket logisticsConfigRemoved(CelestialAsset.ID assetId, String resourceKey) {
        OutpostSyncPacket pkt = new OutpostSyncPacket();
        pkt.assetId = assetId;
        pkt.syncType = LOGISTICS_CONFIG_REMOVED;
        pkt.resourceKey = resourceKey;
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
                buf.writeLong(energyStored);

                buf.writeInt(fullSyncDeltas.size());
                for (OutpostSyncPacket d : fullSyncDeltas) {
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
                PacketUtil.readEnum(buf, Buildable.Status.class);
                energyStored = buf.readLong();

                int count = buf.readInt();
                fullSyncDeltas = new ArrayList<>(count);

                for (int i = 0; i < count; i++) {
                    OutpostSyncPacket d = new OutpostSyncPacket();
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
            case MODULE_REMOVED -> buf.writeInt(moduleIndex);

            case INVENTORY_UPDATE -> {
                PacketUtil.writeString(buf, resourceKey);
                buf.writeLong(inventoryDelta);
            }

            case LOGISTICS_CONFIG_UPDATED -> {
                PacketUtil.writeString(buf, resourceKey);
                writeLogisticsConfig(buf, logConfig);
            }

            case LOGISTICS_CONFIG_REMOVED -> PacketUtil.writeString(buf, resourceKey);
        }
    }

    private void readDelta(ByteBuf buf) {
        switch (syncType) {
            case MODULE_ADDED, MODULE_UPDATED -> {
                moduleIndex = buf.readInt();
                moduleData = readModule(buf);
            }
            case MODULE_REMOVED -> moduleIndex = buf.readInt();

            case INVENTORY_UPDATE -> {
                resourceKey = PacketUtil.readString(buf);
                inventoryDelta = buf.readLong();
            }

            case LOGISTICS_CONFIG_UPDATED -> {
                resourceKey = PacketUtil.readString(buf);
                logConfig = readLogisticsConfig(buf);
            }

            case LOGISTICS_CONFIG_REMOVED -> resourceKey = PacketUtil.readString(buf);
        }
    }

    private static void writeModule(ByteBuf buf, AutomatedOutpostModule module) {
        PacketUtil.writeEnum(buf, module.getKind());
        PacketUtil.writeEnum(buf, module.status());

        switch (module.getKind()) {
            case MINER -> {
                ModuleMiner m = (ModuleMiner) module;
                buf.writeInt(m.blacklistedItemKeys.size());
                for (String k : m.blacklistedItemKeys) PacketUtil.writeString(buf, k);
                buf.writeBoolean(m.getCopySettingsToOtherMiners());
            }
            case HAMMER -> {
                ModuleHammer h = (ModuleHammer) module;
                PacketUtil.writeEnum(
                    buf,
                    h.getConfig()
                        .mode());
                buf.writeDouble(
                    h.getConfig()
                        .threshold());
                PacketUtil.writeEnum(buf, h.getRoutePriority());
            }
            case BIG_HAMMER -> {
                ModuleBigHammer bh = (ModuleBigHammer) module;
                PacketUtil.writeEnum(
                    buf,
                    bh.getConfig()
                        .mode());
                buf.writeDouble(
                    bh.getConfig()
                        .threshold());
                buf.writeBoolean(bh.getPlanetaryHandling());
                PacketUtil.writeEnum(buf, bh.getRoutePriority());
            }
            case POWER -> {}
        }
    }

    private static AutomatedOutpostModule readModule(ByteBuf buf) {
        OutpostModuleKind kind = PacketUtil.readEnum(buf, OutpostModuleKind.class);
        AutomatedOutpostModule.Status status = PacketUtil.readEnum(buf, AutomatedOutpostModule.Status.class);

        AutomatedOutpostModule module = switch (kind) {
            case MINER -> {
                int c = buf.readInt();
                List<String> blacklist = new ArrayList<>(c);
                for (int i = 0; i < c; i++) blacklist.add(PacketUtil.readString(buf));
                yield new ModuleMiner(blacklist).withCopySettingsToOtherMiners(buf.readBoolean());
            }
            case HAMMER -> {
                AllowShootingConfig cfg = new AllowShootingConfig(
                    PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class),
                    buf.readDouble());
                yield new ModuleHammer(cfg, PacketUtil.readEnum(buf, OrbitalTransferPlanner.RoutePriority.class));
            }
            case BIG_HAMMER -> {
                AllowShootingConfig cfg = new AllowShootingConfig(
                    PacketUtil.readEnum(buf, AllowShootingConfig.Mode.class),
                    buf.readDouble());
                boolean planetary = buf.readBoolean();
                yield new ModuleBigHammer(
                    planetary,
                    cfg,
                    PacketUtil.readEnum(buf, OrbitalTransferPlanner.RoutePriority.class));
            }
            case POWER -> new ModulePower();
        };

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

    public static final class Handler implements IMessageHandler<OutpostSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OutpostSyncPacket packet, MessageContext ctx) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> handle(packet));
            return null;
        }

        private void handle(OutpostSyncPacket packet) {
            switch (packet.syncType) {
                case FULL_SYNC -> handleFull(packet);
                default -> {
                    if (CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedOutpost state) {
                        handleDelta(state, packet);
                    }
                }
            }
        }

        private void handleFull(OutpostSyncPacket packet) {
            AutomatedOutpost state = CelestialAssetStore.findAsset(packet.assetId) instanceof AutomatedOutpost o ? o
                : null;
            if (state == null) {
                CelestialAsset newAsset = CelestialAsset
                    .create(packet.celestialBodyId, CelestialAsset.Kind.AUTOMATED_OUTPOST, packet.assetStatus);
                if (!(newAsset instanceof AutomatedOutpost newState)) return;
                state = newState;
                CelestialClient.add(newState);
            }

            state.setEnergyStored(packet.energyStored);

            state.clearModules();
            state.inventory.clear();
            state.logisticsConfig.clear();

            for (OutpostSyncPacket d : packet.fullSyncDeltas) {
                handleDelta(state, d);
            }

            state.bumpSyncRevision();
        }

        private void handleDelta(AutomatedOutpost state, OutpostSyncPacket packet) {
            switch (packet.syncType) {
                case MODULE_ADDED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modules()
                            .set(packet.moduleIndex, packet.moduleData);
                    } else {
                        state.addModule(packet.moduleData);
                    }
                }
                case MODULE_REMOVED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        state.modules()
                            .remove(packet.moduleIndex);
                    }
                }
                case MODULE_UPDATED -> {
                    if (packet.moduleIndex < state.modules()
                        .size()) {
                        applyModuleUpdate(
                            state.modules()
                                .get(packet.moduleIndex),
                            packet.moduleData);
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
            }
        }

        private void applyModuleUpdate(AutomatedOutpostModule target, AutomatedOutpostModule source) {
            target.updateStatus(source.status());

            if (target instanceof ModuleMiner tm && source instanceof ModuleMiner sm) {
                tm.blacklistedItemKeys.clear();
                tm.blacklistedItemKeys.addAll(sm.blacklistedItemKeys);
                tm.withCopySettingsToOtherMiners(sm.getCopySettingsToOtherMiners());
            }

            if (target instanceof ModuleHammer th && source instanceof ModuleHammer sh) {
                th.setConfig(sh.getConfig());
                th.setPriority(sh.getRoutePriority());
            }

            if (target instanceof ModuleBigHammer tbh && source instanceof ModuleBigHammer sbh) {
                tbh.setPlanetaryHandling(sbh.getPlanetaryHandling());
            }
        }
    }
}
