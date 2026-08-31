package com.gtnewhorizons.galaxia.core.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.TeamAction;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class AssetModuleUpdatePacket implements IMessage {

    private static final Logger LOG = LogManager.getLogger("Galaxia");

    private static final int MAX_RECIPE_PAYLOAD_BYTES = 4096;
    private static final int MAX_RECIPE_STACKS = 64;

    private CelestialAsset.ID assetId;
    private int moduleIndex;
    private ModuleInstance.ID moduleId;
    private ConfigAction configAction;
    private byte bytePayload;
    private byte[] rawPayload;

    public AssetModuleUpdatePacket() {}

    private static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action) {
        AssetModuleUpdatePacket packet = new AssetModuleUpdatePacket();
        packet.assetId = assetId;
        packet.moduleIndex = moduleIndex;
        packet.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        packet.configAction = Objects.requireNonNull(action, "action");
        return packet;
    }

    public static AssetModuleUpdatePacket config(CelestialAsset.ID assetId, int moduleIndex, ModuleInstance.ID moduleId,
        ConfigAction action, Enum<?> payload) {
        AssetModuleUpdatePacket packet = config(assetId, moduleIndex, moduleId, action);
        packet.bytePayload = (byte) Objects.requireNonNull(payload, "payload")
            .ordinal();
        return packet;
    }

    public static AssetModuleUpdatePacket recipeSlotPayload(CelestialAsset.ID assetId, int moduleIndex,
        ModuleInstance.ID moduleId, ConfigAction action, byte slotIndex, SavedRecipe slot) {
        if (action != ConfigAction.ADD_RECIPE_SLOT && action != ConfigAction.UPDATE_RECIPE_SLOT
            && action != ConfigAction.REMOVE_RECIPE_SLOT) {
            throw new IllegalArgumentException("not a recipe slot action: " + action);
        }
        AssetModuleUpdatePacket packet = config(assetId, moduleIndex, moduleId, action);
        if (action == ConfigAction.REMOVE_RECIPE_SLOT) {
            packet.rawPayload = new byte[] { slotIndex };
            return packet;
        }
        if (slot == null) throw new IllegalArgumentException("recipe slot payload must not be null");

        ByteBuf payload = Unpooled.buffer();
        payload.writeByte(slotIndex);
        payload.writeByte(
            slot.recipe()
                .recipeMapOrdinal());
        payload.writeInt(
            slot.recipe()
                .recipeIndex());
        payload.writeLong(
            slot.recipe()
                .contentHash());
        payload.writeInt(
            slot.recipe()
                .duration());
        payload.writeInt(
            slot.recipe()
                .eut());
        writeItemStacks(
            payload,
            slot.recipe()
                .inputs());
        writeItemStacks(
            payload,
            slot.recipe()
                .outputs());
        writeIntArray(
            payload,
            slot.recipe()
                .outputChances());
        writeFluidStacks(
            payload,
            slot.recipe()
                .fluidInputs());
        writeFluidStacks(
            payload,
            slot.recipe()
                .fluidOutputs());
        writeIntArray(
            payload,
            slot.recipe()
                .fluidOutputChances());
        payload.writeBoolean(slot.enabled());
        payload.writeLong(slot.requestAmount());
        payload.writeByte(slot.priority());
        payload.writeByte(slot.orderSize());
        PacketUtil.writeString(payload, slot.displayName());
        if (payload.writerIndex() > MAX_RECIPE_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("recipe payload exceeds limit: " + payload.writerIndex());
        }
        packet.rawPayload = new byte[payload.writerIndex()];
        payload.readBytes(packet.rawPayload);
        return packet;
    }

    public enum ConfigAction {
        SET_RECIPE_SCHEDULER_MODE,
        ADD_RECIPE_SLOT,
        UPDATE_RECIPE_SLOT,
        REMOVE_RECIPE_SLOT
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketUtil.writeId(buf, assetId);
        buf.writeInt(moduleIndex);
        PacketUtil.writeId(buf, moduleId);
        PacketUtil.writeEnum(buf, configAction);
        switch (configAction) {
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> writeRawPayload(buf);
            case SET_RECIPE_SCHEDULER_MODE -> buf.writeByte(bytePayload);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        assetId = PacketUtil.readAssetId(buf);
        moduleIndex = buf.readInt();
        moduleId = PacketUtil.readModuleId(buf);
        configAction = PacketUtil.readEnum(buf, ConfigAction.class);
        switch (configAction) {
            case SET_RECIPE_SCHEDULER_MODE -> bytePayload = buf.readByte();
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> rawPayload = readRawPayload(
                buf,
                MAX_RECIPE_PAYLOAD_BYTES,
                "recipe");
        }
    }

    private void writeRawPayload(ByteBuf buf) {
        int length = rawPayload == null ? 0 : rawPayload.length;
        buf.writeInt(length);
        if (length > 0) buf.writeBytes(rawPayload);
    }

    private static byte[] readRawPayload(ByteBuf buf, int maximumLength, String name) {
        int length = buf.readInt();
        if (length <= 0 || length > maximumLength || length > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid " + name + " payload length: " + length);
        }
        byte[] payload = new byte[length];
        buf.readBytes(payload);
        return payload;
    }

    public ConfigAction getConfigAction() {
        return configAction;
    }

    public byte[] getRawPayload() {
        return rawPayload;
    }

    public boolean apply(UUID teamId) {
        CelestialAsset asset = CelestialAssetStore.findAsset(assetId);
        if (!(asset instanceof AutomatedFacility state)) return false;
        if (!CelestialAssetStore.isOwnedBy(teamId, assetId) || configAction == null || moduleId == null) return false;

        moduleIndex = state.moduleIndex(moduleId);
        if (moduleIndex < 0 || moduleIndex >= state.modules()
            .size()) {
            return false;
        }
        ModuleInstance module = state.modules()
            .get(moduleIndex);
        if (!moduleId.equals(module.id)) return false;

        handleConfig(state, module);
        return true;
    }

    CelestialAsset.ID assetId() {
        return assetId;
    }

    private void handleConfig(AutomatedFacility state, ModuleInstance module) {
        switch (configAction) {
            case SET_RECIPE_SCHEDULER_MODE -> handleRecipeSchedulerMode(state, module);
            case ADD_RECIPE_SLOT, UPDATE_RECIPE_SLOT, REMOVE_RECIPE_SLOT -> handleRecipeSlot(state, module);
        }
    }

    private void handleRecipeSchedulerMode(AutomatedFacility state, ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule)) return;
        RecipeSchedulerMode mode = PacketUtil.enumFromByte(Byte.toUnsignedInt(bytePayload), RecipeSchedulerMode.class);
        if (mode == null) throw new IllegalArgumentException("invalid recipe scheduler mode: " + bytePayload);

        RecipeConfig config = state.recipeConfig(module);
        state.setRecipeConfig(
            module,
            new RecipeConfig(config.savedRecipes(), mode, config.notDoablePolicy(), (byte) 0, (byte) 0));
    }

    private void handleRecipeSlot(AutomatedFacility state, ModuleInstance module) {
        if (!(module.component() instanceof IRecipeModule recipeModule)) return;
        if (rawPayload == null) throw new IllegalArgumentException("missing recipe slot payload");

        ByteBuf payload = Unpooled.wrappedBuffer(rawPayload);
        int slotIndex = Byte.toUnsignedInt(payload.readByte());
        if (slotIndex >= SavedRecipeList.MAX_SAVED_RECIPES) {
            throw new IllegalArgumentException("recipe slot index out of range: " + slotIndex);
        }

        RecipeConfig config = state.recipeConfig(module);
        if (configAction == ConfigAction.REMOVE_RECIPE_SLOT) {
            if (rawPayload.length != 1) {
                throw new IllegalArgumentException("remove recipe slot payload must be exactly 1 byte");
            }
            if (applyRecipeSlotMutation(config.savedRecipes(), configAction, slotIndex, null)) {
                state.setRecipeConfig(module, config);
            }
            return;
        }
        if (rawPayload.length < 2 + Integer.BYTES + Long.BYTES) {
            throw new IllegalArgumentException("truncated recipe slot payload");
        }

        byte recipeMapOrdinal = payload.readByte();
        int recipeIndex = payload.readInt();
        long contentHash = payload.readLong();
        int duration = payload.readInt();
        int eut = payload.readInt();
        ItemStack[] inputs = readItemStacks(payload);
        ItemStack[] outputs = readItemStacks(payload);
        int[] outputChances = readIntArray(payload);
        FluidStack[] fluidInputs = readFluidStacks(payload);
        FluidStack[] fluidOutputs = readFluidStacks(payload);
        int[] fluidOutputChances = readIntArray(payload);
        boolean enabled = payload.readBoolean();
        long requestAmount = payload.readLong();
        byte priority = payload.readByte();
        byte orderSize = payload.readByte();
        String displayName = PacketUtil.readString(payload);
        if (payload.isReadable()) throw new IllegalArgumentException("unexpected trailing recipe payload bytes");

        RecipeSnapshot reference = new RecipeSnapshot(
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
        RecipeSnapshot recipe = recipeForSlotMutation(configAction, config, slotIndex, recipeModule, reference);
        if (recipe == null) return;
        SavedRecipe slot = new SavedRecipe(recipe, enabled, requestAmount, priority, orderSize, displayName);
        if (applyRecipeSlotMutation(config.savedRecipes(), configAction, slotIndex, slot)) {
            state.setRecipeConfig(module, config);
        }
    }

    static @Nullable RecipeSnapshot recipeForSlotMutation(ConfigAction action, RecipeConfig config, int slotIndex,
        IRecipeModule recipeModule, RecipeSnapshot reference) {
        if (action == ConfigAction.UPDATE_RECIPE_SLOT) {
            SavedRecipe existing = config.savedRecipes()
                .getOrNull(slotIndex);
            return existing != null ? existing.recipe() : null;
        }
        return RecipeSlotPayloadValidator.validate(recipeModule, reference);
    }

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

    private static void writeItemStacks(ByteBuf buf, ItemStack[] stacks) {
        if (stacks == null) {
            buf.writeInt(-1);
            return;
        }
        if (stacks.length > MAX_RECIPE_STACKS) throw new IllegalArgumentException("too many item stacks");
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
        int length = buf.readInt();
        if (length == -1) return null;
        if (length < -1 || length > MAX_RECIPE_STACKS || length > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid item stack array length: " + length);
        }
        ItemStack[] stacks = new ItemStack[length];
        for (int i = 0; i < length; i++) {
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
        if (stacks.length > MAX_RECIPE_STACKS) throw new IllegalArgumentException("too many fluid stacks");
        buf.writeInt(stacks.length);
        for (FluidStack stack : stacks) {
            buf.writeBoolean(stack != null);
            if (stack == null) continue;
            PacketUtil.writeString(buf, fluidName(stack));
            buf.writeInt(stack.amount);
        }
    }

    private static FluidStack[] readFluidStacks(ByteBuf buf) {
        int length = buf.readInt();
        if (length == -1) return null;
        if (length < -1 || length > MAX_RECIPE_STACKS || length > buf.readableBytes()) {
            throw new IllegalArgumentException("invalid fluid stack array length: " + length);
        }
        FluidStack[] stacks = new FluidStack[length];
        for (int i = 0; i < length; i++) {
            if (buf.readableBytes() < 1) throw new IllegalArgumentException("truncated fluid stack marker");
            if (!buf.readBoolean()) continue;
            if (buf.readableBytes() < 2) throw new IllegalArgumentException("truncated fluid stack name");
            String name = PacketUtil.readString(buf);
            if (buf.readableBytes() < 4) throw new IllegalArgumentException("truncated fluid stack amount");
            int amount = buf.readInt();
            Fluid fluid = FluidRegistry.getFluid(name);
            if (fluid != null) stacks[i] = new FluidStack(fluid, amount);
        }
        return stacks;
    }

    private static void writeIntArray(ByteBuf buf, int[] values) {
        if (values == null) {
            buf.writeInt(-1);
            return;
        }
        if (values.length > MAX_RECIPE_STACKS) throw new IllegalArgumentException("too many recipe values");
        buf.writeInt(values.length);
        for (int value : values) buf.writeInt(value);
    }

    private static int[] readIntArray(ByteBuf buf) {
        int length = buf.readInt();
        if (length == -1) return null;
        if (length < -1 || length > MAX_RECIPE_STACKS || length > buf.readableBytes() / Integer.BYTES) {
            throw new IllegalArgumentException("invalid int array length: " + length);
        }
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = buf.readInt();
        return values;
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
            if (player == null) return null;
            ServerTickTaskQueue.schedule(() -> {
                if (!GTTeamsCompat.hasPermission(player, TeamAction.MODIFY_MODULE)) return;
                UUID teamId = GTTeamsCompat.getTeam(player);
                if (message.apply(teamId)) AssetStateSync.SERVER.publishInteractive(message.assetId);
            });
            return null;
        }
    }
}
