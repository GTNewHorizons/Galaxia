package com.gtnewhorizons.galaxia.core.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.TempTeamCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacilityInventory.BoundKind;
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
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.MutationKind;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class AssetModuleUpdatePacket implements IMessage {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private static final int ACTION_TYPE = 0;
    private static final int CONFIG_TYPE = 1;
    private static final int MAX_RECIPE_PAYLOAD_BYTES = 4096;
    private static final int MAX_RECIPE_STACKS = 64;
    private static final int HAMMER_UPGRADE_PAYLOAD_BYTES = 4;
    private static final int MAX_TILE_PICKER_TARGETS = 256;
    private static final int MAX_TILE_COORD_PAYLOAD_BYTES = Integer.BYTES + MAX_TILE_PICKER_TARGETS * Integer.BYTES * 2;
    private static final int MODULE_UPGRADE_TARGET_HEADER_BYTES = 4;
    private static final int MAX_MODULE_UPGRADE_TARGET_PAYLOAD_BYTES = MODULE_UPGRADE_TARGET_HEADER_BYTES
        + MAX_TILE_COORD_PAYLOAD_BYTES;
    private static final int NO_HAMMER_VARIANT = 255;

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

    public static AssetModuleUpdatePacket moduleSettingsGroup(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, short groupId) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.SET_SETTINGS_GROUP);
        pkt.shortPayload = groupId;
        return pkt;
    }

    public static AssetModuleUpdatePacket createModuleSettingsGroup(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId) {
        return createModuleSettingsGroup(assetId, moduleIndex, moduleId, "");
    }

    public static AssetModuleUpdatePacket createModuleSettingsGroup(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, String displayName) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.CREATE_SETTINGS_GROUP);
        pkt.stringPayload = displayName == null ? "" : displayName;
        return pkt;
    }

    public static AssetModuleUpdatePacket renameModuleSettingsGroup(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, short groupId, String displayName) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.RENAME_SETTINGS_GROUP);
        pkt.shortPayload = groupId;
        pkt.stringPayload = Objects.requireNonNull(displayName, "displayName");
        return pkt;
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

    public static AssetModuleUpdatePacket minerFocusTierPlan(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, MinerFocusTier focusTier) {
        if (focusTier == null) {
            throw new IllegalArgumentException("focusTier must not be null");
        }
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.PLAN_MINER_FOCUS_TIER);
        pkt.bytePayload = (byte) focusTier.ordinal();
        return pkt;
    }

    public static AssetModuleUpdatePacket minerFocusOre(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, String oreKey) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.SET_MINER_FOCUS_ORE);
        pkt.stringPayload = oreKey == null ? "" : oreKey;
        return pkt;
    }

    public static AssetModuleUpdatePacket copyMinerSettings(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, List<StationTileCoord> targetCoords) {
        Objects.requireNonNull(targetCoords, "targetCoords");
        if (targetCoords.isEmpty()) {
            throw new IllegalArgumentException("copy miner settings target list must not be empty");
        }
        if (targetCoords.size() > MAX_TILE_PICKER_TARGETS) {
            throw new IllegalArgumentException("too many copy miner settings targets: " + targetCoords.size());
        }
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.COPY_MINER_SETTINGS);
        ByteBuf payloadBuf = Unpooled.buffer(Integer.BYTES + targetCoords.size() * Integer.BYTES * 2);
        payloadBuf.writeInt(targetCoords.size());
        for (StationTileCoord coord : targetCoords) {
            Objects.requireNonNull(coord, "target coord");
            payloadBuf.writeInt(coord.dx());
            payloadBuf.writeInt(coord.dy());
        }
        pkt.rawPayload = new byte[payloadBuf.writerIndex()];
        payloadBuf.readBytes(pkt.rawPayload);
        return pkt;
    }

    public static AssetModuleUpdatePacket moduleUpgradeTargets(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ModuleTier targetTier, @Nullable HammerVariant targetHammerVariant,
        boolean reserveItems, boolean voidCompletionRefund, List<StationTileCoord> targetCoords) {
        Objects.requireNonNull(targetTier, "targetTier");
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, ConfigAction.PLAN_MODULE_UPGRADE_TARGETS);
        byte[] targetPayload = encodeTileCoordPayload(targetCoords);
        ByteBuf payloadBuf = Unpooled.buffer(MODULE_UPGRADE_TARGET_HEADER_BYTES + targetPayload.length);
        payloadBuf.writeByte(targetTier.ordinal());
        payloadBuf.writeByte(targetHammerVariant == null ? NO_HAMMER_VARIANT : targetHammerVariant.ordinal());
        payloadBuf.writeBoolean(reserveItems);
        payloadBuf.writeBoolean(voidCompletionRefund);
        payloadBuf.writeBytes(targetPayload);
        pkt.rawPayload = new byte[payloadBuf.writerIndex()];
        payloadBuf.readBytes(pkt.rawPayload);
        return pkt;
    }

    private static byte[] encodeTileCoordPayload(List<StationTileCoord> targetCoords) {
        Objects.requireNonNull(targetCoords, "targetCoords");
        if (targetCoords.isEmpty()) {
            throw new IllegalArgumentException("tile target list must not be empty");
        }
        if (targetCoords.size() > MAX_TILE_PICKER_TARGETS) {
            throw new IllegalArgumentException("too many tile targets: " + targetCoords.size());
        }
        ByteBuf payloadBuf = Unpooled.buffer(Integer.BYTES + targetCoords.size() * Integer.BYTES * 2);
        payloadBuf.writeInt(targetCoords.size());
        for (StationTileCoord coord : targetCoords) {
            Objects.requireNonNull(coord, "target coord");
            payloadBuf.writeInt(coord.dx());
            payloadBuf.writeInt(coord.dy());
        }
        byte[] payload = new byte[payloadBuf.writerIndex()];
        payloadBuf.readBytes(payload);
        return payload;
    }

    static List<StationTileCoord> decodeTileCoordPayload(byte[] payload) {
        if (payload == null || payload.length < Integer.BYTES || payload.length > MAX_TILE_COORD_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                "invalid tile coord payload length: " + (payload == null ? 0 : payload.length));
        }
        ByteBuf payloadBuf = Unpooled.wrappedBuffer(payload);
        int count = payloadBuf.readInt();
        if (count <= 0 || count > MAX_TILE_PICKER_TARGETS) {
            throw new IllegalArgumentException("invalid tile coord payload target count: " + count);
        }
        if (payloadBuf.readableBytes() != count * Integer.BYTES * 2) {
            throw new IllegalArgumentException("malformed tile coord payload for target count: " + count);
        }
        List<StationTileCoord> coords = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            coords.add(StationTileCoord.of(payloadBuf.readInt(), payloadBuf.readInt()));
        }
        return coords;
    }

    private static ModuleUpgradeTargetsPayload decodeModuleUpgradeTargetsPayload(byte[] payload) {
        if (payload == null || payload.length < MODULE_UPGRADE_TARGET_HEADER_BYTES + Integer.BYTES
            || payload.length > MAX_MODULE_UPGRADE_TARGET_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                "invalid module upgrade target payload length: " + (payload == null ? 0 : payload.length));
        }
        ByteBuf payloadBuf = Unpooled.wrappedBuffer(payload);
        ModuleTier targetTier = PacketUtil.enumFromByte(payloadBuf.readUnsignedByte(), ModuleTier.class);
        if (targetTier == null) {
            throw new IllegalArgumentException("invalid module upgrade target tier");
        }
        int variantOrdinal = payloadBuf.readUnsignedByte();
        HammerVariant targetHammerVariant = variantOrdinal == NO_HAMMER_VARIANT ? null
            : PacketUtil.enumFromByte(variantOrdinal, HammerVariant.class);
        if (variantOrdinal != NO_HAMMER_VARIANT && targetHammerVariant == null) {
            throw new IllegalArgumentException("invalid module upgrade hammer variant: " + variantOrdinal);
        }
        boolean reserveItems = payloadBuf.readBoolean();
        boolean voidCompletionRefund = payloadBuf.readBoolean();
        byte[] coordPayload = new byte[payloadBuf.readableBytes()];
        payloadBuf.readBytes(coordPayload);
        return new ModuleUpgradeTargetsPayload(
            targetTier,
            targetHammerVariant,
            reserveItems,
            voidCompletionRefund,
            decodeTileCoordPayload(coordPayload));
    }

    public static AssetModuleUpdatePacket recipeSlotPayload(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action, byte slotIndex, SavedRecipe slot) {
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
            payloadBuf.writeLong(slot.requestAmount());
            payloadBuf.writeByte(slot.priority());
            payloadBuf.writeByte(slot.orderSize());
            PacketUtil.writeString(payloadBuf, slot.displayName());
            pkt.rawPayload = new byte[payloadBuf.writerIndex()];
            payloadBuf.readBytes(pkt.rawPayload);
        }
        return pkt;
    }

    public static AssetModuleUpdatePacket inventoryBoundPayload(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action, BoundKind kind, String resourceKey, long amount) {
        AssetModuleUpdatePacket pkt = config(assetId, moduleIndex, moduleId, action);
        io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.buffer();
        PacketUtil.writeEnum(payloadBuf, kind);
        PacketUtil.writeString(payloadBuf, resourceKey);
        payloadBuf.writeLong(amount);
        pkt.rawPayload = new byte[payloadBuf.writerIndex()];
        payloadBuf.readBytes(pkt.rawPayload);
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
        PLAN_MINER_FOCUS_TIER,
        SET_MINER_FOCUS_ORE,
        SET_ROUTE_PRIORITY,
        SET_TIER,
        SET_PRIORITY,
        SET_ENABLED,
        SET_SETTINGS_GROUP,
        CREATE_SETTINGS_GROUP,
        RENAME_SETTINGS_GROUP,
        CANCEL_MODULE_OPERATION,
        COPY_MINER_SETTINGS,
        PLAN_MODULE_UPGRADE_TARGETS,
        SET_RECIPE_SCHEDULER_MODE,
        ADD_RECIPE_SLOT,
        UPDATE_RECIPE_SLOT,
        REMOVE_RECIPE_SLOT,
        SET_INVENTORY_BOUND,
        CLEAR_INVENTORY_BOUND
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
                case PLAN_MINER_FOCUS_TIER -> buf.writeByte(bytePayload);
                case SET_MINER_FOCUS_ORE -> PacketUtil.writeString(buf, stringPayload);
                case SET_ALLOW_SHOOTING_MODE, SET_HAMMER_VARIANT, SET_ROUTE_PRIORITY, SET_RECIPE_SCHEDULER_MODE -> buf
                    .writeByte(bytePayload);
                case PLAN_HAMMER_UPGRADE -> {
                    if (rawPayload == null || rawPayload.length != HAMMER_UPGRADE_PAYLOAD_BYTES) {
                        throw new IllegalArgumentException("invalid hammer upgrade payload");
                    }
                    buf.writeBytes(rawPayload);
                }
                case SET_ALLOW_SHOOTING_THRESHOLD -> buf.writeDouble(doublePayload);
                case SET_TIER, SET_PRIORITY, SET_ENABLED -> buf.writeByte(bytePayload);
                case SET_SETTINGS_GROUP -> buf.writeShort(shortPayload);
                case CREATE_SETTINGS_GROUP -> PacketUtil.writeString(buf, stringPayload == null ? "" : stringPayload);
                case RENAME_SETTINGS_GROUP -> {
                    buf.writeShort(shortPayload);
                    PacketUtil.writeString(buf, stringPayload);
                }
                case CANCEL_MODULE_OPERATION -> {}
                case COPY_MINER_SETTINGS, PLAN_MODULE_UPGRADE_TARGETS -> {
                    if (rawPayload != null) {
                        buf.writeInt(rawPayload.length);
                        buf.writeBytes(rawPayload);
                    } else {
                        buf.writeInt(0);
                    }
                }
                case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT, SET_INVENTORY_BOUND, CLEAR_INVENTORY_BOUND -> {
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
            case PLAN_MINER_FOCUS_TIER -> bytePayload = buf.readByte();
            case SET_MINER_FOCUS_ORE -> stringPayload = PacketUtil.readString(buf);
            case SET_ALLOW_SHOOTING_MODE, SET_HAMMER_VARIANT, SET_ROUTE_PRIORITY, SET_RECIPE_SCHEDULER_MODE -> bytePayload = buf
                .readByte();
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
            case CREATE_SETTINGS_GROUP -> stringPayload = PacketUtil.readString(buf);
            case RENAME_SETTINGS_GROUP -> {
                shortPayload = buf.readShort();
                stringPayload = PacketUtil.readString(buf);
            }
            case CANCEL_MODULE_OPERATION -> {}
            case COPY_MINER_SETTINGS -> {
                int len = buf.readInt();
                if (len <= 0 || len > MAX_TILE_COORD_PAYLOAD_BYTES || len > buf.readableBytes()) {
                    throw new IllegalArgumentException("invalid tile coord payload length: " + len);
                }
                rawPayload = new byte[len];
                buf.readBytes(rawPayload);
                decodeTileCoordPayload(rawPayload);
            }
            case PLAN_MODULE_UPGRADE_TARGETS -> {
                int len = buf.readInt();
                if (len <= 0 || len > MAX_MODULE_UPGRADE_TARGET_PAYLOAD_BYTES || len > buf.readableBytes()) {
                    throw new IllegalArgumentException("invalid module upgrade target payload length: " + len);
                }
                rawPayload = new byte[len];
                buf.readBytes(rawPayload);
                decodeModuleUpgradeTargetsPayload(rawPayload);
            }
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT, SET_INVENTORY_BOUND, CLEAR_INVENTORY_BOUND -> {
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
            || getConfigAction() == ConfigAction.CREATE_SETTINGS_GROUP
            || getConfigAction() == ConfigAction.RENAME_SETTINGS_GROUP
            || getConfigAction() == ConfigAction.COPY_MINER_SETTINGS
            || getConfigAction() == ConfigAction.PLAN_MODULE_UPGRADE_TARGETS)) {
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
            case PLAN_MINER_FOCUS_TIER -> handleMinerFocusTierPlan(packet, state, module, creative);
            case SET_MINER_FOCUS_ORE -> handleMinerFocusOre(packet, module);
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
                    planModuleTierUpgrade(state, module, tier, creative);
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
            case CREATE_SETTINGS_GROUP -> state.createSettingsGroupForModule(
                module,
                packet.stringPayload == null || packet.stringPayload.isBlank() ? null : packet.stringPayload);
            case RENAME_SETTINGS_GROUP -> state
                .renameSettingsGroupForModule(module, packet.shortPayload, packet.stringPayload);
            case CANCEL_MODULE_OPERATION -> state.cancelModuleOperation(module);
            case COPY_MINER_SETTINGS -> handleCopyMinerSettings(packet, state, module);
            case PLAN_MODULE_UPGRADE_TARGETS -> handleModuleUpgradeTargets(packet, state, module, creative);
            case SET_RECIPE_SCHEDULER_MODE -> handleRecipeSchedulerMode(packet, state, module);
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> handleRecipeSlot(packet, state, module);
            case SET_INVENTORY_BOUND, CLEAR_INVENTORY_BOUND -> handleInventoryBound(packet, state);
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

    private static boolean planModuleTierUpgrade(AutomatedFacility state, ModuleInstance module, ModuleTier targetTier,
        boolean creative) {
        ModuleTierData sourceData = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        ModuleTierData targetData = FacilityModuleRegistry.get(module.kind())
            .getTierData(targetTier);
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(targetData.constructionCost());
        Map<ItemStackWrapper, Long> completionRefundCost = FacilityModuleRegistry
            .operationCost(sourceData.constructionCost());
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new ModuleTierOperation(targetTier),
            sourceData.buildTicks(),
            cost,
            completionRefundCost,
            sourceData.completionRefundPercent(),
            false,
            false);
        if (creative) {
            if (state == null) {
                throw new IllegalStateException(
                    "Creative tier upgrade requires facility state for module " + module.id);
            }
            state.applyCreativeModuleOperation(module, plan);
            return true;
        }
        ModuleOperationState existingOperation = module.operationOrNull();
        if (existingOperation != null && !existingOperation.phase()
            .isTerminal()) {
            LOG.warn(
                "Rejected tier upgrade for module {} because operation {} is active",
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

    private static void handleMinerFocusTierPlan(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance module, boolean creative) {
        if (!(module.component() instanceof ModuleMiner miner)) {
            throw new IllegalStateException("PLAN_MINER_FOCUS_TIER sent to non-miner module " + module.id);
        }
        MinerFocusTier targetTier = PacketUtil
            .enumFromByte(Byte.toUnsignedInt(packet.bytePayload), MinerFocusTier.class);
        if (targetTier == null) {
            throw new IllegalStateException("PLAN_MINER_FOCUS_TIER invalid tier for module " + module.id);
        }
        String targetOreKey = targetTier == MinerFocusTier.NONE ? null : miner.focusOreKeyOrNull();
        ModuleTierData sourceData = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(sourceData.constructionCost());
        ModuleOperationPlan plan = new ModuleOperationPlan(
            new MinerFocusOperation(module.tier(), targetTier.name(), targetOreKey),
            sourceData.buildTicks(),
            cost,
            cost,
            sourceData.completionRefundPercent(),
            false,
            false);
        if (creative) {
            state.applyCreativeModuleOperation(module, plan);
            return;
        }
        ModuleOperationState existingOperation = module.operationOrNull();
        if (existingOperation != null && !existingOperation.phase()
            .isTerminal()) {
            throw new IllegalStateException(
                "Module " + module.id + " already has active operation " + existingOperation.phase());
        }
        module.setOperation(ModuleOperationState.waiting(plan));
    }

    private static void handleMinerFocusOre(AssetModuleUpdatePacket packet, ModuleInstance module) {
        if (!(module.component() instanceof ModuleMiner miner)) {
            throw new IllegalStateException("SET_MINER_FOCUS_ORE sent to non-miner module " + module.id);
        }
        String targetOreKey = packet.stringPayload == null || packet.stringPayload.isBlank() ? null
            : packet.stringPayload;
        miner.setFocusOre(targetOreKey);
    }

    private static void handleCopyMinerSettings(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance source) {
        if (!(source.component() instanceof ModuleMiner)) {
            throw new IllegalStateException("COPY_MINER_SETTINGS sent to non-miner module " + source.id);
        }
        StationLayout layout = state.stationLayout();
        if (layout == null) {
            throw new IllegalStateException("COPY_MINER_SETTINGS requires a station layout for " + state.assetId);
        }
        for (StationTileCoord targetCoord : decodeTileCoordPayload(packet.rawPayload)) {
            ModuleInstance target = layout.moduleAt(targetCoord);
            if (target == null) {
                throw new IllegalStateException(
                    "COPY_MINER_SETTINGS target tile is empty: " + targetCoord.dx() + "," + targetCoord.dy());
            }
            state.copyMinerRuntimeSettings(source, target);
        }
    }

    private static void handleModuleUpgradeTargets(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance source, boolean creative) {
        ModuleUpgradeTargetsPayload payload = decodeModuleUpgradeTargetsPayload(packet.rawPayload);
        StationLayout layout = state.stationLayout();
        if (layout == null) {
            throw new IllegalStateException(
                "PLAN_MODULE_UPGRADE_TARGETS requires a station layout for " + state.assetId);
        }
        Set<ModuleInstance.ID> plannedTargets = new HashSet<>();
        for (StationTileCoord targetCoord : payload.targetCoords()) {
            ModuleInstance target = layout.moduleAt(targetCoord);
            if (target == null) {
                throw new IllegalStateException(
                    "PLAN_MODULE_UPGRADE_TARGETS target tile is empty: " + targetCoord.dx() + "," + targetCoord.dy());
            }
            if (!plannedTargets.add(target.id)) continue;
            ModuleOperationState existingOperation = target.operationOrNull();
            if (!creative && existingOperation != null
                && !existingOperation.phase()
                    .isTerminal()) {
                LOG.warn(
                    "Skipped module upgrade target {} because build {} is active",
                    target.id,
                    existingOperation.phase());
                continue;
            }
            if (source.component() instanceof ModuleHammer) {
                handleHammerUpgradeTarget(state, source, target, payload, creative);
            } else {
                handleGenericUpgradeTarget(state, source, target, payload, creative);
            }
        }
    }

    private static void handleHammerUpgradeTarget(AutomatedFacility state, ModuleInstance source, ModuleInstance target,
        ModuleUpgradeTargetsPayload payload, boolean creative) {
        if (!(target.component() instanceof ModuleHammer targetHammer)) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS hammer source cannot target " + target.kind());
        }
        if (payload.targetHammerVariant() == null) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS missing hammer variant");
        }
        if (source.kind() != target.kind()) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS target kind mismatch: " + target.kind());
        }
        if (targetHammer.variant() == payload.targetHammerVariant() && target.tier() == payload.targetTier()) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS target already matches requested hammer spec");
        }
        if (planHammerUpgrade(
            state,
            target,
            targetHammer,
            payload.targetHammerVariant(),
            payload.targetTier(),
            payload.reserveItems(),
            payload.voidCompletionRefund(),
            creative) && !creative) {
            state.markModuleDirty(target.id);
        }
    }

    private static void handleGenericUpgradeTarget(AutomatedFacility state, ModuleInstance source,
        ModuleInstance target, ModuleUpgradeTargetsPayload payload, boolean creative) {
        if (payload.targetHammerVariant() != null) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS non-hammer target cannot use hammer variant");
        }
        if (source.kind() != target.kind()) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS target kind mismatch: " + target.kind());
        }
        if (!target.kind()
            .allowedTiers()
            .contains(payload.targetTier())) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS rejected tier " + payload.targetTier());
        }
        if (target.tier() == payload.targetTier()) {
            throw new IllegalStateException("PLAN_MODULE_UPGRADE_TARGETS target already has requested tier");
        }
        if (planModuleTierUpgrade(state, target, payload.targetTier(), creative) && !creative) {
            state.markModuleDirty(target.id);
        }
    }

    private static void handleRecipeSchedulerMode(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) return;
        RecipeSchedulerMode mode = PacketUtil.enumFromByte(packet.bytePayload, RecipeSchedulerMode.class);
        if (mode == null) throw new IllegalArgumentException("invalid recipe scheduler mode: " + packet.bytePayload);

        RecipeConfig config = state.recipeConfig(module);
        state.setRecipeConfig(
            module,
            new RecipeConfig(config.savedRecipes(), mode, config.notDoablePolicy(), (byte) 0, (byte) 0));
    }

    private static void handleInventoryBound(AssetModuleUpdatePacket packet, AutomatedFacility state) {
        if (packet.rawPayload == null) throw new IllegalArgumentException("missing inventory bound payload");
        io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.wrappedBuffer(packet.rawPayload);
        BoundKind kind = PacketUtil.readEnum(payloadBuf, BoundKind.class);
        String resourceKey = PacketUtil.readString(payloadBuf);
        long amount = payloadBuf.readLong();
        if (kind == null) throw new IllegalArgumentException("invalid inventory bound kind");
        if (packet.getConfigAction() == ConfigAction.SET_INVENTORY_BOUND) {
            state.inventory.setBound(kind, resourceKey, amount);
            state.markInventoryBoundDelta(kind, resourceKey, true, amount);
        } else {
            state.inventory.clearBound(kind, resourceKey);
            state.markInventoryBoundDelta(kind, resourceKey, false, amount);
        }
    }

    private static void handleRecipeSlot(AssetModuleUpdatePacket packet, AutomatedFacility state,
        ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) return;
        if (packet.rawPayload == null) throw new IllegalArgumentException("missing recipe slot payload");

        io.netty.buffer.ByteBuf payloadBuf = io.netty.buffer.Unpooled.wrappedBuffer(packet.rawPayload);
        int slotIndex = Byte.toUnsignedInt(payloadBuf.readByte());
        if (slotIndex >= SavedRecipeList.MAX_SAVED_RECIPES) {
            throw new IllegalArgumentException("recipe slot index out of range: " + slotIndex);
        }

        RecipeConfig config = state.recipeConfig(module);
        ConfigAction action = packet.getConfigAction();

        if (action == ConfigAction.REMOVE_RECIPE_SLOT) {
            if (packet.rawPayload.length != 1) {
                throw new IllegalArgumentException("remove recipe slot payload must be exactly 1 byte");
            }
            if (!applyRecipeSlotMutation(config.savedRecipes(), action, slotIndex, null)) return;
            state.setRecipeConfig(module, config);
            return;
        }

        if (packet.rawPayload.length < 2 + Integer.BYTES + Long.BYTES) {
            throw new IllegalArgumentException("truncated recipe slot payload");
        }
        byte recipeMapOrdinal = payloadBuf.readByte();
        int recipeIndex = payloadBuf.readInt();
        long contentHash = payloadBuf.readLong();
        int duration = payloadBuf.readInt();
        int eut = payloadBuf.readInt();
        ItemStack[] inputs = readItemStacks(payloadBuf);
        ItemStack[] outputs = readItemStacks(payloadBuf);
        int[] outputChances = readIntArray(payloadBuf);
        FluidStack[] fluidInputs = readFluidStacks(payloadBuf);
        FluidStack[] fluidOutputs = readFluidStacks(payloadBuf);
        int[] fluidOutputChances = readIntArray(payloadBuf);
        boolean enabled = payloadBuf.readBoolean();
        long requestAmount = payloadBuf.readLong();
        byte priority = payloadBuf.readByte();
        byte orderSize = payloadBuf.readByte();
        String displayName = PacketUtil.readString(payloadBuf);
        RecipeSnapshot ref = new RecipeSnapshot(
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
        RecipeSnapshot recipe = recipeForSlotMutation(action, config, slotIndex, recipeModule, ref);
        if (recipe == null) return;
        SavedRecipe slot = new SavedRecipe(recipe, enabled, requestAmount, priority, orderSize, displayName);

        if (!applyRecipeSlotMutation(config.savedRecipes(), action, slotIndex, slot)) return;
        state.setRecipeConfig(module, config);
    }

    static @Nullable RecipeSnapshot recipeForSlotMutation(ConfigAction action, RecipeConfig config, int slotIndex,
        IRecipeModule recipeModule, RecipeSnapshot ref) {
        if (action == ConfigAction.UPDATE_RECIPE_SLOT) {
            SavedRecipe existing = config.savedRecipes()
                .getOrNull(slotIndex);
            return existing != null ? existing.recipe() : null;
        }
        return RecipeSlotPayloadValidator.validate(recipeModule, ref);
    }

    private record ModuleUpgradeTargetsPayload(ModuleTier targetTier, @Nullable HammerVariant targetHammerVariant,
        boolean reserveItems, boolean voidCompletionRefund, List<StationTileCoord> targetCoords) {}

    static boolean applyRecipeSlotMutation(SavedRecipeList slots, ConfigAction action, int slotIndex,
        @Nullable SavedRecipe slot) {
        if (slots == null || action == null || slotIndex < 0 || slotIndex >= SavedRecipeList.MAX_SAVED_RECIPES) {
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

    public static class Handler implements IMessageHandler<AssetModuleUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(AssetModuleUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            UUID teamId = TempTeamCompat.getTeam(player);
            return message.apply(teamId);
        }
    }
}
