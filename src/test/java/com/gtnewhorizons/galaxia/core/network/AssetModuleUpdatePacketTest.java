package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.GT5RecipeRef;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class AssetModuleUpdatePacketTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();
    private static final ModuleInstance.ID MODULE_ID = new ModuleInstance.ID(UUID.randomUUID());

    // ---------- ConfigAction ordinal stability ----------

    @Test
    void configAction_ordinals_stable() {
        assertEquals(0, AssetModuleUpdatePacket.ConfigAction.ADD_MINER_BLACKLIST.ordinal());
        assertEquals(1, AssetModuleUpdatePacket.ConfigAction.REMOVE_MINER_BLACKLIST.ordinal());
        assertEquals(2, AssetModuleUpdatePacket.ConfigAction.SET_MINER_COPY_SETTINGS.ordinal());
        assertEquals(3, AssetModuleUpdatePacket.ConfigAction.SET_ALLOW_SHOOTING_MODE.ordinal());
        assertEquals(4, AssetModuleUpdatePacket.ConfigAction.SET_ALLOW_SHOOTING_THRESHOLD.ordinal());
        assertEquals(5, AssetModuleUpdatePacket.ConfigAction.SET_PLANETARY_HANDLING.ordinal());
        assertEquals(6, AssetModuleUpdatePacket.ConfigAction.SET_ROUTE_PRIORITY.ordinal());
        assertEquals(7, AssetModuleUpdatePacket.ConfigAction.SET_TIER.ordinal());
        assertEquals(8, AssetModuleUpdatePacket.ConfigAction.SET_PRIORITY.ordinal());
        assertEquals(9, AssetModuleUpdatePacket.ConfigAction.SET_ENABLED.ordinal());
        assertEquals(10, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT.ordinal());
        assertEquals(11, AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT.ordinal());
        assertEquals(12, AssetModuleUpdatePacket.ConfigAction.REMOVE_RECIPE_SLOT.ordinal());
    }

    @Test
    void action_ordinals_stable() {
        assertEquals(0, AssetModuleUpdatePacket.Action.ENABLE.ordinal());
        assertEquals(1, AssetModuleUpdatePacket.Action.DISABLE.ordinal());
        assertEquals(2, AssetModuleUpdatePacket.Action.DESTROY.ordinal());
    }

    // ---------- Recipe slot encode/decode round-trip ----------

    @Test
    void recipeSlotAdd_encodeDecode_roundTrip() {
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 1, 42, 12345L), true, 10, 100, (byte) 5, (byte) 8);
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
        assertEquals(25, decoded.getRawPayload().length);

        // Decode payload manually: slotIndex=3, recipeMapOrdinal=1, recipeIndex=42, contentHash=12345,
        // enabled=true, inputGuard=10, outputGuard=100, priority=5, orderSize=8
        ByteBuf payloadBuf = Unpooled.wrappedBuffer(decoded.getRawPayload());
        assertEquals((byte) 3, payloadBuf.readByte()); // slotIndex
        assertEquals((byte) 1, payloadBuf.readByte()); // recipeMapOrdinal
        assertEquals(42, payloadBuf.readInt()); // recipeIndex
        assertEquals(12345L, payloadBuf.readLong()); // contentHash
        assertTrue(payloadBuf.readBoolean()); // enabled
        assertEquals(10, payloadBuf.readInt()); // inputGuard
        assertEquals(100, payloadBuf.readInt()); // outputGuard
        assertEquals((byte) 5, payloadBuf.readByte()); // priority
        assertEquals((byte) 8, payloadBuf.readByte()); // orderSize
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
        RecipeSlot slot = new RecipeSlot(new GT5RecipeRef((byte) 2, 7, 999L), false, 5, 50, (byte) 1, (byte) 3);
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
    }
}
