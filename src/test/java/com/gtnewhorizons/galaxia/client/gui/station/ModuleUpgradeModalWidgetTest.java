package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ModuleUpgradeModalWidgetTest {

    @Test
    void footerAndFlagControlsDoNotOverlap() {
        List<ModuleUpgradeModalWidget.ControlRect> rects = ModuleUpgradeModalWidget.controlRects();

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
        ModuleUpgradeModalWidget.ControlRect lastOptionRow = ModuleUpgradeModalWidget.optionRect(1, 4);
        ModuleUpgradeModalWidget.ControlRect reserve = ModuleUpgradeModalWidget.control("reserve");

        assertTrue(reserve.y() - lastOptionRow.bottom() >= ModuleUpgradeModalWidget.CONTROL_GAP);
    }

    @Test
    void optionGroupsUseOneRowPerCategory() {
        ModuleUpgradeModalWidget.ControlRect firstTier = ModuleUpgradeModalWidget.optionRect(0, 0);
        ModuleUpgradeModalWidget.ControlRect lastTier = ModuleUpgradeModalWidget.optionRect(0, 2);
        ModuleUpgradeModalWidget.ControlRect firstFocus = ModuleUpgradeModalWidget.optionRect(1, 0);
        ModuleUpgradeModalWidget.ControlRect lastFocus = ModuleUpgradeModalWidget.optionRect(1, 3);

        assertTrue(firstTier.y() == lastTier.y());
        assertTrue(firstFocus.y() == lastFocus.y());
        assertTrue(firstFocus.y() > firstTier.y());
    }
}
