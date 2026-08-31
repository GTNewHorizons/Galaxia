package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void settingsCapableModuleHasExactlyOnePrivateOrSharedOwner() {
        AutomatedFacility facility = facility();
        ModuleInstance shared = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance privateModule = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));

        FacilityModuleSettingsSnapshot before = facility.moduleSettingsSnapshot();
        assertExactlyOneOwner(before, shared.id);
        assertExactlyOneOwner(before, privateModule.id);

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CreateSettingsGroup(facility.assetId, shared.id, "Shared miners"),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        FacilityModuleSettingsSnapshot after = facility.moduleSettingsSnapshot();
        assertExactlyOneOwner(after, shared.id);
        assertExactlyOneOwner(after, privateModule.id);
        assertTrue(
            after.membership()
                .containsKey(shared.id));
        assertTrue(
            after.privateSettings()
                .containsKey(privateModule.id));
    }

    @Test
    void creatingGroupCopiesEffectiveSettingsAndJoinsSource() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        Set<String> expectedSettings = oreKeys(
            facility.moduleSettingsSnapshot()
                .privateSettings()
                .get(source.id));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CreateSettingsGroup(facility.assetId, source.id, "Shared miners"),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        FacilityModuleSettingsSnapshot after = facility.moduleSettingsSnapshot();
        SettingsGroup.ID groupId = after.membership()
            .get(source.id);
        assertNotNull(groupId);
        assertFalse(
            after.privateSettings()
                .containsKey(source.id));
        assertEquals(
            expectedSettings,
            oreKeys(
                after.groups()
                    .get(groupId)
                    .settings()));
    }

    @Test
    void leavingGroupCopiesSharedSettingsToPrivateAndRemovesEmptyGroup() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, source.id, "Shared miners"),
                FacilityCommand.Authority.NONE));
        FacilityModuleSettingsSnapshot shared = facility.moduleSettingsSnapshot();
        SettingsGroup.ID groupId = shared.membership()
            .get(source.id);
        Set<String> expectedSettings = oreKeys(
            shared.groups()
                .get(groupId)
                .settings());

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.LeaveSettingsGroup(facility.assetId, source.id),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        FacilityModuleSettingsSnapshot after = facility.moduleSettingsSnapshot();
        assertFalse(
            after.membership()
                .containsKey(source.id));
        assertFalse(
            after.groups()
                .containsKey(groupId));
        assertEquals(
            expectedSettings,
            oreKeys(
                after.privateSettings()
                    .get(source.id)));
    }

    @Test
    void copyingSettingsMakesEveryTargetPrivateAndNeverJoinsSourceGroup() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance firstTarget = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ModuleInstance secondTarget = addMiner(facility, moduleId(3), StationTileCoord.of(7, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.setMinerOreBlacklisted(firstTarget, "ore:copper", true);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, source.id, "Source miners"),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, firstTarget.id, "Target miners"),
                FacilityCommand.Authority.NONE));
        FacilityModuleSettingsSnapshot grouped = facility.moduleSettingsSnapshot();
        SettingsGroup.ID sourceGroupId = grouped.membership()
            .get(source.id);
        SettingsGroup.ID targetGroupId = grouped.membership()
            .get(firstTarget.id);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.JoinSettingsGroup(facility.assetId, secondTarget.id, targetGroupId),
                FacilityCommand.Authority.NONE));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(
                facility.assetId,
                source.id,
                List.of(firstTarget.id, secondTarget.id)),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        FacilityModuleSettingsSnapshot after = facility.moduleSettingsSnapshot();
        Set<String> sourceSettings = oreKeys(
            after.groups()
                .get(sourceGroupId)
                .settings());
        for (ModuleInstance target : List.of(firstTarget, secondTarget)) {
            assertFalse(
                after.membership()
                    .containsKey(target.id));
            assertEquals(
                sourceSettings,
                oreKeys(
                    after.privateSettings()
                        .get(target.id)));
        }
        assertEquals(
            sourceGroupId,
            after.membership()
                .get(source.id));
        assertFalse(
            after.groups()
                .containsKey(targetGroupId));
    }

    @Test
    void repeatedIdenticalCopyIsUnchangedWithoutRevisionBump() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance target = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.setMinerOreBlacklisted(target, "ore:copper", true);
        FacilityCommand.CopyModuleSettings command = new FacilityCommand.CopyModuleSettings(
            facility.assetId,
            source.id,
            List.of(target.id));
        assertSame(FacilityCommand.Result.CHANGED, facility.applyCommand(command, FacilityCommand.Authority.NONE));
        FacilityModuleSettingsSnapshot beforeRepeat = facility.moduleSettingsSnapshot();
        int revisionBeforeRepeat = facility.getStateRevision();

        FacilityCommand.Result repeated = facility.applyCommand(command, FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.UNCHANGED, repeated);
        assertEquals(beforeRepeat, facility.moduleSettingsSnapshot());
        assertEquals(revisionBeforeRepeat, facility.getStateRevision());
    }

    @Test
    void mixedCopyReturnsChangedWhenOneTargetAlreadyMatches() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance matchingTarget = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ModuleInstance staleTarget = addMiner(facility, moduleId(3), StationTileCoord.of(7, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.setMinerOreBlacklisted(staleTarget, "ore:copper", true);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(matchingTarget.id)),
                FacilityCommand.Authority.NONE));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(
                facility.assetId,
                source.id,
                List.of(matchingTarget.id, staleTarget.id)),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        FacilityModuleSettingsSnapshot after = facility.moduleSettingsSnapshot();
        assertEquals(
            oreKeys(
                after.privateSettings()
                    .get(source.id)),
            oreKeys(
                after.privateSettings()
                    .get(matchingTarget.id)));
        assertEquals(
            oreKeys(
                after.privateSettings()
                    .get(source.id)),
            oreKeys(
                after.privateSettings()
                    .get(staleTarget.id)));
    }

    @Test
    void equalImmutableSettingsStillRunSubtypeCopyValidation() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance target = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ((ModuleMiner) source.component()).setFocus(MinerFocusTier.I, "ore:iron", 0);
        FacilityModuleSettingsSnapshot before = facility.moduleSettingsSnapshot();
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(before, facility.moduleSettingsSnapshot());
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    @Test
    void copyChangesWhenOnlySubtypeStateDiffers() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance target = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        ModuleMiner targetMiner = (ModuleMiner) target.component();
        sourceMiner.setFocus(MinerFocusTier.I, "ore:iron", 0);
        targetMiner.setFocus(MinerFocusTier.I, "ore:copper", 0);
        assertEquals(
            facility.moduleSettingsSnapshot()
                .privateSettings()
                .get(source.id),
            facility.moduleSettingsSnapshot()
                .privateSettings()
                .get(target.id));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertEquals("ore:iron", targetMiner.focusOreKeyOrNull());
    }

    @Test
    void reconstructedModuleAnchorDoesNotChangeModuleIdMembership() {
        AutomatedFacility original = facility();
        ModuleInstance originalModule = addMiner(original, moduleId(1), StationTileCoord.of(1, 0));
        assertSame(
            FacilityCommand.Result.CHANGED,
            original.applyCommand(
                new FacilityCommand.CreateSettingsGroup(original.assetId, originalModule.id, "Shared miners"),
                FacilityCommand.Authority.NONE));
        FacilityModuleSettingsSnapshot saved = original.moduleSettingsSnapshot();
        SettingsGroup.ID groupId = saved.membership()
            .get(originalModule.id);

        AutomatedFacility reconstructed = facility();
        ModuleInstance movedModule = addMiner(reconstructed, originalModule.id, StationTileCoord.of(7, 0));
        reconstructed.restoreModuleSettings(saved);

        assertEquals(
            groupId,
            reconstructed.moduleSettingsSnapshot()
                .membership()
                .get(movedModule.id));
        assertEquals(
            1,
            reconstructed.moduleSettingsSnapshot()
                .membership()
                .size());
    }

    @Test
    void restoringGroupsDerivesNextPositiveIdWithoutCollision() {
        AutomatedFacility facility = facility();
        ModuleInstance shared = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance privateModule = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        FacilityModuleSettingsSnapshot initial = facility.moduleSettingsSnapshot();
        SettingsGroup.ID restoredGroupId = new SettingsGroup.ID(7);
        ModuleSettings sharedSettings = initial.privateSettings()
            .get(shared.id);
        FacilityModuleSettingsSnapshot restored = new FacilityModuleSettingsSnapshot(
            Map.of(
                privateModule.id,
                initial.privateSettings()
                    .get(privateModule.id)),
            Map.of(
                restoredGroupId,
                new SettingsGroup(restoredGroupId, shared.kind(), "Restored miners", sharedSettings)),
            Map.of(shared.id, restoredGroupId));
        facility.restoreModuleSettings(restored);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, privateModule.id, "Next miners"),
                FacilityCommand.Authority.NONE));

        FacilityModuleSettingsSnapshot after = facility.moduleSettingsSnapshot();
        SettingsGroup.ID nextGroupId = after.membership()
            .get(privateModule.id);
        assertEquals(new SettingsGroup.ID(8), nextGroupId);
        assertTrue(
            after.groups()
                .containsKey(restoredGroupId));
        assertTrue(
            after.groups()
                .containsKey(nextGroupId));
    }

    @Test
    void invalidReconstructedMembershipDoesNotChangeExistingSnapshot() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        FacilityModuleSettingsSnapshot before = facility.moduleSettingsSnapshot();
        FacilityModuleSettingsSnapshot invalid = new FacilityModuleSettingsSnapshot(
            Map.of(),
            before.groups(),
            Map.of(module.id, new SettingsGroup.ID(99)));

        assertThrows(IllegalArgumentException.class, () -> facility.restoreModuleSettings(invalid));

        assertEquals(before, facility.moduleSettingsSnapshot());
    }

    @Test
    void invalidBatchCopyMakesNoPartialSettingsChange() {
        AutomatedFacility facility = facility();
        ModuleInstance source = addMiner(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance validTarget = addMiner(facility, moduleId(2), StationTileCoord.of(4, 0));
        facility.setMinerOreBlacklisted(source, "ore:iron", true);
        facility.setMinerOreBlacklisted(validTarget, "ore:copper", true);
        FacilityModuleSettingsSnapshot before = facility.moduleSettingsSnapshot();
        int revisionBefore = facility.getStateRevision();

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(validTarget.id, moduleId(99))),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(before, facility.moduleSettingsSnapshot());
        assertEquals(revisionBefore, facility.getStateRevision());
    }

    private static void assertExactlyOneOwner(FacilityModuleSettingsSnapshot snapshot, ModuleInstance.ID moduleId) {
        boolean privatelyOwned = snapshot.privateSettings()
            .containsKey(moduleId);
        boolean shared = snapshot.membership()
            .containsKey(moduleId);
        assertTrue(privatelyOwned ^ shared, "Expected exactly one settings owner for " + moduleId);
        if (shared) {
            assertTrue(
                snapshot.groups()
                    .containsKey(
                        snapshot.membership()
                            .get(moduleId)));
        }
    }

    private static Set<String> oreKeys(ModuleSettings settings) {
        assertTrue(settings instanceof MinerSettings, "Expected miner settings but got " + settings);
        return ((MinerSettings) settings).blacklistedOreKeys();
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
