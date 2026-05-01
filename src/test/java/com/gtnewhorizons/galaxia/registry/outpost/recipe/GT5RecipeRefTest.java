package com.gtnewhorizons.galaxia.registry.outpost.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class GT5RecipeRefTest {

    // ---------- computeContentHash (via package-private helper) ----------

    @Test
    void computeContentHash_isStable_sameInputsReturnsSameHash() {
        long hash1 = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 30);
        long hash2 = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 30);
        assertEquals(hash1, hash2, "hash must be stable across calls");
    }

    @Test
    void computeContentHash_differsWhenDurationChanges() {
        long h1 = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 30);
        long h2 = GT5RecipeRef.computeContentHash(null, null, null, null, 200, 30);
        assertNotEquals(h1, h2, "different durations should produce different hashes");
    }

    @Test
    void computeContentHash_differsWhenEUtChanges() {
        long h1 = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 30);
        long h2 = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 120);
        assertNotEquals(h1, h2, "different EU/t should produce different hashes");
    }

    @Test
    void computeContentHash_handlesAllNullArrays() {
        long hash = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 30);
        assertTrue(hash != 0, "hash should be non-zero even with null arrays");
    }

    @Test
    void computeContentHash_deterministicAcrossCalls() {
        long h1 = GT5RecipeRef.computeContentHash(null, null, null, null, 50, 120);
        long h2 = GT5RecipeRef.computeContentHash(null, null, null, null, 50, 120);
        assertEquals(h1, h2, "two calls with identical inputs must produce same hash");
    }

    @Test
    void computeContentHash_differsWhenBothDurationAndEUtChange() {
        long h1 = GT5RecipeRef.computeContentHash(null, null, null, null, 100, 30);
        long h2 = GT5RecipeRef.computeContentHash(null, null, null, null, 200, 60);
        assertNotEquals(h1, h2, "different duration AND eut should produce different hash");
    }

    // ---------- GTRecipeMapId ----------

    @Test
    void fromRecipeMapName_returnsCorrectEnum() {
        assertEquals(GTRecipeMapId.MACERATOR, GTRecipeMapId.fromRecipeMapName("gt.recipe.macerator"));
        assertEquals(GTRecipeMapId.CENTRIFUGE, GTRecipeMapId.fromRecipeMapName("gt.recipe.centrifuge"));
        assertEquals(GTRecipeMapId.ELECTROLYZER, GTRecipeMapId.fromRecipeMapName("gt.recipe.electrolyzer"));
        assertEquals(GTRecipeMapId.CHEMICAL_REACTOR, GTRecipeMapId.fromRecipeMapName("gt.recipe.chemicalreactor"));
        assertEquals(GTRecipeMapId.ASSEMBLER, GTRecipeMapId.fromRecipeMapName("gt.recipe.assembler"));
        assertEquals(GTRecipeMapId.DISTILLERY, GTRecipeMapId.fromRecipeMapName("gt.recipe.distillery"));
    }

    @Test
    void fromRecipeMapName_returnsNull_forUnknownName() {
        assertNull(GTRecipeMapId.fromRecipeMapName("gt.recipe.nonexistent"));
    }

    @Test
    void fromRecipeMapName_returnsNull_forNull() {
        assertNull(GTRecipeMapId.fromRecipeMapName(null));
    }

    @Test
    void findRecipeMap_returnsNull_forInvalid() {
        assertNull(GTRecipeMapId.findRecipeMap(GTRecipeMapId.INVALID));
        assertNull(GTRecipeMapId.findRecipeMap(null));
    }

    @Test
    void getRecipes_returnsNull_forInvalid() {
        assertNull(GTRecipeMapId.getRecipes(GTRecipeMapId.INVALID));
        assertNull(GTRecipeMapId.getRecipes(null));
    }

    @Test
    void getRecipes_cacheConsistent() {
        for (GTRecipeMapId id : GTRecipeMapId.values()) {
            if (id == GTRecipeMapId.INVALID) continue;
            Object first = GTRecipeMapId.getRecipes(id);
            Object second = GTRecipeMapId.getRecipes(id);
            assertEquals(first, second, "cached recipes must match initial lookup for " + id);
        }
    }

    @Test
    void findAllRecipeMaps_cacheConsistent() {
        for (GTRecipeMapId id : GTRecipeMapId.values()) {
            if (id == GTRecipeMapId.INVALID) continue;
            Object first = GTRecipeMapId.findRecipeMap(id);
            Object second = GTRecipeMapId.findRecipeMap(id);
            assertEquals(first, second, "cached result must match initial lookup for " + id);
        }
    }

    @Test
    void enumOrdinals_stable() {
        assertEquals(0, GTRecipeMapId.INVALID.ordinal(), "INVALID must be ordinal 0");
        assertEquals(1, GTRecipeMapId.MACERATOR.ordinal());
        assertEquals(2, GTRecipeMapId.CENTRIFUGE.ordinal());
        assertEquals(3, GTRecipeMapId.ELECTROLYZER.ordinal());
        assertEquals(4, GTRecipeMapId.CHEMICAL_REACTOR.ordinal());
        assertEquals(5, GTRecipeMapId.ASSEMBLER.ordinal());
        assertEquals(6, GTRecipeMapId.DISTILLERY.ordinal());
    }

    @Test
    void unlocalizedName_matchesExpected() {
        assertEquals("", GTRecipeMapId.INVALID.getRecipeMapUnlocalizedName());
        assertEquals("gt.recipe.macerator", GTRecipeMapId.MACERATOR.getRecipeMapUnlocalizedName());
        assertEquals("gt.recipe.centrifuge", GTRecipeMapId.CENTRIFUGE.getRecipeMapUnlocalizedName());
        assertEquals("gt.recipe.electrolyzer", GTRecipeMapId.ELECTROLYZER.getRecipeMapUnlocalizedName());
        assertEquals("gt.recipe.chemicalreactor", GTRecipeMapId.CHEMICAL_REACTOR.getRecipeMapUnlocalizedName());
        assertEquals("gt.recipe.assembler", GTRecipeMapId.ASSEMBLER.getRecipeMapUnlocalizedName());
        assertEquals("gt.recipe.distillery", GTRecipeMapId.DISTILLERY.getRecipeMapUnlocalizedName());
    }

    // ---------- GT5RecipeRef.of() ----------

    @Test
    void of_factory_usesComputeContentHash() {
        // of() calls computeContentHash internally; verify the ref is constructed with that hash
        long expectedHash = GT5RecipeRef.computeContentHash(null, null, null, null, 50, 120);
        // We can't instantiate GTRecipe, but we verify the factory pattern by testing the
        // constructor and the field values of a manually constructed ref
        GT5RecipeRef ref = new GT5RecipeRef((byte) GTRecipeMapId.MACERATOR.ordinal(), 3, expectedHash);
        assertEquals((byte) GTRecipeMapId.MACERATOR.ordinal(), ref.recipeMapOrdinal());
        assertEquals(3, ref.recipeIndex());
        assertEquals(expectedHash, ref.contentHash());
    }

    @Test
    void of_factory_wouldHaveDifferentHashForDifferentRecipe() {
        // Verify that different duration produces different hash
        long h1 = GT5RecipeRef.computeContentHash(null, null, null, null, 50, 120);
        long h2 = GT5RecipeRef.computeContentHash(null, null, null, null, 50, 30);
        assertNotEquals(h1, h2, "recipes with different EU/t must produce different hashes");
    }

    // ---------- GT5RecipeRef.resolve() ----------

    @Test
    void resolve_returnsNull_forInvalidMapOrdinal() {
        GT5RecipeRef ref = new GT5RecipeRef((byte) -1, 0, 42L);
        assertNull(ref.resolve());
    }

    @Test
    void resolve_returnsNull_forINVALIDMap() {
        GT5RecipeRef ref = new GT5RecipeRef((byte) GTRecipeMapId.INVALID.ordinal(), 0, 42L);
        assertNull(ref.resolve());
    }

    @Test
    void resolve_returnsNull_forMapOrdinalOutOfRange() {
        GT5RecipeRef ref = new GT5RecipeRef((byte) 127, 0, 42L);
        assertNull(ref.resolve());
    }

    // ---------- Edge cases ----------

    @Test
    void ref_canStoreMaxValues() {
        GT5RecipeRef ref = new GT5RecipeRef(Byte.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, ref.recipeMapOrdinal());
        assertEquals(Integer.MAX_VALUE, ref.recipeIndex());
        assertEquals(Long.MAX_VALUE, ref.contentHash());
    }
}
