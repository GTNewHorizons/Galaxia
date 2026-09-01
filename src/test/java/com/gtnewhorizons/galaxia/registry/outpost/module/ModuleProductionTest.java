package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class ModuleProductionTest {

    @BeforeAll
    static void initRegistry() {
        GalaxiaTestBootstrap.ensureFacilityModules();
    }

    // ---------- allowedTiers / defaultTier ----------

    @Test
    void allowedTiers_hvEvIv() {
        EnumSet<ModuleTier> expected = EnumSet.of(ModuleTier.HV, ModuleTier.EV, ModuleTier.IV);
        assertEquals(expected, FacilityModuleKind.CENTRIFUGE.allowedTiers());
        assertEquals(expected, FacilityModuleKind.ELECTROLYZER.allowedTiers());
        assertEquals(expected, FacilityModuleKind.CHEMICAL_REACTOR.allowedTiers());
        assertEquals(expected, FacilityModuleKind.ASSEMBLER.allowedTiers());
        assertEquals(expected, FacilityModuleKind.DISTILLERY.allowedTiers());
    }

    @Test
    void defaultTier_hv() {
        assertEquals(ModuleTier.HV, FacilityModuleKind.CENTRIFUGE.defaultTier());
        assertEquals(ModuleTier.HV, FacilityModuleKind.ELECTROLYZER.defaultTier());
        assertEquals(ModuleTier.HV, FacilityModuleKind.CHEMICAL_REACTOR.defaultTier());
        assertEquals(ModuleTier.HV, FacilityModuleKind.ASSEMBLER.defaultTier());
        assertEquals(ModuleTier.HV, FacilityModuleKind.DISTILLERY.defaultTier());
    }

    @Test
    void notCapacityModules() {
        assertFalse(FacilityModuleKind.CENTRIFUGE.isCapacityModule());
        assertFalse(FacilityModuleKind.ELECTROLYZER.isCapacityModule());
        assertFalse(FacilityModuleKind.CHEMICAL_REACTOR.isCapacityModule());
        assertFalse(FacilityModuleKind.ASSEMBLER.isCapacityModule());
        assertFalse(FacilityModuleKind.DISTILLERY.isCapacityModule());
    }

    @Test
    void recipeDefinitionsOwnMapMetadataAndSharedRuntime() {
        assertRecipe(FacilityModuleKind.CENTRIFUGE, "gt.recipe.centrifuge");
        assertRecipe(FacilityModuleKind.ELECTROLYZER, "gt.recipe.electrolyzer");
        assertRecipe(FacilityModuleKind.CHEMICAL_REACTOR, "gt.recipe.chemicalreactor");
        assertRecipe(FacilityModuleKind.ASSEMBLER, "gt.recipe.assembler");
        assertRecipe(FacilityModuleKind.DISTILLERY, "gt.recipe.distillery");
    }

    private static void assertRecipe(FacilityModuleKind kind, String mapName) {
        ModuleInstance instance = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), kind, null, ModuleShape.SINGLE, ModuleTier.HV);
        assertEquals(
            mapName,
            instance.recipe()
                .mapName());
        assertEquals((byte) 1, ((IParallelModule) instance.component()).getParallel());
    }
}
