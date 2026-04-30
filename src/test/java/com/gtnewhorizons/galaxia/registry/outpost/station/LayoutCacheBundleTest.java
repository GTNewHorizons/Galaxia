package com.gtnewhorizons.galaxia.registry.outpost.station;

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
                    case PLACE, DECONSTRUCT -> assertTrue(
                        result.contains(CacheKind.DUPLICATE_COUNTS),
                        () -> "PLACE/DECONSTRUCT should contain DUPLICATE_COUNTS for " + mutation + " x " + kind);
                    case SET_TIER, SET_PARALLEL, SET_ENABLED -> assertTrue(
                        result.isEmpty(),
                        () -> mutation + " should return empty set for " + kind + " but got " + result);
                }
            }
        }
    }
}
