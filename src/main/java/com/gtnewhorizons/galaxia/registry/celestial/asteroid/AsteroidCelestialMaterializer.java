package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public final class AsteroidCelestialMaterializer {

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

        return restoredOrGeneratedCatalog(minorId.parentBodyId(), profile).resolve(minorId)
            .map(AsteroidInitialKnowledgeRules::initialFacts);
    }

    private static Optional<CelestialObject> resolveMinorBody(MinorCelestialBodyId minorId, CelestialObject belt) {
        AsteroidFieldProfile profile = belt.properties()
            .asteroidFieldProfile();
        if (profile == null) return Optional.empty();

        return restoredOrGeneratedCatalog(minorId.parentBodyId(), profile).resolve(minorId)
            .map(node -> materialize(node, profile));
    }

    public static CelestialObject materialize(AsteroidFieldNode node, AsteroidFieldProfile profile) {
        double radius = node.orbitSlot()
            .radiusBetween(profile.innerOrbitalRadius(), profile.outerOrbitalRadius());
        double spriteSize = switch (node.sizeClass()) {
            case LARGE -> 0.04;
            case MEDIUM -> 0.02;
            case SMALL -> 0.01;
        };
        double soiRadius = switch (node.sizeClass()) {
            case LARGE -> 180.0;
            case MEDIUM -> 120.0;
            case SMALL -> 80.0;
        };

        return CelestialObject.builder()
            .id(CelestialObjectKey.minorBody(node.id()))
            .name(node.displayName())
            .parent(CelestialObjectKey.registered(node.beltId()))
            .objectClass(CelestialObject.Class.ASTEROID)
            .circularOrbit(
                radius,
                0.00091,
                Math.toRadians(
                    node.orbitSlot()
                        .angleOffsetDeg()))
            .texture(EnumTextures.ICON_MOON.get())
            .spriteSize(spriteSize)
            .properties(
                b -> b.orbitalGravity(6.0e4, soiRadius)
                    .visitable(true)
                    .canCreateStation(false)
                    .canCreateOutpost(true)
                    .temperature(41)
                    .radiation(0.52)
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

    public static List<CelestialObject> knownChildren(@Nonnull CelestialObjectKey parentId,
        @Nonnull CelestialDiscoveryView discoveryView, boolean includeHidden,
        @Nonnull Function<CelestialObjectId, Optional<CelestialObject>> beltLookup) {

        if (parentId == null || !parentId.isRegistered()) return List.of();
        return beltLookup.apply(parentId.registeredBodyId())
            .map(parent -> knownChildren(parentId.registeredBodyId(), parent, discoveryView, includeHidden))
            .orElseGet(List::of);
    }

    private static List<CelestialObject> knownChildren(CelestialObjectId parentId, CelestialObject parent,
        CelestialDiscoveryView discoveryView, boolean includeHidden) {

        AsteroidFieldProfile profile = parent.properties()
            .asteroidFieldProfile();
        if (profile == null) return List.of();

        return restoredOrGeneratedCatalog(parentId, profile).nodes()
            .stream()
            .filter(node -> includeHidden || isVisible(node, discoveryView))
            .map(node -> materialize(node, profile))
            .toList();
    }

    public static AsteroidFieldNodeCatalog restoredOrGeneratedCatalog(CelestialObjectId beltId,
        AsteroidFieldProfile profile) {

        return AsteroidFieldNodeCatalog.restored(beltId)
            .orElseGet(() -> AsteroidFieldNodeCatalog.fromGenerated(beltId, profile));
    }

    private static boolean isVisible(AsteroidFieldNode node, CelestialDiscoveryView discoveryView) {
        DiscoveryState initialState = AsteroidFieldResolver.initialDetectionState(node);
        CelestialDiscoveryView view = discoveryView == null ? CelestialDiscoveryView.empty() : discoveryView;
        return view.isVisible(CelestialObjectKey.minorBody(node.id()), initialState);
    }
}
