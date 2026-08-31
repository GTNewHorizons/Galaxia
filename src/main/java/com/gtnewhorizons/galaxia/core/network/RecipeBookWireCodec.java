package com.gtnewhorizons.galaxia.core.network;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Shared bounded wire format for complete immutable recipe books. */
final class RecipeBookWireCodec {

    static final int MAX_BOOK_BYTES = 63 * 1024;
    static final int MAX_DISPLAY_NAME_BYTES = 1024;

    private static final int MAX_RECIPE_ARRAY_ENTRIES = 64;
    private static final int MAX_FLUID_NAME_BYTES = 1024;
    private static final int OWNER_PRIVATE = 0;
    private static final int OWNER_GROUP = 1;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private RecipeBookWireCodec() {}

    static void writeOwner(ByteBuf buf, RecipeBookOwner owner) {
        if (owner instanceof RecipeBookOwner.Private privateOwner) {
            ModuleInstance.ID moduleId = requireModuleId(privateOwner.moduleId());
            buf.writeByte(OWNER_PRIVATE);
            PacketUtil.writeId(buf, moduleId);
            return;
        }
        if (owner instanceof RecipeBookOwner.Group groupOwner) {
            SettingsGroup.ID groupId = requireGroupId(groupOwner.groupId());
            buf.writeByte(OWNER_GROUP);
            buf.writeInt(groupId.value());
            return;
        }
        throw malformed("Missing or unsupported recipe book owner");
    }

    static RecipeBookOwner readOwner(ByteBuf buf) {
        int discriminant = buf.readUnsignedByte();
        return switch (discriminant) {
            case OWNER_PRIVATE -> new RecipeBookOwner.Private(requireModuleId(PacketUtil.readModuleId(buf)));
            case OWNER_GROUP -> new RecipeBookOwner.Group(requireGroupId(new SettingsGroup.ID(buf.readInt())));
            default -> throw malformed("Unknown recipe book owner discriminant " + discriminant);
        };
    }

    static void writeBook(ByteBuf destination, RecipeBook book) {
        if (book == null) throw malformed("Missing recipe book");
        ByteBuf payload = Unpooled.buffer();
        try {
            writePayload(payload, book);
            int length = payload.readableBytes();
            if (length > MAX_BOOK_BYTES) throw malformed("Recipe book payload exceeds 63 KiB");
            destination.writeInt(length);
            destination.writeBytes(payload);
        } finally {
            payload.release();
        }
    }

    static RecipeBook readBook(ByteBuf source) {
        int length = source.readInt();
        if (length < 0 || length > MAX_BOOK_BYTES) {
            throw malformed("Invalid recipe book payload length: " + length);
        }
        if (length > source.readableBytes()) throw malformed("Truncated recipe book payload");
        ByteBuf payload = source.readSlice(length);
        RecipeBook book = readPayload(payload);
        if (payload.isReadable()) throw malformed("Trailing recipe book payload bytes");
        return book;
    }

    private static void writePayload(ByteBuf buf, RecipeBook book) {
        PacketUtil.writeEnum(buf, book.mode());
        PacketUtil.writeEnum(buf, book.notDoablePolicy());
        PacketUtil.writeBoundedCount(
            buf,
            book.recipes()
                .size(),
            "saved recipes",
            RecipeBook.MAX_RECIPES);
        for (SavedRecipe recipe : book.recipes()) writeSavedRecipe(buf, recipe);
    }

    private static RecipeBook readPayload(ByteBuf buf) {
        RecipeSchedulerMode mode = PacketUtil.readEnum(buf, RecipeSchedulerMode.class);
        NotDoablePolicy policy = PacketUtil.readEnum(buf, NotDoablePolicy.class);
        int count = PacketUtil.readBoundedCount(buf, "saved recipes", RecipeBook.MAX_RECIPES);
        List<SavedRecipe> recipes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) recipes.add(readSavedRecipe(buf));
        return new RecipeBook(recipes, mode, policy);
    }

    private static void writeSavedRecipe(ByteBuf buf, SavedRecipe saved) {
        if (saved == null) throw malformed("Missing saved recipe");
        RecipeSnapshot snapshot = saved.recipe();
        buf.writeByte(snapshot.recipeMapOrdinal());
        buf.writeInt(snapshot.recipeIndex());
        buf.writeLong(snapshot.contentHash());
        buf.writeInt(snapshot.duration());
        buf.writeInt(snapshot.eut());
        writeItemStacks(buf, snapshot.inputs());
        writeItemStacks(buf, snapshot.outputs());
        writeIntArray(buf, snapshot.outputChances());
        writeFluidStacks(buf, snapshot.fluidInputs());
        writeFluidStacks(buf, snapshot.fluidOutputs());
        writeIntArray(buf, snapshot.fluidOutputChances());
        buf.writeBoolean(saved.enabled());
        buf.writeLong(saved.requestAmount());
        buf.writeByte(saved.priority());
        buf.writeByte(saved.orderSize());
        writeUtf8(buf, saved.displayName(), MAX_DISPLAY_NAME_BYTES, "recipe display name");
    }

    private static SavedRecipe readSavedRecipe(ByteBuf buf) {
        byte recipeMapOrdinal = buf.readByte();
        int recipeIndex = buf.readInt();
        long contentHash = buf.readLong();
        int duration = buf.readInt();
        int eut = buf.readInt();
        if (duration <= 0) throw malformed("Recipe duration must be positive");
        if (eut < 0) throw malformed("Recipe EU/t must not be negative");
        ItemStack[] inputs = readItemStacks(buf);
        ItemStack[] outputs = readItemStacks(buf);
        int[] outputChances = readIntArray(buf);
        FluidStack[] fluidInputs = readFluidStacks(buf);
        FluidStack[] fluidOutputs = readFluidStacks(buf);
        int[] fluidOutputChances = readIntArray(buf);
        RecipeSnapshot snapshot = new RecipeSnapshot(
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
        boolean enabled = readBoolean(buf);
        long requestAmount = buf.readLong();
        byte priority = buf.readByte();
        byte orderSize = buf.readByte();
        String displayName = readUtf8(buf, MAX_DISPLAY_NAME_BYTES, "recipe display name");
        return new SavedRecipe(snapshot, enabled, requestAmount, priority, orderSize, displayName);
    }

    private static void writeItemStacks(ByteBuf buf, ItemStack[] stacks) {
        writeArrayLength(buf, stacks == null ? -1 : stacks.length, "item stack");
        if (stacks == null) return;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0 || stack.getItemDamage() < 0) {
                throw malformed("Invalid recipe item stack");
            }
            int itemId = Item.getIdFromItem(stack.getItem());
            if (itemId <= 0) throw malformed("Unregistered recipe item stack");
            buf.writeInt(itemId);
            buf.writeInt(stack.getItemDamage());
            buf.writeInt(stack.stackSize);
            ByteBufUtils.writeTag(buf, stack.getTagCompound());
        }
    }

    private static ItemStack[] readItemStacks(ByteBuf buf) {
        int length = readArrayLength(buf, "item stack");
        if (length == -1) return null;
        ItemStack[] stacks = new ItemStack[length];
        for (int i = 0; i < length; i++) {
            int itemId = buf.readInt();
            int metadata = buf.readInt();
            int amount = buf.readInt();
            Item item = itemId > 0 ? Item.getItemById(itemId) : null;
            if (item == null || metadata < 0 || amount <= 0) throw malformed("Invalid recipe item stack");
            NBTTagCompound tag = ByteBufUtils.readTag(buf);
            ItemStack stack = new ItemStack(item, amount, metadata);
            stack.setTagCompound(tag);
            stacks[i] = stack;
        }
        return stacks;
    }

    private static void writeFluidStacks(ByteBuf buf, FluidStack[] stacks) {
        writeArrayLength(buf, stacks == null ? -1 : stacks.length, "fluid stack");
        if (stacks == null) return;
        for (FluidStack stack : stacks) {
            Fluid fluid = fluidType(stack);
            if (stack == null || fluid == null || stack.amount <= 0) throw malformed("Invalid recipe fluid stack");
            writeUtf8(buf, fluid.getName(), MAX_FLUID_NAME_BYTES, "fluid name");
            buf.writeInt(stack.amount);
            ByteBufUtils.writeTag(buf, stack.tag);
        }
    }

    private static FluidStack[] readFluidStacks(ByteBuf buf) {
        int length = readArrayLength(buf, "fluid stack");
        if (length == -1) return null;
        FluidStack[] stacks = new FluidStack[length];
        for (int i = 0; i < length; i++) {
            String fluidName = readUtf8(buf, MAX_FLUID_NAME_BYTES, "fluid name");
            int amount = buf.readInt();
            Fluid fluid = fluidName.isBlank() ? null : FluidRegistry.getFluid(fluidName);
            if (fluid == null || amount <= 0) throw malformed("Invalid recipe fluid stack");
            FluidStack stack = new FluidStack(fluid, amount);
            stack.tag = ByteBufUtils.readTag(buf);
            stacks[i] = stack;
        }
        return stacks;
    }

    private static void writeIntArray(ByteBuf buf, int[] values) {
        writeArrayLength(buf, values == null ? -1 : values.length, "chance");
        if (values == null) return;
        for (int value : values) buf.writeInt(value);
    }

    private static int[] readIntArray(ByteBuf buf) {
        int length = readArrayLength(buf, "chance");
        if (length == -1) return null;
        if (length > buf.readableBytes() / Integer.BYTES) throw malformed("Truncated recipe chance array");
        int[] values = new int[length];
        for (int i = 0; i < length; i++) values[i] = buf.readInt();
        return values;
    }

    private static void writeArrayLength(ByteBuf buf, int length, String name) {
        if (length < -1 || length > MAX_RECIPE_ARRAY_ENTRIES) {
            throw malformed("Invalid recipe " + name + " array length: " + length);
        }
        buf.writeInt(length);
    }

    private static int readArrayLength(ByteBuf buf, String name) {
        int length = buf.readInt();
        if (length < -1 || length > MAX_RECIPE_ARRAY_ENTRIES) {
            throw malformed("Invalid recipe " + name + " array length: " + length);
        }
        return length;
    }

    private static void writeUtf8(ByteBuf buf, String value, int maximumBytes, String name) {
        if (value == null) throw malformed("Missing " + name);
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) throw malformed(name + " exceeds " + maximumBytes + " UTF-8 bytes");
        buf.writeShort(encoded.length);
        buf.writeBytes(encoded);
    }

    private static String readUtf8(ByteBuf buf, int maximumBytes, String name) {
        int length = buf.readUnsignedShort();
        if (length > maximumBytes) throw malformed(name + " exceeds " + maximumBytes + " UTF-8 bytes");
        if (length > buf.readableBytes()) throw malformed("Truncated " + name);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException malformedUtf8) {
            throw malformed("Malformed UTF-8 " + name);
        }
    }

    private static boolean readBoolean(ByteBuf buf) {
        int value = buf.readUnsignedByte();
        if (value == 0) return false;
        if (value == 1) return true;
        throw malformed("Invalid recipe boolean value: " + value);
    }

    private static ModuleInstance.ID requireModuleId(ModuleInstance.ID id) {
        if (id == null || id.id() == null || ZERO_UUID.equals(id.id())) throw malformed("Invalid recipe module ID");
        return id;
    }

    private static SettingsGroup.ID requireGroupId(SettingsGroup.ID id) {
        if (id == null || id.value() <= 0) throw malformed("Invalid recipe settings group ID");
        return id;
    }

    private static Fluid fluidType(FluidStack stack) {
        if (stack == null) return null;
        try {
            return stack.getFluid();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static IllegalArgumentException malformed(String message) {
        return new IllegalArgumentException(message);
    }
}
