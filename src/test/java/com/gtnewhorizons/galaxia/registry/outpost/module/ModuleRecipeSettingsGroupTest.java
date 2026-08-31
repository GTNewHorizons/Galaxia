package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipeList;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleRecipeSettingsGroupTest {

    @BeforeAll
    static void initRegistries() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void recipeSettingsGroupSharesAndCopiesConfigOnLeave() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ModuleInstance first = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance second = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);

        facility.setRecipeConfig(first, config(RecipeSchedulerMode.ORDER));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, first.id, "Dust line"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID groupId = facility.moduleSettingsSnapshot()
            .membership()
            .get(first.id);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.JoinSettingsGroup(facility.assetId, second.id, groupId),
                FacilityCommand.Authority.NONE));

        assertEquals(
            RecipeSchedulerMode.ORDER,
            recipeModule(second).getRecipeConfig()
                .mode());

        facility.setRecipeConfig(second, config(RecipeSchedulerMode.RANDOM));

        assertEquals(
            RecipeSchedulerMode.RANDOM,
            recipeModule(first).getRecipeConfig()
                .mode());

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.LeaveSettingsGroup(facility.assetId, second.id),
                FacilityCommand.Authority.NONE));
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

    @Test
    void settingsCopyDoesNotCopyRecipeScheduleProgress() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ModuleInstance source = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance target = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(source);
        facility.addModule(target);
        facility.setRecipeConfig(source, config(RecipeSchedulerMode.ORDER));
        recipeModule(target).setRecipeConfig(config(RecipeSchedulerMode.RANDOM, (byte) 4, (byte) 2));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        RecipeConfig copied = recipeModule(target).getRecipeConfig();
        assertEquals(RecipeSchedulerMode.ORDER, copied.mode());
        assertEquals(4, copied.orderCursor());
        assertEquals(2, copied.orderRemaining());
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
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
        return config(mode, (byte) 0, (byte) 0);
    }

    private static RecipeConfig config(RecipeSchedulerMode mode, byte orderCursor, byte orderRemaining) {
        return new RecipeConfig(new SavedRecipeList(), mode, NotDoablePolicy.SKIP, orderCursor, orderRemaining);
    }
}
