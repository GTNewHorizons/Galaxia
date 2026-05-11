package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        if (target == null) return false;
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

    static List<StationTileCoord> confirmedTargets(AutomatedFacility facility, ModuleInstance source,
        ModuleTier targetTier, @Nullable HammerVariant targetHammerVariant, List<StationTileCoord> selectedCoords) {
        List<StationTileCoord> targets = new ArrayList<>();
        if (facility == null || source == null || selectedCoords == null || selectedCoords.isEmpty()) return targets;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return targets;

        Set<ModuleInstance.ID> seenModules = new HashSet<>();
        for (StationTileCoord coord : selectedCoords) {
            if (coord == null) continue;
            ModuleInstance target = layout.moduleAt(coord);
            if (target == null || !seenModules.add(target.id)) continue;
            if (!isCompatibleTarget(facility, source, targetTier, targetHammerVariant, coord)) continue;
            targets.add(target.anchor());
        }
        return targets;
    }
}
