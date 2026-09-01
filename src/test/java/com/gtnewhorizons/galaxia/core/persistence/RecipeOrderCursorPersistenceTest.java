package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.core.state.AssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialServerRuntime;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeBook;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

/**
 * Tests that a recipe book and its independent ORDER schedule persist through a save/load round trip.
 */
final class RecipeOrderCursorPersistenceTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    @Test
    void orderCursorAndRemainingSurviveRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager(CelestialServerRuntime.create());
        AutomatedFacility station = new AutomatedFacility(
            ASSET_ID,
            CelestialObjectId.MARS,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        ModuleInstance macerator = createMaceratorWithOrderConfig(station);

        // Save
        NBTTagCompound encoded = AssetState.encode(new UUID(0L, 1L), station);

        // Load
        AutomatedFacility decoded = (AutomatedFacility) AssetState.decode(encoded)
            .asset();

        // Verify
        assertEquals(
            1,
            decoded.modules()
                .size());
        ModuleInstance loaded = decoded.modules()
            .get(0);
        assertTrue(loaded.component() instanceof IRecipeModule);

        RecipeBook loadedBook = decoded.recipeBook(loaded);
        assertEquals(RecipeSchedulerMode.ORDER, loadedBook.mode(), "ORDER mode must survive");
        assertEquals(
            new RecipeBook.ScheduleState((byte) 1, (byte) 3),
            decoded.recipeScheduleState(loaded),
            "schedule state must survive independently of the recipe book");

        // Verify recipe slot content survived
        assertEquals(
            3,
            loadedBook.recipes()
                .size(),
            "3 slots must survive");
        // Spot-check first slot's fields
        SavedRecipe firstSlot = loadedBook.recipes()
            .get(0);
        assertTrue(firstSlot.enabled(), "slot 0 enabled must survive");
        assertEquals(0L, firstSlot.requestAmount(), "slot 0 requestAmount must survive empty");
        assertEquals((byte) 5, firstSlot.priority(), "slot 0 priority must survive");
        assertEquals((byte) 2, firstSlot.orderSize(), "slot 0 orderSize must survive");

        // Verify other module state survived
        assertEquals(FacilityModuleKind.MACERATOR, loaded.kind(), "module kind must survive");
        assertEquals(ModuleTier.HV, loaded.tier(), "module tier must survive");
        assertTrue(loaded.enabled(), "enabled state must survive");
        assertEquals(StationTileCoord.of(1, 0), loaded.anchor(), "anchor must survive");
    }

    private static ModuleInstance createMaceratorWithOrderConfig(AutomatedFacility station) {
        ModuleInstance macerator = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MACERATOR,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.HV);
        macerator.updateStatus(Buildable.Status.OPERATIONAL);
        station.stationLayout()
            .place(macerator);
        station.addModule(macerator);

        RecipeBook book = new RecipeBook(
            List.of(recipe(0, true, 5, 2), recipe(1, true, 3, 4), recipe(2, false, 1, 1)),
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP);
        assertSame(
            FacilityCommand.Result.CHANGED,
            station.applyCommand(
                new FacilityCommand.ReplaceRecipeBook(
                    station.assetId,
                    new RecipeBook.Owner.Private(macerator.id),
                    book),
                FacilityCommand.Authority.NONE));
        station.restoreRecipeScheduleState(macerator, new RecipeBook.ScheduleState((byte) 1, (byte) 3));

        return macerator;
    }

    private static SavedRecipe recipe(int index, boolean enabled, int priority, int orderSize) {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) 1,
            index,
            new ItemStack[] { new ItemStack(Items.iron_ingot, 1, 0) },
            new ItemStack[] { new ItemStack(Items.diamond, index + 1, 0) },
            null,
            null,
            20 + index,
            30 + index);
        return new SavedRecipe(snapshot, enabled, 0L, (byte) priority, (byte) orderSize);
    }
}
