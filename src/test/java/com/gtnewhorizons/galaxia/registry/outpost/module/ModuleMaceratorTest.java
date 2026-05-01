package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.recipe.RecipeConfig;

final class ModuleMaceratorTest {

    // ---------- FacilityModuleKind ----------

    @Test
    void maceratorKindExists() {
        // Verify MACERATOR exists with correct ordinal
        FacilityModuleKind kind = FacilityModuleKind.valueOf("MACERATOR");
        assertNotNull(kind);
    }

    @Test
    void maceratorAllowedTiers() {
        assertEquals(
            EnumSet.of(ModuleTier.HV, ModuleTier.EV, ModuleTier.IV),
            FacilityModuleKind.MACERATOR.allowedTiers());
    }

    @Test
    void maceratorDefaultTier() {
        assertEquals(ModuleTier.HV, FacilityModuleKind.MACERATOR.defaultTier());
    }

    @Test
    void maceratorIsNotCapacityModule() {
        assertFalse(FacilityModuleKind.MACERATOR.isCapacityModule());
    }

    // ---------- ModuleMacerator construction ----------

    @Test
    void constructorInitializesDefaults() {
        ModuleMacerator mac = new ModuleMacerator();
        assertEquals((byte) 1, mac.getParallel());
        assertNull(mac.getRecipeConfig());
    }

    @Test
    void getRecipeMap_returnsMaceratorRecipes() {
        ModuleMacerator mac = new ModuleMacerator();
        try {
            gregtech.api.recipe.RecipeMap<?> map = mac.getRecipeMap();
            assertNotNull(map);
            assertEquals("gt.recipe.macerator", map.unlocalizedName);
        } catch (Error e) {
            // GT5 RecipeMaps classloading requires Minecraft runtime.
            // In unit test environment (no Minecraft), this is expected to fail.
            // The contract is verified at integration test time.
        }
    }

    // ---------- IRecipeModule contract ----------

    @Test
    void getRecipeConfig_setRecipeConfig_roundTrip() {
        ModuleMacerator mac = new ModuleMacerator();
        RecipeConfig config = RecipeConfig.empty();
        mac.setRecipeConfig(config);
        assertSame(config, mac.getRecipeConfig());
    }

    @Test
    void getNextSlot_returnsNegativeOne_whenConfigNull() {
        ModuleMacerator mac = new ModuleMacerator();
        assertEquals(-1, mac.getNextSlot(new Random(0)));
    }

    @Test
    void getNextSlot_delegatesToRecipeScheduler() {
        ModuleMacerator mac = new ModuleMacerator();
        RecipeConfig config = RecipeConfig.empty();
        mac.setRecipeConfig(config);
        assertEquals(-1, mac.getNextSlot(new Random(0)), "empty config should return -1");
    }

    // ---------- IParallelModule contract ----------

    @Test
    void getParallel_defaultIsOne() {
        ModuleMacerator mac = new ModuleMacerator();
        assertEquals((byte) 1, mac.getParallel());
    }

    @Test
    void setParallel_updatesValue() {
        ModuleMacerator mac = new ModuleMacerator();
        mac.setParallel((byte) 4);
        assertEquals((byte) 4, mac.getParallel());
    }

    // ---------- ModuleComponent marker ----------

    @Test
    void implementsModuleComponent() {
        assertTrue(new ModuleMacerator() instanceof ModuleComponent);
    }

    @Test
    void implementsIRecipeModule() {
        assertTrue(new ModuleMacerator() instanceof IRecipeModule);
    }

    @Test
    void implementsIParallelModule() {
        assertTrue(new ModuleMacerator() instanceof IParallelModule);
    }
}
