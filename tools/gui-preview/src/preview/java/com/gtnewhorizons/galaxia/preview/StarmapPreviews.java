package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticChartGui;
import dev.modularui.preview.PreviewEntrypoint;

final class StarmapPreviews {

    private StarmapPreviews() {}

    static PreviewEntrypoint overview() {
        return PreviewEntrypoint.of(GalacticChartGui.class, context -> {
            PreviewSupport.initializeStarmap();
            return new GalacticChartGui().build(PreviewSupport.sync(context), PreviewSupport.player());
        });
    }
}
