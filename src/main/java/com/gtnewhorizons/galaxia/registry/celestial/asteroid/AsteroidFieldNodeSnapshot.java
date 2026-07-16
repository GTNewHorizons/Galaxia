package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public record AsteroidFieldNodeSnapshot(int index, @Nonnull String displayName, @Nonnull AsteroidNodeKind kind,
    @Nonnull AsteroidSizeClass sizeClass, @Nonnull DiscoveryState initialDetectionState,
    @Nonnull CelestialResourceKnowledgeState initialOreKnowledgeState, double angleOffsetDeg, double orbitalDepth01,
    @Nonnull OreProfileSnapshot oreProfile, @Nonnull AppearanceSnapshot appearance) {

    public AsteroidFieldNodeSnapshot {
        if (index < 0) {
            throw new IllegalArgumentException("node index must be non-negative");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        validateNode(
            CelestialObjectId.FROZEN_BELT,
            index,
            displayName,
            kind,
            sizeClass,
            initialDetectionState,
            initialOreKnowledgeState,
            angleOffsetDeg,
            orbitalDepth01,
            oreProfile,
            appearance);
    }

    public static AsteroidFieldNodeSnapshot fromNode(@Nonnull AsteroidFieldNode node) {
        return new AsteroidFieldNodeSnapshot(
            node.index(),
            node.displayName(),
            node.kind(),
            node.sizeClass(),
            node.initialDetectionState(),
            AsteroidFieldResolver.initialOreKnowledge(node),
            node.angleOffsetDeg(),
            node.orbitalDepth01(),
            OreProfileSnapshot.from(node.oreProfile()),
            AppearanceSnapshot.from(node.appearance()));
    }

    public AsteroidFieldNode toNode(@Nonnull CelestialObjectId beltId) {
        return validateNode(
            beltId,
            index,
            displayName,
            kind,
            sizeClass,
            initialDetectionState,
            initialOreKnowledgeState,
            angleOffsetDeg,
            orbitalDepth01,
            oreProfile,
            appearance);
    }

    private static AsteroidFieldNode validateNode(@Nonnull CelestialObjectId beltId, int index,
        @Nonnull String displayName, @Nonnull AsteroidNodeKind kind, @Nonnull AsteroidSizeClass sizeClass,
        @Nonnull DiscoveryState initialDetectionState,
        @Nonnull CelestialResourceKnowledgeState initialOreKnowledgeState, double angleOffsetDeg, double orbitalDepth01,
        @Nonnull OreProfileSnapshot oreProfile, @Nonnull AppearanceSnapshot appearance) {

        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName,
            kind,
            sizeClass,
            initialDetectionState,
            initialOreKnowledgeState,
            angleOffsetDeg,
            orbitalDepth01,
            oreProfile.toProfile(),
            appearance.toProfile());
    }

    public record OreProfileSnapshot(@Nonnull String id, @Nonnull List<String> gtOreVeinIds) {

        public OreProfileSnapshot {
            if (gtOreVeinIds == null) gtOreVeinIds = List.of();
            else gtOreVeinIds = Collections.unmodifiableList(new ArrayList<>(gtOreVeinIds));
            new AsteroidOreProfile(id, gtOreVeinIds);
        }

        public static OreProfileSnapshot from(@Nonnull AsteroidOreProfile profile) {
            return new OreProfileSnapshot(profile.id(), profile.gtOreVeinIds());
        }

        AsteroidOreProfile toProfile() {
            return new AsteroidOreProfile(id, gtOreVeinIds);
        }
    }

    public record AppearanceSnapshot(@Nonnull String iconRecipeId, long variantSeed) {

        public AppearanceSnapshot {
            new AsteroidAppearanceProfile(iconRecipeId, variantSeed);
        }

        public static AppearanceSnapshot from(@Nonnull AsteroidAppearanceProfile appearance) {
            return new AppearanceSnapshot(appearance.iconRecipeId(), appearance.variantSeed());
        }

        AsteroidAppearanceProfile toProfile() {
            return new AsteroidAppearanceProfile(iconRecipeId, variantSeed);
        }
    }
}
