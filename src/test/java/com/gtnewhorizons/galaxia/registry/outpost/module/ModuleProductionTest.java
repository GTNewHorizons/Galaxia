package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleAssembler;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleCentrifuge;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleChemicalReactor;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleDistillery;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleElectrolyzer;

final class ModuleProductionTest {

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

    // ---------- Construction ----------

    @Test
    void centrifugeConstruction() {
        ModuleCentrifuge m = new ModuleCentrifuge();
        assertEquals((byte) 1, m.getParallel());
        assertNull(m.getRecipeConfig());
    }

    @Test
    void electrolyzerConstruction() {
        ModuleElectrolyzer m = new ModuleElectrolyzer();
        assertEquals((byte) 1, m.getParallel());
        assertNull(m.getRecipeConfig());
    }

    @Test
    void chemicalReactorConstruction() {
        ModuleChemicalReactor m = new ModuleChemicalReactor();
        assertEquals((byte) 1, m.getParallel());
        assertNull(m.getRecipeConfig());
    }

    @Test
    void assemblerConstruction() {
        ModuleAssembler m = new ModuleAssembler();
        assertEquals((byte) 1, m.getParallel());
        assertNull(m.getRecipeConfig());
    }

    @Test
    void distilleryConstruction() {
        ModuleDistillery m = new ModuleDistillery();
        assertEquals((byte) 1, m.getParallel());
        assertNull(m.getRecipeConfig());
    }

    // ---------- getRecipeMapName ----------

    @Test
    void getRecipeMapName_centrifuge() {
        ModuleCentrifuge m = new ModuleCentrifuge();
        assertEquals("gt.recipe.centrifuge", m.getRecipeMapName());
    }

    @Test
    void getRecipeMapName_electrolyzer() {
        ModuleElectrolyzer m = new ModuleElectrolyzer();
        assertEquals("gt.recipe.electrolyzer", m.getRecipeMapName());
    }

    @Test
    void getRecipeMapName_chemicalReactor() {
        ModuleChemicalReactor m = new ModuleChemicalReactor();
        assertEquals("gt.recipe.chemicalreactor", m.getRecipeMapName());
    }

    @Test
    void getRecipeMapName_assembler() {
        ModuleAssembler m = new ModuleAssembler();
        assertEquals("gt.recipe.assembler", m.getRecipeMapName());
    }

    @Test
    void getRecipeMapName_distillery() {
        ModuleDistillery m = new ModuleDistillery();
        assertEquals("gt.recipe.distillery", m.getRecipeMapName());
    }
}
