package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;
import com.gtnewhorizons.galaxia.testing.TestFluidStacks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class AssetModuleUpdatePacketTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();
    private static final ModuleInstance.ID MODULE_ID = new ModuleInstance.ID(UUID.randomUUID());
    private static final UUID TEAM = UUID.randomUUID();

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
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
        SavedRecipe slot = new SavedRecipe(
            RecipeSnapshot.unresolved((byte) 1, 42, 12345L),
            true,
            0L,
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

        // Decode payload manually: slotIndex=3, recipeMapOrdinal=1, recipeIndex=42, contentHash=12345.
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
        assertEquals(0L, payloadBuf.readLong()); // requestAmount
        assertEquals((byte) 5, payloadBuf.readByte()); // priority
        assertEquals((byte) 8, payloadBuf.readByte()); // orderSize
    }

    @Test
    void recipeSlotAdd_fullSnapshotPayload_roundTripIncludesFluidsRecipeStatsAndOutputChances() {
        Item itemOutput = Items.diamond;
        FluidStack fluidInput = fluidStack("galaxia_packet_input_fluid", 144);
        FluidStack fluidOutput = fluidStack("galaxia_packet_output_fluid", 72);
        SavedRecipe slot = new SavedRecipe(
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
            0L,
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
        assertEquals(0L, payloadBuf.readLong());
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
        SavedRecipe slot = new SavedRecipe(RecipeSnapshot.unresolved((byte) 2, 7, 999L), false, 0L, (byte) 1, (byte) 3);
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
        assertEquals(0L, payloadBuf.readLong()); // requestAmount
        assertEquals((byte) 1, payloadBuf.readByte()); // priority
        assertEquals((byte) 3, payloadBuf.readByte()); // orderSize
    }

    @Test
    void rawPayload_defaultsToNull() {
        AssetModuleUpdatePacket pkt = new AssetModuleUpdatePacket();
        assertNull(pkt.getRawPayload());
    }

    @Test
    void legacySettingsActionsAreNotPartOfTheModuleUpdateWire() {
        List<String> actionNames = java.util.Arrays.stream(AssetModuleUpdatePacket.ConfigAction.values())
            .map(Enum::name)
            .toList();

        assertFalse(actionNames.contains("SET_SETTINGS_GROUP"));
        assertFalse(actionNames.contains("CREATE_SETTINGS_GROUP"));
        assertFalse(actionNames.contains("RENAME_SETTINGS_GROUP"));
        assertFalse(actionNames.contains("COPY_MODULE_SETTINGS"));
        assertFalse(actionNames.contains("SET_MINER_ORE_BLACKLISTED"));
    }

    @Test
    void fromBytesCrashesOnRecipePayloadLargerThanCap() {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, ASSET_ID);
        buf.writeInt(0);
        PacketUtil.writeId(buf, MODULE_ID);
        PacketUtil.writeEnum(buf, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT);
        buf.writeInt(4097);

        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();

        assertThrows(IllegalArgumentException.class, () -> decoded.fromBytes(buf));
    }

    @Test
    void applyRecipeSchedulerModeUpdatesRecipeModule() {
        AutomatedFacility facility = addRecipeFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket.config(
            facility.assetId,
            0,
            module.id,
            AssetModuleUpdatePacket.ConfigAction.SET_RECIPE_SCHEDULER_MODE,
            RecipeSchedulerMode.RANDOM);

        assertTrue(packet.apply(TEAM));
        assertEquals(
            RecipeSchedulerMode.RANDOM,
            facility.recipeConfig(module)
                .mode());
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
        SavedRecipeList slots = new SavedRecipeList();
        SavedRecipe slot = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 1L), true, 0L, (byte) 1, (byte) 1);

        boolean changed = AssetModuleUpdatePacket
            .applyRecipeSlotMutation(slots, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT, 0, slot);

        assertTrue(changed);
        assertEquals(1, slots.size());
        assertSame(slot, slots.get(0));
    }

    @Test
    void applyRecipeSlotMutation_addWithGapIndexIsRejected() {
        SavedRecipeList slots = new SavedRecipeList();
        SavedRecipe slot = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 1L), true, 0L, (byte) 1, (byte) 1);

        boolean changed = AssetModuleUpdatePacket
            .applyRecipeSlotMutation(slots, AssetModuleUpdatePacket.ConfigAction.ADD_RECIPE_SLOT, 1, slot);

        assertFalse(changed);
        assertTrue(slots.isEmpty());
    }

    @Test
    void applyRecipeSlotMutation_updateMissingSlotIsRejected() {
        SavedRecipeList slots = new SavedRecipeList();
        SavedRecipe slot = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 1L), true, 0L, (byte) 1, (byte) 1);

        boolean changed = AssetModuleUpdatePacket
            .applyRecipeSlotMutation(slots, AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT, 0, slot);

        assertFalse(changed);
        assertTrue(slots.isEmpty());
    }

    @Test
    void recipeForSlotMutation_updatePreservesExistingServerRecipe() {
        RecipeSnapshot existingRecipe = RecipeSnapshot.unresolved((byte) 1, 0, 1L);
        RecipeSnapshot clientRecipe = RecipeSnapshot.unresolved((byte) 2, 7, 999L);
        RecipeConfig config = RecipeConfig.empty();
        config.savedRecipes()
            .add(new SavedRecipe(existingRecipe, true, 0L, (byte) 1, (byte) 1));

        RecipeSnapshot resolved = AssetModuleUpdatePacket.recipeForSlotMutation(
            AssetModuleUpdatePacket.ConfigAction.UPDATE_RECIPE_SLOT,
            config,
            0,
            null,
            clientRecipe);

        assertSame(existingRecipe, resolved);
    }

    private static AutomatedFacility addRecipeFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.MACERATOR
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.NONE);
        facility.addModule(module);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility addHammerFacilityToServer(ModuleTier tier) {
        return addModuleFacilityToServer(FacilityModuleKind.HAMMER, tier);
    }

    private static AutomatedFacility addModuleFacilityToServer(FacilityModuleKind kind, ModuleTier tier) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = kind.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, tier);
        facility.addModule(module);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility addDebugDataGeneratorFacilityToServer() {
        return addModuleFacilityToServer(FacilityModuleKind.DEBUG_DATA_GENERATOR, ModuleTier.HV);
    }

    private static ModuleOperationPlan hammerOperationPlan(ModuleInstance module, ModuleTier targetTier,
        HammerVariant targetVariant) {
        int buildTicks = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier())
            .buildTicks();
        Map<ItemStackWrapper, Long> cost = FacilityModuleRegistry.operationCost(
            FacilityModuleRegistry.get(module.kind())
                .getTierData(targetTier)
                .constructionCost());
        return new ModuleOperationPlan(
            new HammerModuleOperation(targetTier, targetVariant.name()),
            buildTicks,
            cost,
            false);
    }

    private static AssetModuleUpdatePacket roundTrip(AssetModuleUpdatePacket packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        AssetModuleUpdatePacket decoded = new AssetModuleUpdatePacket();
        decoded.fromBytes(buf);
        return decoded;
    }

    private static AssetSyncPacket roundTrip(AssetSyncPacket packet) {
        ByteBuf buf = Unpooled.buffer();
        packet.toBytes(buf);
        AssetSyncPacket decoded = new AssetSyncPacket();
        decoded.fromBytes(buf);
        return decoded;
    }

    private static AssetModuleUpdatePacket decodeRecipePayload(CelestialAsset.ID assetId, ModuleInstance.ID moduleId,
        byte[] rawPayload) {
        ByteBuf buf = Unpooled.buffer();
        PacketUtil.writeId(buf, assetId);
        buf.writeInt(0);
        PacketUtil.writeId(buf, moduleId);
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
        return TestFluidStacks.stack(fluidName, amount);
    }
}
