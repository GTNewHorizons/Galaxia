package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeFacts.DiscoveryState;

public record AsteroidStarmapProjection(@Nonnull CelestialObject body, @Nonnull MinorCelestialBodyId id,
    @Nonnull AsteroidNodeKind nodeKind, @Nonnull AsteroidSizeClass sizeClass, @Nonnull DiscoveryState detectionState,
    @Nonnull CelestialResourceKnowledgeState oreKnowledgeState, @Nonnull Optional<String> visibleOreProfileId,
    @Nonnull List<String> visibleGtOreVeinIds, @Nonnull AsteroidAppearanceProfile appearanceProfile,
    boolean debugHidden, boolean scanInProgress, boolean sensorRevealed) {

    private static final float MAP_ICON_BASE_SCALE = 60f;
    private static final float MIN_RENDERED_DIAMETER = 2f;
    private static final float NO_SPRITE_RADIUS = 0.0f;
    private static final float MINIMUM_SPRITE_SIZE = 0.0001f;
    private static final int LARGE_PRESENTATION_PRIORITY = 80;
    private static final int MEDIUM_PRESENTATION_PRIORITY = 50;
    private static final int SMALL_PRESENTATION_PRIORITY = 20;
    private static final int LORE_PRESENTATION_PRIORITY_BONUS = 30;
    private static final float DIAMETER_MULTIPLIER = 2.0f;
    private static final float LARGE_MINIMUM_RADIUS = 0.45f;
    private static final float MEDIUM_MINIMUM_RADIUS = 0.40f;
    private static final float SMALL_MINIMUM_RADIUS = 0.35f;

    public AsteroidStarmapProjection {
        if (oreKnowledgeState == CelestialResourceKnowledgeState.UNKNOWN && visibleOreProfileId.isPresent()) {
            throw new IllegalArgumentException("Unknown asteroid ore cannot expose an ore profile id");
        }
        if (visibleGtOreVeinIds == null) visibleGtOreVeinIds = List.of();
        else visibleGtOreVeinIds = Collections.unmodifiableList(new ArrayList<>(visibleGtOreVeinIds));
        if (oreKnowledgeState != CelestialResourceKnowledgeState.PROFILE && !visibleGtOreVeinIds.isEmpty()) {
            throw new IllegalArgumentException("Asteroid ore veins require profile-level knowledge");
        }
        if (debugHidden && detectionState != DiscoveryState.HIDDEN) {
            throw new IllegalArgumentException("Only hidden asteroid projections can be debug-hidden");
        }
        if (scanInProgress && detectionState != DiscoveryState.HIDDEN) {
            throw new IllegalArgumentException("Only hidden asteroid projections can be scan-in-progress ghosts");
        }
        if (sensorRevealed && detectionState != DiscoveryState.HIDDEN) {
            throw new IllegalArgumentException("Only hidden asteroid projections can be sensor-revealed ghosts");
        }
    }

    public static float spriteRadius(@Nullable CelestialObject body, float spriteSize, double relativeZoom) {
        if (body == null || !body.isAsteroid() || spriteSize <= MINIMUM_SPRITE_SIZE) return NO_SPRITE_RADIUS;
        return Math.max(NO_SPRITE_RADIUS, spriteSize * MAP_ICON_BASE_SCALE * (float) relativeZoom);
    }

    public static boolean shouldCull(@Nullable CelestialObject body, @Nullable AsteroidStarmapProjection projection,
        float naturalRadius) {
        if (body == null || !body.isAsteroid() || projection == null) return false;
        return projection.shouldCull(naturalRadius);
    }

    public boolean shouldCull(float naturalRadius) {
        return shouldCullAtNaturalRadius(naturalRadius, MIN_RENDERED_DIAMETER);
    }

    public boolean drawDefaultLabel() {
        return nodeKind == AsteroidNodeKind.LORE || nodeKind == AsteroidNodeKind.UNIQUE;
    }

    public int presentationPriority() {
        int priority = switch (sizeClass) {
            case LARGE -> LARGE_PRESENTATION_PRIORITY;
            case MEDIUM -> MEDIUM_PRESENTATION_PRIORITY;
            case SMALL -> SMALL_PRESENTATION_PRIORITY;
        };
        if (nodeKind == AsteroidNodeKind.LORE) priority += LORE_PRESENTATION_PRIORITY_BONUS;
        return priority;
    }

    public boolean canShowOreDetails() {
        return oreKnowledgeState == CelestialResourceKnowledgeState.PROFILE;
    }

    public boolean shouldCullAtNaturalRadius(float naturalRadius, float minimumRenderedDiameter) {
        if (naturalRadius * DIAMETER_MULTIPLIER < minimumRenderedDiameter) return true;
        float minimumRadius = switch (sizeClass) {
            case LARGE -> LARGE_MINIMUM_RADIUS;
            case MEDIUM -> MEDIUM_MINIMUM_RADIUS;
            case SMALL -> SMALL_MINIMUM_RADIUS;
        };
        return naturalRadius < minimumRadius;
    }
}
