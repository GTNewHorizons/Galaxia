package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/** Product contract for detached recipe-book editing. */
final class RecipeBookEditorModelTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void editsStayDetachedUntilReplacementIsBuilt() {
        SavedRecipe first = savedRecipe(0, "First");
        SavedRecipe second = savedRecipe(1, "Second");
        RecipeBook source = new RecipeBook(List.of(first, second), RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP);
        RecipeBook.Owner owner = new RecipeBook.Owner.Private(moduleId(1));
        RecipeBookEditorModel editor = RecipeBookEditorModel.edit(owner, source);

        assertThrows(
            UnsupportedOperationException.class,
            () -> editor.recipes()
                .clear());
        assertEquals(0, editor.selectedIndex());
        assertTrue(editor.rename(0, "  Renamed  "));
        assertTrue(
            editor.update(
                0,
                withEnabled(
                    editor.recipes()
                        .get(0),
                    false)));
        assertTrue(editor.add(snapshot(2)));
        editor.cycleMode();
        editor.cycleNotDoablePolicy();

        assertEquals(List.of(first, second), source.recipes());
        assertEquals(RecipeSchedulerMode.PRIORITY, source.mode());
        assertEquals(NotDoablePolicy.SKIP, source.notDoablePolicy());

        RecipeBook replacement = editor.replacement();
        assertEquals(owner, editor.owner());
        assertEquals(
            3,
            replacement.recipes()
                .size());
        assertEquals(
            "Renamed",
            replacement.recipes()
                .get(0)
                .displayName());
        assertFalse(
            replacement.recipes()
                .get(0)
                .enabled());
        SavedRecipe added = replacement.recipes()
            .get(2);
        assertTrue(added.enabled());
        assertEquals(0L, added.requestAmount());
        assertEquals((byte) 1, added.priority());
        assertEquals((byte) 1, added.orderSize());
        assertEquals("", added.displayName());
        assertEquals(RecipeSchedulerMode.ORDER, replacement.mode());
        assertEquals(NotDoablePolicy.BACK_TO_BEGINNING, replacement.notDoablePolicy());
    }

    @Test
    void fullBookRejectsAdditionalRecipes() {
        List<SavedRecipe> recipes = new ArrayList<>();
        for (int i = 0; i < RecipeBook.MAX_RECIPES; i++) recipes.add(savedRecipe(i, "Recipe " + i));
        RecipeBookEditorModel editor = RecipeBookEditorModel.edit(
            new RecipeBook.Owner.Private(moduleId(1)),
            new RecipeBook(recipes, RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP));

        assertFalse(editor.canAdd());
        assertFalse(editor.add(snapshot(RecipeBook.MAX_RECIPES)));
        assertEquals(
            RecipeBook.MAX_RECIPES,
            editor.recipes()
                .size());
    }

    @Test
    void selectionTracksRemovalsAndEmptyToNonEmptyTransition() {
        RecipeBook source = new RecipeBook(
            List.of(savedRecipe(0, "First"), savedRecipe(1, "Second"), savedRecipe(2, "Third")),
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP);
        RecipeBookEditorModel editor = RecipeBookEditorModel.edit(new RecipeBook.Owner.Private(moduleId(1)), source);

        assertTrue(editor.select(2));
        assertTrue(editor.remove(0));
        assertEquals(1, editor.selectedIndex());
        assertEquals(
            "Third",
            editor.selectedRecipe()
                .displayName());

        assertTrue(editor.remove(1));
        assertEquals(0, editor.selectedIndex());
        assertEquals(
            "Second",
            editor.selectedRecipe()
                .displayName());

        assertTrue(editor.remove(0));
        assertEquals(-1, editor.selectedIndex());
        assertTrue(editor.add(snapshot(3)));
        assertEquals(0, editor.selectedIndex());
    }

    @Test
    void renameSupportsClearingAndInvalidIndexesDoNothing() {
        RecipeBookEditorModel editor = RecipeBookEditorModel.edit(
            new RecipeBook.Owner.Private(moduleId(1)),
            new RecipeBook(List.of(savedRecipe(0, "Current")), RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP));

        assertTrue(editor.rename(0, "   "));
        assertEquals(
            "",
            editor.recipes()
                .get(0)
                .displayName());
        assertFalse(editor.rename(2, "Missing"));
        assertFalse(editor.update(-1, savedRecipe(4, "Invalid")));
        assertFalse(editor.remove(2));
        assertFalse(editor.select(2));
    }

    @Test
    void draftAdditionDoesNotPredictIntoAuthoritativeFacilityState() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);
        FacilityModuleKind kind = FacilityModuleKind.MACERATOR;
        ModuleInstance module = FacilityModuleRegistry
            .create(moduleId(1), kind, StationTileCoord.of(1, 0), kind.defaultShape(), kind.defaultTier());
        module.completeConstruction();
        facility.addModule(module);
        facility.stationLayout()
            .place(module);
        RecipeBook source = facility.recipeBook(module);
        RecipeBookEditorModel editor = RecipeBookEditorModel.edit(facility.recipeBookOwner(module), source);

        assertTrue(editor.add(snapshot(4)));

        assertTrue(
            source.recipes()
                .isEmpty());
        assertTrue(
            facility.recipeBook(module)
                .recipes()
                .isEmpty());
        assertEquals(
            1,
            editor.recipes()
                .size());
    }

    private static SavedRecipe withEnabled(SavedRecipe recipe, boolean enabled) {
        return new SavedRecipe(
            recipe.recipe(),
            enabled,
            recipe.requestAmount(),
            recipe.priority(),
            recipe.orderSize(),
            recipe.displayName());
    }

    private static SavedRecipe savedRecipe(int index, String name) {
        return new SavedRecipe(snapshot(index), true, 0L, (byte) 1, (byte) 1, name);
    }

    private static RecipeSnapshot snapshot(int index) {
        return RecipeSnapshot.resolved(
            (byte) 1,
            index,
            new ItemStack[] { new ItemStack(Items.diamond, 1, 0) },
            new ItemStack[] { new ItemStack(Items.diamond, index + 1, 0) },
            null,
            null,
            100,
            32);
    }

    private static ModuleInstance.ID moduleId(long value) {
        return new ModuleInstance.ID(new UUID(0L, value));
    }
}
