package com.gtnewhorizons.galaxia.core.network;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class FacilityCommandPacket implements IMessage {

    static final int MAX_PACKET_BYTES = 64 * 1024;
    static final int MAX_STRING_BYTES = 1024;
    private static final int MAX_FILTERS = 256;
    private static final int MAX_FILTER_DATA_BYTES = 32 * 1024;
    private static final int MAX_PLACEMENTS = 256;
    private static final int MAX_TARGET_IDS = 256;

    static final int OP_ADJUST_INVENTORY = 0;
    static final int OP_CLEAR_INVENTORY_RESOURCE = 1;
    static final int OP_SET_INVENTORY_BOUND = 2;
    static final int OP_CLEAR_INVENTORY_BOUND = 3;
    static final int OP_REPLACE_FILTERS = 4;
    static final int OP_PUT_LOGISTICS_CONFIG = 5;
    static final int OP_REMOVE_LOGISTICS_CONFIG = 6;
    static final int OP_BUILD_MODULES = 7;
    static final int OP_COPY_BUILD_MODULES = 8;
    static final int OP_REQUEST_MODULE_DECONSTRUCTION = 9;
    static final int OP_CANCEL_MODULE_OPERATION = 10;
    static final int OP_SET_HAMMER_SHOOTING_CONFIG = 11;
    static final int OP_SET_HAMMER_ROUTE_PRIORITY = 12;
    static final int OP_SET_MINER_FOCUS_ORE = 13;
    static final int OP_CONFIGURE_DEBUG_DATA_GENERATOR = 14;
    static final int OP_PLAN_HAMMER_UPGRADE = 15;
    static final int OP_PLAN_TIER_UPGRADE = 16;
    static final int OP_PLAN_MINER_FOCUS_UPGRADE = 17;
    static final int OP_CREATE_SETTINGS_GROUP = 18;
    static final int OP_RENAME_SETTINGS_GROUP = 19;
    static final int OP_JOIN_SETTINGS_GROUP = 20;
    static final int OP_LEAVE_SETTINGS_GROUP = 21;
    static final int OP_COPY_MODULE_SETTINGS = 22;
    static final int OP_SET_MINER_ORE_BLACKLISTED = 23;
    static final int OP_REPLACE_RECIPE_BOOK = 24;

    private FacilityCommand command;

    public FacilityCommandPacket() {}

    public FacilityCommandPacket(FacilityCommand command) {
        this.command = command;
    }

    @Override
    public void toBytes(ByteBuf destination) {
        ByteBuf encoded = Unpooled.buffer();
        try {
            encode(encoded, command);
            if (encoded.readableBytes() > MAX_PACKET_BYTES) {
                throw malformed("Facility command packet exceeds 64 KiB");
            }
            destination.writeBytes(encoded);
        } finally {
            encoded.release();
        }
    }

    @Override
    public void fromBytes(ByteBuf source) {
        command = null;
        try {
            if (source.readableBytes() > MAX_PACKET_BYTES) {
                throw malformed("Facility command packet exceeds 64 KiB");
            }
            FacilityCommand decoded = decode(source);
            if (source.isReadable()) throw malformed("Trailing facility command bytes");
            command = decoded;
        } catch (RuntimeException malformedPacket) {
            command = null;
            source.skipBytes(source.readableBytes());
        }
    }

    FacilityCommand command() {
        return command;
    }

    private static void encode(ByteBuf buf, FacilityCommand command) {
        if (command == null || command.facilityId() == null) throw malformed("Missing facility command");
        int opcode = opcode(command);
        buf.writeByte(opcode);
        writeUuid(
            buf,
            command.facilityId()
                .id());

        if (command instanceof FacilityCommand.AdjustInventory adjust) {
            writeInventoryKey(buf, adjust.resource());
            writeEnum(buf, adjust.direction());
            buf.writeLong(adjust.amount());
        } else if (command instanceof FacilityCommand.ClearInventoryResource clear) {
            writeInventoryKey(buf, clear.resource());
        } else if (command instanceof FacilityCommand.SetInventoryBound setBound) {
            writeEnum(buf, setBound.kind());
            writeBoundInventoryKey(buf, setBound.kind(), setBound.resource());
            buf.writeLong(setBound.amount());
        } else if (command instanceof FacilityCommand.ClearInventoryBound clearBound) {
            writeEnum(buf, clearBound.kind());
            writeBoundInventoryKey(buf, clearBound.kind(), clearBound.resource());
        } else if (command instanceof FacilityCommand.ReplaceFilters replaceFilters) {
            writeEnum(buf, replaceFilters.kind());
            writeFilters(buf, replaceFilters.filterKeys());
        } else if (command instanceof FacilityCommand.PutLogisticsConfig putConfig) {
            writeInventoryKey(buf, putConfig.resource());
            LogisticsResourceConfig config = require(putConfig.config(), "logistics config");
            buf.writeInt(config.minReserve());
            buf.writeInt(config.orderSize());
            writeBoolean(buf, config.isImportEnabled());
            writeBoolean(buf, config.isSupplyEnabled());
            writeEnum(buf, putConfig.accessMode());
        } else if (command instanceof FacilityCommand.RemoveLogisticsConfig removeConfig) {
            writeInventoryKey(buf, removeConfig.resource());
        } else if (command instanceof FacilityCommand.BuildModules build) {
            writeEnum(buf, build.kind());
            writeEnum(buf, build.shape());
            writeBuildPhysicalSpec(buf, build.physicalSpec());
            writeNullableSettingsGroupId(buf, build.settingsGroupId());
            writeBoolean(buf, build.instantBuild());
            writePlacements(buf, build.placements());
        } else if (command instanceof FacilityCommand.CopyBuildModules copy) {
            writeModuleId(buf, copy.sourceModuleId());
            writeBoolean(buf, copy.instantBuild());
            writePlacements(buf, copy.placements());
        } else if (command instanceof FacilityCommand.RequestModuleDeconstruction deconstruct) {
            writeModuleId(buf, deconstruct.moduleId());
        } else if (command instanceof FacilityCommand.CancelModuleOperation cancel) {
            writeModuleId(buf, cancel.moduleId());
        } else if (command instanceof FacilityCommand.ReplaceRecipeBook replaceBook) {
            RecipeBookWireCodec.writeOwner(buf, replaceBook.owner());
            RecipeBookWireCodec.writeBook(buf, replaceBook.replacement());
        } else if (command instanceof FacilityCommand.CreateSettingsGroup createGroup) {
            writeModuleId(buf, createGroup.moduleId());
            writeString(buf, createGroup.displayName());
        } else if (command instanceof FacilityCommand.RenameSettingsGroup renameGroup) {
            writeSettingsGroupId(buf, renameGroup.groupId());
            writeString(buf, renameGroup.displayName());
        } else if (command instanceof FacilityCommand.JoinSettingsGroup joinGroup) {
            writeModuleId(buf, joinGroup.moduleId());
            writeSettingsGroupId(buf, joinGroup.groupId());
        } else if (command instanceof FacilityCommand.LeaveSettingsGroup leaveGroup) {
            writeModuleId(buf, leaveGroup.moduleId());
        } else if (command instanceof FacilityCommand.CopyModuleSettings copySettings) {
            writeModuleId(buf, copySettings.sourceModuleId());
            writeTargetIds(buf, copySettings.targetModuleIds());
        } else if (command instanceof FacilityCommand.SetMinerOreBlacklisted setBlacklist) {
            writeModuleId(buf, setBlacklist.moduleId());
            writeOreKey(buf, setBlacklist.oreKey());
            writeBoolean(buf, setBlacklist.blacklisted());
        } else if (command instanceof FacilityCommand.SetHammerShootingConfig setConfig) {
            writeModuleId(buf, setConfig.moduleId());
            AllowShootingConfig config = require(setConfig.config(), "hammer shooting config");
            writeEnum(buf, config.mode());
            buf.writeDouble(config.threshold());
        } else if (command instanceof FacilityCommand.SetHammerRoutePriority setRoute) {
            writeModuleId(buf, setRoute.moduleId());
            writeEnum(buf, setRoute.priority());
        } else if (command instanceof FacilityCommand.SetMinerFocusOre setOre) {
            writeModuleId(buf, setOre.moduleId());
            writeNullableString(buf, setOre.oreKey());
        } else if (command instanceof FacilityCommand.ConfigureDebugDataGenerator configureDebug) {
            writeModuleId(buf, configureDebug.moduleId());
            writeDebugConfig(buf, configureDebug.config());
        } else if (command instanceof FacilityCommand.PlanHammerUpgrade planHammer) {
            writeTargetIds(buf, planHammer.targetModuleIds());
            writeEnum(buf, planHammer.targetVariant());
            writeEnum(buf, planHammer.targetTier());
            writeBoolean(buf, planHammer.reserveItems());
            writeBoolean(buf, planHammer.voidCompletionRefund());
        } else if (command instanceof FacilityCommand.PlanTierUpgrade planTier) {
            writeTargetIds(buf, planTier.targetModuleIds());
            writeEnum(buf, planTier.targetTier());
            writeBoolean(buf, planTier.reserveItems());
        } else if (command instanceof FacilityCommand.PlanMinerFocusUpgrade planFocus) {
            writeModuleId(buf, planFocus.moduleId());
            writeEnum(buf, planFocus.targetModuleTier());
            writeEnum(buf, planFocus.targetFocusTier());
        } else {
            throw malformed("Unsupported facility command");
        }
    }

    private static FacilityCommand decode(ByteBuf buf) {
        int opcode = buf.readUnsignedByte();
        CelestialAsset.ID facilityId = new CelestialAsset.ID(readUuid(buf));
        return switch (opcode) {
            case OP_ADJUST_INVENTORY -> new FacilityCommand.AdjustInventory(
                facilityId,
                readInventoryKey(buf),
                readEnum(buf, FacilityCommand.InventoryAdjustment.class),
                buf.readLong());
            case OP_CLEAR_INVENTORY_RESOURCE -> new FacilityCommand.ClearInventoryResource(
                facilityId,
                readInventoryKey(buf));
            case OP_SET_INVENTORY_BOUND -> readSetInventoryBound(buf, facilityId);
            case OP_CLEAR_INVENTORY_BOUND -> readClearInventoryBound(buf, facilityId);
            case OP_REPLACE_FILTERS -> new FacilityCommand.ReplaceFilters(
                facilityId,
                readEnum(buf, FacilityCommand.FilterKind.class),
                readFilters(buf));
            case OP_PUT_LOGISTICS_CONFIG -> new FacilityCommand.PutLogisticsConfig(
                facilityId,
                readInventoryKey(buf),
                new LogisticsResourceConfig(buf.readInt(), buf.readInt(), readBoolean(buf), readBoolean(buf)),
                readEnum(buf, LogisticsConfigAccessMode.class));
            case OP_REMOVE_LOGISTICS_CONFIG -> new FacilityCommand.RemoveLogisticsConfig(
                facilityId,
                readInventoryKey(buf));
            case OP_BUILD_MODULES -> readBuildModules(buf, facilityId);
            case OP_COPY_BUILD_MODULES -> new FacilityCommand.CopyBuildModules(
                facilityId,
                readModuleId(buf),
                readBoolean(buf),
                readPlacements(buf));
            case OP_REQUEST_MODULE_DECONSTRUCTION -> new FacilityCommand.RequestModuleDeconstruction(
                facilityId,
                readModuleId(buf));
            case OP_CANCEL_MODULE_OPERATION -> new FacilityCommand.CancelModuleOperation(facilityId, readModuleId(buf));
            case OP_REPLACE_RECIPE_BOOK -> new FacilityCommand.ReplaceRecipeBook(
                facilityId,
                RecipeBookWireCodec.readOwner(buf),
                RecipeBookWireCodec.readBook(buf));
            case OP_CREATE_SETTINGS_GROUP -> new FacilityCommand.CreateSettingsGroup(
                facilityId,
                readModuleId(buf),
                readString(buf));
            case OP_RENAME_SETTINGS_GROUP -> new FacilityCommand.RenameSettingsGroup(
                facilityId,
                readSettingsGroupId(buf),
                readString(buf));
            case OP_JOIN_SETTINGS_GROUP -> new FacilityCommand.JoinSettingsGroup(
                facilityId,
                readModuleId(buf),
                readSettingsGroupId(buf));
            case OP_LEAVE_SETTINGS_GROUP -> new FacilityCommand.LeaveSettingsGroup(facilityId, readModuleId(buf));
            case OP_COPY_MODULE_SETTINGS -> new FacilityCommand.CopyModuleSettings(
                facilityId,
                readModuleId(buf),
                readTargetIds(buf));
            case OP_SET_MINER_ORE_BLACKLISTED -> new FacilityCommand.SetMinerOreBlacklisted(
                facilityId,
                readModuleId(buf),
                readOreKey(buf),
                readBoolean(buf));
            case OP_SET_HAMMER_SHOOTING_CONFIG -> new FacilityCommand.SetHammerShootingConfig(
                facilityId,
                readModuleId(buf),
                new AllowShootingConfig(readEnum(buf, AllowShootingConfig.Mode.class), buf.readDouble()));
            case OP_SET_HAMMER_ROUTE_PRIORITY -> new FacilityCommand.SetHammerRoutePriority(
                facilityId,
                readModuleId(buf),
                readEnum(buf, OrbitalTransferPlanner.RoutePriority.class));
            case OP_SET_MINER_FOCUS_ORE -> new FacilityCommand.SetMinerFocusOre(
                facilityId,
                readModuleId(buf),
                readNullableString(buf));
            case OP_CONFIGURE_DEBUG_DATA_GENERATOR -> new FacilityCommand.ConfigureDebugDataGenerator(
                facilityId,
                readModuleId(buf),
                readDebugConfig(buf));
            case OP_PLAN_HAMMER_UPGRADE -> new FacilityCommand.PlanHammerUpgrade(
                facilityId,
                readTargetIds(buf),
                readEnum(buf, HammerVariant.class),
                readEnum(buf, ModuleTier.class),
                readBoolean(buf),
                readBoolean(buf));
            case OP_PLAN_TIER_UPGRADE -> new FacilityCommand.PlanTierUpgrade(
                facilityId,
                readTargetIds(buf),
                readEnum(buf, ModuleTier.class),
                readBoolean(buf));
            case OP_PLAN_MINER_FOCUS_UPGRADE -> new FacilityCommand.PlanMinerFocusUpgrade(
                facilityId,
                readModuleId(buf),
                readEnum(buf, ModuleTier.class),
                readEnum(buf, MinerFocusTier.class));
            default -> throw malformed("Unknown facility command opcode " + opcode);
        };
    }

    private static FacilityCommand.SetInventoryBound readSetInventoryBound(ByteBuf buf, CelestialAsset.ID facilityId) {
        BoundKind kind = readEnum(buf, BoundKind.class);
        InventoryKey resource = readBoundInventoryKey(buf, kind);
        return new FacilityCommand.SetInventoryBound(facilityId, kind, resource, buf.readLong());
    }

    private static FacilityCommand.ClearInventoryBound readClearInventoryBound(ByteBuf buf,
        CelestialAsset.ID facilityId) {
        BoundKind kind = readEnum(buf, BoundKind.class);
        return new FacilityCommand.ClearInventoryBound(facilityId, kind, readBoundInventoryKey(buf, kind));
    }

    private static FacilityCommand.BuildModules readBuildModules(ByteBuf buf, CelestialAsset.ID facilityId) {
        FacilityModuleKind kind = readEnum(buf, FacilityModuleKind.class);
        ModuleShape shape = readEnum(buf, ModuleShape.class);
        IModuleComponent.BuildPhysicalSpec physicalSpec = readBuildPhysicalSpec(buf);
        SettingsGroup.ID settingsGroupId = readNullableSettingsGroupId(buf);
        boolean instantBuild = readBoolean(buf);
        List<ModulePlacement> placements = readPlacements(buf);
        return new FacilityCommand.BuildModules(
            facilityId,
            kind,
            shape,
            physicalSpec,
            settingsGroupId,
            instantBuild,
            placements);
    }

    private static int opcode(FacilityCommand command) {
        if (command instanceof FacilityCommand.AdjustInventory) return OP_ADJUST_INVENTORY;
        if (command instanceof FacilityCommand.ClearInventoryResource) return OP_CLEAR_INVENTORY_RESOURCE;
        if (command instanceof FacilityCommand.SetInventoryBound) return OP_SET_INVENTORY_BOUND;
        if (command instanceof FacilityCommand.ClearInventoryBound) return OP_CLEAR_INVENTORY_BOUND;
        if (command instanceof FacilityCommand.ReplaceFilters) return OP_REPLACE_FILTERS;
        if (command instanceof FacilityCommand.PutLogisticsConfig) return OP_PUT_LOGISTICS_CONFIG;
        if (command instanceof FacilityCommand.RemoveLogisticsConfig) return OP_REMOVE_LOGISTICS_CONFIG;
        if (command instanceof FacilityCommand.BuildModules) return OP_BUILD_MODULES;
        if (command instanceof FacilityCommand.CopyBuildModules) return OP_COPY_BUILD_MODULES;
        if (command instanceof FacilityCommand.RequestModuleDeconstruction) return OP_REQUEST_MODULE_DECONSTRUCTION;
        if (command instanceof FacilityCommand.CancelModuleOperation) return OP_CANCEL_MODULE_OPERATION;
        if (command instanceof FacilityCommand.ReplaceRecipeBook) return OP_REPLACE_RECIPE_BOOK;
        if (command instanceof FacilityCommand.CreateSettingsGroup) return OP_CREATE_SETTINGS_GROUP;
        if (command instanceof FacilityCommand.RenameSettingsGroup) return OP_RENAME_SETTINGS_GROUP;
        if (command instanceof FacilityCommand.JoinSettingsGroup) return OP_JOIN_SETTINGS_GROUP;
        if (command instanceof FacilityCommand.LeaveSettingsGroup) return OP_LEAVE_SETTINGS_GROUP;
        if (command instanceof FacilityCommand.CopyModuleSettings) return OP_COPY_MODULE_SETTINGS;
        if (command instanceof FacilityCommand.SetMinerOreBlacklisted) return OP_SET_MINER_ORE_BLACKLISTED;
        if (command instanceof FacilityCommand.SetHammerShootingConfig) return OP_SET_HAMMER_SHOOTING_CONFIG;
        if (command instanceof FacilityCommand.SetHammerRoutePriority) return OP_SET_HAMMER_ROUTE_PRIORITY;
        if (command instanceof FacilityCommand.SetMinerFocusOre) return OP_SET_MINER_FOCUS_ORE;
        if (command instanceof FacilityCommand.ConfigureDebugDataGenerator) return OP_CONFIGURE_DEBUG_DATA_GENERATOR;
        if (command instanceof FacilityCommand.PlanHammerUpgrade) return OP_PLAN_HAMMER_UPGRADE;
        if (command instanceof FacilityCommand.PlanTierUpgrade) return OP_PLAN_TIER_UPGRADE;
        if (command instanceof FacilityCommand.PlanMinerFocusUpgrade) return OP_PLAN_MINER_FOCUS_UPGRADE;
        throw malformed("Unsupported facility command");
    }

    private static void writeInventoryKey(ByteBuf buf, InventoryKey key) {
        require(key, "inventory key");
        if (key instanceof ItemStackWrapper item) {
            if (item.nbt() != null) throw malformed("Tagged item inventory keys are not supported on this wire");
            String encoded = item.toKey();
            if (encoded.startsWith("unknown:")) throw malformed("Unregistered item inventory key");
            buf.writeByte(0);
            writeString(buf, encoded);
            return;
        }
        FluidKey fluid = (FluidKey) key;
        if (fluid.tag() != null) throw malformed("Tagged fluid inventory keys are not supported on this wire");
        String name = fluid.fluid()
            .getName();
        if (FluidRegistry.getFluid(name) != fluid.fluid()) throw malformed("Unregistered fluid inventory key");
        buf.writeByte(1);
        writeString(buf, name);
    }

    private static void writeBuildPhysicalSpec(ByteBuf buf, IModuleComponent.BuildPhysicalSpec spec) {
        require(spec, "build physical spec");
        if (spec instanceof IModuleComponent.BuildPhysicalSpec.Tier tier) {
            buf.writeByte(0);
            writeEnum(buf, tier.tier());
            return;
        }
        if (spec instanceof IModuleComponent.BuildPhysicalSpec.Hammer hammer) {
            buf.writeByte(1);
            writeEnum(buf, hammer.tier());
            writeEnum(buf, hammer.variant());
            return;
        }
        if (spec instanceof IModuleComponent.BuildPhysicalSpec.Miner miner) {
            buf.writeByte(2);
            writeEnum(buf, miner.tier());
            writeEnum(buf, miner.focusTier());
            return;
        }
        throw malformed("Unsupported build physical spec");
    }

    private static IModuleComponent.BuildPhysicalSpec readBuildPhysicalSpec(ByteBuf buf) {
        int type = buf.readUnsignedByte();
        ModuleTier tier = readEnum(buf, ModuleTier.class);
        return switch (type) {
            case 0 -> new IModuleComponent.BuildPhysicalSpec.Tier(tier);
            case 1 -> new IModuleComponent.BuildPhysicalSpec.Hammer(tier, readEnum(buf, HammerVariant.class));
            case 2 -> new IModuleComponent.BuildPhysicalSpec.Miner(tier, readEnum(buf, MinerFocusTier.class));
            default -> throw malformed("Unknown build physical spec " + type);
        };
    }

    private static InventoryKey readInventoryKey(ByteBuf buf) {
        int type = buf.readUnsignedByte();
        if (type != 0 && type != 1) throw malformed("Unknown inventory key type " + type);
        String encoded = readString(buf);
        if (type == 0) {
            ItemStackWrapper item = ItemStackWrapper.fromKey(encoded);
            if (item == null || !encoded.equals(item.toKey())) throw malformed("Invalid item inventory key");
            return item;
        }
        Fluid fluid = FluidRegistry.getFluid(encoded);
        if (fluid == null || !encoded.equals(fluid.getName())) throw malformed("Invalid fluid inventory key");
        return new FluidKey(fluid, null);
    }

    private static void writeBoundInventoryKey(ByteBuf buf, BoundKind kind, InventoryKey key) {
        validateBoundKey(kind, key);
        writeInventoryKey(buf, key);
    }

    private static InventoryKey readBoundInventoryKey(ByteBuf buf, BoundKind kind) {
        InventoryKey key = readInventoryKey(buf);
        validateBoundKey(kind, key);
        return key;
    }

    private static void validateBoundKey(BoundKind kind, InventoryKey key) {
        require(kind, "bound kind");
        require(key, "bound inventory key");
        boolean itemBound = kind == BoundKind.ITEM_LOWER || kind == BoundKind.ITEM_UPPER;
        if (itemBound != key.isItem()) throw malformed("Inventory key type does not match bound kind");
    }

    private static void writeFilters(ByteBuf buf, List<String> filters) {
        require(filters, "filters");
        validateCount(filters.size(), MAX_FILTERS, "filter");
        int totalBytes = 0;
        List<byte[]> encoded = new ArrayList<>(filters.size());
        for (String filter : filters) {
            byte[] bytes = encodeString(filter);
            totalBytes = Math.addExact(totalBytes, bytes.length);
            if (totalBytes > MAX_FILTER_DATA_BYTES) throw malformed("Filter string data exceeds 32 KiB");
            encoded.add(bytes);
        }
        buf.writeInt(encoded.size());
        for (byte[] bytes : encoded) writeEncodedString(buf, bytes);
    }

    private static List<String> readFilters(ByteBuf buf) {
        int count = readCount(buf, MAX_FILTERS, "filter");
        requireMinimumElementBytes(buf, count, 2, "filter");
        List<String> filters = new ArrayList<>(count);
        int totalBytes = 0;
        for (int i = 0; i < count; i++) {
            int length = readStringLength(buf);
            totalBytes = Math.addExact(totalBytes, length);
            if (totalBytes > MAX_FILTER_DATA_BYTES) throw malformed("Filter string data exceeds 32 KiB");
            filters.add(readStringBytes(buf, length));
        }
        return filters;
    }

    private static void writePlacements(ByteBuf buf, List<ModulePlacement> placements) {
        require(placements, "module placements");
        validateCount(placements.size(), MAX_PLACEMENTS, "module placement");
        buf.writeInt(placements.size());
        for (ModulePlacement placement : placements) {
            require(placement, "module placement");
            StationTileCoord anchor = require(placement.anchor(), "module placement anchor");
            if (placement.rotation() < 0 || placement.rotation() > 3) {
                throw malformed("Module placement rotation must be 0..3");
            }
            buf.writeByte(anchor.dx());
            buf.writeByte(anchor.dy());
            buf.writeByte(placement.rotation());
        }
    }

    private static List<ModulePlacement> readPlacements(ByteBuf buf) {
        int count = readCount(buf, MAX_PLACEMENTS, "module placement");
        requireMinimumElementBytes(buf, count, 3, "module placement");
        List<ModulePlacement> placements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            StationTileCoord anchor = new StationTileCoord(buf.readByte(), buf.readByte());
            int rotation = buf.readUnsignedByte();
            if (rotation > 3) throw malformed("Module placement rotation must be 0..3");
            placements.add(new ModulePlacement(anchor, rotation));
        }
        return placements;
    }

    private static void writeTargetIds(ByteBuf buf, List<ModuleInstance.ID> ids) {
        require(ids, "module target IDs");
        validateCount(ids.size(), MAX_TARGET_IDS, "module target ID");
        Set<ModuleInstance.ID> unique = new HashSet<>();
        buf.writeInt(ids.size());
        for (ModuleInstance.ID id : ids) {
            if (id == null || !unique.add(id)) throw malformed("Duplicate or null module target ID");
            writeModuleId(buf, id);
        }
    }

    private static List<ModuleInstance.ID> readTargetIds(ByteBuf buf) {
        int count = readCount(buf, MAX_TARGET_IDS, "module target ID");
        requireMinimumElementBytes(buf, count, 16, "module target ID");
        List<ModuleInstance.ID> ids = new ArrayList<>(count);
        Set<ModuleInstance.ID> unique = new HashSet<>();
        for (int i = 0; i < count; i++) {
            ModuleInstance.ID id = readModuleId(buf);
            if (!unique.add(id)) throw malformed("Duplicate module target ID");
            ids.add(id);
        }
        return ids;
    }

    private static void writeDebugConfig(ByteBuf buf, ModuleDebugDataGenerator.Config config) {
        require(config, "debug data generator config");
        writeEnum(buf, config.mode());
        writeBoolean(buf, config.enabled());
        writeEnum(buf, config.dataType());
        buf.writeLong(config.amountKb());
        buf.writeInt(config.durationTicks());
        writeNullableCelestialKey(buf, config.originBodyKey());
    }

    private static ModuleDebugDataGenerator.Config readDebugConfig(ByteBuf buf) {
        ModuleDebugDataGenerator.Mode mode = readEnum(buf, ModuleDebugDataGenerator.Mode.class);
        boolean enabled = readBoolean(buf);
        SatelliteDataType dataType = readEnum(buf, SatelliteDataType.class);
        long amountKb = buf.readLong();
        int durationTicks = buf.readInt();
        CelestialObjectKey origin = readNullableCelestialKey(buf);
        ModuleDebugDataGenerator.Config config = new ModuleDebugDataGenerator.Config(
            mode,
            enabled,
            dataType,
            amountKb,
            durationTicks,
            origin);
        if (config.enabled() != enabled || config.amountKb() != amountKb || config.durationTicks() != durationTicks) {
            throw malformed("Non-canonical debug data generator config");
        }
        return config;
    }

    private static void writeNullableCelestialKey(ByteBuf buf, CelestialObjectKey key) {
        writeBoolean(buf, key != null);
        if (key == null) return;
        writeBoolean(buf, key.isRegistered());
        if (key.isRegistered()) {
            writeEnum(buf, key.registeredBodyId());
        } else {
            writeEnum(
                buf,
                key.minorBodyId()
                    .parentBodyId());
            buf.writeInt(
                key.minorBodyId()
                    .index());
        }
    }

    private static CelestialObjectKey readNullableCelestialKey(ByteBuf buf) {
        if (!readBoolean(buf)) return null;
        if (readBoolean(buf)) return CelestialObjectKey.registered(readEnum(buf, CelestialObjectId.class));
        return CelestialObjectKey
            .minorBody(new MinorCelestialBodyId(readEnum(buf, CelestialObjectId.class), buf.readInt()));
    }

    private static void writeNullableString(ByteBuf buf, String value) {
        writeBoolean(buf, value != null);
        if (value != null) writeString(buf, value);
    }

    private static String readNullableString(ByteBuf buf) {
        return readBoolean(buf) ? readString(buf) : null;
    }

    private static void writeOreKey(ByteBuf buf, String oreKey) {
        String validated = require(oreKey, "miner ore key");
        if (validated.isBlank()) throw malformed("Miner ore key must not be blank");
        writeString(buf, validated);
    }

    private static String readOreKey(ByteBuf buf) {
        String oreKey = readString(buf);
        if (oreKey.isBlank()) throw malformed("Miner ore key must not be blank");
        return oreKey;
    }

    private static <T extends Enum<T>> void writeEnum(ByteBuf buf, T value) {
        PacketUtil.writeEnum(buf, require(value, "enum value"));
    }

    private static <T extends Enum<T>> T readEnum(ByteBuf buf, Class<T> type) {
        try {
            return PacketUtil.readEnum(buf, type);
        } catch (IllegalStateException unknownValue) {
            throw malformed(unknownValue.getMessage());
        }
    }

    private static void writeBoolean(ByteBuf buf, boolean value) {
        buf.writeByte(value ? 1 : 0);
    }

    private static boolean readBoolean(ByteBuf buf) {
        int value = buf.readUnsignedByte();
        if (value > 1) throw malformed("Boolean value must be 0 or 1");
        return value == 1;
    }

    private static void writeString(ByteBuf buf, String value) {
        writeEncodedString(buf, encodeString(value));
    }

    private static byte[] encodeString(String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(require(value, "string")));
            if (encoded.remaining() > MAX_STRING_BYTES) throw malformed("UTF-8 string exceeds 1024 bytes");
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException invalidUtf8) {
            throw malformed("Malformed UTF-8 string");
        }
    }

    private static void writeEncodedString(ByteBuf buf, byte[] bytes) {
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        return readStringBytes(buf, readStringLength(buf));
    }

    private static int readStringLength(ByteBuf buf) {
        int length = buf.readUnsignedShort();
        if (length > MAX_STRING_BYTES) throw malformed("UTF-8 string exceeds 1024 bytes");
        if (buf.readableBytes() < length) throw malformed("Truncated UTF-8 string");
        return length;
    }

    private static String readStringBytes(ByteBuf buf, int length) {
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw malformed("Malformed UTF-8 string");
        }
    }

    private static int readCount(ByteBuf buf, int maximum, String description) {
        int count = buf.readInt();
        validateCount(count, maximum, description);
        return count;
    }

    private static void requireMinimumElementBytes(ByteBuf buf, int count, int bytesPerElement, String description) {
        long minimumBytes = (long) count * bytesPerElement;
        if (buf.readableBytes() < minimumBytes) {
            throw malformed("Truncated " + description + " list");
        }
    }

    private static void validateCount(int count, int maximum, String description) {
        if (count < 0 || count > maximum) {
            throw malformed("Invalid " + description + " count " + count);
        }
    }

    private static void writeModuleId(ByteBuf buf, ModuleInstance.ID id) {
        PacketUtil.writeId(buf, require(id, "module ID").id());
    }

    private static ModuleInstance.ID readModuleId(ByteBuf buf) {
        return PacketUtil.readModuleId(buf);
    }

    private static void writeNullableSettingsGroupId(ByteBuf buf, SettingsGroup.ID id) {
        writeBoolean(buf, id != null);
        if (id != null) writeSettingsGroupId(buf, id);
    }

    private static SettingsGroup.ID readNullableSettingsGroupId(ByteBuf buf) {
        return readBoolean(buf) ? readSettingsGroupId(buf) : null;
    }

    private static void writeSettingsGroupId(ByteBuf buf, SettingsGroup.ID id) {
        buf.writeInt(require(id, "settings group ID").value());
    }

    private static SettingsGroup.ID readSettingsGroupId(ByteBuf buf) {
        return new SettingsGroup.ID(buf.readInt());
    }

    private static void writeUuid(ByteBuf buf, UUID id) {
        PacketUtil.writeId(buf, require(id, "UUID"));
    }

    private static UUID readUuid(ByteBuf buf) {
        return PacketUtil.readId(buf);
    }

    private static <T> T require(T value, String description) {
        if (value == null) throw malformed("Missing " + description);
        return value;
    }

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(message);
    }

    public static final class Handler implements IMessageHandler<FacilityCommandPacket, IMessage> {

        private static final FacilityCommandGateway GATEWAY = new FacilityCommandGateway();

        @Override
        public IMessage onMessage(FacilityCommandPacket message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (player != null) {
                ServerTickTaskQueue.schedule(() -> GATEWAY.execute(player, message.command));
            }
            return null;
        }
    }
}
