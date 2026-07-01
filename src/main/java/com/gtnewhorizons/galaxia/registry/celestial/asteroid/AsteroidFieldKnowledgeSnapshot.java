package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

public record AsteroidFieldKnowledgeSnapshot(CelestialObjectId beltId, List<Entry> entries) {

    public AsteroidFieldKnowledgeSnapshot {
        beltId = Objects.requireNonNull(beltId, "beltId cannot be null");
        List<Entry> copiedEntries = new ArrayList<>(entries == null ? List.of() : entries);
        for (Entry entry : copiedEntries) {
            Objects.requireNonNull(entry, "entry cannot be null");
        }
        entries = Collections.unmodifiableList(copiedEntries);
    }

    public record Entry(int index, AsteroidDetectionState detectionState, AsteroidOreKnowledgeState oreKnowledgeState) {

        public Entry {
            if (index < 0) throw new IllegalArgumentException("index must be non-negative");
            detectionState = Objects.requireNonNull(detectionState, "detectionState cannot be null");
            oreKnowledgeState = Objects.requireNonNull(oreKnowledgeState, "oreKnowledgeState cannot be null");
        }
    }
}
