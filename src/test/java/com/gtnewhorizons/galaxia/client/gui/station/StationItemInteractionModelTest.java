package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.LogisticsResourceConfig;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
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
            .filter(entry -> entry.groupId() == group.id())
            .findFirst()
            .orElseThrow();
        assertEquals(2, upkeep.count());
        assertTrue(
            upkeep.amountPerMinute()
                .microUnitsPerMinute() > 0);
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PROXIMA_CENTAURI,
            CelestialAsset.Kind.AUTOMATED_STATION,
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
        ModuleInstance module = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.HAMMER, anchor, ModuleShape.SINGLE, ModuleTier.EV);
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
}
