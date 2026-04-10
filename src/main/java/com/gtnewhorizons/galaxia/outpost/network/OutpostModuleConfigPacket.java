package com.gtnewhorizons.galaxia.outpost.network;

import java.nio.charset.StandardCharsets;

import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.outpost.AutomatedOutpostState;
import com.gtnewhorizons.galaxia.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.outpost.module.BigHammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.HammerModuleData;
import com.gtnewhorizons.galaxia.outpost.module.MinerModuleData;
import com.gtnewhorizons.galaxia.outpost.persistence.OutpostDataStore;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class OutpostModuleConfigPacket implements IMessage {

    private String assetId;
    private int moduleIndex;
    private String action;
    private String payload;

    public OutpostModuleConfigPacket() {}

    public OutpostModuleConfigPacket(String assetId, int moduleIndex, String action, String payload) {
        this.assetId = assetId;
        this.moduleIndex = moduleIndex;
        this.action = action;
        this.payload = payload == null ? "" : payload;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeString(buf, assetId);
        buf.writeInt(moduleIndex);
        writeString(buf, action);
        writeString(buf, payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = readString(buf);
        moduleIndex = buf.readInt();
        action = readString(buf);
        payload = readString(buf);
    }

    public static final class Handler implements IMessageHandler<OutpostModuleConfigPacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostModuleConfigPacket packet, MessageContext ctx) {
            AutomatedOutpostState state = OutpostDataStore.get()
                .getByAssetId(packet.assetId);
            if (state == null) return null;
            if (packet.moduleIndex < 0 || packet.moduleIndex >= state.modules()
                .size()) return null;
            AutomatedOutpostModule module = state.modules()
                .get(packet.moduleIndex);

            switch (packet.action) {
                case "ADD_MINER_BLACKLIST" -> {
                    if (!(module.getData() instanceof MinerModuleData minerData)) return null;
                    MinerModuleData updated = minerData.withAddedBlacklist(packet.payload);
                    module.setData(updated);
                    if (updated.copySettingsToOtherMiners()) {
                        copyMinerSettingsToOtherMiners(state, packet.moduleIndex, updated);
                    }
                }
                case "REMOVE_MINER_BLACKLIST" -> {
                    if (!(module.getData() instanceof MinerModuleData minerData)) return null;
                    MinerModuleData updated = minerData.withRemovedBlacklist(packet.payload);
                    module.setData(updated);
                    if (updated.copySettingsToOtherMiners()) {
                        copyMinerSettingsToOtherMiners(state, packet.moduleIndex, updated);
                    }
                }
                case "SET_MINER_COPY_SETTINGS" -> {
                    if (!(module.getData() instanceof MinerModuleData minerData)) return null;
                    MinerModuleData updated = minerData
                        .withCopySettingsToOtherMiners(Boolean.parseBoolean(packet.payload));
                    module.setData(updated);
                    if (updated.copySettingsToOtherMiners()) {
                        copyMinerSettingsToOtherMiners(state, packet.moduleIndex, updated);
                    }
                }
                case "SET_ALLOW_SHOOTING_MODE" -> {
                    AllowShootingConfig.Mode mode;
                    try {
                        mode = AllowShootingConfig.Mode.valueOf(packet.payload);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    if (module.getData() instanceof HammerModuleData hd) {
                        double threshold = hd.effectiveShooting()
                            .threshold();
                        module.setData(
                            new HammerModuleData(
                                new AllowShootingConfig(mode, threshold),
                                hd.effectiveRoutePriority()));
                    } else if (module.getData() instanceof BigHammerModuleData bd) {
                        double threshold = bd.effectiveShooting()
                            .threshold();
                        module.setData(
                            new BigHammerModuleData(
                                bd.planetaryTransferHandling(),
                                new AllowShootingConfig(mode, threshold),
                                bd.effectiveRoutePriority()));
                    } else {
                        return null;
                    }
                }
                case "SET_ALLOW_SHOOTING_THRESHOLD" -> {
                    double threshold;
                    try {
                        threshold = Double.parseDouble(packet.payload);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (module.getData() instanceof HammerModuleData hd) {
                        module.setData(
                            new HammerModuleData(
                                new AllowShootingConfig(
                                    hd.effectiveShooting()
                                        .mode(),
                                    threshold),
                                hd.effectiveRoutePriority()));
                    } else if (module.getData() instanceof BigHammerModuleData bd) {
                        module.setData(
                            new BigHammerModuleData(
                                bd.planetaryTransferHandling(),
                                new AllowShootingConfig(
                                    bd.effectiveShooting()
                                        .mode(),
                                    threshold),
                                bd.effectiveRoutePriority()));
                    } else {
                        return null;
                    }
                }
                case "SET_PLANETARY_HANDLING" -> {
                    if (!(module.getData() instanceof BigHammerModuleData bd)) return null;
                    module.setData(
                        new BigHammerModuleData(
                            Boolean.parseBoolean(packet.payload),
                            bd.allowShooting(),
                            bd.effectiveRoutePriority()));
                }
                case "SET_ROUTE_PRIORITY" -> {
                    OrbitalTransferPlanner.RoutePriority priority;
                    try {
                        priority = OrbitalTransferPlanner.RoutePriority.valueOf(packet.payload);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    if (module.getData() instanceof HammerModuleData hd) {
                        module.setData(new HammerModuleData(hd.effectiveShooting(), priority));
                    } else if (module.getData() instanceof BigHammerModuleData bd) {
                        module.setData(
                            new BigHammerModuleData(bd.planetaryTransferHandling(), bd.effectiveShooting(), priority));
                    } else {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
            return new OutpostFullSyncPacket(state);
        }
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void copyMinerSettingsToOtherMiners(AutomatedOutpostState state, int sourceModuleIndex,
        MinerModuleData sourceData) {
        for (int i = 0; i < state.modules()
            .size(); i++) {
            if (i == sourceModuleIndex) continue;
            AutomatedOutpostModule other = state.modules()
                .get(i);
            if (!(other.getData() instanceof MinerModuleData)) continue;
            other
                .setData(new MinerModuleData(sourceData.blacklistedItemKeys(), sourceData.copySettingsToOtherMiners()));
        }
    }
}
