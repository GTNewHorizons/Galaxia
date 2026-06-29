package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AsteroidOreProfile(String id, double weight, List<String> gtOreVeinIds) {

    public AsteroidOreProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Asteroid ore profile id is required");
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("Asteroid ore profile weight must be finite and positive");
        }
        if (gtOreVeinIds == null) gtOreVeinIds = List.of();
        else gtOreVeinIds = Collections.unmodifiableList(new ArrayList<>(gtOreVeinIds));
    }
}
