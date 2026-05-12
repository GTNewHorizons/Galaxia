package com.gtnewhorizons.galaxia.core.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.NotDoablePolicy;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSchedulerMode;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeSnapshot;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.SavedRecipe;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

/**
 * Tests that ORDER mode cursor state (orderCursor, orderRemaining) persists
 * through save/load round-trip.
 */
final class RecipeOrderCursorPersistenceTest {

    private static final CelestialAsset.ID ASSET_ID = CelestialAsset.ID.create();

    @BeforeAll
    static void init() {
        com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void orderCursorAndRemainingSurviveRoundTrip() throws Exception {
        FacilityPersistenceManager manager = new FacilityPersistenceManager();
        AutomatedFacility station = new AutomatedFacility(
            ASSET_ID,
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        ModuleInstance macerator = createMaceratorWithOrderConfig(station);

        // Save
        FacilityPersistenceManager.FacilityStateJson encoded = manager.encodeFacilityState(station);

        // Load
        AutomatedFacility decoded = new AutomatedFacility(
            ASSET_ID,
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        manager.decodeFacilityState(decoded, encoded);

        // Verify
        assertEquals(
            1,
            decoded.modules()
                .size());
        ModuleInstance loaded = decoded.modules()
            .get(0);
        assertTrue(loaded.component() instanceof IRecipeModule);

        IRecipeModule recipeModule = (IRecipeModule) loaded.component();
        assertNotNull(recipeModule.getRecipeConfig(), "RecipeConfig must survive round-trip");
        assertEquals(
            RecipeSchedulerMode.ORDER,
            recipeModule.getRecipeConfig()
                .mode(),
            "ORDER mode must survive");
        assertEquals(
            (byte) 1,
            recipeModule.getRecipeConfig()
                .orderCursor(),
            "orderCursor must survive");
        assertEquals(
            (byte) 3,
            recipeModule.getRecipeConfig()
                .orderRemaining(),
            "orderRemaining must survive");

        // Verify recipe slot content survived
        RecipeConfig loadedConfig = recipeModule.getRecipeConfig();
        assertEquals(
            3,
            loadedConfig.savedRecipes()
                .size(),
            "3 slots must survive");
        // Spot-check first slot's fields
        SavedRecipe firstSlot = loadedConfig.savedRecipes()
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

        // Create RecipeConfig with ORDER mode, 3 slots, cursor=1, remaining=3
        RecipeConfig config = RecipeConfig.empty();
        // Config is in PRIORITY mode by default — change to ORDER
        config = new RecipeConfig(
            config.savedRecipes(),
            RecipeSchedulerMode.ORDER,
            NotDoablePolicy.SKIP,
            (byte) 1,
            (byte) 3);

        // Add 3 recipe slots
        SavedRecipe slot1 = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 0, 42L), true, 0L, (byte) 5, (byte) 2);
        SavedRecipe slot2 = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 1, 43L), true, 0L, (byte) 3, (byte) 4);
        SavedRecipe slot3 = new SavedRecipe(RecipeSnapshot.unresolved((byte) 1, 2, 44L), false, 0L, (byte) 1, (byte) 1);
        config.savedRecipes()
            .add(slot1);
        config.savedRecipes()
            .add(slot2);
        config.savedRecipes()
            .add(slot3);

        // Set config via IRecipeModule
        ((IRecipeModule) macerator.component()).setRecipeConfig(config);

        return macerator;
    }
}
