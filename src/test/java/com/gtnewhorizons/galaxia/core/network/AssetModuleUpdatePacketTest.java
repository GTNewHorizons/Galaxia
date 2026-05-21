package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.TestFMLRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.MinerFocusOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPlan;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleTierOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
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
        TestFMLRegistry.init();
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
    void hammerUpgradePlanPayload_roundTripsTargetAndReserveFlag() {
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket
            .hammerUpgradePlan(ASSET_ID, 0, MODULE_ID, HammerVariant.BIG, ModuleTier.ZPM, true, true);

        AssetModuleUpdatePacket decoded = roundTrip(packet);

        assertEquals(AssetModuleUpdatePacket.ConfigAction.PLAN_HAMMER_UPGRADE, decoded.getConfigAction());
        assertArrayEquals(
            new byte[] { (byte) HammerVariant.BIG.ordinal(), (byte) ModuleTier.ZPM.ordinal(), 1, 1 },
            decoded.getRawPayload());
    }

    @Test
    void applyHammerVariantPlansRebuildWithoutMutatingModule() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleHammer hammer = (ModuleHammer) module.component();
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket.config(
            facility.assetId,
            0,
            module.id,
            AssetModuleUpdatePacket.ConfigAction.SET_HAMMER_VARIANT,
            HammerVariant.BIG);

        packet.apply(TEAM);

        assertEquals(ModuleTier.EV, module.tier());
        assertEquals(HammerVariant.BASE, hammer.variant());
        assertNotNull(module.operationOrNull());
        assertEquals(
            ModuleOperationPhase.WAITING_FOR_MATERIALS,
            module.operationOrNull()
                .phase());
        assertTrue(
            module.operationOrNull()
                .plan()
                .spec() instanceof HammerModuleOperation);
        assertEquals(
            ModuleTier.LuV,
            module.operationOrNull()
                .plan()
                .spec()
                .targetTier());
        assertEquals(
            "BIG",
            ((HammerModuleOperation) module.operationOrNull()
                .plan()
                .spec()).targetVariantKey());
    }

    @Test
    void applyHammerTierPlansRebuildWithoutMutatingTier() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket
            .config(facility.assetId, 0, module.id, AssetModuleUpdatePacket.ConfigAction.SET_TIER, ModuleTier.IV);

        packet.apply(TEAM);

        assertEquals(ModuleTier.EV, module.tier());
        assertNotNull(module.operationOrNull());
        assertEquals(
            ModuleTier.IV,
            module.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyNonHammerTierCreatesPhysicalOperationWithoutMutatingTier() {
        AutomatedFacility facility = addModuleFacilityToServer(FacilityModuleKind.STORAGE, ModuleTier.HV);
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket
            .config(facility.assetId, 0, module.id, AssetModuleUpdatePacket.ConfigAction.SET_TIER, ModuleTier.EV);

        packet.apply(TEAM);

        assertEquals(ModuleTier.HV, module.tier());
        assertNotNull(module.operationOrNull());
        assertTrue(
            module.operationOrNull()
                .plan()
                .spec() instanceof ModuleTierOperation);
        assertEquals(
            ModuleTier.EV,
            module.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyHammerUpgradePlanCreatesSingleTargetSpecWithReserveFlag() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket
                .hammerUpgradePlan(facility.assetId, 0, module.id, HammerVariant.BIG, ModuleTier.ZPM, true, true));

        packet.apply(TEAM);

        assertEquals(ModuleTier.EV, module.tier());
        assertEquals(HammerVariant.BASE, ((ModuleHammer) module.component()).variant());
        assertNotNull(module.operationOrNull());
        assertTrue(
            module.operationOrNull()
                .reserveItems());
        assertTrue(
            module.operationOrNull()
                .plan()
                .voidCompletionRefund());
        assertEquals(
            ModuleTier.ZPM,
            module.operationOrNull()
                .plan()
                .spec()
                .targetTier());
        assertEquals(
            "BIG",
            ((HammerModuleOperation) module.operationOrNull()
                .plan()
                .spec()).targetVariantKey());
    }

    @Test
    void applyHammerUpgradePlanInCreativeAppliesTargetImmediately() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket
                .hammerUpgradePlan(facility.assetId, 0, module.id, HammerVariant.BIG, ModuleTier.ZPM, false, false));

        packet.apply(TEAM, true);

        assertEquals(ModuleTier.ZPM, module.tier());
        assertEquals(HammerVariant.BIG, ((ModuleHammer) module.component()).variant());
        assertNull(module.operationOrNull());
    }

    @Test
    void applyHammerUpgradePlanInCreativeReplacesEmptyWaitingOperation() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        module
            .setOperation(ModuleOperationState.waiting(hammerOperationPlan(module, ModuleTier.IV, HammerVariant.BASE)));
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket
                .hammerUpgradePlan(facility.assetId, 0, module.id, HammerVariant.BIG, ModuleTier.ZPM, false, false));

        packet.apply(TEAM, true);

        assertEquals(ModuleTier.ZPM, module.tier());
        assertEquals(HammerVariant.BIG, ((ModuleHammer) module.component()).variant());
        assertNull(module.operationOrNull());
    }

    @Test
    void applyHammerUpgradePlanInCreativeRejectsOperationWithStoredItems() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        module.setOperation(
            ModuleOperationState.restore(
                hammerOperationPlan(module, ModuleTier.IV, HammerVariant.BASE),
                ModuleOperationPhase.WAITING_FOR_MATERIALS,
                0,
                Map.of("minecraft:iron_ingot:0", 1L),
                Map.of()));
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket
                .hammerUpgradePlan(facility.assetId, 0, module.id, HammerVariant.BIG, ModuleTier.ZPM, false, false));

        assertThrows(IllegalStateException.class, () -> packet.apply(TEAM, true));
        assertEquals(ModuleTier.EV, module.tier());
        assertEquals(HammerVariant.BASE, ((ModuleHammer) module.component()).variant());
    }

    @Test
    void applyHammerPhysicalChangeIgnoresActiveOperationRequest() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        module
            .setOperation(ModuleOperationState.waiting(hammerOperationPlan(module, ModuleTier.IV, HammerVariant.BASE)));
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket
            .config(facility.assetId, 0, module.id, AssetModuleUpdatePacket.ConfigAction.SET_TIER, ModuleTier.LuV);

        assertDoesNotThrow(() -> packet.apply(TEAM));
        assertEquals(
            ModuleTier.IV,
            module.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyCancelModuleOperationCancelsActiveOperation() {
        AutomatedFacility facility = addHammerFacilityToServer(ModuleTier.EV);
        ModuleInstance module = facility.modules()
            .get(0);
        module
            .setOperation(ModuleOperationState.waiting(hammerOperationPlan(module, ModuleTier.IV, HammerVariant.BASE)));
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.cancelModuleOperation(facility.assetId, 0, module.id));

        packet.apply(TEAM);

        assertEquals(
            ModuleOperationPhase.CANCELLED,
            module.operationOrNull()
                .phase());
    }

    @Test
    void applyMinerBlacklistUpdatesOreState() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        AssetModuleUpdatePacket packet = AssetModuleUpdatePacket
            .minerOreBlacklisted(facility.assetId, 0, module.id, "ore:iron", true);

        packet.apply(TEAM);

        assertTrue(facility.isMinerOreBlacklisted(module, "ore:iron"));
    }

    @Test
    void applyMinerFocusTierPlanCreatesPhysicalOperationWithoutChangingOreImmediately() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.I, "ore:iron", 1200);

        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.minerFocusTierPlan(facility.assetId, 0, module.id, MinerFocusTier.II));

        packet.apply(TEAM);

        assertEquals(MinerFocusTier.I, miner.focusTier());
        assertEquals("ore:iron", miner.focusOreKeyOrNull());
        assertEquals(1200, miner.focusAlignmentProgress());
        assertNotNull(module.operationOrNull());
        assertEquals(
            ModuleOperationPhase.WAITING_FOR_MATERIALS,
            module.operationOrNull()
                .phase());
        assertEquals(
            "II",
            ((MinerFocusOperation) module.operationOrNull()
                .plan()
                .spec()).targetFocusTierKey());
        assertEquals(
            "ore:iron",
            ((MinerFocusOperation) module.operationOrNull()
                .plan()
                .spec()).targetFocusOreKey());
    }

    @Test
    void applyMinerFocusTierPlanInstallsFocusFromNone() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();

        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.minerFocusTierPlan(facility.assetId, 0, module.id, MinerFocusTier.I));

        packet.apply(TEAM);

        assertEquals(MinerFocusTier.NONE, miner.focusTier());
        assertNull(miner.focusOreKeyOrNull());
        assertNotNull(module.operationOrNull());
        assertEquals(
            "I",
            ((MinerFocusOperation) module.operationOrNull()
                .plan()
                .spec()).targetFocusTierKey());
        assertNull(
            ((MinerFocusOperation) module.operationOrNull()
                .plan()
                .spec()).targetFocusOreKey());
    }

    @Test
    void applyMinerFocusOreUpdatesRuntimeConfigAndResetsAlignment() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        ModuleMiner miner = (ModuleMiner) module.component();
        miner.setFocus(MinerFocusTier.II, "ore:iron", 1200);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.minerFocusOre(facility.assetId, 0, module.id, "ore:gold"));

        packet.apply(TEAM);

        assertNull(module.operationOrNull());
        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertEquals("ore:gold", miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void copyModuleSettingsPayload_roundTripsTargetTiles() {
        AssetModuleUpdatePacket decoded = roundTrip(
            AssetModuleUpdatePacket.copyModuleSettings(
                ASSET_ID,
                0,
                MODULE_ID,
                List.of(StationTileCoord.of(2, 0), StationTileCoord.of(3, -1))));

        assertEquals(AssetModuleUpdatePacket.ConfigAction.COPY_MODULE_SETTINGS, decoded.getConfigAction());
        assertEquals(
            List.of(StationTileCoord.of(2, 0), StationTileCoord.of(3, -1)),
            AssetModuleUpdatePacket.decodeTileCoordPayload(decoded.getRawPayload()));
    }

    @Test
    void applyCopyModuleSettingsCopiesRuntimeConfigWithoutPhysicalFocusTier() {
        AutomatedFacility facility = addTwoMinerFacilityToServer();
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        ModuleMiner targetMiner = (ModuleMiner) target.component();
        sourceMiner.setFocus(MinerFocusTier.II, "ore:iron", 1200);
        targetMiner.setFocus(MinerFocusTier.I, "ore:gold", 900);
        facility.setMinerOreBlacklisted(source, "ore:copper", true);
        facility.createSettingsGroupForModule(source, "Shared miners");

        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.copyModuleSettings(facility.assetId, 0, source.id, List.of(target.anchor())));

        packet.apply(TEAM);

        assertEquals(source.groupId(), target.groupId());
        assertTrue(facility.isMinerOreBlacklisted(target, "ore:copper"));
        assertEquals(MinerFocusTier.I, targetMiner.focusTier());
        assertEquals("ore:iron", targetMiner.focusOreKeyOrNull());
        assertEquals(0, targetMiner.focusAlignmentProgress());
    }

    @Test
    void applyCopyModuleSettingsRejectsFocusedSourceForTargetWithoutFocusTier() {
        AutomatedFacility facility = addTwoMinerFacilityToServer();
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        ModuleMiner targetMiner = (ModuleMiner) target.component();
        sourceMiner.setFocus(MinerFocusTier.I, "ore:iron", 0);
        targetMiner.setFocus(MinerFocusTier.NONE, null, 0);
        short originalTargetGroupId = target.groupId();
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.copyModuleSettings(facility.assetId, 0, source.id, List.of(target.anchor())));

        assertThrows(IllegalStateException.class, () -> packet.apply(TEAM));
        assertEquals(originalTargetGroupId, target.groupId());
        assertEquals(MinerFocusTier.NONE, targetMiner.focusTier());
        assertNull(targetMiner.focusOreKeyOrNull());
    }

    @Test
    void applyCopyModuleSettingsCopiesRecipeConfig() {
        org.junit.jupiter.api.Assumptions.assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.MACERATOR, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        RecipeConfig config = RecipeConfig.empty();
        SavedRecipe slot = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 7, 42L), true, 0L, (byte) 3, (byte) 2);
        config.savedRecipes()
            .add(slot);
        facility.setRecipeConfig(source, config);

        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.copyModuleSettings(facility.assetId, 0, source.id, List.of(target.anchor())));

        packet.apply(TEAM);

        RecipeConfig copied = ((IRecipeModule) target.component()).getRecipeConfig();
        assertNotNull(copied);
        assertEquals(config.mode(), copied.mode());
        assertEquals(config.notDoablePolicy(), copied.notDoablePolicy());
        assertEquals(
            config.savedRecipes()
                .size(),
            copied.savedRecipes()
                .size());
        assertEquals(
            slot.recipe()
                .contentHash(),
            copied.savedRecipes()
                .get(0)
                .recipe()
                .contentHash());
        assertEquals(
            slot.priority(),
            copied.savedRecipes()
                .get(0)
                .priority());
        assertEquals(
            slot.orderSize(),
            copied.savedRecipes()
                .get(0)
                .orderSize());
    }

    @Test
    void applyModuleUpgradeTargetsPlansHammerUpgradeForSelectedTargetsOnly() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.LuV,
                HammerVariant.BIG,
                true,
                true,
                List.of(target.anchor())));

        packet.apply(TEAM);

        assertNull(source.operationOrNull());
        assertEquals(ModuleTier.EV, target.tier());
        assertNotNull(target.operationOrNull());
        assertTrue(
            target.operationOrNull()
                .reserveItems());
        assertTrue(
            target.operationOrNull()
                .plan()
                .voidCompletionRefund());
        assertTrue(
            target.operationOrNull()
                .plan()
                .spec() instanceof HammerModuleOperation);
        assertEquals(
            ModuleTier.LuV,
            target.operationOrNull()
                .plan()
                .spec()
                .targetTier());
        assertEquals(
            "BIG",
            ((HammerModuleOperation) target.operationOrNull()
                .plan()
                .spec()).targetVariantKey());
    }

    @Test
    void applyModuleUpgradeTargetsCanIncludeSourceModule() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.LuV,
                HammerVariant.BIG,
                false,
                false,
                List.of(source.anchor(), target.anchor())));

        packet.apply(TEAM);

        assertNotNull(source.operationOrNull());
        assertNotNull(target.operationOrNull());
        assertEquals(
            ModuleTier.LuV,
            source.operationOrNull()
                .plan()
                .spec()
                .targetTier());
        assertEquals(
            ModuleTier.LuV,
            target.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyModuleUpgradeTargetsUsesCreativeModeForAllTargets() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.LuV,
                HammerVariant.BIG,
                false,
                false,
                List.of(source.anchor(), target.anchor())));

        packet.apply(TEAM, true);

        assertEquals(ModuleTier.LuV, source.tier());
        assertEquals(HammerVariant.BIG, ((ModuleHammer) source.component()).variant());
        assertNull(source.operationOrNull());
        assertEquals(ModuleTier.LuV, target.tier());
        assertEquals(HammerVariant.BIG, ((ModuleHammer) target.component()).variant());
        assertNull(target.operationOrNull());
    }

    @Test
    void applyModuleUpgradeTargetsMarksTargetDirtyForImmediateSync() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.LuV,
                HammerVariant.BIG,
                false,
                false,
                List.of(target.anchor())));
        int revisionBefore = facility.getSyncRevision();

        AssetSyncPacket sync = packet.apply(TEAM);

        assertNotNull(target.operationOrNull());
        assertNotNull(sync);
        assertTrue(facility.getSyncRevision() > revisionBefore);
        assertEquals(facility.getSyncRevision(), sync.syncRevision());
        ModuleInstance syncedTarget = roundTrip(sync).fullSyncDeltas()
            .stream()
            .filter(delta -> delta.syncType() == AssetSyncPacket.MODULE_ADDED)
            .map(AssetSyncPacket::moduleData)
            .filter(module -> module.id.equals(target.id))
            .findFirst()
            .orElseThrow();
        assertNotNull(syncedTarget.operationOrNull());
        assertEquals(
            ModuleTier.LuV,
            syncedTarget.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyModuleUpgradeTargetsSkipsTargetWithActiveBuild() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        target
            .setOperation(ModuleOperationState.waiting(hammerOperationPlan(target, ModuleTier.IV, HammerVariant.BASE)));
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.LuV,
                HammerVariant.BIG,
                false,
                false,
                List.of(target.anchor())));

        packet.apply(TEAM);
        assertEquals(
            ModuleTier.IV,
            target.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyModuleUpgradeTargetsDeduplicatesSelectedTargets() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.HAMMER, ModuleTier.EV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.LuV,
                HammerVariant.BIG,
                false,
                false,
                List.of(target.anchor(), target.anchor())));

        packet.apply(TEAM);

        assertEquals(
            ModuleTier.LuV,
            target.operationOrNull()
                .plan()
                .spec()
                .targetTier());
        assertEquals(
            "BIG",
            ((HammerModuleOperation) target.operationOrNull()
                .plan()
                .spec()).targetVariantKey());
    }

    @Test
    void applyModuleUpgradeTargetsPlansGenericTierUpgradeForNonHammerTargets() {
        AutomatedFacility facility = addTwoModuleFacilityToServer(FacilityModuleKind.STORAGE, ModuleTier.HV);
        ModuleInstance source = facility.modules()
            .get(0);
        ModuleInstance target = facility.modules()
            .get(1);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleUpgradeTargets(
                facility.assetId,
                0,
                source.id,
                ModuleTier.EV,
                null,
                false,
                false,
                List.of(target.anchor())));

        packet.apply(TEAM);

        assertNull(source.operationOrNull());
        assertEquals(ModuleTier.HV, target.tier());
        assertNotNull(target.operationOrNull());
        assertTrue(
            target.operationOrNull()
                .plan()
                .spec() instanceof ModuleTierOperation);
        assertEquals(
            ModuleTier.EV,
            target.operationOrNull()
                .plan()
                .spec()
                .targetTier());
    }

    @Test
    void applyCreateModuleSettingsGroupCopiesCurrentMinerBlacklist() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        facility.setMinerOreBlacklisted(module, "ore:iron", true);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.createModuleSettingsGroup(facility.assetId, 0, module.id, "  Priority miners  "));

        packet.apply(TEAM);

        assertNotEquals(0, module.groupId());
        assertEquals(
            "Priority miners",
            facility.settingsGroups()
                .require(module.groupId())
                .displayName());
        assertEquals(
            1,
            facility.settingsGroups()
                .groups()
                .size());
        assertTrue(facility.isMinerOreBlacklisted(module, "ore:iron"));
    }

    @Test
    void applyRenameModuleSettingsGroupUpdatesJoinableGroup() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        short groupId = facility.createSettingsGroupForModule(module, "Old miners")
            .id();
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.renameModuleSettingsGroup(facility.assetId, 0, module.id, groupId, "New miners"));

        AssetSyncPacket sync = packet.apply(TEAM);

        assertEquals(
            "New miners",
            facility.settingsGroups()
                .require(groupId)
                .displayName());
        assertNotNull(sync);
        assertFalse(
            sync.fullSyncDeltas()
                .isEmpty());
    }

    @Test
    void applyModuleSettingsGroupZeroLeavesGroupWithCopiedSettings() {
        AutomatedFacility facility = addMinerFacilityToServer();
        ModuleInstance module = facility.modules()
            .get(0);
        facility.setMinerOreBlacklisted(module, "ore:iron", true);
        facility.createSettingsGroupForModule(module, null);
        AssetModuleUpdatePacket packet = roundTrip(
            AssetModuleUpdatePacket.moduleSettingsGroup(facility.assetId, 0, module.id, (short) 0));

        packet.apply(TEAM);

        assertNotEquals(0, module.groupId());
        assertEquals(
            1,
            facility.settingsGroups()
                .groups()
                .size());
        assertTrue(facility.isMinerOreBlacklisted(module, "ore:iron"));
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
            CelestialObjectId.PANSPIRA,
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
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = kind.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, tier);
        facility.addModule(module);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
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

    private static AutomatedFacility addMinerFacilityToServer() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance module = FacilityModuleKind.MINER
            .create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, ModuleTier.EV);
        facility.addModule(module);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
    }

    private static AutomatedFacility addTwoMinerFacilityToServer() {
        return addTwoModuleFacilityToServer(FacilityModuleKind.MINER, ModuleTier.EV);
    }

    private static AutomatedFacility addTwoModuleFacilityToServer(FacilityModuleKind kind, ModuleTier tier) {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance source = kind.create(StationTileCoord.of(1, 0), ModuleShape.SINGLE, tier);
        ModuleInstance target = kind.create(StationTileCoord.of(2, 0), ModuleShape.SINGLE, tier);
        facility.addModule(source);
        facility.addModule(target);
        facility.stationLayout()
            .place(source);
        facility.stationLayout()
            .place(target);
        CelestialAssetStore.SERVER.registerAssetInternal(TEAM, facility);
        return facility;
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
