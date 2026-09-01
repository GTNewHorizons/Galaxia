package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import com.gtnewhorizons.galaxia.registry.outpost.BoundKind;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.FluidKey;
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
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

final class FacilityCommandPacketTest {

    private static final CelestialAsset.ID FACILITY_ID = new CelestialAsset.ID(
        UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final ModuleInstance.ID MODULE_ID = new ModuleInstance.ID(
        UUID.fromString("20000000-0000-0000-0000-000000000002"));
    private static final ModuleInstance.ID SECOND_MODULE_ID = new ModuleInstance.ID(
        UUID.fromString("30000000-0000-0000-0000-000000000003"));
    private static final Fluid TEST_FLUID = new Fluid("facility_command_packet_fluid");

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
        FluidRegistry.registerFluid(TEST_FLUID);
    }

    @Test
    void everyFacilityCommandVariantRoundTripsThroughItsTypedWirePayload() {
        ItemStack taggedStack = new ItemStack(Items.stick);
        taggedStack.stackTagCompound = new NBTTagCompound();
        taggedStack.stackTagCompound.setString("identity", "tagged-item");
        ItemStackWrapper item = ItemStackWrapper.of(taggedStack);
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("identity", "tagged-fluid");
        FluidKey fluid = new FluidKey(TEST_FLUID, fluidTag);
        List<ModulePlacement> placements = List.of(new ModulePlacement(new StationTileCoord((byte) 4, (byte) -3), 3));
        RecipeBook recipeBook = recipeBook();
        List<FacilityCommand> commands = List.of(
            new FacilityCommand.AdjustInventory(FACILITY_ID, item, FacilityCommand.InventoryAdjustment.INSERT, 8L),
            new FacilityCommand.ClearInventoryResource(FACILITY_ID, fluid),
            new FacilityCommand.SetInventoryBound(FACILITY_ID, BoundKind.ITEM_LOWER, item, 9L),
            new FacilityCommand.ClearInventoryBound(FACILITY_ID, BoundKind.FLUID_UPPER, fluid),
            new FacilityCommand.ReplaceFilters(
                FACILITY_ID,
                FacilityCommand.FilterKind.ITEM,
                List.of("ore:iron", "ore:copper")),
            new FacilityCommand.PutLogisticsConfig(
                FACILITY_ID,
                fluid,
                new LogisticsResourceConfig(2, 4, true, false),
                LogisticsConfigAccessMode.IMPORT_ONLY),
            new FacilityCommand.RemoveLogisticsConfig(FACILITY_ID, item),
            new FacilityCommand.BuildModules(
                FACILITY_ID,
                FacilityModuleKind.HAMMER,
                ModuleShape.SINGLE,
                new IModuleComponent.BuildPhysicalSpec.Hammer(ModuleTier.EV, HammerVariant.BASE),
                new SettingsGroup.ID(7),
                true,
                placements),
            new FacilityCommand.BuildModules(
                FACILITY_ID,
                FacilityModuleKind.POWER,
                ModuleShape.SINGLE,
                new IModuleComponent.BuildPhysicalSpec.Tier(ModuleTier.HV),
                null,
                false,
                placements),
            new FacilityCommand.CopyBuildModules(FACILITY_ID, MODULE_ID, false, placements),
            new FacilityCommand.RequestModuleDeconstruction(FACILITY_ID, MODULE_ID),
            new FacilityCommand.CancelModuleOperation(FACILITY_ID, MODULE_ID),
            new FacilityCommand.ReplaceRecipeBook(FACILITY_ID, new RecipeBookOwner.Private(MODULE_ID), recipeBook),
            new FacilityCommand.ReplaceRecipeBook(
                FACILITY_ID,
                new RecipeBookOwner.Group(new SettingsGroup.ID(7)),
                recipeBook),
            new FacilityCommand.CreateSettingsGroup(FACILITY_ID, MODULE_ID, "Shared miners"),
            new FacilityCommand.RenameSettingsGroup(FACILITY_ID, new SettingsGroup.ID(7), "Priority miners"),
            new FacilityCommand.SetSettingsGroup(FACILITY_ID, MODULE_ID, new SettingsGroup.ID(7)),
            new FacilityCommand.SetSettingsGroup(FACILITY_ID, MODULE_ID, null),
            new FacilityCommand.CopyModuleSettings(FACILITY_ID, MODULE_ID, List.of(SECOND_MODULE_ID)),
            new FacilityCommand.ReplaceMinerSettings(
                FACILITY_ID,
                MODULE_ID,
                new MinerSettings(Set.of("ore:iron", "ore:copper"))),
            new FacilityCommand.ConfigureHammer(
                FACILITY_ID,
                MODULE_ID,
                new AllowShootingConfig(AllowShootingConfig.Mode.WHEN_DV_UNDER, 3.5),
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_DV),
            new FacilityCommand.SetMinerFocusOre(FACILITY_ID, MODULE_ID, "ore:iron"),
            new FacilityCommand.ConfigureDebugDataGenerator(
                FACILITY_ID,
                MODULE_ID,
                ModuleDebugDataGenerator.Config.consume(
                    SatelliteDataType.RESEARCH,
                    25L,
                    40,
                    CelestialObjectKey.registered(CelestialObjectId.MARS))),
            new FacilityCommand.PlanHammerUpgrade(
                FACILITY_ID,
                List.of(MODULE_ID, SECOND_MODULE_ID),
                HammerVariant.BIG,
                ModuleTier.ZPM,
                true,
                false),
            new FacilityCommand.PlanTierUpgrade(FACILITY_ID, List.of(MODULE_ID, SECOND_MODULE_ID), ModuleTier.IV, true),
            new FacilityCommand.PlanMinerFocusUpgrade(FACILITY_ID, MODULE_ID, ModuleTier.IV, MinerFocusTier.I));

        for (FacilityCommand command : commands) {
            ByteBuf encoded = Unpooled.buffer();
            new FacilityCommandPacket(command).toBytes(encoded);
            FacilityCommandPacket decoded = new FacilityCommandPacket();
            decoded.fromBytes(encoded);
            assertEquals(
                command,
                decoded.command(),
                command.getClass()
                    .getSimpleName());
            assertFalse(encoded.isReadable());
        }
    }

    @Test
    void malformedNbtEnvelopesNeverProducePartialCommands() throws Exception {
        FacilityCommand command = new FacilityCommand.ClearInventoryResource(
            FACILITY_ID,
            ItemStackWrapper.of(new ItemStack(Items.stick)));
        List<ByteBuf> malformed = new ArrayList<>();

        NBTTagCompound unknown = envelope(command);
        unknown.setString("type", "unknown");
        malformed.add(wire(unknown));

        NBTTagCompound extra = envelope(command);
        extra.setByte("extra", (byte) 1);
        malformed.add(wire(extra));

        NBTTagCompound wrongDataType = envelope(command);
        wrongDataType.setString("data", "wrong");
        malformed.add(wire(wrongDataType));

        NBTTagCompound invalidEnum = envelope(
            new FacilityCommand.AdjustInventory(
                FACILITY_ID,
                ItemStackWrapper.of(new ItemStack(Items.stick)),
                FacilityCommand.InventoryAdjustment.INSERT,
                1L));
        invalidEnum.getCompoundTag("data")
            .setString("direction", "UNKNOWN");
        malformed.add(wire(invalidEnum));

        NBTTagCompound wrongBoundSubtype = envelope(
            new FacilityCommand.SetInventoryBound(
                FACILITY_ID,
                BoundKind.ITEM_LOWER,
                ItemStackWrapper.of(new ItemStack(Items.stick)),
                1L));
        wrongBoundSubtype.getCompoundTag("data")
            .setString("kind", BoundKind.FLUID_LOWER.name());
        malformed.add(wire(wrongBoundSubtype));

        NBTTagCompound duplicateTargets = envelope(
            new FacilityCommand.PlanTierUpgrade(FACILITY_ID, List.of(MODULE_ID), ModuleTier.IV, false));
        duplicateTargets.getCompoundTag("data")
            .getTagList("targets", 8)
            .appendTag(
                new NBTTagString(
                    MODULE_ID.id()
                        .toString()));
        malformed.add(wire(duplicateTargets));

        NBTTagCompound wrongTargetSubtype = envelope(
            new FacilityCommand.PlanTierUpgrade(FACILITY_ID, List.of(MODULE_ID), ModuleTier.IV, false));
        NBTTagList compounds = new NBTTagList();
        compounds.appendTag(new NBTTagCompound());
        wrongTargetSubtype.getCompoundTag("data")
            .setTag("targets", compounds);
        malformed.add(wire(wrongTargetSubtype));

        NBTTagCompound invalidBoolean = envelope(
            new FacilityCommand.PlanTierUpgrade(FACILITY_ID, List.of(MODULE_ID), ModuleTier.IV, false));
        invalidBoolean.getCompoundTag("data")
            .setByte("reserveItems", (byte) 2);
        malformed.add(wire(invalidBoolean));

        NBTTagCompound invalidGroup = envelope(
            new FacilityCommand.SetSettingsGroup(FACILITY_ID, MODULE_ID, new SettingsGroup.ID(1)));
        invalidGroup.getCompoundTag("data")
            .setInteger("group", 0);
        malformed.add(wire(invalidGroup));

        NBTTagCompound blankOre = envelope(
            new FacilityCommand.ReplaceMinerSettings(FACILITY_ID, MODULE_ID, new MinerSettings(Set.of("ore:iron"))));
        NBTTagList blankBlacklist = new NBTTagList();
        blankBlacklist.appendTag(new NBTTagString(" "));
        blankOre.getCompoundTag("data")
            .getCompoundTag("settings")
            .setTag("blacklist", blankBlacklist);
        malformed.add(wire(blankOre));

        NBTTagCompound duplicateOre = envelope(
            new FacilityCommand.ReplaceMinerSettings(FACILITY_ID, MODULE_ID, new MinerSettings(Set.of("ore:iron"))));
        duplicateOre.getCompoundTag("data")
            .getCompoundTag("settings")
            .getTagList("blacklist", 8)
            .appendTag(new NBTTagString("ore:iron"));
        malformed.add(wire(duplicateOre));

        NBTTagCompound invalidOwner = envelope(
            new FacilityCommand.ReplaceRecipeBook(FACILITY_ID, new RecipeBookOwner.Private(MODULE_ID), recipeBook()));
        invalidOwner.getCompoundTag("data")
            .getCompoundTag("owner")
            .setString("type", "unknown");
        malformed.add(wire(invalidOwner));

        ByteBuf valid = wire(envelope(command));
        malformed.add(valid.copy(0, valid.readableBytes() - 1));
        ByteBuf trailing = valid.copy();
        trailing.writeByte(0);
        malformed.add(trailing);
        ByteBuf compressedTrailing = valid.copy();
        int payloadLength = compressedTrailing.getUnsignedShort(0);
        compressedTrailing.setShort(0, payloadLength + 1);
        compressedTrailing.writeByte(0);
        malformed.add(compressedTrailing);

        ByteBuf exactMaximumBody = Unpooled.buffer(FacilityCommandPacket.MAX_MESSAGE_BODY_BYTES);
        exactMaximumBody.writeShort(FacilityCommandPacket.MAX_COMPRESSED_NBT_BYTES);
        exactMaximumBody.writeZero(FacilityCommandPacket.MAX_COMPRESSED_NBT_BYTES);
        malformed.add(exactMaximumBody);
        ByteBuf oversizedBody = Unpooled.buffer(FacilityCommandPacket.MAX_MESSAGE_BODY_BYTES + 1);
        oversizedBody.writeZero(FacilityCommandPacket.MAX_MESSAGE_BODY_BYTES + 1);
        malformed.add(oversizedBody);

        for (int i = 0; i < malformed.size(); i++) {
            ByteBuf raw = malformed.get(i);
            FacilityCommandPacket packet = new FacilityCommandPacket();
            packet.fromBytes(raw);
            assertNull(packet.command(), "malformed case " + i);
            assertFalse(raw.isReadable());
        }
    }

    @Test
    void unknownCommandDataFieldsDoNotInvalidateRequiredState() throws Exception {
        FacilityCommand command = new FacilityCommand.ClearInventoryResource(
            FACILITY_ID,
            ItemStackWrapper.of(new ItemStack(Items.stick)));
        NBTTagCompound encoded = envelope(command);
        encoded.getCompoundTag("data")
            .setString("ignored", "future field");
        FacilityCommandPacket packet = new FacilityCommandPacket();

        packet.fromBytes(wire(encoded));

        assertEquals(command, packet.command());
    }

    @Test
    void decoderRejectsNonCanonicalRecipeState() throws Exception {
        NBTTagCompound oversizedArray = envelope(replaceRecipeBook(recipeBook()));
        NBTTagCompound oversizedRecipe = firstRecipe(oversizedArray);
        NBTTagList inputs = oversizedRecipe.getTagList("itemInputs", 10);
        NBTTagCompound input = inputs.getCompoundTagAt(0);
        while (inputs.tagCount() < 65) inputs.appendTag(input.copy());
        oversizedRecipe.setLong(
            "hash",
            oversizedRecipeBook().recipes()
                .get(0)
                .recipe()
                .contentHash());

        NBTTagCompound oversizedName = envelope(replaceRecipeBook(recipeBook()));
        firstRecipe(oversizedName).setString("displayName", "x".repeat(1025));

        NBTTagCompound negativeEut = envelope(replaceRecipeBook(recipeBook()));
        NBTTagCompound negativeEutRecipe = firstRecipe(negativeEut);
        negativeEutRecipe.setInteger("eut", -1);
        negativeEutRecipe.setLong(
            "hash",
            recipeBook(0, "Sticks").recipes()
                .get(0)
                .recipe()
                .contentHash());

        NBTTagCompound negativeMetadata = envelope(replaceRecipeBook(recipeBook()));
        NBTTagCompound negativeMetadataRecipe = firstRecipe(negativeMetadata);
        negativeMetadataRecipe.getTagList("itemInputs", 10)
            .getCompoundTagAt(0)
            .getCompoundTag("stack")
            .setShort("Damage", (short) -1);
        negativeMetadataRecipe.setLong(
            "hash",
            negativeMetadataRecipeBook().recipes()
                .get(0)
                .recipe()
                .contentHash());

        NBTTagCompound zeroOwner = envelope(replaceRecipeBook(recipeBook()));
        zeroOwner.getCompoundTag("data")
            .getCompoundTag("owner")
            .setString("module", new UUID(0L, 0L).toString());

        List<NBTTagCompound> malformedCases = List
            .of(oversizedArray, oversizedName, negativeEut, negativeMetadata, zeroOwner);
        for (int i = 0; i < malformedCases.size(); i++) {
            FacilityCommandPacket packet = new FacilityCommandPacket();
            packet.fromBytes(wire(malformedCases.get(i)));
            assertTrue(packet.command() == null, "malformed recipe case " + i);
        }
    }

    @Test
    void encoderRejectsNonCanonicalRecipeStateBeforeWritingDestination() {
        ByteBuf destination = Unpooled.buffer();
        List<FacilityCommand> invalid = List.of(
            replaceRecipeBook(oversizedRecipeBook()),
            replaceRecipeBook(recipeBook(32, "x".repeat(1025))),
            new FacilityCommand.ReplaceRecipeBook(
                FACILITY_ID,
                new RecipeBookOwner.Private(new ModuleInstance.ID(new UUID(0L, 0L))),
                recipeBook()));

        for (int i = 0; i < invalid.size(); i++) {
            FacilityCommand command = invalid.get(i);
            assertThrows(
                RuntimeException.class,
                () -> new FacilityCommandPacket(command).toBytes(destination),
                "invalid recipe case " + i);
            assertEquals(0, destination.writerIndex());
        }
    }

    @Test
    void encoderRejectsLimitsBeforeCopyingAnyBytesToForgeBuffer() {
        ByteBuf destination = Unpooled.buffer();
        String tooLong = "x".repeat(FacilityCommandPacket.MAX_STRING_BYTES + 1);
        assertThrows(
            RuntimeException.class,
            () -> new FacilityCommandPacket(
                new FacilityCommand.ReplaceFilters(FACILITY_ID, FacilityCommand.FilterKind.ITEM, List.of(tooLong)))
                    .toBytes(destination));
        assertEquals(0, destination.writerIndex());

        assertThrows(
            RuntimeException.class,
            () -> new FacilityCommandPacket(
                new FacilityCommand.ReplaceMinerSettings(FACILITY_ID, MODULE_ID, new MinerSettings(Set.of(tooLong))))
                    .toBytes(destination));
        assertEquals(0, destination.writerIndex());

        Set<String> excessiveOres = new LinkedHashSet<>();
        for (int i = 0; i < 257; i++) excessiveOres.add("ore:" + i);
        assertThrows(
            RuntimeException.class,
            () -> new FacilityCommandPacket(
                new FacilityCommand.ReplaceMinerSettings(FACILITY_ID, MODULE_ID, new MinerSettings(excessiveOres)))
                    .toBytes(destination));
        assertEquals(0, destination.writerIndex());

        List<String> excessiveFilterData = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            String prefix = Integer.toString(i);
            excessiveFilterData.add(prefix + "x".repeat(1024 - prefix.length()));
        }
        assertThrows(
            RuntimeException.class,
            () -> new FacilityCommandPacket(
                new FacilityCommand.ReplaceFilters(FACILITY_ID, FacilityCommand.FilterKind.ITEM, excessiveFilterData))
                    .toBytes(destination));
        assertEquals(0, destination.writerIndex());

        List<ModuleInstance.ID> tooManyTargets = new ArrayList<>();
        for (int i = 0; i < 257; i++) tooManyTargets.add(ModuleInstance.ID.create());
        assertThrows(
            RuntimeException.class,
            () -> new FacilityCommandPacket(
                new FacilityCommand.PlanTierUpgrade(FACILITY_ID, tooManyTargets, ModuleTier.IV, false))
                    .toBytes(destination));
        assertEquals(0, destination.writerIndex());
    }

    private static NBTTagCompound envelope(FacilityCommand command) throws Exception {
        ByteBuf encoded = Unpooled.buffer();
        new FacilityCommandPacket(command).toBytes(encoded);
        int length = encoded.readUnsignedShort();
        byte[] compressed = new byte[length];
        encoded.readBytes(compressed);
        return CompressedStreamTools
            .func_152457_a(compressed, new NBTSizeTracker(FacilityCommandPacket.MAX_DECOMPRESSED_NBT_BYTES));
    }

    private static ByteBuf wire(NBTTagCompound envelope) throws IOException {
        byte[] compressed = CompressedStreamTools.compress(envelope);
        ByteBuf encoded = Unpooled.buffer();
        encoded.writeShort(compressed.length);
        encoded.writeBytes(compressed);
        return encoded;
    }

    private static RecipeBook recipeBook() {
        return recipeBook(32, "Sticks");
    }

    private static RecipeBook recipeBook(int eut, String displayName) {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            3,
            new ItemStack[] { new ItemStack(Items.stick, 2, 0) },
            new ItemStack[] { new ItemStack(Items.diamond, 1, 0) },
            null,
            null,
            100,
            eut);
        SavedRecipe recipe = new SavedRecipe(snapshot, true, 8L, (byte) 2, (byte) 3, displayName);
        return new RecipeBook(List.of(recipe), RecipeSchedulerMode.ORDER, NotDoablePolicy.BACK_TO_BEGINNING);
    }

    private static RecipeBook oversizedRecipeBook() {
        ItemStack[] inputs = new ItemStack[65];
        Arrays.setAll(inputs, ignored -> new ItemStack(Items.stick, 2, 0));
        RecipeSnapshot snapshot = RecipeSnapshot
            .resolved((byte) 1, 3, inputs, new ItemStack[] { new ItemStack(Items.diamond, 1, 0) }, null, null, 100, 32);
        return book(snapshot);
    }

    private static RecipeBook negativeMetadataRecipeBook() {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            3,
            new ItemStack[] { new ItemStack(Items.stick, 2, -1) },
            new ItemStack[] { new ItemStack(Items.diamond, 1, 0) },
            null,
            null,
            100,
            32);
        return book(snapshot);
    }

    private static RecipeBook book(RecipeSnapshot snapshot) {
        SavedRecipe recipe = new SavedRecipe(snapshot, true, 8L, (byte) 2, (byte) 3, "Sticks");
        return new RecipeBook(List.of(recipe), RecipeSchedulerMode.ORDER, NotDoablePolicy.BACK_TO_BEGINNING);
    }

    private static FacilityCommand.ReplaceRecipeBook replaceRecipeBook(RecipeBook book) {
        return new FacilityCommand.ReplaceRecipeBook(FACILITY_ID, new RecipeBookOwner.Private(MODULE_ID), book);
    }

    private static NBTTagCompound firstRecipe(NBTTagCompound envelope) {
        return envelope.getCompoundTag("data")
            .getCompoundTag("recipeBook")
            .getTagList("recipes", 10)
            .getCompoundTagAt(0);
    }
}
