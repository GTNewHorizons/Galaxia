package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import sun.misc.Unsafe;

final class AssetModuleUpdatePacketTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();
    private static final ModuleInstance.ID MODULE_ID = new ModuleInstance.ID(UUID.randomUUID());

    // ---------- Recipe slot encode/decode round-trip ----------

    @Test
    void recipeSlotAdd_encodeDecode_roundTrip() {
        RecipeSlot slot = new RecipeSlot(
            RecipeSnapshot.unresolved((byte) 1, 42, 12345L),
            true,
            10,
            100,
            (byte) 5,
            (byte) 8);
        AssetModuleUpdatePacket original = AssetModuleUpdatePacket.recipeSlotPayload(
            ASSET_ID,
            0,
            MODULE_ID,
            AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT,
            (byte) 3,
            slot);

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        // Decode
        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();
        decoded.fromBytes(buf);

        assertEquals(AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT, decoded.getConfigAction());
        assertNotNull(decoded.getRawPayload());
        assertTrue(decoded.getRawPayload().length > 25);

        // Decode payload manually: slotIndex=3, recipeMapOrdinal=1, recipeIndex=42, contentHash=12345,
        // enabled=true, inputGuard=10, outputGuard=100, priority=5, orderSize=8
        ByteBuf payloadBuf = Unpooled.wrappedBuffer(decoded.getRawPayload());
        assertEquals((byte) 3, payloadBuf.readByte()); // slotIndex
        assertEquals((byte) 1, payloadBuf.readByte()); // recipeMapOrdinal
        assertEquals(42, payloadBuf.readInt()); // recipeIndex
        assertEquals(12345L, payloadBuf.readLong()); // contentHash
        assertEquals(0, payloadBuf.readInt()); // duration
        assertEquals(0, payloadBuf.readInt()); // EU/t
        assertEquals(-1, payloadBuf.readInt()); // item inputs
        assertEquals(-1, payloadBuf.readInt()); // item outputs
        assertEquals(-1, payloadBuf.readInt()); // fluid inputs
        assertEquals(-1, payloadBuf.readInt()); // fluid outputs
        assertTrue(payloadBuf.readBoolean()); // enabled
        assertEquals(10, payloadBuf.readInt()); // inputGuard
        assertEquals(100, payloadBuf.readInt()); // outputGuard
        assertEquals((byte) 5, payloadBuf.readByte()); // priority
        assertEquals((byte) 8, payloadBuf.readByte()); // orderSize
    }

    @Test
    void recipeSlotAdd_fullSnapshotPayload_roundTripIncludesFluidsAndRecipeStats() {
        FluidStack fluidInput = fluidStack("galaxia_packet_input_fluid", 144);
        FluidStack fluidOutput = fluidStack("galaxia_packet_output_fluid", 72);
        RecipeSlot slot = new RecipeSlot(
            new RecipeSnapshot(
                (byte) 1,
                42,
                12345L,
                null,
                null,
                new FluidStack[] { fluidInput },
                new FluidStack[] { fluidOutput },
                200,
                512),
            true,
            10,
            100,
            (byte) 5,
            (byte) 8);

        AssetModuleUpdatePacket original = AssetModuleUpdatePacket.recipeSlotPayload(
            ASSET_ID,
            0,
            MODULE_ID,
            AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT,
            (byte) 3,
            slot);

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();
        decoded.fromBytes(buf);

        assertEquals(AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT, decoded.getConfigAction());
        assertNotNull(decoded.getRawPayload());
        assertTrue(decoded.getRawPayload().length > 25);

        ByteBuf payloadBuf = Unpooled.wrappedBuffer(decoded.getRawPayload());
        assertEquals((byte) 3, payloadBuf.readByte());
        assertEquals((byte) 1, payloadBuf.readByte());
        assertEquals(42, payloadBuf.readInt());
        assertEquals(12345L, payloadBuf.readLong());
        assertEquals(200, payloadBuf.readInt());
        assertEquals(512, payloadBuf.readInt());

        assertEquals(-1, payloadBuf.readInt()); // null item inputs
        assertEquals(-1, payloadBuf.readInt()); // null item outputs

        assertEquals(1, payloadBuf.readInt());
        assertTrue(payloadBuf.readBoolean());
        assertEquals("galaxia_packet_input_fluid", PacketUtil.readString(payloadBuf));
        assertEquals(144, payloadBuf.readInt());

        assertEquals(1, payloadBuf.readInt());
        assertTrue(payloadBuf.readBoolean());
        assertEquals("galaxia_packet_output_fluid", PacketUtil.readString(payloadBuf));
        assertEquals(72, payloadBuf.readInt());

        assertTrue(payloadBuf.readBoolean());
        assertEquals(10, payloadBuf.readInt());
        assertEquals(100, payloadBuf.readInt());
        assertEquals((byte) 5, payloadBuf.readByte());
        assertEquals((byte) 8, payloadBuf.readByte());
    }

    @Test
    void recipeSlotRemove_encodeDecode_roundTrip() {
        AssetModuleUpdatePacket original = AssetModuleUpdatePacket.recipeSlotPayload(
            ASSET_ID,
            0,
            MODULE_ID,
            AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT,
            (byte) 7,
            null);

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();
        decoded.fromBytes(buf);

        assertEquals(AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT, decoded.getConfigAction());
        assertNotNull(decoded.getRawPayload());
        assertEquals(1, decoded.getRawPayload().length);

        ByteBuf payloadBuf = Unpooled.wrappedBuffer(decoded.getRawPayload());
        assertEquals((byte) 7, payloadBuf.readByte()); // slotIndex
    }

    @Test
    void recipeSlotUpdate_encodeDecode_roundTrip() {
        RecipeSlot slot = new RecipeSlot(
            RecipeSnapshot.unresolved((byte) 2, 7, 999L),
            false,
            5,
            50,
            (byte) 1,
            (byte) 3);
        AssetModuleUpdatePacket original = AssetModuleUpdatePacket.recipeSlotPayload(
            ASSET_ID,
            0,
            MODULE_ID,
            AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT,
            (byte) 0,
            slot);

        ByteBuf buf = Unpooled.buffer();
        original.toBytes(buf);

        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();
        decoded.fromBytes(buf);

        assertEquals(AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT, decoded.getConfigAction());
        assertNotNull(decoded.getRawPayload());

        ByteBuf payloadBuf = Unpooled.wrappedBuffer(decoded.getRawPayload());
        assertEquals((byte) 0, payloadBuf.readByte()); // slotIndex
        assertEquals((byte) 2, payloadBuf.readByte()); // recipeMapOrdinal
        assertEquals(7, payloadBuf.readInt()); // recipeIndex
        assertEquals(999L, payloadBuf.readLong()); // contentHash
        assertEquals(0, payloadBuf.readInt()); // duration
        assertEquals(0, payloadBuf.readInt()); // EU/t
        assertEquals(-1, payloadBuf.readInt()); // item inputs
        assertEquals(-1, payloadBuf.readInt()); // item outputs
        assertEquals(-1, payloadBuf.readInt()); // fluid inputs
        assertEquals(-1, payloadBuf.readInt()); // fluid outputs
        assertFalse(payloadBuf.readBoolean()); // enabled
        assertEquals(5, payloadBuf.readInt()); // inputGuard
        assertEquals(50, payloadBuf.readInt()); // outputGuard
        assertEquals((byte) 1, payloadBuf.readByte()); // priority
        assertEquals((byte) 3, payloadBuf.readByte()); // orderSize
    }

    @Test
    void rawPayload_defaultsToNull() {
        AssetModuleUpdatePacket pkt = new AssetModuleUpdatePacket();
        assertNull(pkt.getRawPayload());
    }

    @Test
    void nonRecipeActions_haveNullRawPayload() {
        AssetModuleUpdatePacket pkt = AssetModuleUpdatePacket
            .config(ASSET_ID, 0, MODULE_ID, AssetModuleUpdatePacket.ConfigAction.SET_TIER, (byte) 2);
        ByteBuf buf = Unpooled.buffer();
        pkt.toBytes(buf);
        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();
        decoded.fromBytes(buf);
        assertEquals(AssetModuleUpdatePacket.ConfigAction.SET_TIER, decoded.getConfigAction());
        assertNull(decoded.getRawPayload());
    }

    private static FluidStack fluidStack(String fluidName, int amount) {
        try {
            FluidStack stack = (FluidStack) unsafe().allocateInstance(FluidStack.class);
            var fluidField = FluidStack.class.getDeclaredField("fluid");
            fluidField.setAccessible(true);
            fluidField.set(stack, new Fluid(fluidName));
            stack.amount = amount;
            return stack;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Unsafe unsafe() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
