package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.DynamicCelestialObjectProvider;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryView;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public final class AsteroidDynamicCelestialObjectProvider implements DynamicCelestialObjectProvider {

    private final Function<CelestialObjectKey, Optional<CelestialObject>> registeredObjects;

    public AsteroidDynamicCelestialObjectProvider(
        @Nonnull Function<CelestialObjectKey, Optional<CelestialObject>> registeredObjects) {
        if (registeredObjects == null) throw new IllegalArgumentException("registeredObjects is required");
        this.registeredObjects = registeredObjects;
    }

    @Override
    public Optional<CelestialObject> resolve(@Nonnull CelestialObjectKey key) {
        if (key == null || !key.isMinorBody()) return Optional.empty();

        MinorCelestialBodyId minorId = key.minorBodyId();
        Optional<AsteroidFieldProfile> profile = profile(minorId.parentBodyId());
        if (profile.isEmpty() || !profile.get()
            .hasNodeIndex(minorId.index())) {
            return Optional.empty();
        }

        AsteroidFieldNode node = AsteroidFieldResolver
            .resolveNode(minorId.parentBodyId(), profile.get(), minorId.index());
        return Optional.of(toDynamicAsteroidObject(node, profile.get()));
    }

    @Override
    public List<CelestialObject> children(@Nonnull CelestialObjectKey parentId,
        @Nonnull CelestialDiscoveryView discoveryView, boolean includeHidden) {
        if (parentId == null || !parentId.isRegistered()) return List.of();
        Optional<AsteroidFieldProfile> profile = profile(parentId.registeredBodyId());
        if (profile.isEmpty()) return List.of();

        return AsteroidFieldResolver.resolveAll(parentId.registeredBodyId(), profile.get())
            .stream()
            .filter(node -> includeHidden || isVisibleMinorBody(node, discoveryView))
            .map(node -> toDynamicAsteroidObject(node, profile.get()))
            .toList();
    }

    private Optional<AsteroidFieldProfile> profile(CelestialObjectId id) {
        return registeredObjects.apply(CelestialObjectKey.registered(id))
            .map(CelestialObject::properties)
            .map(properties -> properties.asteroidFieldProfile());
    }

    private static boolean isVisibleMinorBody(AsteroidFieldNode node, CelestialDiscoveryView discoveryView) {
        DiscoveryState initialState = AsteroidFieldResolver.initialDetectionState(node);
        DiscoveryState state = discoveryView == null ? initialState
            : discoveryView.discoveryState(CelestialObjectKey.minorBody(node.id()))
                .orElse(initialState);
        return state == DiscoveryState.DISCOVERED;
    }

    private static CelestialObject toDynamicAsteroidObject(AsteroidFieldNode node, AsteroidFieldProfile profile) {
        double radius = profile.innerOrbitalRadius()
            + (profile.outerOrbitalRadius() - profile.innerOrbitalRadius()) * node.orbitalDepth01();
        double spriteSize = switch (node.sizeClass()) {
            case LARGE -> 0.04;
            case MEDIUM -> 0.01;
            case SMALL -> 0.0025;
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
            .circularOrbit(radius, 0.00091, Math.toRadians(node.angleOffsetDeg()))
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
}
