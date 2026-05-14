package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

final class ModuleRecipeSettingsGroupTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void recipeSettingsGroupSharesAndCopiesConfigOnLeave() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ModuleInstance first = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance second = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);
        assertNotEquals(0, first.groupId());
        assertNotEquals(0, second.groupId());

        facility.setRecipeConfig(first, config(RecipeSchedulerMode.ORDER));
        SettingsGroup group = facility.createSettingsGroupForModule(first, "Dust line");
        facility.assignSettingsGroup(second, group.id());

        assertEquals(
            RecipeSchedulerMode.ORDER,
            recipeModule(second).getRecipeConfig()
                .mode());

        facility.setRecipeConfig(second, config(RecipeSchedulerMode.RANDOM));

        assertEquals(
            RecipeSchedulerMode.RANDOM,
            recipeModule(first).getRecipeConfig()
                .mode());

        facility.leaveSettingsGroup(second);
        facility.setRecipeConfig(first, config(RecipeSchedulerMode.PRIORITY));

        assertEquals(
            RecipeSchedulerMode.PRIORITY,
            recipeModule(first).getRecipeConfig()
                .mode());
        assertEquals(
            RecipeSchedulerMode.RANDOM,
            recipeModule(second).getRecipeConfig()
                .mode());
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
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

    private static IRecipeModule recipeModule(ModuleInstance module) {
        assertNotNull(module.component());
        return (IRecipeModule) module.component();
    }

    private static RecipeConfig config(RecipeSchedulerMode mode) {
        return new RecipeConfig(new SavedRecipeList(), mode, NotDoablePolicy.SKIP, (byte) 0, (byte) 0);
    }
}
