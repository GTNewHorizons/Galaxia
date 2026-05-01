package com.gtnewhorizons.galaxia.core.network;

import java.util.Objects;
import java.util.function.Function;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.GT5RecipeRef;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class AssetModuleUpdatePacket implements IMessage {

    private static final int ACTION_TYPE = 0;
    private static final int CONFIG_TYPE = 1;

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private ModuleInstance.ID moduleId;
    private int type;
    private Action action;
    private ConfigAction configAction;

    private String stringPayload;
    private byte bytePayload;
    private double doublePayload;
    private byte[] rawPayload;

    public AssetModuleUpdatePacket() {}

    public static AssetModuleUpdatePacket action(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance.ID moduleId,
        Action action) {
        AssetModuleUpdatePacket pkt = new AssetModuleUpdatePacket();
        pkt.assetId = assetId;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        pkt.type = ACTION_TYPE;
        pkt.action = action;
        return pkt;
    }

    private static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action) {
        AssetModuleUpdatePacket pkt = new AssetModuleUpdatePacket();
        pkt.assetId = assetId;
        pkt.moduleIndex = moduleIndex;
        pkt.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        pkt.type = CONFIG_TYPE;
        pkt.configAction = action;
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance.ID moduleId,
        ConfigAction action, String payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        pkt.stringPayload = payload == null ? "" : payload;
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance.ID moduleId,
        ConfigAction action, boolean payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        pkt.bytePayload = (byte) (payload ? 1 : 0);
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance.ID moduleId,
        ConfigAction action, double payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        pkt.doublePayload = payload;
        return pkt;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance.ID moduleId,
        ConfigAction action, Enum<?> payload) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        pkt.bytePayload = (byte) payload.ordinal();
        return pkt;
    }

    public static AssetModuleUpdatePacket recipeSlotPayload(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action, byte slotIndex, RecipeSlot slot) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        if (action == ConfigAction.REMOVE_RECIPE_SLOT) {
            pkt.rawPayload = new byte[] { slotIndex };
        } else if (slot != null) {
            io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.buffer(25);
            payloadBuf.writeByte(slotIndex);
            payloadBuf.writeByte(
                slot.recipeRef()
                    .recipeMapOrdinal());
            payloadBuf.writeInt(
                slot.recipeRef()
                    .recipeIndex());
            payloadBuf.writeLong(
                slot.recipeRef()
                    .contentHash());
            payloadBuf.writeBoolean(slot.enabled());
            payloadBuf.writeInt(slot.inputGuard());
            payloadBuf.writeInt(slot.outputGuard());
            payloadBuf.writeByte(slot.priority());
            payloadBuf.writeByte(slot.orderSize());
            pkt.rawPayload = new byte[payloadBuf.writerIndex()];
            payloadBuf.readBytes(pkt.rawPayload);
        }
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
        SET_ROUTE_PRIORITY,
        SET_TIER,
        SET_PRIORITY,
        SET_ENABLED,
        ADD_RECIPE_SLOT,
        UPDATE_RECIPE_SLOT,
        REMOVE_RECIPE_SLOT
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        buf.writeInt(moduleIndex);
        PacketUtil.writeId(buf, moduleId);
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
                case SET_TIER, SET_PRIORITY, SET_ENABLED -> buf.writeByte(bytePayload);
                case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> {
                    if (rawPayload != null) {
                        buf.writeInt(rawPayload.length);
                        buf.writeBytes(rawPayload);
                    } else {
                        buf.writeInt(0);
                    }
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        moduleId = PacketUtil.readModuleId(buf);
        type = buf.readUnsignedByte();
        int rawAction = buf.readUnsignedByte();

        if (type == ACTION_TYPE) {
            action = PacketUtil.enumFromByte(rawAction, Action.class);
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

        configAction = PacketUtil.enumFromByte(rawAction, ConfigAction.class);
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
            case SET_TIER, SET_PRIORITY, SET_ENABLED -> bytePayload = buf.readByte();
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> {
                int len = buf.readInt();
                if (len > 0) {
                    rawPayload = new byte[len];
                    buf.readBytes(rawPayload);
                }
            }
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
        return PacketUtil.enumFromByte(Byte.toUnsignedInt(bytePayload), enumClass);
    }

    public byte[] getRawPayload() {
        return rawPayload;
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
            packet.moduleIndex = state.moduleIndex(packet.moduleId);
            if (packet.moduleIndex < 0 || packet.moduleIndex >= modules.size()) return null;

            ModuleInstance module = modules.get(packet.moduleIndex);
            if (!packet.moduleId.equals(module.id)) return null;

            switch (packet.type) {
                case ACTION_TYPE -> handleAction(packet, state, module);
                case CONFIG_TYPE -> handleConfig(packet, state, module);
                default -> {
                    return null;
                }
            }

            if (packet.type == ACTION_TYPE && packet.getAction() == Action.DESTROY) {
                return AssetSyncPacket.moduleRemoved(packet.assetId, packet.moduleIndex, module.id)
                    .withSyncRevision(state.getSyncRevision());
            }
            state.markModuleDirty(module.id);
            return AssetSyncPacket.moduleUpdated(packet.assetId, packet.moduleIndex, module)
                .withSyncRevision(state.getSyncRevision());
        }

        private void handleAction(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module) {
            switch (packet.getAction()) {
                case ENABLE -> {
                    if (module.status() == Buildable.Status.DISABLED) {
                        module.updateStatus(Buildable.Status.OPERATIONAL);
                    }
                }
                case DISABLE -> module.updateStatus(Buildable.Status.DISABLED);
                case DESTROY -> state.removeModule(module.id);
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
                    if (module.component() instanceof ModuleHammer hammer) {
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
                case SET_TIER -> {
                    ModuleTier tier = PacketUtil.enumFromByte(Byte.toUnsignedInt(packet.bytePayload), ModuleTier.class);
                    if (tier == null || !module.kind()
                        .allowedTiers()
                        .contains(tier)) {
                        Galaxia.LOG.warn(
                            "[Outpost] ModuleUpdate: rejected tier {} for {} on {}",
                            tier,
                            module.kind(),
                            packet.assetId);
                        return;
                    }
                    module.setTier(tier);
                    state.layoutCache()
                        .applyMutation(MutationKind.SET_TIER, module.kind(), module);
                }
                case SET_PRIORITY -> {
                    ModulePriority priority = PacketUtil
                        .enumFromByte(Byte.toUnsignedInt(packet.bytePayload), ModulePriority.class);
                    if (priority != null) module.setPriorityOverride(priority);
                }
                case SET_ENABLED -> {
                    module.setEnabled(packet.getBooleanPayload());
                    state.layoutCache()
                        .applyMutation(MutationKind.SET_ENABLED, module.kind(), module);
                }
                case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> handleRecipeSlot(packet, state, module);
            }
        }

        private void handleRecipeSlot(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module) {
            if (!(module.component() instanceof IRecipeModule recipeModule)) return;
            if (packet.rawPayload == null) return;

            io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.wrappedBuffer(packet.rawPayload);
            int slotIndex = Byte.toUnsignedInt(payloadBuf.readByte());
            if (slotIndex >= RecipeSlotList.MAX_RECIPE_SLOTS) return;

            RecipeConfig config = recipeModule.getRecipeConfig();
            ConfigAction action = packet.getConfigAction();

            if (action == ConfigAction.REMOVE_RECIPE_SLOT) {
                if (config == null) return;
                if (config.slots()
                    .getOrNull(slotIndex) == null) return;
                config.slots()
                    .remove(slotIndex);
                state.markModuleDirty(module.id);
                return;
            }

            // ADD or UPDATE: decode RecipeSlot from payload
            if (packet.rawPayload.length < 25) return;
            byte recipeMapOrdinal = payloadBuf.readByte();
            int recipeIndex = payloadBuf.readInt();
            long contentHash = payloadBuf.readLong();
            boolean enabled = payloadBuf.readBoolean();
            int inputGuard = payloadBuf.readInt();
            int outputGuard = payloadBuf.readInt();
            byte priority = payloadBuf.readByte();
            byte orderSize = payloadBuf.readByte();

            GT5RecipeRef ref = new GT5RecipeRef(recipeMapOrdinal, recipeIndex, contentHash);
            RecipeSlot slot = new RecipeSlot(ref, enabled, inputGuard, outputGuard, priority, orderSize);

            if (config == null) {
                config = RecipeConfig.empty();
                recipeModule.setRecipeConfig(config);
            }

            config.slots()
                .set(slotIndex, slot);
            state.markModuleDirty(module.id);
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
