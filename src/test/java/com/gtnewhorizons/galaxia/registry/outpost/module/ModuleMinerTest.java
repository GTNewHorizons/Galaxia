package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
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

final class ModuleMinerTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void ungroupedMinerBlacklistIsSparseAndValidated() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);

        assertNotEquals(0, miner.groupId());
        assertFalse(
            facility.settingsGroups()
                .require(miner.groupId())
                .isJoinable());
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

        SettingsGroup group = facility.createSettingsGroupForModule(first, "Tin line");
        facility.assignSettingsGroup(second, group.id());

        assertTrue(facility.isMinerOreBlacklisted(second, "ore:iron"));

        facility.setMinerOreBlacklisted(second, "ore:copper", true);
        assertTrue(facility.isMinerOreBlacklisted(first, "ore:copper"));

        facility.leaveSettingsGroup(second);
        facility.setMinerOreBlacklisted(first, "ore:gold", true);

        assertTrue(facility.isMinerOreBlacklisted(second, "ore:copper"));
        assertFalse(facility.isMinerOreBlacklisted(second, "ore:gold"));
    }

    @Test
    void noGroupKeepsExistingPrivateSettingsGroup() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        short originalGroupId = miner.groupId();

        facility.assignSettingsGroup(miner, (short) 0);
        facility.assignSettingsGroup(miner, (short) 0);

        assertTrue(
            facility.settingsGroups()
                .groups()
                .containsKey(originalGroupId));
        assertTrue(
            facility.settingsGroups()
                .require(originalGroupId)
                .members()
                .contains(miner.anchor()));
        assertFalse(
            facility.settingsGroups()
                .require(originalGroupId)
                .isJoinable());
        assertEquals(
            1,
            facility.settingsGroups()
                .groups()
                .size());
    }

    @Test
    void noGroupOnSingletonPublicGroupPrivatizesInsteadOfCreatingNewGroup() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        SettingsGroup group = facility.settingsGroups()
            .require(miner.groupId());
        group.setJoinable(true);
        short originalGroupId = miner.groupId();

        facility.assignSettingsGroup(miner, (short) 0);

        assertEquals(originalGroupId, miner.groupId());
        assertFalse(group.isJoinable());
        assertEquals(
            1,
            facility.settingsGroups()
                .groups()
                .size());
    }

    @Test
    void createGroupPublishesCurrentPrivateSettingsGroup() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        short originalGroupId = miner.groupId();

        SettingsGroup group = facility.createSettingsGroupForModule(miner, "Public miners");

        assertTrue(group.isJoinable());
        assertEquals(originalGroupId, group.id());
        assertEquals("Public miners", group.displayName());
        assertEquals(
            1,
            facility.settingsGroups()
                .groups()
                .size());
    }

    @Test
    void renameSettingsGroupRequiresJoinableGroupOfSameKind() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);

        assertThrows(
            IllegalStateException.class,
            () -> facility.renameSettingsGroupForModule(miner, miner.groupId(), "Hidden miners"));

        SettingsGroup group = facility.createSettingsGroupForModule(miner, "Public miners");
        facility.renameSettingsGroupForModule(miner, group.id(), "  Priority miners  ");

        assertEquals("Priority miners", group.displayName());
    }

    @Test
    void renameSettingsGroupRejectsBlankName() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        SettingsGroup group = facility.createSettingsGroupForModule(miner, "Public miners");

        assertThrows(
            IllegalArgumentException.class,
            () -> facility.renameSettingsGroupForModule(miner, group.id(), " "));
    }

    @Test
    void copySettingsFromPrivateGroupDoesNotJoinTargetToSourceGroup() {
        AutomatedFacility facility = createFacility();
        ModuleInstance source = createMiner(StationTileCoord.of(1, 0));
        ModuleInstance target = createMiner(StationTileCoord.of(2, 0));
        facility.addModule(source);
        facility.addModule(target);
        short sourceGroupId = source.groupId();
        short targetGroupId = target.groupId();
        facility.setMinerOreBlacklisted(source, "ore:iron", true);

        facility.copyModuleRuntimeSettings(source, target);

        assertEquals(sourceGroupId, source.groupId());
        assertEquals(targetGroupId, target.groupId());
        assertFalse(
            facility.settingsGroups()
                .require(target.groupId())
                .isJoinable());
        assertTrue(facility.isMinerOreBlacklisted(target, "ore:iron"));
    }

    @Test
    void privateSettingsGroupCannotBeJoinedDirectly() {
        AutomatedFacility facility = createFacility();
        ModuleInstance source = createMiner(StationTileCoord.of(1, 0));
        ModuleInstance target = createMiner(StationTileCoord.of(2, 0));
        facility.addModule(source);
        facility.addModule(target);

        assertThrows(IllegalStateException.class, () -> facility.assignSettingsGroup(target, source.groupId()));
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
        AutomatedFacility facility = createFacility();
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
    void rareCrystalAddsGemMiningCandidates() {
        AutomatedFacility facility = createFeatureFacility();
        ModuleInstance miner = createMiner(
            findMinerAnchorWithFeature(facility, PlanetaryFeatureRegistry.RARE_CRYSTAL_FORMATION.key()));
        List<ItemStack> candidates = new java.util.ArrayList<>();
        candidates.add(new ItemStack(Items.iron_ingot));

        candidates.addAll(
            ModuleMiner.featureMiningEffects(miner, facility)
                .candidates());

        assertTrue(
            candidates.stream()
                .anyMatch(stack -> stack.getItem() == Items.diamond || stack.getItem() == Items.emerald));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static AutomatedFacility createFeatureFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.FROZEN_BELT,
            CelestialAsset.Kind.AUTOMATED_STATION,
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
