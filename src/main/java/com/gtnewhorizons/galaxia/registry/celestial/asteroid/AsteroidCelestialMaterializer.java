package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeClientState.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

public final class AsteroidCelestialMaterializer {

    private static final double LARGE_SPRITE_SIZE = 0.04;
    private static final double MEDIUM_SPRITE_SIZE = 0.02;
    private static final double SMALL_SPRITE_SIZE = 0.01;
    private static final double LARGE_SPHERE_OF_INFLUENCE_RADIUS = 180.0;
    private static final double MEDIUM_SPHERE_OF_INFLUENCE_RADIUS = 120.0;
    private static final double SMALL_SPHERE_OF_INFLUENCE_RADIUS = 80.0;
    private static final double ORBIT_SPEED = 0.00091;
    private static final double STANDARD_GRAVITATIONAL_PARAMETER = 6.0e4;
    private static final int TEMPERATURE = 41;
    private static final double RADIATION = 0.52;

    private AsteroidCelestialMaterializer() {}

    public static Optional<CelestialObject> resolveMinorBody(@Nonnull CelestialObjectKey key,
        @Nonnull Function<CelestialObjectId, Optional<CelestialObject>> beltLookup) {

        if (!key.isMinorBody()) return Optional.empty();

        MinorCelestialBodyId minorId = key.minorBodyId();
        return beltLookup.apply(minorId.parentBodyId())
            .flatMap(belt -> resolveMinorBody(minorId, belt));
    }

    /**
     * Definition-default facts for a minor body, resolved from its asteroid content
     * node. Returns empty when the key is not a resolvable minor body so callers can
     * fail loudly at the registry boundary.
     */
    public static Optional<CelestialKnowledgeFacts> initialKnowledge(@Nonnull CelestialObjectKey key,
        @Nonnull Function<CelestialObjectId, Optional<CelestialObject>> beltLookup) {

        if (!key.isMinorBody()) return Optional.empty();

        MinorCelestialBodyId minorId = key.minorBodyId();
        return beltLookup.apply(minorId.parentBodyId())
            .flatMap(belt -> resolveNodeFacts(minorId, belt));
    }

    private static Optional<CelestialKnowledgeFacts> resolveNodeFacts(MinorCelestialBodyId minorId,
        CelestialObject belt) {
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) return Optional.empty();

        return AsteroidFieldResolver.findNode(minorId.parentBodyId(), profile, minorId.index())
            .map(AsteroidFieldResolver::initialFacts);
    }

    private static Optional<CelestialObject> resolveMinorBody(MinorCelestialBodyId minorId, CelestialObject belt) {
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) return Optional.empty();

        return AsteroidFieldResolver.findNode(minorId.parentBodyId(), profile, minorId.index())
            .map(node -> materialize(node, profile));
    }

    public static CelestialObject materialize(AsteroidFieldNode node, AsteroidFieldProfile profile) {
        double radius = node.orbitSlot()
            .radiusBetween(profile.innerOrbitalRadius(), profile.outerOrbitalRadius());
        double spriteSize = switch (node.sizeClass()) {
            case LARGE -> LARGE_SPRITE_SIZE;
            case MEDIUM -> MEDIUM_SPRITE_SIZE;
            case SMALL -> SMALL_SPRITE_SIZE;
        };
        double soiRadius = switch (node.sizeClass()) {
            case LARGE -> LARGE_SPHERE_OF_INFLUENCE_RADIUS;
            case MEDIUM -> MEDIUM_SPHERE_OF_INFLUENCE_RADIUS;
            case SMALL -> SMALL_SPHERE_OF_INFLUENCE_RADIUS;
        };

        return CelestialObject.builder()
            .key(CelestialObjectKey.minorBody(node.id()))
            .name(node.displayName())
            .parent(CelestialObjectKey.registered(node.beltId()))
            .objectClass(CelestialObject.Class.ASTEROID)
            .circularOrbit(
                radius,
                ORBIT_SPEED,
                Math.toRadians(
                    node.orbitSlot()
                        .angleOffsetDeg()))
            .texture(EnumTextures.ICON_MOON.get())
            .spriteSize(spriteSize)
            .properties(
                b -> b.orbitalGravity(STANDARD_GRAVITATIONAL_PARAMETER, soiRadius)
                    .visitable(true)
                    .canCreateStation(false)
                    .canCreateOutpost(true)
                    .temperature(TEMPERATURE)
                    .radiation(RADIATION)
                    .oreProfile(
                        node.oreProfile()
                            .id())
                    .gtOreVeinIds(
                        node.oreProfile()
                            .gtOreVeinIds()
                            .toArray(new String[0]))
                    .asteroidMetadata(node.kind(), node.sizeClass())
                    .metadata("surface", "undefined"))
            .build();
    }

    public static List<CelestialObject> knownChildren(@Nonnull CelestialObjectKey parentKey,
        @Nonnull CelestialDiscoveryView discoveryView, boolean includeHidden,
        @Nonnull Function<CelestialObjectId, Optional<CelestialObject>> beltLookup) {

        if (parentKey == null || !parentKey.isRegistered()) return List.of();
        return beltLookup.apply(parentKey.registeredBodyId())
            .map(parent -> knownChildren(parentKey.registeredBodyId(), parent, discoveryView, includeHidden))
            .orElseGet(List::of);
    }

    private static List<CelestialObject> knownChildren(CelestialObjectId parentId, CelestialObject parent,
        CelestialDiscoveryView discoveryView, boolean includeHidden) {

        AsteroidFieldProfile profile = parent.properties()
            .asteroidFieldProfile();
        if (profile == null) return List.of();

        return AsteroidFieldResolver.resolveAll(parentId, profile)
            .stream()
            .filter(node -> includeHidden || isVisible(node, discoveryView))
            .map(node -> materialize(node, profile))
            .toList();
    }

    private static boolean isVisible(AsteroidFieldNode node, CelestialDiscoveryView discoveryView) {
        DiscoveryState initialState = node.initialDetectionState();
        CelestialDiscoveryView view = discoveryView == null ? CelestialDiscoveryView.empty() : discoveryView;
        return view.isVisible(CelestialObjectKey.minorBody(node.id()), initialState);
    }
}
