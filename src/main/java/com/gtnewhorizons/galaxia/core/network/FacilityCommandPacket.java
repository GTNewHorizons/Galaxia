package com.gtnewhorizons.galaxia.core.network;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.gtnewhorizons.galaxia.core.state.InventoryKeyState;
import com.gtnewhorizons.galaxia.core.state.ModuleSettingsState;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.core.state.RecipeBookState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.LogisticsConfigAccessMode;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDebugDataGenerator;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public final class FacilityCommandPacket implements IMessage {

    static final int MAX_MESSAGE_BODY_BYTES = 32_765;
    static final int COMPRESSED_LENGTH_PREFIX_BYTES = 2;
    static final int MAX_COMPRESSED_NBT_BYTES = MAX_MESSAGE_BODY_BYTES - COMPRESSED_LENGTH_PREFIX_BYTES;
    static final long MAX_DECOMPRESSED_NBT_BYTES = 2L * 1024 * 1024;
    static final int MAX_STRING_BYTES = 1024;
    private static final int MAX_FILTERS = 256;
    private static final int MAX_FILTER_DATA_BYTES = 32 * 1024;
    private static final int MAX_PLACEMENTS = 256;
    private static final int MAX_TARGET_IDS = 256;
    private static final int MAX_MINER_ORES = 256;
    private static final int MAX_MINER_ORE_DATA_BYTES = 32 * 1024;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private FacilityCommand command;

    public FacilityCommandPacket() {}

    public FacilityCommandPacket(FacilityCommand command) {
        this.command = command;
    }

    @Override
    public void toBytes(ByteBuf destination) {
        byte[] payload = compress(encodeEnvelope(command));
        if (payload.length == 0 || payload.length > MAX_COMPRESSED_NBT_BYTES) {
            throw malformed("Facility command compressed NBT exceeds the Forge message body limit");
        }
        destination.writeShort(payload.length);
        destination.writeBytes(payload);
    }

    @Override
    public void fromBytes(ByteBuf source) {
        command = null;
        try {
            if (source == null || source.readableBytes() < COMPRESSED_LENGTH_PREFIX_BYTES
                || source.readableBytes() > MAX_MESSAGE_BODY_BYTES) {
                throw malformed("Invalid facility command message body size");
            }
            int length = source.readUnsignedShort();
            if (length < 1 || length > MAX_COMPRESSED_NBT_BYTES || source.readableBytes() != length) {
                throw malformed("Invalid facility command compressed NBT length");
            }
            byte[] payload = new byte[length];
            source.readBytes(payload);
            command = decodeEnvelope(decompress(payload));
        } catch (RuntimeException malformedPacket) {
            command = null;
            if (source != null) source.skipBytes(source.readableBytes());
        }
    }

    FacilityCommand command() {
        return command;
    }

    private static NBTTagCompound encodeEnvelope(FacilityCommand command) {
        require(command, "facility command");
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound envelope = new NBTTagCompound();
        envelope.setString("type", encodeCommand(command, data));
        envelope.setString("facility", uuid(require(command.facilityId(), "facility ID").id(), "facility ID"));
        envelope.setTag("data", data);
        decodeEnvelope(envelope);
        return envelope;
    }

    private static FacilityCommand decodeEnvelope(NBTTagCompound envelope) {
        if (!envelope.func_150296_c()
            .equals(Set.of("type", "facility", "data"))) throw malformed("Unexpected facility command envelope fields");
        NbtReader root = new NbtReader(envelope, "facilityCommand");
        String type = root.string("type");
        CelestialAsset.ID facility = new CelestialAsset.ID(parseUuid(root.string("facility"), "facility ID"));
        NbtReader data = root.compound("data");
        return switch (type) {
            case "adjust_inventory" -> new FacilityCommand.AdjustInventory(
                facility,
                resource(data),
                data.enumValue(FacilityCommand.InventoryAdjustment.class, "direction"),
                data.longValue("amount"));
            case "clear_inventory_resource" -> new FacilityCommand.ClearInventoryResource(facility, resource(data));
            case "set_inventory_bound" -> readSetBound(facility, data, false);
            case "clear_inventory_bound" -> readSetBound(facility, data, true);
            case "replace_filters" -> new FacilityCommand.ReplaceFilters(
                facility,
                data.enumValue(FacilityCommand.FilterKind.class, "kind"),
                filters(data));
            case "put_logistics_config" -> new FacilityCommand.PutLogisticsConfig(
                facility,
                resource(data),
                new LogisticsResourceConfig(
                    data.integer("minReserve"),
                    data.integer("orderSize"),
                    data.bool("import"),
                    data.bool("supply")),
                data.enumValue(LogisticsConfigAccessMode.class, "access"));
            case "remove_logistics_config" -> new FacilityCommand.RemoveLogisticsConfig(facility, resource(data));
            case "build_modules" -> readBuild(facility, data);
            case "copy_build_modules" -> new FacilityCommand.CopyBuildModules(
                facility,
                moduleId(data, "sourceModule"),
                data.bool("instant"),
                placements(data));
            case "request_module_deconstruction" -> new FacilityCommand.RequestModuleDeconstruction(
                facility,
                moduleId(data, "module"));
            case "cancel_module_operation" -> new FacilityCommand.CancelModuleOperation(
                facility,
                moduleId(data, "module"));
            case "replace_recipe_book" -> new FacilityCommand.ReplaceRecipeBook(
                facility,
                owner(data.compound("owner")),
                RecipeBookState.decode(
                    data.compound("recipeBook")
                        .tag()));
            case "create_settings_group" -> new FacilityCommand.CreateSettingsGroup(
                facility,
                moduleId(data, "module"),
                bounded(data, "name"));
            case "rename_settings_group" -> new FacilityCommand.RenameSettingsGroup(
                facility,
                groupId(data, "group"),
                bounded(data, "name"));
            case "set_settings_group" -> new FacilityCommand.SetSettingsGroup(
                facility,
                moduleId(data, "module"),
                data.bool("hasGroup") ? groupId(data, "group") : null);
            case "copy_module_settings" -> new FacilityCommand.CopyModuleSettings(
                facility,
                moduleId(data, "sourceModule"),
                targets(data));
            case "replace_miner_settings" -> new FacilityCommand.ReplaceMinerSettings(
                facility,
                moduleId(data, "module"),
                minerSettings(data));
            case "configure_hammer" -> new FacilityCommand.ConfigureHammer(
                facility,
                moduleId(data, "module"),
                new AllowShootingConfig(
                    data.enumValue(AllowShootingConfig.Mode.class, "mode"),
                    data.doubleValue("threshold")),
                data.enumValue(OrbitalTransferPlanner.RoutePriority.class, "priority"));
            case "set_miner_focus_ore" -> {
                String ore = data.bool("hasOre") ? bounded(data, "ore") : null;
                if (ore != null && ore.isBlank()) throw malformed("Miner ore key must not be blank");
                yield new FacilityCommand.SetMinerFocusOre(facility, moduleId(data, "module"), ore);
            }
            case "configure_debug_data_generator" -> readDebug(facility, data);
            case "plan_hammer_upgrade" -> new FacilityCommand.PlanHammerUpgrade(
                facility,
                targets(data),
                data.enumValue(HammerVariant.class, "variant"),
                data.enumValue(ModuleTier.class, "tier"),
                data.bool("reserveItems"),
                data.bool("voidRefund"));
            case "plan_tier_upgrade" -> new FacilityCommand.PlanTierUpgrade(
                facility,
                targets(data),
                data.enumValue(ModuleTier.class, "tier"),
                data.bool("reserveItems"));
            case "plan_miner_focus_upgrade" -> new FacilityCommand.PlanMinerFocusUpgrade(
                facility,
                moduleId(data, "module"),
                data.enumValue(ModuleTier.class, "moduleTier"),
                data.enumValue(MinerFocusTier.class, "focusTier"));
            default -> throw malformed("Unknown facility command type " + type);
        };
    }

    private static String encodeCommand(FacilityCommand command, NBTTagCompound data) {
        return switch (command) {
            case FacilityCommand.AdjustInventory value -> {
                putResource(data, value.resource());
                putEnum(data, "direction", value.direction());
                data.setLong("amount", value.amount());
                yield "adjust_inventory";
            }
            case FacilityCommand.ClearInventoryResource value -> {
                putResource(data, value.resource());
                yield "clear_inventory_resource";
            }
            case FacilityCommand.SetInventoryBound value -> {
                putEnum(data, "kind", value.kind());
                putResource(data, value.resource());
                data.setLong("amount", value.amount());
                yield "set_inventory_bound";
            }
            case FacilityCommand.ClearInventoryBound value -> {
                putEnum(data, "kind", value.kind());
                putResource(data, value.resource());
                yield "clear_inventory_bound";
            }
            case FacilityCommand.ReplaceFilters value -> {
                putEnum(data, "kind", value.kind());
                NBTTagList filters = new NBTTagList();
                for (String filter : require(value.filterKeys(), "filter list")) {
                    filters.appendTag(new NBTTagString(require(filter, "filter")));
                }
                data.setTag("filters", filters);
                yield "replace_filters";
            }
            case FacilityCommand.PutLogisticsConfig value -> {
                putResource(data, value.resource());
                LogisticsResourceConfig config = require(value.config(), "logistics config");
                data.setInteger("minReserve", config.minReserve());
                data.setInteger("orderSize", config.orderSize());
                putBool(data, "import", config.isImportEnabled());
                putBool(data, "supply", config.isSupplyEnabled());
                putEnum(data, "access", value.accessMode());
                yield "put_logistics_config";
            }
            case FacilityCommand.RemoveLogisticsConfig value -> {
                putResource(data, value.resource());
                yield "remove_logistics_config";
            }
            case FacilityCommand.BuildModules value -> {
                putEnum(data, "kind", value.kind());
                putEnum(data, "shape", value.shape());
                data.setTag("physical", physical(value.physicalSpec()));
                putNullableGroup(data, value.settingsGroupId());
                putBool(data, "instant", value.instantBuild());
                data.setTag("placements", placements(value.placements()));
                yield "build_modules";
            }
            case FacilityCommand.CopyBuildModules value -> {
                putModuleId(data, "sourceModule", value.sourceModuleId());
                putBool(data, "instant", value.instantBuild());
                data.setTag("placements", placements(value.placements()));
                yield "copy_build_modules";
            }
            case FacilityCommand.RequestModuleDeconstruction value -> {
                putModuleId(data, "module", value.moduleId());
                yield "request_module_deconstruction";
            }
            case FacilityCommand.CancelModuleOperation value -> {
                putModuleId(data, "module", value.moduleId());
                yield "cancel_module_operation";
            }
            case FacilityCommand.ReplaceRecipeBook value -> {
                data.setTag("owner", owner(value.owner()));
                data.setTag("recipeBook", RecipeBookState.encode(value.replacement()));
                yield "replace_recipe_book";
            }
            case FacilityCommand.CreateSettingsGroup value -> {
                putModuleId(data, "module", value.moduleId());
                putBounded(data, "name", value.displayName());
                yield "create_settings_group";
            }
            case FacilityCommand.RenameSettingsGroup value -> {
                putGroupId(data, "group", value.groupId());
                putBounded(data, "name", value.displayName());
                yield "rename_settings_group";
            }
            case FacilityCommand.SetSettingsGroup value -> {
                putModuleId(data, "module", value.moduleId());
                putNullableGroup(data, value.groupId());
                yield "set_settings_group";
            }
            case FacilityCommand.CopyModuleSettings value -> {
                putModuleId(data, "sourceModule", value.sourceModuleId());
                data.setTag("targets", targets(value.targetModuleIds()));
                yield "copy_module_settings";
            }
            case FacilityCommand.ReplaceMinerSettings value -> {
                putModuleId(data, "module", value.moduleId());
                data.setTag("settings", ModuleSettingsState.encode(value.replacement()));
                yield "replace_miner_settings";
            }
            case FacilityCommand.ConfigureHammer value -> {
                putModuleId(data, "module", value.moduleId());
                AllowShootingConfig config = require(value.config(), "hammer shooting config");
                putEnum(data, "mode", config.mode());
                data.setDouble("threshold", config.threshold());
                putEnum(data, "priority", value.priority());
                yield "configure_hammer";
            }
            case FacilityCommand.SetMinerFocusOre value -> {
                putModuleId(data, "module", value.moduleId());
                putBool(data, "hasOre", value.oreKey() != null);
                if (value.oreKey() != null) data.setString("ore", value.oreKey());
                yield "set_miner_focus_ore";
            }
            case FacilityCommand.ConfigureDebugDataGenerator value -> {
                putModuleId(data, "module", value.moduleId());
                putDebug(data, value.config());
                yield "configure_debug_data_generator";
            }
            case FacilityCommand.PlanHammerUpgrade value -> {
                data.setTag("targets", targets(value.targetModuleIds()));
                putEnum(data, "variant", value.targetVariant());
                putEnum(data, "tier", value.targetTier());
                putBool(data, "reserveItems", value.reserveItems());
                putBool(data, "voidRefund", value.voidCompletionRefund());
                yield "plan_hammer_upgrade";
            }
            case FacilityCommand.PlanTierUpgrade value -> {
                data.setTag("targets", targets(value.targetModuleIds()));
                putEnum(data, "tier", value.targetTier());
                putBool(data, "reserveItems", value.reserveItems());
                yield "plan_tier_upgrade";
            }
            case FacilityCommand.PlanMinerFocusUpgrade value -> {
                putModuleId(data, "module", value.moduleId());
                putEnum(data, "moduleTier", value.targetModuleTier());
                putEnum(data, "focusTier", value.targetFocusTier());
                yield "plan_miner_focus_upgrade";
            }
        };
    }

    private static FacilityCommand readSetBound(CelestialAsset.ID facility, NbtReader data, boolean clear) {
        BoundKind kind = data.enumValue(BoundKind.class, "kind");
        InventoryKey resource = resource(data);
        boolean item = kind == BoundKind.ITEM_LOWER || kind == BoundKind.ITEM_UPPER;
        if (item != resource.isItem()) throw malformed("Inventory key type does not match bound kind");
        return clear ? new FacilityCommand.ClearInventoryBound(facility, kind, resource)
            : new FacilityCommand.SetInventoryBound(facility, kind, resource, data.longValue("amount"));
    }

    private static FacilityCommand.BuildModules readBuild(CelestialAsset.ID facility, NbtReader data) {
        boolean hasGroup = data.bool("hasGroup");
        return new FacilityCommand.BuildModules(
            facility,
            data.enumValue(FacilityModuleKind.class, "kind"),
            data.enumValue(ModuleShape.class, "shape"),
            physical(data.compound("physical")),
            hasGroup ? groupId(data, "group") : null,
            data.bool("instant"),
            placements(data));
    }

    private static FacilityCommand.ConfigureDebugDataGenerator readDebug(CelestialAsset.ID facility, NbtReader data) {
        boolean hasOrigin = data.bool("hasOrigin");
        boolean registered = hasOrigin && data.bool("registeredOrigin");
        boolean enabled = data.bool("enabled");
        long amount = data.longValue("amountKb");
        int duration = data.integer("durationTicks");
        CelestialObjectKey origin = null;
        if (hasOrigin) {
            CelestialObjectId body = data.enumValue(CelestialObjectId.class, "originBody");
            origin = registered ? CelestialObjectKey.registered(body)
                : CelestialObjectKey.minorBody(new MinorCelestialBodyId(body, data.integer("originIndex")));
        }
        ModuleDebugDataGenerator.Config config = new ModuleDebugDataGenerator.Config(
            data.enumValue(ModuleDebugDataGenerator.Mode.class, "mode"),
            enabled,
            data.enumValue(SatelliteDataType.class, "dataType"),
            amount,
            duration,
            origin);
        if (config.enabled() != enabled || config.amountKb() != amount || config.durationTicks() != duration) {
            throw malformed("Non-canonical debug data generator config");
        }
        return new FacilityCommand.ConfigureDebugDataGenerator(facility, moduleId(data, "module"), config);
    }

    private static NBTTagCompound owner(RecipeBookOwner owner) {
        NBTTagCompound out = new NBTTagCompound();
        if (owner instanceof RecipeBookOwner.Private value) {
            out.setString("type", "private");
            putModuleId(out, "module", recipeOwnerModuleId(value.moduleId()));
        } else if (owner instanceof RecipeBookOwner.Group value) {
            out.setString("type", "group");
            putGroupId(out, "group", value.groupId());
        } else {
            throw malformed("Unsupported recipe book owner");
        }
        return out;
    }

    private static RecipeBookOwner owner(NbtReader owner) {
        return switch (owner.string("type")) {
            case "private" -> new RecipeBookOwner.Private(recipeOwnerModuleId(moduleId(owner, "module")));
            case "group" -> new RecipeBookOwner.Group(groupId(owner, "group"));
            default -> throw malformed("Unknown recipe book owner type");
        };
    }

    private static NBTTagCompound physical(IModuleComponent.BuildPhysicalSpec spec) {
        NBTTagCompound out = new NBTTagCompound();
        if (spec instanceof IModuleComponent.BuildPhysicalSpec.Tier value) {
            out.setString("type", "tier");
            putEnum(out, "tier", value.tier());
        } else if (spec instanceof IModuleComponent.BuildPhysicalSpec.Hammer value) {
            out.setString("type", "hammer");
            putEnum(out, "tier", value.tier());
            putEnum(out, "variant", value.variant());
        } else if (spec instanceof IModuleComponent.BuildPhysicalSpec.Miner value) {
            out.setString("type", "miner");
            putEnum(out, "tier", value.tier());
            putEnum(out, "focusTier", value.focusTier());
        } else {
            throw malformed("Unsupported build physical spec");
        }
        return out;
    }

    private static IModuleComponent.BuildPhysicalSpec physical(NbtReader spec) {
        String type = spec.string("type");
        ModuleTier tier = spec.enumValue(ModuleTier.class, "tier");
        return switch (type) {
            case "tier" -> new IModuleComponent.BuildPhysicalSpec.Tier(tier);
            case "hammer" -> new IModuleComponent.BuildPhysicalSpec.Hammer(
                tier,
                spec.enumValue(HammerVariant.class, "variant"));
            case "miner" -> new IModuleComponent.BuildPhysicalSpec.Miner(
                tier,
                spec.enumValue(MinerFocusTier.class, "focusTier"));
            default -> throw malformed("Unknown build physical spec " + type);
        };
    }

    private static NBTTagList placements(List<ModulePlacement> placements) {
        require(placements, "module placement list");
        NBTTagList out = new NBTTagList();
        for (ModulePlacement placement : placements) {
            StationTileCoord anchor = require(require(placement, "module placement").anchor(), "placement anchor");
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", anchor.dx());
            tag.setInteger("y", anchor.dy());
            tag.setInteger("rotation", placement.rotation());
            out.appendTag(tag);
        }
        return out;
    }

    private static List<ModulePlacement> placements(NbtReader data) {
        NBTTagList tags = data.compounds("placements");
        count(tags.tagCount(), MAX_PLACEMENTS, "module placement");
        List<ModulePlacement> out = new ArrayList<>(tags.tagCount());
        for (int i = 0; i < tags.tagCount(); i++) {
            NbtReader placement = data.element("placements", i, tags.getCompoundTagAt(i));
            int rotation = placement.integer("rotation");
            if (rotation < 0 || rotation > 3) throw malformed("Invalid placement rotation");
            int x = placement.integer("x");
            int y = placement.integer("y");
            if (x < Byte.MIN_VALUE || x > Byte.MAX_VALUE || y < Byte.MIN_VALUE || y > Byte.MAX_VALUE) {
                throw malformed("Module placement coordinates are out of range");
            }
            out.add(new ModulePlacement(new StationTileCoord((byte) x, (byte) y), rotation));
        }
        return out;
    }

    private static NBTTagList targets(List<ModuleInstance.ID> ids) {
        require(ids, "module target ID list");
        NBTTagList out = new NBTTagList();
        for (ModuleInstance.ID id : ids) {
            out.appendTag(new NBTTagString(uuid(require(id, "module target ID").id(), "module target ID")));
        }
        return out;
    }

    private static List<ModuleInstance.ID> targets(NbtReader data) {
        NBTTagList tags = data.strings("targets");
        count(tags.tagCount(), MAX_TARGET_IDS, "module target ID");
        List<ModuleInstance.ID> out = new ArrayList<>(tags.tagCount());
        Set<ModuleInstance.ID> unique = new HashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            ModuleInstance.ID id = new ModuleInstance.ID(parseUuid(tags.getStringTagAt(i), "module target ID"));
            if (!unique.add(id)) throw malformed("Duplicate module target ID");
            out.add(id);
        }
        return out;
    }

    private static List<String> filters(NbtReader data) {
        NBTTagList tags = data.strings("filters");
        count(tags.tagCount(), MAX_FILTERS, "filter");
        List<String> out = new ArrayList<>(tags.tagCount());
        Set<String> unique = new HashSet<>();
        int total = 0;
        for (int i = 0; i < tags.tagCount(); i++) {
            String value = validateString(tags.getStringTagAt(i));
            if (!unique.add(value)) throw malformed("Duplicate filter");
            total = Math.addExact(total, value.getBytes(StandardCharsets.UTF_8).length);
            if (total > MAX_FILTER_DATA_BYTES) throw malformed("Filter string data is too large");
            out.add(value);
        }
        return out;
    }

    private static MinerSettings minerSettings(NbtReader data) {
        var decoded = ModuleSettingsState.decode(
            FacilityModuleKind.MINER,
            data.compound("settings")
                .tag());
        if (!(decoded instanceof MinerSettings miner)) throw malformed("Expected miner settings");
        Set<String> ores = miner.blacklistedOreKeys();
        count(ores.size(), MAX_MINER_ORES, "miner ore");
        int total = 0;
        for (String ore : ores) {
            String valid = validateString(ore);
            if (valid.isBlank()) throw malformed("Miner ore key must not be blank");
            total = Math.addExact(total, valid.getBytes(StandardCharsets.UTF_8).length);
            if (total > MAX_MINER_ORE_DATA_BYTES) throw malformed("Miner ore string data is too large");
        }
        return miner;
    }

    private static void putDebug(NBTTagCompound data, ModuleDebugDataGenerator.Config config) {
        require(config, "debug data generator config");
        putEnum(data, "mode", config.mode());
        putBool(data, "enabled", config.enabled());
        putEnum(data, "dataType", config.dataType());
        data.setLong("amountKb", config.amountKb());
        data.setInteger("durationTicks", config.durationTicks());
        CelestialObjectKey origin = config.originBodyKey();
        putBool(data, "hasOrigin", origin != null);
        if (origin == null) return;
        boolean registered = origin.isRegistered();
        putBool(data, "registeredOrigin", registered);
        if (registered) {
            putEnum(data, "originBody", origin.registeredBodyId());
        } else {
            MinorCelestialBodyId minor = origin.minorBodyId();
            putEnum(data, "originBody", minor.parentBodyId());
            data.setInteger("originIndex", minor.index());
        }
    }

    private static void putResource(NBTTagCompound data, InventoryKey resource) {
        data.setTag("resource", InventoryKeyState.encode(require(resource, "inventory key")));
    }

    private static InventoryKey resource(NbtReader data) {
        try {
            return InventoryKeyState.decode(
                data.compound("resource")
                    .tag());
        } catch (RuntimeException invalid) {
            throw malformed("Invalid inventory key");
        }
    }

    private static void putModuleId(NBTTagCompound data, String key, ModuleInstance.ID id) {
        data.setString(key, uuid(require(id, "module ID").id(), "module ID"));
    }

    private static ModuleInstance.ID moduleId(NbtReader data, String key) {
        return new ModuleInstance.ID(parseUuid(data.string(key), "module ID"));
    }

    private static ModuleInstance.ID recipeOwnerModuleId(ModuleInstance.ID id) {
        ModuleInstance.ID valid = require(id, "recipe owner module ID");
        if (ZERO_UUID.equals(require(valid.id(), "recipe owner module UUID"))) {
            throw malformed("Invalid recipe owner module ID");
        }
        return valid;
    }

    private static void putNullableGroup(NBTTagCompound data, SettingsGroup.ID id) {
        putBool(data, "hasGroup", id != null);
        if (id != null) putGroupId(data, "group", id);
    }

    private static void putGroupId(NBTTagCompound data, String key, SettingsGroup.ID id) {
        data.setInteger(key, require(id, "settings group ID").value());
    }

    private static SettingsGroup.ID groupId(NbtReader data, String key) {
        return new SettingsGroup.ID(data.integer(key));
    }

    private static void putBounded(NBTTagCompound data, String key, String value) {
        data.setString(key, validateString(value));
    }

    private static String bounded(NbtReader data, String key) {
        return validateString(data.string(key));
    }

    private static String validateString(String value) {
        String valid = require(value, "string");
        if (valid.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) {
            throw malformed("UTF-8 string exceeds 1024 bytes");
        }
        return valid;
    }

    private static <T extends Enum<T>> void putEnum(NBTTagCompound data, String key, T value) {
        data.setString(key, require(value, "enum value").name());
    }

    private static void putBool(NBTTagCompound data, String key, boolean value) {
        data.setByte(key, (byte) (value ? 1 : 0));
    }

    private static void count(int count, int maximum, String description) {
        if (count < 0 || count > maximum) throw malformed("Invalid " + description + " count " + count);
    }

    private static String uuid(UUID value, String description) {
        return require(value, description).toString();
    }

    private static UUID parseUuid(String value, String description) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString()
                .equals(value)) throw malformed("Non-canonical " + description);
            return parsed;
        } catch (IllegalArgumentException invalid) {
            throw malformed("Invalid " + description);
        }
    }

    private static byte[] compress(NBTTagCompound envelope) {
        try {
            return CompressedStreamTools.compress(envelope);
        } catch (IOException invalid) {
            throw malformed("Could not compress facility command NBT");
        }
    }

    private static NBTTagCompound decompress(byte[] payload) {
        try {
            NBTTagCompound envelope = CompressedStreamTools
                .func_152457_a(payload, new NBTSizeTracker(MAX_DECOMPRESSED_NBT_BYTES));
            if (envelope == null) throw malformed("Null facility command NBT");
            if (!Arrays.equals(payload, compress(envelope)))
                throw malformed("Non-canonical or trailing compressed NBT");
            return envelope;
        } catch (IOException | RuntimeException invalid) {
            throw malformed("Malformed compressed facility command NBT");
        }
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
            if (player != null) ServerTickTaskQueue.schedule(() -> GATEWAY.execute(player, message.command));
            return null;
        }
    }
}
