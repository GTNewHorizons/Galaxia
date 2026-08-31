package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBookOwner;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeScheduleState;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
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
    void recipeSettingsGroupSharesOneBookAndLeaveCopiesItsCurrentValue() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ModuleInstance first = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance second = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);

        replace(facility, new RecipeBookOwner.Private(first.id), book(RecipeSchedulerMode.ORDER));
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
        RecipeBookOwner.Group groupOwner = new RecipeBookOwner.Group(groupId);

        assertEquals(
            RecipeSchedulerMode.ORDER,
            facility.recipeBook(second)
                .mode());
        replace(facility, groupOwner, book(RecipeSchedulerMode.RANDOM));
        assertSame(facility.recipeBook(first), facility.recipeBook(second));
        assertEquals(
            RecipeSchedulerMode.RANDOM,
            facility.recipeBook(first)
                .mode());

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.LeaveSettingsGroup(facility.assetId, second.id),
                FacilityCommand.Authority.NONE));
        replace(facility, groupOwner, book(RecipeSchedulerMode.PRIORITY));

        assertEquals(
            RecipeSchedulerMode.PRIORITY,
            facility.recipeBook(first)
                .mode());
        assertEquals(
            RecipeSchedulerMode.RANDOM,
            facility.recipeBook(second)
                .mode());
    }

    @Test
    void settingsCopyReplacesTheBookAndResetsTargetScheduleProgress() {
        assumeTrue(FacilityModuleKind.MACERATOR.isAvailable());
        AutomatedFacility facility = createFacility();
        ModuleInstance source = createMachine(StationTileCoord.of(1, 0));
        ModuleInstance target = createMachine(StationTileCoord.of(2, 0));
        facility.addModule(source);
        facility.addModule(target);
        replace(facility, new RecipeBookOwner.Private(source.id), book(RecipeSchedulerMode.ORDER));
        replace(facility, new RecipeBookOwner.Private(target.id), book(RecipeSchedulerMode.RANDOM));
        facility.restoreRecipeScheduleState(target, new RecipeScheduleState((byte) 4, (byte) 2));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.CopyModuleSettings(facility.assetId, source.id, List.of(target.id)),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertEquals(
            RecipeSchedulerMode.ORDER,
            facility.recipeBook(target)
                .mode());
        assertEquals(RecipeScheduleState.RESET, facility.recipeScheduleState(target));
    }

    private static void replace(AutomatedFacility facility, RecipeBookOwner owner, RecipeBook book) {
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.ReplaceRecipeBook(facility.assetId, owner, book),
                FacilityCommand.Authority.NONE));
    }

    private static RecipeBook book(RecipeSchedulerMode mode) {
        return new RecipeBook(List.of(), mode, NotDoablePolicy.SKIP);
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
}
