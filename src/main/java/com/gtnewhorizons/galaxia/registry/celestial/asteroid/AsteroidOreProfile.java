package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AsteroidOreProfile(String id, List<String> gtOreVeinIds) {

    public AsteroidOreProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Asteroid ore profile id is required");
        }
        if (gtOreVeinIds == null) gtOreVeinIds = List.of();
        else gtOreVeinIds = Collections.unmodifiableList(new ArrayList<>(gtOreVeinIds));
    }
}
