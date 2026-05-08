package com.gtnewhorizons.galaxia.core.network;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;

import io.netty.buffer.ByteBuf;

public final class AssetModuleUpdatePacket {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private static final int ACTION_TYPE = 0;
    private static final int CONFIG_TYPE = 1;
    private static final int MAX_RECIPE_PAYLOAD_BYTES = 4096;
    private static final int MAX_RECIPE_STACKS = 64;
    private static final int HAMMER_UPGRADE_PAYLOAD_BYTES = 4;

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private ModuleInstance.ID moduleId;
    private int type;
    private Action action;
    private ConfigAction configAction;

    private String stringPayload;
    private byte bytePayload;
    private short shortPayload;
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
        pkt.stringPayload = Objects.requireNonNull(payload, "payload");
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
        pkt.bytePayload = (byte) Objects.requireNonNull(payload, "payload")
            .ordinal();
        return pkt;
    }

    public static AssetModuleUpdatePacket minerOreBlacklisted(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, String oreKey, boolean blacklisted) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.SET_MINER_ORE_BLACKLISTED);
        pkt.stringPayload = Objects.requireNonNull(oreKey, "oreKey");
        pkt.bytePayload = (byte) (blacklisted ? 1 : 0);
        return pkt;
    }

    public static AssetModuleUpdatePacket minerSettingsGroup(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, short groupId) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.SET_SETTINGS_GROUP);
        pkt.shortPayload = groupId;
        return pkt;
    }

    public static AssetModuleUpdatePacket createMinerSettingsGroup(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId) {
        return config(assetId, moduleIndex, moduleId, ConfigAction.CREATE_SETTINGS_GROUP);
    }

    public static AssetModuleUpdatePacket cancelModuleOperation(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId) {
        return config(assetId, moduleIndex, moduleId, ConfigAction.CANCEL_MODULE_OPERATION);
    }

    public static AssetModuleUpdatePacket hammerUpgradePlan(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, HammerVariant variant, ModuleTier tier, boolean reserveItems,
        boolean voidCompletionRefund) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.PLAN_HAMMER_UPGRADE);
        pkt.rawPayload = new byte[] { (byte) Objects.requireNonNull(variant, "variant")
            .ordinal(),
            (byte) Objects.requireNonNull(tier, "tier")
                .ordinal(),
            (byte) (reserveItems ? 1 : 0), (byte) (voidCompletionRefund ? 1 : 0) };
        return pkt;
    }

    public static AssetModuleUpdatePacket minerFocusPlan(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, MinerFocusTier focusTier, String oreKey) {
        if (focusTier == null) {
            throw new IllegalArgumentException("focusTier must not be null");
        }
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.PLAN_MINER_FOCUS);
        pkt.bytePayload = (byte) focusTier.ordinal();
        pkt.stringPayload = oreKey == null ? "" : oreKey;
        return pkt;
    }

    public static AssetModuleUpdatePacket recipeSlotPayload(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action, byte slotIndex, RecipeSlot slot) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        if (action == ConfigAction.REMOVE_RECIPE_SLOT) {
            pkt.rawPayload = new byte[] { slotIndex };
        } else if (slot != null) {
            io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.buffer();
            payloadBuf.writeByte(slotIndex);
            payloadBuf.writeByte(
                slot.recipe()
                    .recipeMapOrdinal());
            payloadBuf.writeInt(
                slot.recipe()
                    .recipeIndex());
            payloadBuf.writeLong(
                slot.recipe()
                    .contentHash());
            payloadBuf.writeInt(
                slot.recipe()
                    .duration());
            payloadBuf.writeInt(
                slot.recipe()
                    .eut());
            writeItemStacks(
                payloadBuf,
                slot.recipe()
                    .inputs());
            writeItemStacks(
                payloadBuf,
                slot.recipe()
                    .outputs());
            writeIntArray(
                payloadBuf,
                slot.recipe()
                    .outputChances());
            writeFluidStacks(
                payloadBuf,
                slot.recipe()
                    .fluidInputs());
            writeFluidStacks(
                payloadBuf,
                slot.recipe()
                    .fluidOutputs());
            writeIntArray(
                payloadBuf,
                slot.recipe()
                    .fluidOutputChances());
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
        SET_MINER_ORE_BLACKLISTED,
        SET_ALLOW_SHOOTING_MODE,
        SET_ALLOW_SHOOTING_THRESHOLD,
        SET_HAMMER_VARIANT,
        PLAN_HAMMER_UPGRADE,
        PLAN_MINER_FOCUS,
        SET_ROUTE_PRIORITY,
        SET_TIER,
        SET_PRIORITY,
        SET_ENABLED,
        SET_SETTINGS_GROUP,
        CREATE_SETTINGS_GROUP,
        CANCEL_MODULE_OPERATION,
        ADD_RECIPE_SLOT,
        UPDATE_RECIPE_SLOT,
        REMOVE_RECIPE_SLOT
    }

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
            LOG.warn("[Network] Writing AssetModuleUpdatePacket with unknown type: {}", type);
            buf.writeByte(0);
        }

        if (type == CONFIG_TYPE && configAction != null) {
            switch (configAction) {
                case SET_MINER_ORE_BLACKLISTED -> {
                    PacketUtil.writeString(buf, stringPayload);
                    buf.writeByte(bytePayload);
                }
                case PLAN_MINER_FOCUS -> {
                    buf.writeByte(bytePayload);
                    PacketUtil.writeString(buf, stringPayload);
                }
                case SET_ALLOW_SHOOTING_MODE, SET_HAMMER_VARIANT, SET_ROUTE_PRIORITY -> buf.writeByte(bytePayload);
                case PLAN_HAMMER_UPGRADE -> {
                    if (rawPayload == null || rawPayload.length != HAMMER_UPGRADE_PAYLOAD_BYTES) {
                        throw new IllegalArgumentException("invalid hammer upgrade payload");
                    }
                    buf.writeBytes(rawPayload);
                }
                case SET_ALLOW_SHOOTING_THRESHOLD -> buf.writeDouble(doublePayload);
                case SET_TIER, SET_PRIORITY, SET_ENABLED -> buf.writeByte(bytePayload);
                case SET_SETTINGS_GROUP -> buf.writeShort(shortPayload);
                case CREATE_SETTINGS_GROUP, CANCEL_MODULE_OPERATION -> {}
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

    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        moduleId = PacketUtil.readModuleId(buf);
        type = buf.readUnsignedByte();
        int rawAction = buf.readUnsignedByte();

        if (type == ACTION_TYPE) {
            action = PacketUtil.enumFromByte(rawAction, Action.class);
            if (action == null) {
                throw new IllegalStateException("unknown AssetModuleUpdatePacket action ordinal: " + rawAction);
            }
            return;
        }
        if (type != CONFIG_TYPE) {
            throw new IllegalStateException("unknown AssetModuleUpdatePacket type: " + type);
        }

        configAction = PacketUtil.enumFromByte(rawAction, ConfigAction.class);
        if (configAction == null) {
            throw new IllegalStateException("unknown AssetModuleUpdatePacket config action ordinal: " + rawAction);
        }

        switch (configAction) {
            case SET_MINER_ORE_BLACKLISTED -> {
                stringPayload = PacketUtil.readString(buf);
                bytePayload = buf.readByte();
                if (bytePayload != 0 && bytePayload != 1) {
                    throw new IllegalStateException("invalid miner blacklist flag: " + bytePayload);
                }
            }
            case PLAN_MINER_FOCUS -> {
                bytePayload = buf.readByte();
                stringPayload = PacketUtil.readString(buf);
            }
            case SET_ALLOW_SHOOTING_MODE, SET_HAMMER_VARIANT, SET_ROUTE_PRIORITY -> bytePayload = buf.readByte();
            case PLAN_HAMMER_UPGRADE -> {
                if (buf.readableBytes() < HAMMER_UPGRADE_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("missing hammer upgrade payload");
                }
                rawPayload = new byte[HAMMER_UPGRADE_PAYLOAD_BYTES];
                buf.readBytes(rawPayload);
                if (rawPayload[2] != 0 && rawPayload[2] != 1) {
                    throw new IllegalStateException("invalid hammer upgrade reserve flag: " + rawPayload[2]);
                }
                if (rawPayload[3] != 0 && rawPayload[3] != 1) {
                    throw new IllegalStateException("invalid hammer upgrade void refund flag: " + rawPayload[3]);
                }
            }
            case SET_ALLOW_SHOOTING_THRESHOLD -> doublePayload = buf.readDouble();
            case SET_TIER, SET_PRIORITY, SET_ENABLED -> bytePayload = buf.readByte();
            case SET_SETTINGS_GROUP -> shortPayload = buf.readShort();
            case CREATE_SETTINGS_GROUP, CANCEL_MODULE_OPERATION -> {}
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> {
                int len = buf.readInt();
                if (len <= 0 || len > MAX_RECIPE_PAYLOAD_BYTES || len > buf.readableBytes()) {
                    throw new IllegalArgumentException("invalid recipe payload length: " + len);
                }
                rawPayload = new byte[len];
                buf.readBytes(rawPayload);
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

    public AssetSyncPacket apply(UUID teamId) {
        return apply(teamId, false);
    }

    public AssetSyncPacket apply(UUID teamId, boolean creative) {
        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (!(asset instanceof AutomatedFacility state)) return null;
        if (!CelestialAssetStore.isOwnedBy(teamId, assetId)) return null;
        if (type == ACTION_TYPE && action == null) return null;
        if (type == CONFIG_TYPE && configAction == null) return null;

        var modules = state.modules();
        moduleIndex = state.moduleIndex(moduleId);
        if (moduleIndex < 0 || moduleIndex >= modules.size()) return null;

        ModuleInstance module = modules.get(moduleIndex);
        if (!moduleId.equals(module.id)) return null;

        switch (type) {
            case ACTION_TYPE -> handleAction(this, state, module);
            case CONFIG_TYPE -> handleConfig(this, state, module, creative);
            default -> {
                return null;
            }
        }

        if (type == ACTION_TYPE && getAction() == Action.DESTROY) {
            return AssetSyncPacket.moduleRemoved(assetId, moduleIndex, module.id)
                .withSyncRevision(state.getSyncRevision());
        }
        if (type == CONFIG_TYPE && getConfigAction() == ConfigAction.SET_MINER_ORE_BLACKLISTED
            && module.groupId() != 0) {
            return AssetSyncPacket.settingsGroupUpdated(
                assetId,
                state.settingsGroups()
                    .require(module.groupId(), module.kind()))
                .withSyncRevision(state.getSyncRevision());
        }
        if (type == CONFIG_TYPE && (getConfigAction() == ConfigAction.SET_SETTINGS_GROUP
            || getConfigAction() == ConfigAction.CREATE_SETTINGS_GROUP)) {
            return AssetSyncPacket.fullSync(state)
                .withSyncRevision(state.getSyncRevision());
        }
        state.markModuleDirty(module.id);
        return AssetSyncPacket.moduleUpdated(assetId, moduleIndex, module)
            .withSyncRevision(state.getSyncRevision());
    }

    private static void handleAction(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module) {
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

    private static void handleConfig(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module,
        boolean creative) {
        switch (packet.getConfigAction()) {
            case SET_MINER_ORE_BLACKLISTED -> handleMinerOreBlacklisted(packet, state, module);
            case SET_ALLOW_SHOOTING_MODE -> handleHammerConfig(module, h -> {
                AllowShootingConfig.Mode mode = Objects
                    .requireNonNull(packet.getEnumPayload(AllowShootingConfig.Mode.class), "allow shooting mode");
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
            case SET_HAMMER_VARIANT -> {
                if (!(module.component() instanceof ModuleHammer hammer)) {
                    throw new IllegalStateException("SET_HAMMER_VARIANT sent to non-hammer module " + module.id);
                }
                HammerVariant variant = packet.getEnumPayload(HammerVariant.class);
                if (variant == null) {
                    throw new IllegalStateException("SET_HAMMER_VARIANT missing variant for module " + module.id);
                }
                ModuleTier tier = ModuleHammer.tierForVariantSwitch(variant, module.tier());
                planHammerUpgrade(state, module, hammer, variant, tier, creative);
            }
            case PLAN_HAMMER_UPGRADE -> handleHammerUpgradePlan(packet, state, module, creative);
            case PLAN_MINER_FOCUS -> handleMinerFocusPlan(packet, module);
            case SET_ROUTE_PRIORITY -> {
                if (!(module.component() instanceof ModuleHammer hammer)) {
                    throw new IllegalStateException("SET_ROUTE_PRIORITY sent to non-hammer module " + module.id);
                }
                OrbitalTransferPlanner.RoutePriority priority = packet
                    .getEnumPayload(OrbitalTransferPlanner.RoutePriority.class);
                hammer.setRoutePriority(Objects.requireNonNull(priority, "route priority"));
            }
            case SET_TIER -> {
                ModuleTier tier = PacketUtil.enumFromByte(Byte.toUnsignedInt(packet.bytePayload), ModuleTier.class);
                if (tier == null || !module.kind()
                    .allowedTiers()
                    .contains(tier)) {
                    throw new IllegalStateException(
                        "rejected tier " + tier + " for " + module.kind() + " on " + packet.assetId);
                }
                if (module.component() instanceof ModuleHammer hammer) {
                    planHammerUpgrade(state, module, hammer, hammer.variant(), tier, creative);
                } else {
                    module.setTier(tier);
                    state.layoutCache()
                        .applyMutation(MutationKind.SET_TIER, module.kind(), module);
                }
            }
            case SET_PRIORITY -> {
                ModulePriority priority = PacketUtil
                    .enumFromByte(Byte.toUnsignedInt(packet.bytePayload), ModulePriority.class);
                module.setPriorityOverride(Objects.requireNonNull(priority, "priority"));
            }
            case SET_ENABLED -> {
                module.setEnabled(packet.getBooleanPayload());
                state.layoutCache()
                    .applyMutation(MutationKind.SET_ENABLED, module.kind(), module);
            }
            case SET_SETTINGS_GROUP -> state.assignSettingsGroup(module, packet.shortPayload);
            case CREATE_SETTINGS_GROUP -> state.createSettingsGroupForModule(module, null);
            case CANCEL_MODULE_OPERATION -> state.cancelModuleOperation(module);
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> handleRecipeSlot(packet, state, module);
        }
    }

    private static boolean planHammerUpgrade(AutomatedFacility state, ModuleInstance module, ModuleHammer hammer,
        HammerVariant targetVariant, ModuleTier targetTier, boolean creative) {
        return planHammerUpgrade(state, module, hammer, targetVariant, targetTier, false, false, creative);
    }

    private static boolean planHammerUpgrade(AutomatedFacility state, ModuleInstance module, ModuleHammer hammer,
        HammerVariant targetVariant, ModuleTier targetTier, boolean reserveItems, boolean voidCompletionRefund,
        boolean creative) {
        ModuleHammer.requireTier(targetVariant, targetTier);
        ModuleTierData sourceData = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        ModuleTierData targetData = FacilityModuleRegistry.get(module.kind())
            .getTierData(targetTier);
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(targetData.constructionCost());
        Map<ItemStackWrapper, Long> completionRefundCost = FacilityModuleRegistry
            .operationCost(sourceData.constructionCost());
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new HammerModuleOperation(targetTier, targetVariant.name()),
            sourceData.buildTicks(),
            cost,
            completionRefundCost,
            sourceData.completionRefundPercent(),
            reserveItems,
            voidCompletionRefund);
        if (creative) {
            if (state == null) {
                throw new IllegalStateException(
                    "Creative hammer upgrade requires facility state for module " + module.id);
            }
            state.applyCreativeModuleOperation(module, plan);
            return true;
        }
        ModuleOperationState existingOperation = module.operationOrNull();
        if (existingOperation != null && !existingOperation.phase()
            .isTerminal()) {
            LOG.warn(
                "Rejected hammer upgrade for module {} because operation {} is active",
                module.id,
                existingOperation.phase());
            return false;
        }
        module.setOperation(ModuleOperationState.waiting(plan));
        return true;
    }

    private static void handleHammerUpgradePlan(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance module, boolean creative) {
        if (!(module.component() instanceof ModuleHammer hammer)) {
            throw new IllegalStateException("PLAN_HAMMER_UPGRADE sent to non-hammer module " + module.id);
        }
        if (packet.rawPayload == null || packet.rawPayload.length != HAMMER_UPGRADE_PAYLOAD_BYTES) {
            throw new IllegalStateException("PLAN_HAMMER_UPGRADE malformed payload for module " + module.id);
        }
        HammerVariant variant = PacketUtil.enumFromByte(Byte.toUnsignedInt(packet.rawPayload[0]), HammerVariant.class);
        ModuleTier tier = PacketUtil.enumFromByte(Byte.toUnsignedInt(packet.rawPayload[1]), ModuleTier.class);
        if (variant == null || tier == null) {
            throw new IllegalStateException(
                "PLAN_HAMMER_UPGRADE invalid target for module " + module.id + ": " + variant + "/" + tier);
        }
        boolean reserveItems = packet.rawPayload[2] == 1;
        boolean voidCompletionRefund = packet.rawPayload[3] == 1;
        planHammerUpgrade(state, module, hammer, variant, tier, reserveItems, voidCompletionRefund, creative);
    }

    private static void handleMinerFocusPlan(AssetModuleUpdatePacket packet, ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner miner)) {
            throw new IllegalStateException("PLAN_MINER_FOCUS sent to non-miner module " + module.id);
        }
        MinerFocusTier targetTier = PacketUtil
            .enumFromByte(Byte.toUnsignedInt(packet.bytePayload), MinerFocusTier.class);
        if (targetTier == null) {
            throw new IllegalStateException("PLAN_MINER_FOCUS invalid tier for module " + module.id);
        }
        String targetOreKey = targetTier == MinerFocusTier.NONE ? null : packet.stringPayload;
        if (targetTier != MinerFocusTier.NONE && (targetOreKey == null || targetOreKey.isBlank())) {
            throw new IllegalStateException("PLAN_MINER_FOCUS missing target ore for module " + module.id);
        }
        ModuleOperationState existingOperation = module.operationOrNull();
        if (existingOperation != null && !existingOperation.phase()
            .isTerminal()) {
            throw new IllegalStateException(
                "Module " + module.id + " already has active operation " + existingOperation.phase());
        }
        ModuleTierData sourceData = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(sourceData.constructionCost());
        module.setOperation(
            ModuleOperationState.waiting(
                new ModuleOperationPlan(
                    new MinerFocusOperation(module.tier(), targetTier.name(), targetOreKey),
                    sourceData.buildTicks(),
                    cost,
                    cost,
                    sourceData.completionRefundPercent(),
                    false,
                    false)));
    }

    private static void handleRecipeSlot(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) return;
        if (packet.rawPayload == null) throw new IllegalArgumentException("missing recipe slot payload");

        io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.wrappedBuffer(packet.rawPayload);
        int slotIndex = Byte.toUnsignedInt(payloadBuf.readByte());
        if (slotIndex >= RecipeSlotList.MAX_RECIPE_SLOTS) {
            throw new IllegalArgumentException("recipe slot index out of range: " + slotIndex);
        }

        RecipeConfig config = recipeModule.getRecipeConfig();
        ConfigAction action = packet.getConfigAction();

        if (action == ConfigAction.REMOVE_RECIPE_SLOT) {
            if (packet.rawPayload.length != 1) {
                throw new IllegalArgumentException("remove recipe slot payload must be exactly 1 byte");
            }
            if (config == null) return;
            if (!applyRecipeSlotMutation(config.slots(), action, slotIndex, null)) return;
            state.markModuleDirty(module.id);
            return;
        }

        // ADD or UPDATE: decode RecipeSlot from payload
        if (packet.rawPayload.length < 25) {
            throw new IllegalArgumentException("truncated recipe slot payload");
        }
        byte recipeMapOrdinal = payloadBuf.readByte();
        int recipeIndex = payloadBuf.readInt();
        long contentHash = payloadBuf.readLong();
        RecipeSnapshot ref;
        boolean enabled;
        int inputGuard;
        int outputGuard;
        byte priority;
        byte orderSize;
        if (packet.rawPayload.length == 25) {
            enabled = payloadBuf.readBoolean();
            inputGuard = payloadBuf.readInt();
            outputGuard = payloadBuf.readInt();
            priority = payloadBuf.readByte();
            orderSize = payloadBuf.readByte();
            ref = RecipeSnapshot.unresolved(recipeMapOrdinal, recipeIndex, contentHash);
        } else {
            int duration = payloadBuf.readInt();
            int eut = payloadBuf.readInt();
            ItemStack[] inputs = readItemStacks(payloadBuf);
            ItemStack[] outputs = readItemStacks(payloadBuf);
            int[] outputChances = readIntArray(payloadBuf);
            FluidStack[] fluidInputs = readFluidStacks(payloadBuf);
            FluidStack[] fluidOutputs = readFluidStacks(payloadBuf);
            int[] fluidOutputChances = readIntArray(payloadBuf);
            enabled = payloadBuf.readBoolean();
            inputGuard = payloadBuf.readInt();
            outputGuard = payloadBuf.readInt();
            priority = payloadBuf.readByte();
            orderSize = payloadBuf.readByte();
            ref = new RecipeSnapshot(
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
        RecipeSnapshot validated = RecipeSlotPayloadValidator.validate(recipeModule, ref);
        if (validated == null) return;
        RecipeSlot slot = new RecipeSlot(validated, enabled, inputGuard, outputGuard, priority, orderSize);

        if (config == null) {
            config = RecipeConfig.empty();
            recipeModule.setRecipeConfig(config);
        }

        if (!applyRecipeSlotMutation(config.slots(), action, slotIndex, slot)) return;
        state.markModuleDirty(module.id);
    }

    static boolean applyRecipeSlotMutation(RecipeSlotList slots, ConfigAction action, int slotIndex,
        @Nullable RecipeSlot slot) {
        if (slots == null || action == null || slotIndex < 0 || slotIndex >= RecipeSlotList.MAX_RECIPE_SLOTS) {
            return false;
        }

        return switch (action) {
            case ADD_RECIPE_SLOT -> {
                if (slot == null || slotIndex > slots.size()) yield false;
                slots.setOrAppend(slotIndex, slot);
                yield true;
            }
            case UPDATE_RECIPE_SLOT -> {
                if (slot == null || slots.getOrNull(slotIndex) == null) yield false;
                slots.set(slotIndex, slot);
                yield true;
            }
            case REMOVE_RECIPE_SLOT -> {
                if (slots.getOrNull(slotIndex) == null) yield false;
                slots.remove(slotIndex);
                yield true;
            }
            default -> false;
        };
    }

    private static void handleMinerOreBlacklisted(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("SET_MINER_ORE_BLACKLISTED sent to non-miner module " + module.id);
        }
        state.setMinerOreBlacklisted(module, packet.getStringPayload(), packet.getBooleanPayload());
    }

    private static void handleHammerConfig(ModuleInstance module,
        Function<ModuleHammer, AllowShootingConfig> configUpdater) {
        if (!(module.component() instanceof ModuleHammer hammer)) {
            throw new IllegalStateException("hammer config action sent to non-hammer module " + module.id);
        }
        AllowShootingConfig newConfig = configUpdater.apply(hammer);
        hammer.setConfig(Objects.requireNonNull(newConfig, "newConfig"));
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
        int len = buf.readInt();
        if (len == -1) return null;
        if (len < -1 || len > MAX_RECIPE_STACKS || len > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid item stack array length: " + len);
        }
        ItemStack[] stacks = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            if (buf.readableBytes() < 1) throw new IllegalArgumentException("truncated item stack marker");
            if (!buf.readBoolean()) continue;
            if (buf.readableBytes() < 12) throw new IllegalArgumentException("truncated item stack payload");
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
        int len = buf.readInt();
        if (len == -1) return null;
        if (len < -1 || len > MAX_RECIPE_STACKS || len > buf.readableBytes() / 4) {
            throw new IllegalArgumentException("invalid int array length: " + len);
        }
        int[] values = new int[len];
        for (int i = 0; i < len; i++) {
            values[i] = buf.readInt();
        }
        return values;
    }

    private static FluidStack[] readFluidStacks(ByteBuf buf) {
        int len = buf.readInt();
        if (len == -1) return null;
        if (len < -1 || len > MAX_RECIPE_STACKS || len > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid fluid stack array length: " + len);
        }
        FluidStack[] stacks = new FluidStack[len];
        for (int i = 0; i < len; i++) {
            if (buf.readableBytes() < 1) throw new IllegalArgumentException("truncated fluid stack marker");
            if (!buf.readBoolean()) continue;
            if (buf.readableBytes() < 2) throw new IllegalArgumentException("truncated fluid stack name");
            String fluidName = PacketUtil.readString(buf);
            if (buf.readableBytes() < 4) throw new IllegalArgumentException("truncated fluid stack amount");
            int amount = buf.readInt();
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid != null) {
                stacks[i] = new FluidStack(fluid, amount);
            }
        }
        return stacks;
    }

    private static String fluidName(FluidStack stack) {
        Fluid fluid = fluidType(stack);
        return fluid != null ? fluid.getName() : "";
    }

    private static Fluid fluidType(FluidStack stack) {
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            try {
                var field = FluidStack.class.getDeclaredField("fluid");
                field.setAccessible(true);
                return (Fluid) field.get(stack);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

}
