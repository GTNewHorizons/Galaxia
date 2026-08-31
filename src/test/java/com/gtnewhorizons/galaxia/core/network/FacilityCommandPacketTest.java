package com.gtnewhorizons.galaxia.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
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
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
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
        ItemStackWrapper item = ItemStackWrapper.of(new ItemStack(Items.stick));
        FluidKey fluid = new FluidKey(TEST_FLUID, null);
        List<ModulePlacement> placements = List.of(new ModulePlacement(new StationTileCoord((byte) 4, (byte) -3), 3));
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
            new FacilityCommand.CreateSettingsGroup(FACILITY_ID, MODULE_ID, "Shared miners"),
            new FacilityCommand.RenameSettingsGroup(FACILITY_ID, new SettingsGroup.ID(7), "Priority miners"),
            new FacilityCommand.JoinSettingsGroup(FACILITY_ID, MODULE_ID, new SettingsGroup.ID(7)),
            new FacilityCommand.LeaveSettingsGroup(FACILITY_ID, MODULE_ID),
            new FacilityCommand.CopyModuleSettings(FACILITY_ID, MODULE_ID, List.of(SECOND_MODULE_ID)),
            new FacilityCommand.SetMinerOreBlacklisted(FACILITY_ID, MODULE_ID, "ore:iron", true),
            new FacilityCommand.SetHammerShootingConfig(
                FACILITY_ID,
                MODULE_ID,
                new AllowShootingConfig(AllowShootingConfig.Mode.WHEN_DV_UNDER, 3.5)),
            new FacilityCommand.SetHammerRoutePriority(
                FACILITY_ID,
                MODULE_ID,
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
    void malformedWireInputsNeverProducePartialCommands() {
        List<ByteBuf> malformed = new ArrayList<>();
        malformed.add(base(255));

        ByteBuf invalidEnum = base(FacilityCommandPacket.OP_ADJUST_INVENTORY);
        writeItemKey(invalidEnum, "minecraft:stick:0");
        invalidEnum.writeByte(255);
        invalidEnum.writeLong(1L);
        malformed.add(invalidEnum);

        ByteBuf invalidKeyType = base(FacilityCommandPacket.OP_CLEAR_INVENTORY_RESOURCE);
        invalidKeyType.writeByte(2);
        malformed.add(invalidKeyType);

        ByteBuf invalidKey = base(FacilityCommandPacket.OP_CLEAR_INVENTORY_RESOURCE);
        writeItemKey(invalidKey, "missing:item:0");
        malformed.add(invalidKey);

        ByteBuf malformedUtf8 = base(FacilityCommandPacket.OP_REPLACE_FILTERS);
        malformedUtf8.writeByte(FacilityCommand.FilterKind.ITEM.ordinal());
        malformedUtf8.writeInt(1);
        malformedUtf8.writeShort(2);
        malformedUtf8.writeByte(0xC3);
        malformedUtf8.writeByte(0x28);
        malformed.add(malformedUtf8);

        ByteBuf invalidBoolean = base(FacilityCommandPacket.OP_PLAN_TIER_UPGRADE);
        invalidBoolean.writeInt(1);
        writeUuid(invalidBoolean, MODULE_ID.id());
        invalidBoolean.writeByte(ModuleTier.IV.ordinal());
        invalidBoolean.writeByte(2);
        malformed.add(invalidBoolean);

        ByteBuf duplicateTargets = base(FacilityCommandPacket.OP_PLAN_TIER_UPGRADE);
        duplicateTargets.writeInt(2);
        writeUuid(duplicateTargets, MODULE_ID.id());
        writeUuid(duplicateTargets, MODULE_ID.id());
        duplicateTargets.writeByte(ModuleTier.IV.ordinal());
        duplicateTargets.writeByte(0);
        malformed.add(duplicateTargets);

        ByteBuf badRotation = validBuildPrefix();
        badRotation.writeInt(1);
        badRotation.writeByte(0);
        badRotation.writeByte(0);
        badRotation.writeByte(4);
        malformed.add(badRotation);

        ByteBuf impossibleFilterCount = base(FacilityCommandPacket.OP_REPLACE_FILTERS);
        impossibleFilterCount.writeByte(FacilityCommand.FilterKind.ITEM.ordinal());
        impossibleFilterCount.writeInt(257);
        malformed.add(impossibleFilterCount);

        ByteBuf impossiblePlacementCount = validBuildPrefix();
        impossiblePlacementCount.writeInt(257);
        malformed.add(impossiblePlacementCount);

        ByteBuf impossibleTargetCount = base(FacilityCommandPacket.OP_PLAN_TIER_UPGRADE);
        impossibleTargetCount.writeInt(257);
        malformed.add(impossibleTargetCount);

        ByteBuf invalidSettingsGroupId = base(FacilityCommandPacket.OP_JOIN_SETTINGS_GROUP);
        writeUuid(invalidSettingsGroupId, MODULE_ID.id());
        invalidSettingsGroupId.writeInt(0);
        malformed.add(invalidSettingsGroupId);

        ByteBuf blankOreKey = base(FacilityCommandPacket.OP_SET_MINER_ORE_BLACKLISTED);
        writeUuid(blankOreKey, MODULE_ID.id());
        writeString(blankOreKey, " ");
        blankOreKey.writeByte(1);
        malformed.add(blankOreKey);

        ByteBuf invalidBlacklistFlag = base(FacilityCommandPacket.OP_SET_MINER_ORE_BLACKLISTED);
        writeUuid(invalidBlacklistFlag, MODULE_ID.id());
        writeString(invalidBlacklistFlag, "ore:iron");
        invalidBlacklistFlag.writeByte(2);
        malformed.add(invalidBlacklistFlag);

        ByteBuf oversize = Unpooled.buffer(FacilityCommandPacket.MAX_PACKET_BYTES + 1);
        oversize.writeZero(FacilityCommandPacket.MAX_PACKET_BYTES + 1);
        malformed.add(oversize);

        ByteBuf valid = Unpooled.buffer();
        new FacilityCommandPacket(
            new FacilityCommand.ClearInventoryResource(FACILITY_ID, ItemStackWrapper.of(new ItemStack(Items.stick))))
                .toBytes(valid);
        ByteBuf truncated = valid.copy(0, valid.readableBytes() - 1);
        malformed.add(truncated);
        ByteBuf trailing = valid.copy();
        trailing.writeByte(0);
        malformed.add(trailing);

        ByteBuf blacklistTrailing = Unpooled.buffer();
        new FacilityCommandPacket(new FacilityCommand.SetMinerOreBlacklisted(FACILITY_ID, MODULE_ID, "ore:iron", true))
            .toBytes(blacklistTrailing);
        blacklistTrailing.writeByte(0);
        malformed.add(blacklistTrailing);

        for (ByteBuf raw : malformed) {
            FacilityCommandPacket packet = new FacilityCommandPacket();
            packet.fromBytes(raw);
            assertNull(packet.command());
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
                new FacilityCommand.SetMinerOreBlacklisted(FACILITY_ID, MODULE_ID, tooLong, true))
                    .toBytes(destination));
        assertEquals(0, destination.writerIndex());

        List<String> excessiveFilterData = new ArrayList<>();
        for (int i = 0; i < 33; i++) excessiveFilterData.add("x".repeat(1024));
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

    private static ByteBuf base(int opcode) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(opcode);
        writeUuid(buf, FACILITY_ID.id());
        return buf;
    }

    private static ByteBuf validBuildPrefix() {
        ByteBuf buf = base(FacilityCommandPacket.OP_BUILD_MODULES);
        buf.writeByte(FacilityModuleKind.HAMMER.ordinal());
        buf.writeByte(ModuleShape.SINGLE.ordinal());
        buf.writeByte(1);
        buf.writeByte(ModuleTier.EV.ordinal());
        buf.writeByte(HammerVariant.BASE.ordinal());
        buf.writeByte(0);
        buf.writeByte(0);
        return buf;
    }

    private static void writeItemKey(ByteBuf buf, String key) {
        buf.writeByte(0);
        writeString(buf, key);
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static void writeUuid(ByteBuf buf, UUID id) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
    }
}
