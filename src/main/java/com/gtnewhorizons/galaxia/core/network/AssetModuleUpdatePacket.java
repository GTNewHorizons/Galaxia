package com.gtnewhorizons.galaxia.core.network;

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
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
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

    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        moduleId = PacketUtil.readModuleId(buf);
        type = buf.readUnsignedByte();
        int rawAction = buf.readUnsignedByte();

        if (type == ACTION_TYPE) {
            action = PacketUtil.enumFromByte(rawAction, Action.class);
            if (action == null) {
                LOG.warn("[Network] Ignoring AssetModuleUpdatePacket with unknown action ordinal: {}", rawAction);
            }
            return;
        }
        if (type != CONFIG_TYPE) {
            LOG.warn("[Network] Ignoring AssetModuleUpdatePacket with unknown type: {}", type);
            return;
        }

        configAction = PacketUtil.enumFromByte(rawAction, ConfigAction.class);
        if (configAction == null) {
            LOG.warn("[Network] Ignoring AssetModuleUpdatePacket with unknown config action ordinal: {}", rawAction);
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
            case CONFIG_TYPE -> handleConfig(this, state, module);
            default -> {
                return null;
            }
        }

        if (type == ACTION_TYPE && getAction() == Action.DESTROY) {
            return AssetSyncPacket.moduleRemoved(assetId, moduleIndex, module.id)
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

    private static void handleConfig(AssetModuleUpdatePacket packet, AutomatedFacility state, ModuleInstance module) {
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
                    LOG.warn(
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

    private static void handleMinerBlacklist(ModuleInstance module, String payload, boolean add,
        AutomatedFacility state, int moduleIndex) {
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

    private static void handleMinerCopySettings(ModuleInstance module, boolean payload, AutomatedFacility state,
        int moduleIndex) {
        if (!(module.component() instanceof ModuleMiner miner)) return;
        miner.setCopySettingToOtherMiners(payload);
        if (payload) {
            copyMinerSettingsToOtherMiners(state, moduleIndex, miner);
        }
    }

    private static void handleHammerConfig(ModuleInstance module,
        Function<ModuleHammer, AllowShootingConfig> configUpdater) {
        if (!(module.component() instanceof ModuleHammer hammer)) return;
        AllowShootingConfig newConfig = configUpdater.apply(hammer);
        if (newConfig != null) {
            hammer.setConfig(newConfig);
        }
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
