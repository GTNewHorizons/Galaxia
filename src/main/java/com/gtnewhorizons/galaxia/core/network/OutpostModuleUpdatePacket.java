package com.gtnewhorizons.galaxia.core.network;

import java.util.function.Function;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedOutpost;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.AutomatedOutpostModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.IHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleBigHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class OutpostModuleUpdatePacket implements IMessage {

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private int type;
    private int action;

    private String stringPayload;
    private byte bytePayload;
    private double doublePayload;

    public OutpostModuleUpdatePacket() {}

    public static OutpostModuleUpdatePacket action(CelestialAsset.ID assetId, int moduleIndex, Action action) {
        OutpostModuleUpdatePacket pkt = new OutpostModuleUpdatePacket();
        pkt.assetId = assetId;
        pkt.moduleIndex = moduleIndex;
        pkt.type = 0; // ACTION
        pkt.action = action.ordinal();
        return pkt;
    }

    private static OutpostModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action) {
        OutpostModuleUpdatePacket pkt = new OutpostModuleUpdatePacket();
        pkt.assetId = assetId;
        pkt.moduleIndex = moduleIndex;
        pkt.type = 1; // CONFIG
        pkt.action = action.ordinal();
        return pkt;
    }

    public static OutpostModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        String payload) {
        OutpostModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.stringPayload = payload == null ? "" : payload;
        return pkt;
    }

    public static OutpostModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        boolean payload) {
        OutpostModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.bytePayload = (byte) (payload ? 1 : 0);
        return pkt;
    }

    public static OutpostModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        double payload) {
        OutpostModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.doublePayload = payload;
        return pkt;
    }

    public static OutpostModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        Enum<?> payload) {
        OutpostModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.bytePayload = (byte) payload.ordinal();
        return pkt;
    }

    public enum Action {
        ENABLE,
        DISABLE,
        DESTROY
    }

    public enum ConfigAction {
        ADD_MINER_BLACKLIST,
        REMOVE_MINER_BLACKLIST,
        SET_MINER_COPY_SETTINGS,
        SET_ALLOW_SHOOTING_MODE,
        SET_ALLOW_SHOOTING_THRESHOLD,
        SET_PLANETARY_HANDLING,
        SET_ROUTE_PRIORITY
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        buf.writeInt(moduleIndex);
        buf.writeByte(type);
        buf.writeByte(action);

        if (type == 1) { // CONFIG
            switch (ConfigAction.values()[action]) {
                case ADD_MINER_BLACKLIST, REMOVE_MINER_BLACKLIST -> PacketUtil.writeString(buf, stringPayload);
                case SET_MINER_COPY_SETTINGS, SET_PLANETARY_HANDLING -> buf.writeByte(bytePayload);
                case SET_ALLOW_SHOOTING_MODE, SET_ROUTE_PRIORITY -> buf.writeByte(bytePayload);
                case SET_ALLOW_SHOOTING_THRESHOLD -> buf.writeDouble(doublePayload);
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        type = buf.readUnsignedByte();
        action = buf.readUnsignedByte();

        if (type == 1) { // CONFIG
            switch (ConfigAction.values()[action]) {
                case ADD_MINER_BLACKLIST, REMOVE_MINER_BLACKLIST -> stringPayload = PacketUtil.readString(buf);
                case SET_MINER_COPY_SETTINGS, SET_PLANETARY_HANDLING -> bytePayload = buf.readByte();
                case SET_ALLOW_SHOOTING_MODE, SET_ROUTE_PRIORITY -> bytePayload = buf.readByte();
                case SET_ALLOW_SHOOTING_THRESHOLD -> doublePayload = buf.readDouble();
            }
        }
    }

    public Action getAction() {
        return type == 0 ? Action.values()[action] : null;
    }

    public ConfigAction getConfigAction() {
        return type == 1 ? ConfigAction.values()[action] : null;
    }

    public String getStringPayload() {
        return stringPayload;
    }

    public boolean getBooleanPayload() {
        return bytePayload != 0;
    }

    public double getDoublePayload() {
        return doublePayload;
    }

    public <T extends Enum<T>> T getEnumPayload(Class<T> enumClass) {
        return enumClass.getEnumConstants()[bytePayload];
    }

    public static final class Handler implements IMessageHandler<OutpostModuleUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(OutpostModuleUpdatePacket packet, MessageContext ctx) {
            AutomatedOutpost state = CelestialClient.getByAssetId(packet.assetId) instanceof AutomatedOutpost o ? o
                : null;
            if (state == null) return null;

            var modules = state.modules();
            if (packet.moduleIndex < 0 || packet.moduleIndex >= modules.size()) return null;

            AutomatedOutpostModule module = modules.get(packet.moduleIndex);

            switch (packet.type) {
                case 0 -> handleAction(packet, state, module);
                case 1 -> handleConfig(packet, state, module);
            }

            // Return sync packet instead of full sync
            if (packet.type == 0 && packet.getAction() == Action.DESTROY) {
                return OutpostSyncPacket.moduleRemoved(packet.assetId, packet.moduleIndex);
            }
            return OutpostSyncPacket.moduleUpdated(packet.assetId, packet.moduleIndex, module);
        }

        private void handleAction(OutpostModuleUpdatePacket packet, AutomatedOutpost state,
            AutomatedOutpostModule module) {
            switch (packet.getAction()) {
                case ENABLE -> {
                    if (module.status() == Buildable.Status.DISABLED) {
                        module.updateStatus(Buildable.Status.OPERATIONAL);
                    }
                }
                case DISABLE -> module.updateStatus(Buildable.Status.DISABLED);
                case DESTROY -> state.removeModule(packet.moduleIndex);
            }
        }

        private void handleConfig(OutpostModuleUpdatePacket packet, AutomatedOutpost state,
            AutomatedOutpostModule module) {
            switch (packet.getConfigAction()) {
                case ADD_MINER_BLACKLIST -> handleMinerBlacklist(
                    module,
                    packet.getStringPayload(),
                    true,
                    state,
                    packet.moduleIndex);
                case REMOVE_MINER_BLACKLIST -> handleMinerBlacklist(
                    module,
                    packet.getStringPayload(),
                    false,
                    state,
                    packet.moduleIndex);
                case SET_MINER_COPY_SETTINGS -> handleMinerCopySettings(
                    module,
                    packet.getBooleanPayload(),
                    state,
                    packet.moduleIndex);
                case SET_ALLOW_SHOOTING_MODE -> handleHammerConfig(module, h -> {
                    AllowShootingConfig.Mode mode = packet.getEnumPayload(AllowShootingConfig.Mode.class);
                    return new AllowShootingConfig(
                        mode,
                        h.getConfig()
                            .threshold());
                });
                case SET_ALLOW_SHOOTING_THRESHOLD -> handleHammerConfig(
                    module,
                    h -> {
                        return new AllowShootingConfig(
                            h.getConfig()
                                .mode(),
                            packet.getDoublePayload());
                    });
                case SET_PLANETARY_HANDLING -> {
                    if (module instanceof ModuleBigHammer hammer) {
                        hammer.setPlanetaryHandling(packet.getBooleanPayload());
                    }
                }
                case SET_ROUTE_PRIORITY -> handleHammerConfig(module, h -> {
                    OrbitalTransferPlanner.RoutePriority priority = packet
                        .getEnumPayload(OrbitalTransferPlanner.RoutePriority.class);
                    h.setPriority(priority);
                    return h.getConfig();
                });
            }
        }

        private void handleMinerBlacklist(AutomatedOutpostModule module, String payload, boolean add,
            AutomatedOutpost state, int moduleIndex) {
            if (!(module instanceof ModuleMiner miner)) return;
            if (add) {
                miner.withAddedBlacklist(payload);
            } else {
                miner.withRemovedBlacklist(payload);
            }
            if (miner.getCopySettingsToOtherMiners()) {
                copyMinerSettingsToOtherMiners(state, moduleIndex, miner);
            }
        }

        private void handleMinerCopySettings(AutomatedOutpostModule module, boolean payload, AutomatedOutpost state,
            int moduleIndex) {
            if (!(module instanceof ModuleMiner miner)) return;
            miner.withCopySettingsToOtherMiners(payload);
            if (payload) {
                copyMinerSettingsToOtherMiners(state, moduleIndex, miner);
            }
        }

        private void handleHammerConfig(AutomatedOutpostModule module,
            Function<IHammer, AllowShootingConfig> configUpdater) {
            if (!(module instanceof IHammer hammer)) return;
            AllowShootingConfig newConfig = configUpdater.apply(hammer);
            if (newConfig != null) {
                hammer.setConfig(newConfig);
            }
        }
    }

    private static void copyMinerSettingsToOtherMiners(AutomatedOutpost state, int sourceModuleIndex,
        ModuleMiner sourceMiner) {
        for (int i = 0; i < state.modules()
            .size(); i++) {
            if (i == sourceModuleIndex) continue;
            AutomatedOutpostModule other = state.modules()
                .get(i);
            if (!(other instanceof ModuleMiner miner)) continue;
            miner.withCopySettingsToOtherMiners(sourceMiner.getCopySettingsToOtherMiners());
            miner.blacklistedItemKeys.clear();
            miner.blacklistedItemKeys.addAll(sourceMiner.blacklistedItemKeys);
        }
    }
}
