package com.gtnewhorizons.galaxia.core.network;

public record CelestialKnowledgeSyncType(String id) {

    public CelestialKnowledgeSyncType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("sync type id is required");
    }
}
