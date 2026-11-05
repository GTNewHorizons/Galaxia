package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

public record AsteroidAppearanceProfile(String iconRecipeId, long variantSeed) {

    public AsteroidAppearanceProfile {
        if (iconRecipeId == null || iconRecipeId.isBlank()) {
            throw new IllegalArgumentException("Asteroid icon recipe id is required");
        }
    }
}
