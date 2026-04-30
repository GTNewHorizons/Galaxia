package com.gtnewhorizons.galaxia.registry.outpost.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

final class LayoutCacheBundleTest {

    @Test
    void affectedBy_returnsValidEnumSet_forAllCombinations() {
        for (MutationKind mutation : MutationKind.values()) {
            for (FacilityModuleKind kind : FacilityModuleKind.values()) {
                EnumSet<CacheKind> result = LayoutCacheBundle.affectedBy(mutation, kind);
                assertNotNull(result, "result must not be null for " + mutation + " x " + kind);
                switch (mutation) {
                    case PLACE, DECONSTRUCT -> {
                        assertTrue(
                            result.contains(CacheKind.DUPLICATE_COUNTS),
                            () -> "PLACE/DECONSTRUCT should contain DUPLICATE_COUNTS for " + mutation + " x " + kind);
                        if (kind.isCapacityModule()) {
                            assertTrue(
                                result.contains(CacheKind.CAPACITY_CLUSTERS),
                                () -> "PLACE/DECONSTRUCT should contain CAPACITY_CLUSTERS for capacity kind " + mutation
                                    + " x "
                                    + kind);
                        } else {
                            assertTrue(
                                !result.contains(CacheKind.CAPACITY_CLUSTERS),
                                () -> "PLACE/DECONSTRUCT should NOT contain CAPACITY_CLUSTERS for non-capacity kind "
                                    + mutation
                                    + " x "
                                    + kind);
                        }
                        if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                            assertTrue(
                                result.contains(CacheKind.MAINTENANCE_COVERAGE),
                                () -> "PLACE/DECONSTRUCT should contain MAINTENANCE_COVERAGE for MAINTENANCE_BAY "
                                    + mutation
                                    + " x "
                                    + kind);
                        } else {
                            assertTrue(
                                !result.contains(CacheKind.MAINTENANCE_COVERAGE),
                                () -> "PLACE/DECONSTRUCT should NOT contain MAINTENANCE_COVERAGE for non-MAINTENANCE_BAY "
                                    + mutation
                                    + " x "
                                    + kind);
                        }
                    }
                    case SET_TIER -> {
                        if (kind.isCapacityModule()) {
                            assertTrue(
                                result.contains(CacheKind.CAPACITY_CLUSTERS),
                                () -> "SET_TIER should contain CAPACITY_CLUSTERS for capacity kind " + kind);
                        } else {
                            assertTrue(
                                result.isEmpty(),
                                () -> "SET_TIER should return empty set for non-capacity kind " + kind
                                    + " but got "
                                    + result);
                        }
                    }
                    case SET_ENABLED -> {
                        if (kind == FacilityModuleKind.MAINTENANCE_BAY) {
                            assertTrue(
                                result.contains(CacheKind.MAINTENANCE_COVERAGE),
                                () -> "SET_ENABLED should contain MAINTENANCE_COVERAGE for MAINTENANCE_BAY " + kind);
                        } else {
                            assertTrue(
                                result.isEmpty(),
                                () -> "SET_ENABLED should return empty set for " + kind + " but got " + result);
                        }
                    }
                    case SET_PARALLEL -> assertTrue(
                        result.isEmpty(),
                        () -> mutation + " should return empty set for " + kind + " but got " + result);
                }
            }
        }
    }

    @Test
    void duplicateCountNeverGoesBelowZero() {
        LayoutCacheBundle bundle = new LayoutCacheBundle(null);

        // PLACE increments
        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        assertEquals(2, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        // DECONSTRUCT decrements
        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        // Extra DECONSTRUCT must not go below zero
        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.STORAGE));
    }

    @Test
    void duplicateCountsAreIndependentPerKind() {
        LayoutCacheBundle bundle = new LayoutCacheBundle(null);

        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.TANK);

        assertEquals(2, bundle.duplicateCount(FacilityModuleKind.STORAGE));
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.TANK));
        assertEquals(0, bundle.duplicateCount(FacilityModuleKind.BATTERY));

        bundle.applyMutation(MutationKind.DECONSTRUCT, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.TANK));
    }

    @Test
    void setTierDoesNotAffectDuplicateCounts() {
        LayoutCacheBundle bundle = new LayoutCacheBundle(null);

        bundle.applyMutation(MutationKind.PLACE, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));

        // SET_TIER should not change duplicate counts
        bundle.applyMutation(MutationKind.SET_TIER, FacilityModuleKind.STORAGE);
        assertEquals(1, bundle.duplicateCount(FacilityModuleKind.STORAGE));
    }
}
