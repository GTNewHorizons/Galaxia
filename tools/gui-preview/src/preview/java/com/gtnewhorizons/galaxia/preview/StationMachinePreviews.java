package com.gtnewhorizons.galaxia.preview;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerTarget;
import dev.modularui.preview.PreviewEntrypoint;

final class StationMachinePreviews {

    private StationMachinePreviews() {}

    static PreviewEntrypoint controller() {
        return PreviewEntrypoint.of(TileStation.class, context -> {
            PreviewSupport.initializeClient();
            TileStation tile = PreviewSupport.clientTile(new TileStation());
            PreviewSupport.setField(tile, "structureValid", true);
            PreviewSupport.setField(tile, "controllerFlag", TileStation.Role.MAIN);
            return tile.buildUI(null, PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }

    static PreviewEntrypoint hammerTarget() {
        return PreviewEntrypoint.of(TileHammerTarget.class, context -> {
            PreviewSupport.initializeClient();
            TileHammerTarget tile = PreviewSupport.clientTile(new TileHammerTarget());
            PreviewSupport.setField(tile, "structureValid", true);
            return tile.buildUI(null, PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }

    static PreviewEntrypoint hammerCannon() {
        return PreviewEntrypoint.of(TileHammerCannon.class, context -> {
            PreviewSupport.initializeFacilityModules();
            TileHammerCannon tile = PreviewSupport.clientTile(new TileHammerCannon());
            PreviewSupport.setField(tile, "structureValid", true);
            return tile.buildUI(null, PreviewSupport.sync(context), PreviewSupport.settings());
        });
    }
}
