package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public record AsteroidFieldKnowledgeSnapshot(@Nonnull CelestialObjectId beltId, @Nonnull List<Entry> entries,
    @Nonnull List<AsteroidFieldNodeSnapshot> nodeSnapshots) {

    public AsteroidFieldKnowledgeSnapshot {
        List<Entry> copiedEntries = new ArrayList<>(entries == null ? List.of() : entries);
        Set<Integer> entryIndexes = new HashSet<>();
        for (Entry entry : copiedEntries) {
            if (entry == null) {
                throw new IllegalArgumentException("entry cannot be null");
            }
            if (!entryIndexes.add(entry.index())) {
                throw new IllegalStateException("duplicate asteroid knowledge entry index: " + entry.index());
            }
        }
        entries = Collections.unmodifiableList(copiedEntries);
        List<AsteroidFieldNodeSnapshot> copiedNodes = new ArrayList<>(
            nodeSnapshots == null ? List.of() : nodeSnapshots);
        Set<Integer> nodeIndexes = new HashSet<>();
        for (AsteroidFieldNodeSnapshot nodeSnapshot : copiedNodes) {
            if (nodeSnapshot == null) {
                throw new IllegalArgumentException("nodeSnapshot cannot be null");
            }
            if (!nodeIndexes.add(nodeSnapshot.index())) {
                throw new IllegalStateException("duplicate asteroid node snapshot index: " + nodeSnapshot.index());
            }
        }
        nodeSnapshots = Collections.unmodifiableList(copiedNodes);
    }

    public record Entry(int index, @Nonnull DiscoveryState detectionState,
        @Nonnull CelestialResourceKnowledgeState oreKnowledgeState) {

        public Entry {
            if (index < 0) throw new IllegalArgumentException("index must be non-negative");
            if (detectionState == DiscoveryState.HIDDEN
                && oreKnowledgeState != CelestialResourceKnowledgeState.UNKNOWN) {
                throw new IllegalStateException("hidden asteroid snapshot entry cannot expose ore knowledge: " + index);
            }
        }
    }
}
