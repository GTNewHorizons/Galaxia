package com.gtnewhorizons.galaxia.client.gui.station.layer;

import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;

final class ConnectorRoutePolicy {

    private ConnectorRoutePolicy() {}

    static boolean hasModuleConnector(PlacedTile a, PlacedTile b) {
        return a != null && b != null && !sameModule(a, b);
    }

    @Nullable
    static FacilityModuleKind capacityConnectorKind(PlacedTile a, PlacedTile b) {
        if (!hasModuleConnector(a, b)) return null;
        FacilityModuleKind kindA = moduleKindOf(a);
        FacilityModuleKind kindB = moduleKindOf(b);
        if (kindA == null || kindB == null) return null;
        if (!kindA.isCapacityModule() || !kindB.isCapacityModule()) return null;
        return kindA == kindB ? kindA : null;
    }

    @Nullable
    static FacilityModuleKind moduleKindOf(PlacedTile tile) {
        if (tile == null) return null;
        ModuleInstance module = tile.module();
        return module == null ? null : module.kind();
    }

    private static boolean sameModule(PlacedTile a, PlacedTile b) {
        ModuleInstance moduleA = a.module();
        ModuleInstance moduleB = b.module();
        return moduleA != null && moduleB != null && moduleA.id.equals(moduleB.id);
    }
}
