package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class FacilityModuleSettingsCommandTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void settingsCapableModuleAlwaysOwnsOneImmutableBinding() {
        AutomatedFacility facility = facility();
        ModuleInstance shared = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance privateModule = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));

        assertTrue(shared.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private);
        assertTrue(privateModule.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, shared.id, "Shared miners"),
                FacilityCommand.Authority.NONE));

        assertTrue(shared.settingsBinding() instanceof ModuleInstance.SettingsBinding.Shared);
        assertTrue(privateModule.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private);
    }

    @Test
    void creatingGroupKeepsEffectiveSettingsAndBindsSource() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        Set<String> expected = oreKeys(privateSettings(source));

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, source.id, "Shared miners"),
                FacilityCommand.Authority.NONE));

        SettingsGroup group = facility.settingsGroup(sharedGroupId(source));
        assertEquals(expected, oreKeys(group.settings()));
    }

    @Test
    void leavingGroupCopiesCurrentSettingsAndRemovesEmptyGroup() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.applyCommand(
            new FacilityCommand.CreateSettingsGroup(facility.assetId, source.id, "Shared miners"),
            FacilityCommand.Authority.NONE);
        SettingsGroup.ID groupId = sharedGroupId(source);
        Set<String> expected = oreKeys(
            facility.settingsGroup(groupId)
                .settings());

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.LeaveSettingsGroup(facility.assetId, source.id),
                FacilityCommand.Authority.NONE));

        assertEquals(expected, oreKeys(privateSettings(source)));
        assertNull(facility.settingsGroup(groupId));
    }

    @Test
    void copyingSettingsMakesTargetsPrivateAndRemovesTheirEmptyGroup() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance firstTarget = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ModuleInstance secondTarget = addMiner(facility, moduleId(3), StationTileCoord.of(7, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.applyCommand(
            new FacilityCommand.CreateSettingsGroup(facility.assetId, source.id, "Source miners"),
            FacilityCommand.Authority.NONE);
        facility.applyCommand(
            new FacilityCommand.CreateSettingsGroup(facility.assetId, firstTarget.id, "Target miners"),
            FacilityCommand.Authority.NONE);
        SettingsGroup.ID sourceGroupId = sharedGroupId(source);
        SettingsGroup.ID targetGroupId = sharedGroupId(firstTarget);
        facility.applyCommand(
            new FacilityCommand.JoinSettingsGroup(facility.assetId, secondTarget.id, targetGroupId),
            FacilityCommand.Authority.NONE);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CopyModuleSettings(
                    facility.assetId,
                    source.id,
                    List.of(firstTarget.id, secondTarget.id)),
                FacilityCommand.Authority.NONE));

        Set<String> sourceSettings = oreKeys(
            facility.settingsGroup(sourceGroupId)
                .settings());
        assertEquals(sourceSettings, oreKeys(privateSettings(firstTarget)));
        assertEquals(sourceSettings, oreKeys(privateSettings(secondTarget)));
        assertEquals(sourceGroupId, sharedGroupId(source));
        assertNull(facility.settingsGroup(targetGroupId));
    }

    @Test
    void repeatedIdenticalCopyIsUnchanged() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance target = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        FacilityCommand.CopyModuleSettings command = new FacilityCommand.CopyModuleSettings(
            facility.assetId,
            source.id,
            List.of(target.id));
        facility.applyCommand(command, FacilityCommand.Authority.NONE);
        ModuleInstance.SettingsBinding before = target.settingsBinding();

        assertSame(FacilityCommand.Result.UNCHANGED, facility.applyCommand(command, FacilityCommand.Authority.NONE));
        assertEquals(before, target.settingsBinding());
    }

    @Test
    void mixedCopyChangesOnlyStaleTarget() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance matching = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ModuleInstance stale = addMiner(facility, moduleId(3), StationTileCoord.of(7, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.setMinerOreBlacklisted(stale, "ore:copper", true);
        facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(matching.id)),
            FacilityCommand.Authority.NONE);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(matching.id, stale.id)),
                FacilityCommand.Authority.NONE));
        assertEquals(privateSettings(source), privateSettings(matching));
        assertEquals(privateSettings(source), privateSettings(stale));
    }

    @Test
    void subtypeCopyValidationAndStateRemainAuthoritative() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance target = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        ModuleMiner targetMiner = (ModuleMiner) target.component();
        sourceMiner.setFocus(MinerFocusTier.I, "ore:iron", 0);

        FacilityCommand.Result rejected = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
            FacilityCommand.Authority.NONE);
        assertEquals(FacilityCommand.Status.REJECTED, rejected.status());

        targetMiner.setFocus(MinerFocusTier.I, "ore:copper", 0);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
                FacilityCommand.Authority.NONE));
        assertEquals("ore:iron", targetMiner.focusOreKeyOrNull());
    }

    @Test
    void bindingsRoundTripAndNextGroupIdComesFromRestoredGroups() {
        AutomatedFacility original = facility();
        ModuleInstance shared = addMiner(original, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance privateModule = addMiner(original, moduleId(2), StationTileCoord.of(4, 0));
        original.applyCommand(
            new FacilityCommand.CreateSettingsGroup(original.assetId, shared.id, "Shared miners"),
            FacilityCommand.Authority.NONE);
        SettingsGroup.ID restoredGroupId = sharedGroupId(shared);

        AutomatedFacility restored = (AutomatedFacility) AssetState
            .decode(AssetState.encode(UUID.randomUUID(), original))
            .asset();
        ModuleInstance restoredShared = module(restored, shared.id);
        ModuleInstance restoredPrivate = module(restored, privateModule.id);

        assertEquals(restoredGroupId, sharedGroupId(restoredShared));
        assertTrue(restoredPrivate.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private);
        assertSame(
            FacilityCommand.Result.CHANGED,
            restored.applyCommand(
                new FacilityCommand.CreateSettingsGroup(restored.assetId, restoredPrivate.id, "Next miners"),
                FacilityCommand.Authority.NONE));
        assertEquals(new SettingsGroup.ID(restoredGroupId.value() + 1), sharedGroupId(restoredPrivate));
    }

    @Test
    void invalidBatchCopyMakesNoPartialSettingsChange() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance target = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.setMinerOreBlacklisted(target, "ore:copper", true);
        ModuleInstance.SettingsBinding before = target.settingsBinding();

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id, moduleId(99))),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(before, target.settingsBinding());
    }

    private static ModuleSettings privateSettings(ModuleInstance module) {
        assertTrue(module.settingsBinding() instanceof ModuleInstance.SettingsBinding.Private);
        return ((ModuleInstance.SettingsBinding.Private) module.settingsBinding()).settings();
    }

    private static SettingsGroup.ID sharedGroupId(ModuleInstance module) {
        assertTrue(module.settingsBinding() instanceof ModuleInstance.SettingsBinding.Shared);
        return ((ModuleInstance.SettingsBinding.Shared) module.settingsBinding()).groupId();
    }

    private static Set<String> oreKeys(ModuleSettings settings) {
        assertTrue(settings instanceof MinerSettings, "Expected miner settings but got " + settings);
        return ((MinerSettings) settings).blacklistedOreKeys();
    }

    private static ModuleInstance module(AutomatedFacility facility, ModuleInstance.ID id) {
        return facility.modules()
            .stream()
            .filter(module -> id.equals(module.id))
            .findFirst()
            .orElseThrow();
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance addMiner(AutomatedFacility facility, ModuleInstance.ID moduleId,
        StationTileCoord anchor) {
        FacilityModuleKind kind = FacilityModuleKind.MINER;
        ModuleInstance module = FacilityModuleRegistry
            .create(moduleId, kind, anchor, kind.defaultShape(), kind.defaultTier());
        module.completeConstruction();
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        return module;
    }

    private static ModuleInstance.ID moduleId(long value) {
        return new ModuleInstance.ID(new UUID(0L, value));
    }
}
