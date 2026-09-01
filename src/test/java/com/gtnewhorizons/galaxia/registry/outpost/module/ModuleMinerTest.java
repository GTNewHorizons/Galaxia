package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityModuleSettingsSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureContribution;
import com.gtnewhorizons.galaxia.registry.outpost.feature.FeatureModuleContext;
import com.gtnewhorizons.galaxia.registry.outpost.feature.MiningFeatureEffects;
import com.gtnewhorizons.galaxia.registry.outpost.feature.ModuleFeatureModifierBuilder;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeature;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleMinerTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void ungroupedMinerBlacklistIsSparseAndValidated() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);

        FacilityModuleSettingsSnapshot snapshot = facility.moduleSettingsSnapshot();
        assertTrue(
            snapshot.privateSettings()
                .containsKey(miner.id));
        assertFalse(
            snapshot.membership()
                .containsKey(miner.id));
        assertTrue(
            snapshot.groups()
                .isEmpty());
        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));

        facility.setMinerOreBlacklisted(miner, "ore:iron", true);
        assertTrue(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertTrue(
            facility.minerSettings(miner)
                .blacklistedOreKeys()
                .contains("ore:iron"));

        facility.setMinerOreBlacklisted(miner, "ore:iron", false);
        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertFalse(
            facility.minerSettings(miner)
                .blacklistedOreKeys()
                .contains("ore:iron"));
    }

    @Test
    void blacklistVoidsOreAfterRoll() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        facility.setMinerOreBlacklisted(miner, "ore:iron", true);

        assertTrue(ModuleMiner.shouldVoidOre(miner, facility, "ore:iron"));
        assertFalse(ModuleMiner.shouldVoidOre(miner, facility, "ore:copper"));
    }

    @Test
    void activeFocusTierCanExistWithoutSelectedOre() {
        ModuleMiner miner = (ModuleMiner) createMiner().component();

        miner.setFocus(MinerFocusTier.II, null, 1200);

        assertEquals(MinerFocusTier.II, miner.focusTier());
        assertNull(miner.focusOreKeyOrNull());
        assertEquals(0, miner.focusAlignmentProgress());
    }

    @Test
    void minerSettingsGroupSharesAndCopiesSettingsOnLeave() {
        AutomatedFacility facility = createFacility();
        ModuleInstance first = createMiner(StationTileCoord.of(1, 0));
        ModuleInstance second = createMiner(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);
        facility.setMinerOreBlacklisted(first, "ore:iron", true);

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, first.id, "Tin line"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID groupId = facility.moduleSettingsSnapshot()
            .membership()
            .get(first.id);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.JoinSettingsGroup(facility.assetId, second.id, groupId),
                FacilityCommand.Authority.NONE));

        assertTrue(facility.isMinerOreBlacklisted(second, "ore:iron"));

        facility.setMinerOreBlacklisted(second, "ore:copper", true);
        assertTrue(facility.isMinerOreBlacklisted(first, "ore:copper"));

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.LeaveSettingsGroup(facility.assetId, second.id),
                FacilityCommand.Authority.NONE));
        facility.setMinerOreBlacklisted(first, "ore:gold", true);

        assertTrue(facility.isMinerOreBlacklisted(second, "ore:copper"));
        assertFalse(facility.isMinerOreBlacklisted(second, "ore:gold"));
    }

    @Test
    void leaveOnPrivateSettingsIsUnchanged() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        FacilityModuleSettingsSnapshot before = facility.moduleSettingsSnapshot();

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.LeaveSettingsGroup(facility.assetId, miner.id),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.UNCHANGED, result);
        assertEquals(before, facility.moduleSettingsSnapshot());
    }

    @Test
    void renameGroupUsesStableGroupIdAndRejectsBlankName() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, miner.id, "Public miners"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID groupId = facility.moduleSettingsSnapshot()
            .membership()
            .get(miner.id);

        FacilityCommand.Result renamed = facility.applyCommand(
            new FacilityCommand.RenameSettingsGroup(facility.assetId, groupId, "  Priority miners  "),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result rejected = facility.applyCommand(
            new FacilityCommand.RenameSettingsGroup(facility.assetId, groupId, " "),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, renamed);
        assertEquals(
            "Priority miners",
            facility.moduleSettingsSnapshot()
                .groups()
                .get(groupId)
                .displayName());
        assertEquals(FacilityCommand.Status.REJECTED, rejected.status());
    }

    @Test
    void runtimeSettingsCopyRejectsMinerTargetWithoutRequiredFocusTier() {
        AutomatedFacility facility = createFacility();
        ModuleInstance source = createMiner(StationTileCoord.of(1, 0));
        ModuleInstance target = createMiner(StationTileCoord.of(2, 0));
        facility.addModule(source);
        facility.addModule(target);
        ModuleMiner sourceMiner = (ModuleMiner) source.component();
        ModuleMiner targetMiner = (ModuleMiner) target.component();
        sourceMiner.setFocus(MinerFocusTier.II, "ore:iron", 1200);
        targetMiner.setFocus(MinerFocusTier.NONE, null, 0);

        FacilityModuleSettingsSnapshot before = facility.moduleSettingsSnapshot();
        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, result.status());
        assertEquals(before, facility.moduleSettingsSnapshot());
        assertNull(targetMiner.focusOreKeyOrNull());
    }

    @Test
    void mineralVeinContributionScalesAgainstTwoByTwoFootprint() {
        ModuleInstance miner = createMiner();

        PlanetaryFeature feature = PlanetaryFeatureRegistry.feature(PlanetaryFeatureRegistry.MINERAL_VEIN.key());
        ModuleFeatureModifierBuilder builder = new ModuleFeatureModifierBuilder();
        feature.applyModuleModifiers(
            new FeatureModuleContext(
                miner,
                PlanetaryFeatureRegistry.MINERAL_VEIN.key(),
                2,
                miner.shape()
                    .tileCount()),
            builder);
        FeatureContribution contribution = builder
            .build(java.util.Map.of(PlanetaryFeatureRegistry.MINERAL_VEIN.key(), 2))
            .contributions()
            .get(0);

        assertEquals(ModuleShape.QUAD_2x2, miner.shape());
        assertEquals(2, contribution.coveredTiles());
        assertEquals(4, contribution.totalTiles());
    }

    @Test
    void mineralVeinBonusRollsUseCoveredTiles() {
        AutomatedFacility facility = createFeatureFacility();
        facility.setStationFeatureSalt(987654321L);
        ModuleInstance miner = createMiner(
            findMinerAnchorWithFeature(facility, PlanetaryFeatureRegistry.MINERAL_VEIN.key()));

        int expectedCoveredTiles = facility.featureContributions(miner)
            .stream()
            .filter(
                c -> c.key()
                    .equals(PlanetaryFeatureRegistry.MINERAL_VEIN.key()))
            .findFirst()
            .orElseThrow()
            .coveredTiles();

        assertEquals(
            expectedCoveredTiles,
            ModuleMiner.featureMiningEffects(miner, facility)
                .bonusRolls());
    }

    @Test
    void icePocketCanReplaceOreRollWithIce() {
        AutomatedFacility facility = createFeatureFacility();
        ModuleInstance miner = createMiner(
            findMinerAnchorWithFeature(facility, PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key()));
        int coveredTiles = facility.featureContributions(miner)
            .stream()
            .filter(
                c -> c.key()
                    .equals(PlanetaryFeatureRegistry.SUBSURFACE_ICE_POCKET.key()))
            .findFirst()
            .orElseThrow()
            .coveredTiles();
        int chancePercent = coveredTiles * 20;
        MiningFeatureEffects effects = ModuleMiner.featureMiningEffects(miner, facility);

        assertEquals(
            chancePercent,
            effects.replacementRolls()
                .get(0)
                .chancePercent());
        assertNotNull(effects.rollReplacement(randomReturning(chancePercent - 1)));
        assertNull(effects.rollReplacement(randomReturning(chancePercent)));
    }

    @Test
    void rareCrystalDoesNotAddVanillaGemCandidatesWhenGtStacksAreMissing() {
        AutomatedFacility facility = createFeatureFacility();
        ModuleInstance miner = createMiner(
            findMinerAnchorWithFeature(facility, PlanetaryFeatureRegistry.RARE_CRYSTAL_FORMATION.key()));

        List<ItemStack> candidates = ModuleMiner.featureMiningEffects(miner, facility)
            .candidates();

        assertTrue(candidates.isEmpty());
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static AutomatedFacility createFeatureFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.FROZEN_BELT,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance createMiner() {
        return createMiner(StationTileCoord.of(1, 0));
    }

    private static ModuleInstance createMiner(StationTileCoord anchor) {
        ModuleInstance miner = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            anchor,
            FacilityModuleKind.MINER.defaultShape(),
            ModuleTier.EV);
        miner.updateStatus(Buildable.Status.OPERATIONAL);
        return miner;
    }

    private static StationTileCoord findMinerAnchorWithFeature(AutomatedFacility facility,
        PlanetaryFeatureKey feature) {
        for (int dx = StationTileCoord.MIN; dx < StationTileCoord.MAX; dx++) {
            for (int dy = StationTileCoord.MIN; dy < StationTileCoord.MAX; dy++) {
                StationTileCoord anchor = StationTileCoord.of(dx, dy);
                ModuleInstance miner = createMiner(anchor);
                if (facility.featureContributions(miner)
                    .stream()
                    .anyMatch(
                        c -> c.key()
                            .equals(feature))) {
                    return anchor;
                }
            }
        }
        throw new AssertionError("No deterministic feature found for miner test: " + feature);
    }

    private static java.util.Random randomReturning(int value) {
        return new java.util.Random(0L) {

            @Override
            public int nextInt(int bound) {
                return value;
            }
        };
    }
}
