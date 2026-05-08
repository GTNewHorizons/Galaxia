package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleUpgradePickerModel {

    private ModuleUpgradePickerModel() {}

    static StationTileCoord normalizeTarget(AutomatedFacility facility, StationTileCoord coord) {
        if (facility == null || coord == null) return coord;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return coord;
        ModuleInstance module = layout.moduleAt(coord);
        return module == null ? coord : module.anchor();
    }

    static boolean isCompatibleTarget(AutomatedFacility facility, ModuleInstance source, ModuleTier targetTier,
        @Nullable HammerVariant targetHammerVariant, StationTileCoord coord) {
        if (facility == null || source == null || targetTier == null || coord == null) return false;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return false;
        ModuleInstance target = layout.moduleAt(coord);
        if (target == null || source.id.equals(target.id)) return false;
        if (source.kind() != target.kind()) return false;
        ModuleOperationState operation = target.operationOrNull();
        if (operation != null && !operation.phase()
            .isTerminal()) {
            return false;
        }
        if (source.component() instanceof ModuleHammer) {
            if (!(target.component() instanceof ModuleHammer targetHammer)) return false;
            if (targetHammerVariant == null || !ModuleHammer.supportsTier(targetHammerVariant, targetTier)) {
                return false;
            }
            return targetHammer.variant() != targetHammerVariant || target.tier() != targetTier;
        }
        if (targetHammerVariant != null || !target.kind()
            .allowedTiers()
            .contains(targetTier)) {
            return false;
        }
        return target.tier() != targetTier;
    }
}
