package com.gtnewhorizons.galaxia.registry.outpost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product and integration contracts for canonical recipe-book ownership and replacement commands. */
final class RecipeBookOwnershipCommandTest {

    private static final RecipeBook.ScheduleState RESET_SCHEDULE = new RecipeBook.ScheduleState((byte) 0, (byte) 0);

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void privateAndGroupOwnersExposeOneCanonicalBookWhileSchedulesRemainPerModule() {
        AutomatedFacility facility = facility();
        ModuleInstance first = addMacerator(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance second = addMacerator(facility, moduleId(2), StationTileCoord.of(4, 0));
        RecipeBook.Owner.Private firstPrivate = new RecipeBook.Owner.Private(first.id);

        assertSame(facility.recipeBook(firstPrivate), facility.recipeBook(first));

        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, first.id, "Shared macerators"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID groupId = ((ModuleInstance.SettingsBinding.Shared) first.settingsBinding()).groupId();
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.SetSettingsGroup(facility.assetId, second.id, groupId),
                FacilityCommand.Authority.NONE));
        RecipeBook.Owner.Group groupOwner = new RecipeBook.Owner.Group(groupId);

        assertSame(facility.recipeBook(groupOwner), facility.recipeBook(first));
        assertSame(facility.recipeBook(groupOwner), facility.recipeBook(second));

        RecipeBook.ScheduleState firstSchedule = new RecipeBook.ScheduleState((byte) 1, (byte) 2);
        RecipeBook.ScheduleState secondSchedule = new RecipeBook.ScheduleState((byte) 3, (byte) 4);
        facility.restoreRecipeScheduleState(first, firstSchedule);
        facility.restoreRecipeScheduleState(second, secondSchedule);

        assertEquals(firstSchedule, facility.recipeScheduleState(first));
        assertEquals(secondSchedule, facility.recipeScheduleState(second));
    }

    @Test
    void acceptedGroupReplacementIsAtomicAndResetsAllMemberSchedules() {
        AutomatedFacility facility = facility();
        ModuleInstance first = addMacerator(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance second = addMacerator(facility, moduleId(2), StationTileCoord.of(4, 0));
        SettingsGroup.ID groupId = createGroupWithMember(facility, first, second);
        RecipeBook.Owner.Group owner = new RecipeBook.Owner.Group(groupId);
        facility.restoreRecipeScheduleState(first, new RecipeBook.ScheduleState((byte) 1, (byte) 2));
        facility.restoreRecipeScheduleState(second, new RecipeBook.ScheduleState((byte) 3, (byte) 4));
        RecipeBook replacement = book("Replacement", 1);

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, owner, replacement),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertEquals(replacement, facility.recipeBook(owner));
        assertSame(facility.recipeBook(owner), facility.recipeBook(first));
        assertSame(facility.recipeBook(owner), facility.recipeBook(second));
        assertEquals(RESET_SCHEDULE, facility.recipeScheduleState(first));
        assertEquals(RESET_SCHEDULE, facility.recipeScheduleState(second));
    }

    @Test
    void validEqualReplacementIsStillChangedAndResetsSchedule() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addMacerator(facility, moduleId(1), StationTileCoord.of(1, 0));
        RecipeBook.Owner.Private owner = new RecipeBook.Owner.Private(module.id);
        RecipeBook replacement = book("Same", 1);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.ReplaceRecipeBook(facility.assetId, owner, replacement),
                FacilityCommand.Authority.NONE));
        facility.restoreRecipeScheduleState(module, new RecipeBook.ScheduleState((byte) 2, (byte) 3));

        FacilityCommand.Result result = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, owner, replacement),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, result);
        assertEquals(replacement, facility.recipeBook(owner));
        assertEquals(RESET_SCHEDULE, facility.recipeScheduleState(module));
    }

    @Test
    void stalePrivateAndMissingGroupOwnersAreRejectedWithoutMutationOrRedirect() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addMacerator(facility, moduleId(1), StationTileCoord.of(1, 0));
        RecipeBook.Owner.Private stalePrivate = new RecipeBook.Owner.Private(module.id);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, module.id, "Shared macerators"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID actualGroupId = ((ModuleInstance.SettingsBinding.Shared) module.settingsBinding()).groupId();
        RecipeBook.Owner.Group actualOwner = new RecipeBook.Owner.Group(actualGroupId);
        RecipeBook before = facility.recipeBook(actualOwner);
        RecipeBook.ScheduleState scheduleBefore = new RecipeBook.ScheduleState((byte) 2, (byte) 2);
        facility.restoreRecipeScheduleState(module, scheduleBefore);

        FacilityCommand.Result staleResult = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, stalePrivate, book("Stale private", 2)),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result missingGroupResult = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(
                facility.assetId,
                new RecipeBook.Owner.Group(new SettingsGroup.ID(999)),
                book("Missing group", 3)),
            FacilityCommand.Authority.NONE);

        assertEquals(FacilityCommand.Status.REJECTED, staleResult.status());
        assertEquals(FacilityCommand.Status.REJECTED, missingGroupResult.status());
        assertEquals(before, facility.recipeBook(actualOwner));
        assertEquals(scheduleBefore, facility.recipeScheduleState(module));
    }

    @Test
    void deletedGroupIdIsNotReusedByAnotherGroupInTheSameFacilityLifetime() {
        AutomatedFacility facility = facility();
        ModuleInstance first = addMacerator(facility, moduleId(1), StationTileCoord.of(1, 0));
        ModuleInstance second = addMacerator(facility, moduleId(2), StationTileCoord.of(4, 0));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, first.id, "First group"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID deletedGroupId = ((ModuleInstance.SettingsBinding.Shared) first.settingsBinding()).groupId();
        RecipeBook.Owner.Group staleOwner = new RecipeBook.Owner.Group(deletedGroupId);
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.SetSettingsGroup(facility.assetId, first.id, null),
                FacilityCommand.Authority.NONE));
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, second.id, "Second group"),
                FacilityCommand.Authority.NONE));

        SettingsGroup.ID currentGroupId = ((ModuleInstance.SettingsBinding.Shared) second.settingsBinding()).groupId();
        RecipeBook.Owner.Group currentOwner = new RecipeBook.Owner.Group(currentGroupId);
        RecipeBook currentBook = facility.recipeBook(currentOwner);
        RecipeBook.ScheduleState currentSchedule = new RecipeBook.ScheduleState((byte) 2, (byte) 3);
        facility.restoreRecipeScheduleState(second, currentSchedule);

        FacilityCommand.Result staleResult = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, staleOwner, book("Stale owner", 4)),
            FacilityCommand.Authority.NONE);

        assertNotEquals(deletedGroupId, currentGroupId);
        assertEquals(FacilityCommand.Status.REJECTED, staleResult.status());
        assertEquals(currentBook, facility.recipeBook(currentOwner));
        assertEquals(currentSchedule, facility.recipeScheduleState(second));
    }

    @Test
    void acceptedCompleteReplacementsExecuteInServerOrderAndLastBookWins() {
        AutomatedFacility facility = facility();
        ModuleInstance module = addMacerator(facility, moduleId(1), StationTileCoord.of(1, 0));
        RecipeBook.Owner.Private owner = new RecipeBook.Owner.Private(module.id);
        RecipeBook first = book("First", 1);
        RecipeBook second = book("Second", 2);
        assertNotEquals(first, second);

        FacilityCommand.Result firstResult = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, owner, first),
            FacilityCommand.Authority.NONE);
        FacilityCommand.Result secondResult = facility.applyCommand(
            new FacilityCommand.ReplaceRecipeBook(facility.assetId, owner, second),
            FacilityCommand.Authority.NONE);

        assertSame(FacilityCommand.Result.CHANGED, firstResult);
        assertSame(FacilityCommand.Result.CHANGED, secondResult);
        assertEquals(second, facility.recipeBook(owner));
    }

    private static SettingsGroup.ID createGroupWithMember(AutomatedFacility facility, ModuleInstance first,
        ModuleInstance second) {
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.CreateSettingsGroup(facility.assetId, first.id, "Shared macerators"),
                FacilityCommand.Authority.NONE));
        SettingsGroup.ID groupId = ((ModuleInstance.SettingsBinding.Shared) first.settingsBinding()).groupId();
        assertSame(
            FacilityCommand.Result.CHANGED,
            facility.applyCommand(
                new FacilityCommand.SetSettingsGroup(facility.assetId, second.id, groupId),
                FacilityCommand.Authority.NONE));
        return groupId;
    }

    private static RecipeBook book(String name, int recipeIndex) {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            recipeIndex,
            new ItemStack[] { new ItemStack(new Item(), 1, 0) },
            new ItemStack[] { new ItemStack(new Item(), recipeIndex + 1, 0) },
            null,
            null,
            100,
            32);
        SavedRecipe recipe = new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1, name);
        return new RecipeBook(List.of(recipe), RecipeSchedulerMode.ORDER, NotDoablePolicy.SKIP);
    }

    private static AutomatedFacility facility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance addMacerator(AutomatedFacility facility, ModuleInstance.ID moduleId,
        StationTileCoord anchor) {
        FacilityModuleKind kind = FacilityModuleKind.MACERATOR;
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
