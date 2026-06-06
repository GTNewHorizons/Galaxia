package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.Set;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

public record CapacityCluster(FacilityModuleKind kind, Set<StationTileCoord> members, long effectiveCapacity) {}
