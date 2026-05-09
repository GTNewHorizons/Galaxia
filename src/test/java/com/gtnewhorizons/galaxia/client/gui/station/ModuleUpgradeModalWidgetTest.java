package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
