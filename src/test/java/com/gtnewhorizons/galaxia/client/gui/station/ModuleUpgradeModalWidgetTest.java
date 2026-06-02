package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ModuleUpgradeModalWidgetTest {

    @Test
    void footerAndFlagControlsDoNotOverlap() {
        List<ModuleUpgradeModalWidget.ControlRect> rects = ModuleUpgradeModalWidget.controlRectsForTest();

        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                ModuleUpgradeModalWidget.ControlRect left = rects.get(i);
                ModuleUpgradeModalWidget.ControlRect right = rects.get(j);
                assertFalse(left.overlaps(right), left.name() + " overlaps " + right.name());
            }
        }
    }

    @Test
    void hammerOptionGridAndFlagsHaveVisibleGap() {
        ModuleUpgradeModalWidget.ControlRect lastOptionRow = ModuleUpgradeModalWidget.optionRectForTest(1, 4);
        ModuleUpgradeModalWidget.ControlRect reserve = ModuleUpgradeModalWidget.controlRectsForTest()
            .stream()
            .filter(
                rect -> rect.name()
                    .equals("reserve"))
            .findFirst()
            .orElseThrow();

        assertTrue(reserve.y() - lastOptionRow.bottom() >= ModuleUpgradeModalWidget.CONTROL_GAP_FOR_TEST);
    }

    @Test
    void optionGroupsUseOneRowPerCategory() {
        ModuleUpgradeModalWidget.ControlRect firstTier = ModuleUpgradeModalWidget.optionRectForTest(0, 0);
        ModuleUpgradeModalWidget.ControlRect lastTier = ModuleUpgradeModalWidget.optionRectForTest(0, 2);
        ModuleUpgradeModalWidget.ControlRect firstFocus = ModuleUpgradeModalWidget.optionRectForTest(1, 0);
        ModuleUpgradeModalWidget.ControlRect lastFocus = ModuleUpgradeModalWidget.optionRectForTest(1, 3);

        assertTrue(firstTier.y() == lastTier.y());
        assertTrue(firstFocus.y() == lastFocus.y());
        assertTrue(firstFocus.y() > firstTier.y());
    }
}
