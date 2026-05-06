package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSlotList;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import sun.misc.Unsafe;

final class AssetModuleUpdatePacketTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();
    private static final ModuleInstance.ID MODULE_ID = new ModuleInstance.ID(UUID.randomUUID());
    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @BeforeEach
    void cleanStores() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

    @AfterEach
    void cleanStoresAfter() {
        CelestialAssetStore.SERVER.clearInternal();
        CelestialAssetStore.CLIENT.clearInternal();
    }

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
        assertEquals(-1, payloadBuf.readInt()); // item output chances
        assertEquals(-1, payloadBuf.readInt()); // fluid inputs
        assertEquals(-1, payloadBuf.readInt()); // fluid outputs
        assertEquals(-1, payloadBuf.readInt()); // fluid output chances
        assertTrue(payloadBuf.readBoolean()); // enabled
        assertEquals(10, payloadBuf.readInt()); // inputGuard
        assertEquals(100, payloadBuf.readInt()); // outputGuard
        assertEquals((byte) 5, payloadBuf.readByte()); // priority
        assertEquals((byte) 8, payloadBuf.readByte()); // orderSize
    }

    @Test
    void recipeSlotAdd_fullSnapshotPayload_roundTripIncludesFluidsRecipeStatsAndOutputChances() {
        Item itemOutput = new Item();
        FluidStack fluidInput = fluidStack("galaxia_packet_input_fluid", 144);
        FluidStack fluidOutput = fluidStack("galaxia_packet_output_fluid", 72);
        RecipeSlot slot = new RecipeSlot(
            new RecipeSnapshot(
                (byte) 1,
                42,
                12345L,
                null,
                new ItemStack[] { new ItemStack(itemOutput, 2, 0) },
                new FluidStack[] { fluidInput },
                new FluidStack[] { fluidOutput },
                new int[] { 5000 },
                new int[] { 7500 },
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
        assertEquals(1, payloadBuf.readInt());
        assertTrue(payloadBuf.readBoolean());
        assertEquals(Item.getIdFromItem(itemOutput), payloadBuf.readInt());
        assertEquals(0, payloadBuf.readInt());
        assertEquals(2, payloadBuf.readInt());

        assertEquals(1, payloadBuf.readInt());
        assertEquals(5000, payloadBuf.readInt());

        assertEquals(1, payloadBuf.readInt());
        assertTrue(payloadBuf.readBoolean());
        assertEquals("galaxia_packet_input_fluid", PacketUtil.readString(payloadBuf));
        assertEquals(144, payloadBuf.readInt());

        assertEquals(1, payloadBuf.readInt());
        assertTrue(payloadBuf.readBoolean());
        assertEquals("galaxia_packet_output_fluid", PacketUtil.readString(payloadBuf));
        assertEquals(72, payloadBuf.readInt());

        assertEquals(1, payloadBuf.readInt());
        assertEquals(7500, payloadBuf.readInt());

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
        assertEquals(-1, payloadBuf.readInt()); // item output chances
        assertEquals(-1, payloadBuf.readInt()); // fluid inputs
        assertEquals(-1, payloadBuf.readInt()); // fluid outputs
        assertEquals(-1, payloadBuf.readInt()); // fluid output chances
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

    @Test
    void applyHammerVariantRejectsTierMismatch() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket.config(
            facility.assetId,
            0,
            module.id,
            AssetModuleUpdatePacket.ConfigAction.SET_HAMMER_VARIANT,
            HammerVariant.BIG);

        assertThrows(IllegalStateException.class, () -> packet.apply(TEAM));
    }

    @Test
    void applyMinerVoidPercentClampsPlayerInput() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket
            .minerVoidPercent(facility.assetId, 0, module.id, "ore:iron", 150);

        packet.apply(TEAM);

        assertEquals(100, facility.minerVoidChancePercent("ore:iron"));
    }

    @Test
    void fromBytesCrashesOnRecipePayloadLargerThanCap() {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, ASSET_ID);
        buf.writeInt(0);
        PacketUtil.writeId(buf, MODULE_ID);
        buf.writeByte(1);
        PacketUtil.writeEnum(buf, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT);
        buf.writeInt(4097);

        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();

        assertThrows(IllegalArgumentException.class, () -> decoded.fromBytes(buf));
    }

    @Test
    void applyCrashesOnMalformedRecipePayloadWithOversizedNestedItemArray() {
        AutomatedFacility facility = addRecipeFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = decodeRecipePayload(
            facility.assetId,
            module.id,
            malformedRecipePayloadWithItemArrayLength(4097));

        assertThrows(IllegalArgumentException.class, () -> packet.apply(TEAM));
        assertNull(((IRecipeModule) module.component()).getRecipeConfig());
    }

    @Test
    void applyRecipeSlotMutation_addOnEmptyList_appendsAtZero() {
        RecipeSlotList slots = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(RecipeSnapshot.unresolved((byte) 1, 0, 1L), true, 0, 0, (byte) 1, (byte) 1);

        boolean changed = AssetModuleUpdatePacket
            .applyRecipeSlotMutation(slots, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT, 0, slot);

        assertTrue(changed);
        assertEquals(1, slots.size());
        assertSame(slot, slots.get(0));
    }

    @Test
    void applyRecipeSlotMutation_addWithGapIndexIsRejected() {
        RecipeSlotList slots = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(RecipeSnapshot.unresolved((byte) 1, 0, 1L), true, 0, 0, (byte) 1, (byte) 1);

        boolean changed = AssetModuleUpdatePacket
            .applyRecipeSlotMutation(slots, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT, 1, slot);

        assertFalse(changed);
        assertTrue(slots.isEmpty());
    }

    @Test
    void applyRecipeSlotMutation_updateMissingSlotIsRejected() {
        RecipeSlotList slots = new RecipeSlotList();
        RecipeSlot slot = new RecipeSlot(RecipeSnapshot.unresolved((byte) 1, 0, 1L), true, 0, 0, (byte) 1, (byte) 1);

        boolean changed = AssetModuleUpdatePacket
            .applyRecipeSlotMutation(slots, AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT, 0, slot);

        assertFalse(changed);
        assertTrue(slots.isEmpty());
    }

    private static AutomatedFacility addRecipeFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.NONE);
        facility.addModule(module);
        CelestialAssetStore.SERVER.addInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility addHammerFacilityToServer(ModuleTier tier) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.HAMMER.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, tier);
        facility.addModule(module);
        CelestialAssetStore.SERVER.addInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility addMinerFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.MINER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        facility.addModule(module);
        CelestialAssetStore.SERVER.addInternal(TEAM, facility);
        return facility;
    }

    private static AssetModuleUpdatePacket decodeRecipePayload(CelestialAsset.ID assetId, ModuleInstance.ID moduleId,
        byte[] rawPayload) {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, assetId);
        buf.writeInt(0);
        PacketUtil.writeId(buf, moduleId);
        buf.writeByte(1);
        PacketUtil.writeEnum(buf, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT);
        buf.writeInt(rawPayload.length);
        buf.writeBytes(rawPayload);
        AssetModuleUpdatePacket packet = new AssetModuleUpdatePacket();
        packet.fromBytes(buf);
        return packet;
    }

    private static byte[] malformedRecipePayloadWithItemArrayLength(int itemArrayLength) {
        ByteBuf payload = Unpooled.buffer();
        payload.writeByte(0);
        payload.writeByte(1);
        payload.writeInt(0);
        payload.writeLong(0L);
        payload.writeInt(20);
        payload.writeInt(30);
        payload.writeInt(itemArrayLength);
        byte[] raw = new byte[payload.writerIndex()];
        payload.readBytes(raw);
        return raw;
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
