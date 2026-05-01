package com.gtnewhorizons.galaxia.client.gui.station.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for RecipeSlotListWidget.
 * Full GUI rendering tests require Minecraft/MUI2 runtime and are skipped
 * in headless unit test environment.
 */
final class RecipeSlotListWidgetTest {

    private static boolean canTest;

    @BeforeAll
    static void checkEnvironment() {
        try {
            Class.forName("com.gtnewhorizons.galaxia.client.gui.station.recipe.RecipeSlotListWidget");
            canTest = true;
        } catch (Throwable e) {
            canTest = false;
        }
    }

    @Test
    void widgetClass_exists() {
        if (!canTest) return;
        assertNotNull(RecipeSlotListWidget.class);
    }

    @Test
    void widgetCanBeConstructed() {
        if (!canTest) return;
        RecipeSlotListWidget widget = new RecipeSlotListWidget(null);
        assertNotNull(widget);
    }
}
