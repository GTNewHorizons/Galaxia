package com.gtnewhorizons.galaxia.api;

import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.MICROGRAVITY;
import static com.gtnewhorizons.galaxia.api.RecipeEnvironments.NO_ATMOSPHERE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

import gregtech.api.recipe.RecipeEnvironment;

class RecipeEnvironmentsTest {

    @BeforeAll
    static void bootstrap() {
        GalaxiaTestBootstrap.ensureMinecraft();
        RecipeEnvironments.register();
    }

    @Test
    void gravityAndPressureAreIndependentPhysicalConditions() {
        var vacuumWithGravity = new RecipeEnvironments.Conditions(1, 0);
        assertTrue(vacuumWithGravity.isVacuum());
        assertFalse(vacuumWithGravity.isMicrogravity());
        var pressurizedMicrogravity = new RecipeEnvironments.Conditions(0, 101325);
        assertTrue(pressurizedMicrogravity.isMicrogravity());
        assertFalse(pressurizedMicrogravity.isVacuum());
    }

    @Test
    void recipeThresholdsExcludeResidualPressureAndTheGravityBoundary() {
        assertTrue(new RecipeEnvironments.Conditions(0.009999, 0).isMicrogravity());
        assertFalse(new RecipeEnvironments.Conditions(0.01, 0).isMicrogravity());
        assertFalse(new RecipeEnvironments.Conditions(0, 0.001).isVacuum());
        assertThrows(IllegalArgumentException.class, () -> new RecipeEnvironments.Conditions(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RecipeEnvironments.Conditions(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new RecipeEnvironments.Conditions(Double.NaN, 0));
        assertThrows(
            IllegalArgumentException.class,
            () -> new RecipeEnvironments.Conditions(0, Double.POSITIVE_INFINITY));
    }

    @Test
    void nativeRequirementsAreRegisteredAndUnknownWorldHasNoConditions() {
        assertSame(MICROGRAVITY, RecipeEnvironment.get("galaxia:microgravity"));
        assertSame(NO_ATMOSPHERE, RecipeEnvironment.get("galaxia:no_atmosphere"));
        assertTrue(MICROGRAVITY.requiresContinuousValidation());
        assertTrue(NO_ATMOSPHERE.requiresContinuousValidation());
        assertNull(RecipeEnvironments.at(null, 0, 0, 0));
    }
}
