package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

public final class AsteroidOreProfiles {

    public static final AsteroidOreProfile METALLIC = new AsteroidOreProfile("metallic", List.of("ore.mix.iron"));
    public static final AsteroidOreProfile VOLATILE_ICE = new AsteroidOreProfile("volatile_ice", List.of("ore.mix.lapis"));
    public static final AsteroidOreProfile RARE_CRYSTAL = new AsteroidOreProfile(
        "rare_crystal",
        List.of("ore.mix.redstone"));

    private static final Map<String, AsteroidOreProfile> BY_ID = Stream.of(METALLIC, VOLATILE_ICE, RARE_CRYSTAL)
        .collect(Collectors.toUnmodifiableMap(AsteroidOreProfile::id, profile -> profile));

    private AsteroidOreProfiles() {}

    public static AsteroidOreProfile require(@Nonnull String id) {
        AsteroidOreProfile profile = BY_ID.get(id);
        if (profile == null) throw new IllegalStateException("Unknown asteroid ore profile: " + id);
        return profile;
    }

    public static List<AsteroidOreProfile> all() {
        return List.copyOf(BY_ID.values());
    }
}
