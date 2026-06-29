package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Objects;

public final class AsteroidFieldStarmapView {

    private AsteroidFieldStarmapView() {}

    public static List<AsteroidFieldStarmapEntry> visibleEntries(AsteroidFieldKnowledge knowledge) {
        Objects.requireNonNull(knowledge, "knowledge");
        return knowledge.nodes()
            .stream()
            .filter(
                node -> knowledge.entryFor(node.id())
                    .detectionState() == AsteroidDetectionState.DETECTED)
            .map(node -> AsteroidFieldStarmapEntry.from(node, knowledge.entryFor(node.id())))
            .toList();
    }
}
