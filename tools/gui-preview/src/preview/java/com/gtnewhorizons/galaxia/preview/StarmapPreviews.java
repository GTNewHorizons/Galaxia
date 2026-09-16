package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import dev.modularui.preview.PreviewEntrypoint;

final class StarmapPreviews {

    private StarmapPreviews() {}

    static PreviewEntrypoint overview() {
        return PreviewEntrypoint.of(GalacticMapWidget.class, context -> {
            PreviewSupport.initializeStarmap();
            return GalacticMapWidget.build(PreviewSupport.sync(context), PreviewSupport.player());
        });
    }
}
