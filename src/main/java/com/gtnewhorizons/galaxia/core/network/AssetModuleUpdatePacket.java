package com.gtnewhorizons.galaxia.core.network;

import java.util.function.Function;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetModuleUpdatePacket implements IMessage {

    private static final int ACTION_TYPE = 0;
    private static final int CONFIG_TYPE = 1;

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private int type;
    private Action action;
    private ConfigAction configAction;

    private String stringPayload;
    private byte bytePayload;
    private double doublePayload;

    public AssetModuleUpdatePacket() {}

    public static AssetModuleUpdatePacket action(CelestialAsset.ID assetId, int moduleIndex, Action action) {
        AssetModuleUpdatePacket pkt = new AssetModuleUpdatePacket();
        pkt.assetId = assetId;
        pkt.moduleIndex = moduleIndex;
        pkt.type = ACTION_TYPE;
        pkt.action = action;
        return pkt;
    }

    private static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action) {
        AssetModuleUpdatePacket pkt = new AssetModuleUpdatePacket();
        pkt.assetId = assetId;
        pkt.moduleIndex = moduleIndex;
        pkt.type = CONFIG_TYPE;
        pkt.configAction = action;
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        String payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.stringPayload = payload == null ? "" : payload;
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        boolean payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.bytePayload = (byte) (payload ? 1 : 0);
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        double payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
        pkt.doublePayload = payload;
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ConfigAction action,
        Enum<?> payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, action);
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
        if (type == ACTION_TYPE) {
            PacketUtil.writeEnum(buf, action);
        } else if (type == CONFIG_TYPE) {
            PacketUtil.writeEnum(buf, configAction);
        } else {
            Galaxia.LOG.warn("[Network] Writing AssetModuleUpdatePacket with unknown type: {}", type);
            buf.writeByte(0);
        }

        if (type == CONFIG_TYPE && configAction != null) {
            switch (configAction) {
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
        int rawAction = buf.readUnsignedByte();

        if (type == ACTION_TYPE) {
            action = PacketUtil.fromOrdinalOrNull(rawAction, Action.class);
            if (action == null) {
                Galaxia.LOG
                    .warn("[Network] Ignoring AssetModuleUpdatePacket with unknown action ordinal: {}", rawAction);
            }
            return;
        }
        if (type != CONFIG_TYPE) {
            Galaxia.LOG.warn("[Network] Ignoring AssetModuleUpdatePacket with unknown type: {}", type);
            return;
        }

        configAction = PacketUtil.fromOrdinalOrNull(rawAction, ConfigAction.class);
        if (configAction == null) {
            Galaxia.LOG
                .warn("[Network] Ignoring AssetModuleUpdatePacket with unknown config action ordinal: {}", rawAction);
            return;
        }

        switch (configAction) {
            case ADD_MINER_BLACKLIST, REMOVE_MINER_BLACKLIST -> stringPayload = PacketUtil.readString(buf);
            case SET_MINER_COPY_SETTINGS, SET_PLANETARY_HANDLING -> bytePayload = buf.readByte();
            case SET_ALLOW_SHOOTING_MODE, SET_ROUTE_PRIORITY -> bytePayload = buf.readByte();
            case SET_ALLOW_SHOOTING_THRESHOLD -> doublePayload = buf.readDouble();
        }
    }

    public Action getAction() {
        return type == ACTION_TYPE ? action : null;
    }

    public ConfigAction getConfigAction() {
        return type == CONFIG_TYPE ? configAction : null;
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
        return PacketUtil.fromOrdinalOrNull(Byte.toUnsignedInt(bytePayload), enumClass);
    }

    public static final class Handler implements IMessageHandler<AssetModuleUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetModuleUpdatePacket packet, MessageContext ctx) {
            if (ctx.getServerHandler() == null || ctx.getServerHandler().playerEntity == null) return null;

            CelestialAsset asset = CelestialAssetStore.findAsset(packet.assetId);
            if (!(asset instanceof AutomatedFacility state)) return null;
            if (!CelestialAssetStore
                .isOwnedBy(TempTeamCompat.getTeam(ctx.getServerHandler().playerEntity), packet.assetId)) return null;
            if (packet.type == ACTION_TYPE && packet.action == null) return null;
            if (packet.type == CONFIG_TYPE && packet.configAction == null) return null;

            var modules = state.modules();
            if (packet.moduleIndex < 0 || packet.moduleIndex >= modules.size()) return null;

            ModuleInstance module = modules.get(packet.moduleIndex);

            switch (packet.type) {
                case ACTION_TYPE -> handleAction(packet, state, module);
                case CONFIG_TYPE -> handleConfig(packet, state, module);
                default -> {
                    return null;
                }
            }

            if (packet.type == ACTION_TYPE && packet.getAction() == Action.DESTROY) {
                return AssetSyncPacket.moduleRemoved(packet.assetId, packet.moduleIndex);
            }
            return AssetSyncPacket.moduleUpdated(packet.assetId, packet.moduleIndex, module);
        }

        private void handleAction(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module) {
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

        private void handleConfig(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module) {
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
                        h.config()
                            .threshold());
                });
                case SET_ALLOW_SHOOTING_THRESHOLD -> handleHammerConfig(
                    module,
                    h -> new AllowShootingConfig(
                        h.config()
                            .mode(),
                        packet.getDoublePayload()));
                case SET_PLANETARY_HANDLING -> {
                    if (module.kind() == FacilityModuleKind.BIG_HAMMER
                        && module.component() instanceof ModuleHammer hammer) {
                        hammer.setPlanetaryHandling(packet.getBooleanPayload());
                    }
                }
                case SET_ROUTE_PRIORITY -> {
                    if (!(module.component() instanceof ModuleHammer hammer)) return;
                    OrbitalTransferPlanner.RoutePriority priority = packet
                        .getEnumPayload(OrbitalTransferPlanner.RoutePriority.class);
                    if (priority == null) return;
                    hammer.setRoutePriority(priority);
                }
            }
        }

        private void handleMinerBlacklist(ModuleInstance module, String payload, boolean add, AutomatedFacility state,
            int moduleIndex) {
            if (!(module.component() instanceof ModuleMiner miner)) return;
            if (add) {
                miner.addToBlacklist(payload);
            } else {
                miner.removeFromBlacklist(payload);
            }
            if (miner.copySettingsToOtherMiners()) {
                copyMinerSettingsToOtherMiners(state, moduleIndex, miner);
            }
        }

        private void handleMinerCopySettings(ModuleInstance module, boolean payload, AutomatedFacility state,
            int moduleIndex) {
            if (!(module.component() instanceof ModuleMiner miner)) return;
            miner.setCopySettingToOtherMiners(payload);
            if (payload) {
                copyMinerSettingsToOtherMiners(state, moduleIndex, miner);
            }
        }

        private void handleHammerConfig(ModuleInstance module,
            Function<ModuleHammer, AllowShootingConfig> configUpdater) {
            if (!(module.component() instanceof ModuleHammer hammer)) return;
            AllowShootingConfig newConfig = configUpdater.apply(hammer);
            if (newConfig != null) {
                hammer.setConfig(newConfig);
            }
        }
    }

    private static void copyMinerSettingsToOtherMiners(AutomatedFacility state, int sourceModuleIndex,
        ModuleMiner sourceMiner) {
        for (int i = 0; i < state.modules()
            .size(); i++) {
            if (i == sourceModuleIndex) continue;
            ModuleInstance other = state.modules()
                .get(i);
            if (other.component() instanceof ModuleMiner miner) {
                miner.setCopySettingToOtherMiners(sourceMiner.copySettingsToOtherMiners());
                miner.setBlacklist(sourceMiner.blacklistedItemKeys());
            }
        }
    }
}
