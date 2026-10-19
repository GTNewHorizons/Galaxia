package com.gtnewhorizons.galaxia.preview;

import java.util.function.Supplier;

import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerCannon;
import com.gtnewhorizons.galaxia.registry.celestial.station.attachments.TileHammerTarget;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileAirPurifier;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileCoolingCoil;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileOxygenator;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileStationDevice;
import com.gtnewhorizons.galaxia.registry.celestial.station.devices.TileWitherBlocker;
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

    static PreviewEntrypoint oxygenator() {
        return device(TileOxygenator.class, TileOxygenator::new);
    }

    static PreviewEntrypoint airPurifier() {
        return device(TileAirPurifier.class, TileAirPurifier::new);
    }

    static PreviewEntrypoint witherBlocker() {
        return device(TileWitherBlocker.class, TileWitherBlocker::new);
    }

    static PreviewEntrypoint coolingCoil() {
        return device(TileCoolingCoil.class, TileCoolingCoil::new);
    }

    private static <T extends TileStationDevice> PreviewEntrypoint device(Class<T> type, Supplier<T> factory) {
        return PreviewEntrypoint.of(type, context -> {
            PreviewSupport.initializeClient();
            T tile = PreviewSupport.clientTile(factory.get());
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
