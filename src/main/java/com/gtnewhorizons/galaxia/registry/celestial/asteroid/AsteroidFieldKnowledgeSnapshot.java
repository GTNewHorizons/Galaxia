package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public record AsteroidFieldKnowledgeSnapshot(@Nonnull CelestialObjectId beltId, @Nonnull List<Entry> entries) {

    public AsteroidFieldKnowledgeSnapshot {
        List<Entry> copiedEntries = new ArrayList<>(entries == null ? List.of() : entries);
        for (Entry entry : copiedEntries) {
            if (entry == null) {
                throw new IllegalArgumentException("entry cannot be null");
            }
        }
        entries = Collections.unmodifiableList(copiedEntries);
    }

    public record Entry(int index, @Nonnull DiscoveryState detectionState,
        @Nonnull CelestialResourceKnowledgeState oreKnowledgeState) {

        public Entry {
            if (index < 0) throw new IllegalArgumentException("index must be non-negative");
        }
    }
}
