package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationLayout;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleSettingsCopyPickerModel {

    private ModuleSettingsCopyPickerModel() {}

    static StationTileCoord normalizeTarget(AutomatedFacility facility, StationTileCoord coord) {
        if (facility == null || coord == null) return coord;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return coord;
        ModuleInstance module = layout.moduleAt(coord);
        return module == null ? coord : module.anchor();
    }

    static boolean isCompatibleTarget(AutomatedFacility facility, ModuleInstance source, StationTileCoord coord) {
        if (facility == null || source == null || coord == null) return false;
        if (!FacilityModuleRegistry.get(source.kind())
            .settingsGroups()) return false;
        StationLayout layout = facility.stationLayout();
        if (layout == null) return false;
        ModuleInstance target = layout.moduleAt(coord);
        if (target == null || source.id.equals(target.id) || source.kind() != target.kind()) return false;
        if (!FacilityModuleRegistry.get(target.kind())
            .settingsGroups()) return false;
        if (source.component() instanceof ModuleMiner sourceMiner
            && target.component() instanceof ModuleMiner targetMiner) {
            return sourceMiner.focusOreKeyOrNull() == null || targetMiner.focusTier() != MinerFocusTier.NONE;
        }
        return true;
    }
}
