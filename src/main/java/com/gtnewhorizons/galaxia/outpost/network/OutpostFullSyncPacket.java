package com.gtnewhorizons.galaxia.outpost.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.outpost.OutpostModuleKind;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.outpost.module.BigHammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.PowerModuleData;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → Client: synchronizes the complete state of a single outpost.
 */
public final class OutpostFullSyncPacket implements IMessage {

    private String assetId;
    private UUID teamId;
    private String celestialBodyId;
    private String systemId;
    private long energyStored;
    private List<ModuleSyncData> modules;
    private Map<String, Long> inventory;
    private Map<String, LogisticsConfigSyncData> logisticsConfig;

    public OutpostFullSyncPacket() {}

    public OutpostFullSyncPacket(AutomatedOutpostState state) {
        this.assetId = state.assetId;
        this.teamId = state.teamId;
        this.celestialBodyId = state.celestialBodyId;
        this.systemId = state.systemId;
        this.energyStored = state.getEnergyStored();

        this.modules = new ArrayList<>();
        for (AutomatedOutpostModule m : state.modules()) {
            List<String> minerBlacklist = Collections.emptyList();
            String allowShootingMode = AllowShootingConfig.Mode.ALWAYS.name();
            double allowShootingThreshold = 0.0;
            boolean planetaryHandling = false;
            String routePriority = OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF.name();
            boolean minerCopySettings = false;
            if (m.getData() instanceof MinerModuleData minerData) {
                minerBlacklist = minerData.blacklistedItemKeys();
                minerCopySettings = minerData.copySettingsToOtherMiners();
            } else if (m.getData() instanceof HammerModuleData hd) {
                AllowShootingConfig cfg = hd.effectiveShooting();
                allowShootingMode = cfg.mode()
                    .name();
                allowShootingThreshold = cfg.threshold();
                routePriority = hd.effectiveRoutePriority()
                    .name();
            } else if (m.getData() instanceof BigHammerModuleData bd) {
                AllowShootingConfig cfg = bd.effectiveShooting();
                allowShootingMode = cfg.mode()
                    .name();
                allowShootingThreshold = cfg.threshold();
                planetaryHandling = bd.planetaryTransferHandling();
                routePriority = bd.effectiveRoutePriority()
                    .name();
            }
            modules.add(
                new ModuleSyncData(
                    m.kind.name(),
                    m.getStatus()
                        .name(),
                    m.getConstructionProgress(),
                    minerBlacklist,
                    allowShootingMode,
                    allowShootingThreshold,
                    planetaryHandling,
                    routePriority,
                    minerCopySettings));
        }

        this.inventory = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, Long> e : state.inventory.snapshot()
            .entrySet()) {
            inventory.put(
                e.getKey()
                    .toKey(),
                e.getValue());
        }

        this.logisticsConfig = new LinkedHashMap<>();
        for (Map.Entry<ItemStackWrapper, LogisticsResourceConfig> e : state.logisticsConfig.snapshot()
            .entrySet()) {
            LogisticsResourceConfig cfg = e.getValue();
            logisticsConfig.put(
                e.getKey()
                    .toKey(),
                new LogisticsConfigSyncData(
                    cfg.minReserve(),
                    cfg.orderSize(),
                    cfg.isImportEnabled(),
                    cfg.isSupplyEnabled()));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        buf.writeLong(teamId.getMostSignificantBits());
        buf.writeLong(teamId.getLeastSignificantBits());
        writeString(buf, celestialBodyId);
        writeString(buf, systemId);
        buf.writeLong(energyStored);

        buf.writeInt(modules.size());
        for (ModuleSyncData m : modules) {
            writeString(buf, m.kind);
            writeString(buf, m.status);
            buf.writeFloat(m.progress);
            buf.writeInt(m.minerBlacklist.size());
            for (String key : m.minerBlacklist) {
                writeString(buf, key);
            }
            writeString(buf, m.allowShootingMode);
            buf.writeDouble(m.allowShootingThreshold);
            buf.writeBoolean(m.planetaryHandling);
            writeString(buf, m.routePriority);
            buf.writeBoolean(m.minerCopySettings);
        }

        buf.writeInt(inventory.size());
        for (Map.Entry<String, Long> e : inventory.entrySet()) {
            writeString(buf, e.getKey());
            buf.writeLong(e.getValue());
        }

        buf.writeInt(logisticsConfig.size());
        for (Map.Entry<String, LogisticsConfigSyncData> e : logisticsConfig.entrySet()) {
            writeString(buf, e.getKey());
            buf.writeInt(e.getValue().minReserve);
            buf.writeInt(e.getValue().orderSize);
            buf.writeBoolean(e.getValue().isImportEnabled);
            buf.writeBoolean(e.getValue().isSupplyEnabled);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        teamId = new UUID(buf.readLong(), buf.readLong());
        celestialBodyId = readString(buf);
        systemId = readString(buf);
        energyStored = buf.readLong();

        int moduleCount = buf.readInt();
        modules = new ArrayList<>(moduleCount);
        for (int i = 0; i < moduleCount; i++) {
            String kind = readString(buf);
            String status = readString(buf);
            float progress = buf.readFloat();
            int blacklistCount = buf.readInt();
            List<String> minerBlacklist = new ArrayList<>(blacklistCount);
            for (int j = 0; j < blacklistCount; j++) {
                minerBlacklist.add(readString(buf));
            }
            String allowShootingMode = readString(buf);
            double allowShootingThreshold = buf.readDouble();
            boolean planetaryHandling = buf.readBoolean();
            String routePriority = readString(buf);
            boolean minerCopySettings = buf.readBoolean();
            modules.add(
                new ModuleSyncData(
                    kind,
                    status,
                    progress,
                    minerBlacklist,
                    allowShootingMode,
                    allowShootingThreshold,
                    planetaryHandling,
                    routePriority,
                    minerCopySettings));
        }

        int invCount = buf.readInt();
        inventory = new LinkedHashMap<>(invCount);
        for (int i = 0; i < invCount; i++) {
            inventory.put(readString(buf), buf.readLong());
        }

        int logCount = buf.readInt();
        logisticsConfig = new LinkedHashMap<>(logCount);
        for (int i = 0; i < logCount; i++) {
            logisticsConfig.put(
                readString(buf),
                new LogisticsConfigSyncData(buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean()));
        }
    }

    public static final class Handler implements IMessageHandler<OutpostFullSyncPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(OutpostFullSyncPacket packet, MessageContext ctx) {
            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) {
                state = new AutomatedOutpostState(
                    packet.assetId,
                    packet.teamId,
                    packet.celestialBodyId,
                    packet.systemId);
                OutpostDataStore.get()
                    .put(state);
            }
            state.setEnergyStored(packet.energyStored);

            // Rebuild modules (simple approach: clear and re-add)
            state.modulesInternal()
                .clear();
            for (ModuleSyncData md : packet.modules) {
                OutpostModuleKind kind = OutpostModuleKind.valueOf(md.kind);
                AutomatedOutpostModule m = new AutomatedOutpostModule(kind, createModuleData(kind, md));
                m.setStatus(AutomatedOutpostModule.Status.valueOf(md.status));
                m.setConstructionProgress(md.progress);
                state.addModule(m);
            }

            // Sync inventory
            Map<ItemStackWrapper, Long> invSnapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : packet.inventory.entrySet()) {
                ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                if (key != null) invSnapshot.put(key, e.getValue());
            }
            state.inventory.loadFromSnapshot(invSnapshot);

            // Sync logistics
            Map<ItemStackWrapper, LogisticsResourceConfig> logSnapshot = new LinkedHashMap<>();
            for (Map.Entry<String, LogisticsConfigSyncData> e : packet.logisticsConfig.entrySet()) {
                ItemStackWrapper key = ItemStackWrapper.fromKey(e.getKey());
                if (key != null) {
                    LogisticsConfigSyncData d = e.getValue();
                    logSnapshot.put(
                        key,
                        new LogisticsResourceConfig(d.minReserve, d.orderSize, d.isImportEnabled, d.isSupplyEnabled));
                }
            }
            state.logisticsConfig.loadFromSnapshot(logSnapshot);
            state.bumpSyncRevision();

            // Trigger UI refresh if needed
            return null;
        }
    }

    @Desugar
    private static record ModuleSyncData(String kind, String status, float progress, List<String> minerBlacklist,
        String allowShootingMode, double allowShootingThreshold, boolean planetaryHandling, String routePriority,
        boolean minerCopySettings) {}

    @Desugar
    private static record LogisticsConfigSyncData(int minReserve, int orderSize, boolean isImportEnabled,
        boolean isSupplyEnabled) {}

    private static com.gtnewhorizons.galaxia.outpost.module.OutpostModuleData createModuleData(OutpostModuleKind kind,
        ModuleSyncData syncData) {
        return switch (kind) {
            case HAMMER -> new HammerModuleData(parseAllowShooting(syncData), parseRoutePriority(syncData));
            case BIG_HAMMER -> new BigHammerModuleData(
                syncData.planetaryHandling(),
                parseAllowShooting(syncData),
                parseRoutePriority(syncData));
            case MINER -> new MinerModuleData(syncData.minerBlacklist(), syncData.minerCopySettings());
            case POWER -> new PowerModuleData();
        };
    }

    private static AllowShootingConfig parseAllowShooting(ModuleSyncData d) {
        try {
            AllowShootingConfig.Mode mode = AllowShootingConfig.Mode.valueOf(d.allowShootingMode());
            return new AllowShootingConfig(mode, d.allowShootingThreshold());
        } catch (IllegalArgumentException e) {
            return AllowShootingConfig.ALWAYS;
        }
    }

    private static OrbitalTransferPlanner.RoutePriority parseRoutePriority(ModuleSyncData d) {
        try {
            return OrbitalTransferPlanner.RoutePriority.valueOf(d.routePriority());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF;
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
