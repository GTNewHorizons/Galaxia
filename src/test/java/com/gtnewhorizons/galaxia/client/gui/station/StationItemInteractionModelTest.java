package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.TieredModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureKey;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModulePanelAction;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class StationItemInteractionModelTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void groupedRecipeModulesAppearOnceForConsumedItem() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ItemStack input = new ItemStack(Blocks.iron_ore);
        ItemStack output = new ItemStack(Items.iron_ingot);
        ItemStackWrapper resource = ItemStackWrapper.of(input);
        ModuleInstance first = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance second = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);
        facility.setRecipeConfig(first, config(input, output));
        SettingsGroup group = facility.createSettingsGroupForModule(first, "Dust line");
        facility.assignSettingsGroup(second, group.id());

        List<StationItemInteractionModel.Entry> entries = StationItemInteractionModel.forItem(facility, resource);
        List<StationItemInteractionModel.Entry> consumers = entries.stream()
            .filter(entry -> entry.section() == StationItemInteractionModel.Section.MACHINES)
            .filter(entry -> entry.role() == StationItemInteractionModel.Role.CONSUMES)
            .toList();

        assertEquals(1, consumers.size());
        StationItemInteractionModel.Entry consumer = consumers.get(0);
        assertEquals("Dust line", consumer.label());
        assertEquals(2, consumer.count());
        assertEquals(group.id(), consumer.groupId());
        assertNotNull(consumer.targetModuleId());
    }

    @Test
    void logisticsAndGroupedUpkeepDescribeItemInteractions() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ItemStackWrapper resource = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.logisticsConfig.set(resource, new LogisticsResourceConfig(128, 64, true, true));
        facility.addModule(createHammer(StationTileCoord.of(0, 0)));
        ModuleInstance first = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance second = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);
        SettingsGroup group = facility.createSettingsGroupForModule(first, "Dust line");
        facility.assignSettingsGroup(second, group.id());

        List<StationItemInteractionModel.Entry> entries = StationItemInteractionModel.forItem(facility, resource);

        assertTrue(
            entries.stream()
                .anyMatch(
                    entry -> entry.role() == StationItemInteractionModel.Role.CORE_IMPORT && entry.reserve() == 128
                        && entry.orderSize() == 64));
        assertTrue(
            entries.stream()
                .anyMatch(entry -> entry.role() == StationItemInteractionModel.Role.HAMMER_EXPORT));
        StationItemInteractionModel.Entry upkeep = entries.stream()
            .filter(entry -> entry.section() == StationItemInteractionModel.Section.UPKEEP)
            .filter(entry -> entry.kind() == FacilityModuleKind.MACERATOR)
            .findFirst()
            .orElseThrow();
        assertEquals(2, upkeep.count());
        assertEquals(0, upkeep.groupId());
        assertTrue(
            upkeep.amountPerMinute()
                .microUnitsPerMinute() > 0);
    }

    @Test
    void upkeepSplitsSameKindModulesWithDifferentDemand() {
        AutomatedFacility facility = createFacility();
        ItemStackWrapper resource = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.addModule(moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(1, 0), 1L));
        facility.addModule(moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(2, 0), 1L));
        facility.addModule(moduleWithUpkeep(FacilityModuleKind.POWER, StationTileCoord.of(3, 0), 2L));

        List<Integer> counts = StationItemInteractionModel.forItem(facility, resource)
            .stream()
            .filter(entry -> entry.section() == StationItemInteractionModel.Section.UPKEEP)
            .filter(entry -> entry.kind() == FacilityModuleKind.POWER)
            .map(StationItemInteractionModel.Entry::count)
            .sorted()
            .toList();

        assertEquals(List.of(1, 2), counts);
    }

    @Test
    void upkeepAggregatesSameKindModulesWithoutSettingsGroup() {
        AutomatedFacility facility = createFacility();
        ItemStackWrapper resource = ItemStackWrapper.of(new ItemStack(Items.iron_ingot));
        facility.addModule(createModule(FacilityModuleKind.POWER, StationTileCoord.of(1, 0)));
        facility.addModule(createModule(FacilityModuleKind.POWER, StationTileCoord.of(2, 0)));
        facility.addModule(createModule(FacilityModuleKind.POWER, StationTileCoord.of(3, 0)));

        List<StationItemInteractionModel.Entry> upkeepEntries = StationItemInteractionModel.forItem(facility, resource)
            .stream()
            .filter(entry -> entry.section() == StationItemInteractionModel.Section.UPKEEP)
            .filter(entry -> entry.kind() == FacilityModuleKind.POWER)
            .toList();

        assertEquals(1, upkeepEntries.size());
        StationItemInteractionModel.Entry upkeep = upkeepEntries.get(0);
        assertEquals(3, upkeep.count());
        assertEquals(0, upkeep.groupId());
        assertTrue(
            upkeep.amountPerMinute()
                .microUnitsPerMinute() > 0);
    }

    @Test
    void minerAppearsAsProducerForBodyOre() {
        AutomatedFacility facility = createMiningFacility();
        facility.addModule(createMiner(StationTileCoord.of(1, 0)));
        ItemStackWrapper resource = ItemStackWrapper.of(new ItemStack(Blocks.iron_ore));

        List<StationItemInteractionModel.Entry> entries = StationItemInteractionModel.forItem(facility, resource);

        assertTrue(
            entries.stream()
                .anyMatch(
                    entry -> entry.section() == StationItemInteractionModel.Section.MACHINES
                        && entry.role() == StationItemInteractionModel.Role.PRODUCES
                        && entry.kind() == FacilityModuleKind.MINER));
    }

    @Test
    void minerAppearsAsProducerForFeatureMiningCandidate() {
        AutomatedFacility facility = createFeatureMiningFacility();
        facility.setStationFeatureSalt(987654321L);
        facility.addModule(
            createMiner(findMinerAnchorWithFeature(facility, PlanetaryFeatureRegistry.RARE_CRYSTAL_FORMATION.key())));
        ItemStackWrapper diamond = ItemStackWrapper.of(new ItemStack(Items.diamond));

        List<StationItemInteractionModel.Entry> entries = StationItemInteractionModel.forItem(facility, diamond);

        assertTrue(
            entries.stream()
                .anyMatch(
                    entry -> entry.section() == StationItemInteractionModel.Section.MACHINES
                        && entry.role() == StationItemInteractionModel.Role.PRODUCES
                        && entry.kind() == FacilityModuleKind.MINER));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static AutomatedFacility createMiningFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MOON,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static AutomatedFacility createFeatureMiningFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.FROZEN_BELT,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance createMachine(StationTileCoord anchor) {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            anchor,
            ModuleShape.SINGLE,
            ModuleTier.HV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
    }

    private static ModuleInstance createHammer(StationTileCoord anchor) {
        ModuleInstance module = createModule(FacilityModuleKind.HAMMER, anchor);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
    }

    private static ModuleInstance createMiner(StationTileCoord anchor) {
        ModuleInstance module = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            anchor,
            FacilityModuleKind.MINER.defaultShape(),
            ModuleTier.EV);
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
    }

    private static ModuleInstance createModule(FacilityModuleKind kind, StationTileCoord anchor) {
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, anchor, ModuleShape.SINGLE, kind.defaultTier());
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
    }

    private static RecipeConfig config(ItemStack input, ItemStack output) {
        SavedRecipeList recipes = new SavedRecipeList();
        recipes.add(
            new SavedRecipe(
                RecipeSnapshot
                    .resolved((byte) 0, 0, new ItemStack[] { input }, new ItemStack[] { output }, null, null, 100, 32),
                true,
                0L,
                (byte) 0,
                (byte) 1));
        return new RecipeConfig(recipes, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP, (byte) 0, (byte) 0);
    }

    private static ModuleInstance moduleWithUpkeep(FacilityModuleKind kind, StationTileCoord anchor, long itemAmount) {
        ModuleTierData tierData = ModuleTierData.builder()
            .addedEnergyCapacity(0L)
            .powerDraw(0L)
            .cooldown(20)
            .cost(Map.of(new ItemStack(Items.iron_ingot), 1L))
            .upkeepItem(new ItemStack(Items.iron_ingot), itemAmount)
            .build();
        FacilityModuleRegistry.Definition definition = new FacilityModuleRegistry.Definition(
            kind,
            Map.of(ModuleTier.NONE, tierData),
            (module, facility) -> {},
            TestTieredModule::new,
            List.<ModulePanelAction>of(),
            false,
            List.of());
        ModuleInstance module = new ModuleInstance(
            ModuleInstance.ID.create(),
            definition,
            anchor,
            ModuleShape.SINGLE,
            ModuleTier.NONE);
        module.setComponent(new TestTieredModule());
        module.updateStatus(Buildable.Status.OPERATIONAL);
        return module;
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
                        contribution -> contribution.key()
                            .equals(feature))) {
                    return anchor;
                }
            }
        }
        throw new AssertionError("No deterministic feature found for item interaction test: " + feature);
    }

    private static final class TestTieredModule extends TieredModuleComponent {
    }
}
