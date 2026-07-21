package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialResourceKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.DiscoveryState;

public record AsteroidStarmapProjection(@Nonnull CelestialObject body, @Nonnull MinorCelestialBodyId id,
    @Nonnull AsteroidNodeKind nodeKind, @Nonnull AsteroidSizeClass sizeClass, @Nonnull DiscoveryState detectionState,
    @Nonnull CelestialResourceKnowledgeState oreKnowledgeState, @Nonnull Optional<String> visibleOreProfileId,
    @Nonnull List<String> visibleGtOreVeinIds, @Nonnull AsteroidAppearanceProfile appearanceProfile,
    boolean debugHidden, boolean scanInProgress, boolean sensorRevealed) {

    private static final float MAP_ICON_BASE_SCALE = 60f;
    private static final float MIN_RENDERED_DIAMETER = 2f;

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
        if (body == null || body.objectClass() != CelestialObject.Class.ASTEROID || spriteSize <= 0.0001f) return 0f;
        return Math.max(0.0f, spriteSize * MAP_ICON_BASE_SCALE * (float) relativeZoom);
    }

    public static boolean shouldCull(@Nullable CelestialObject body, @Nullable AsteroidStarmapProjection projection,
        float naturalRadius) {
        if (body == null || body.objectClass() != CelestialObject.Class.ASTEROID || projection == null) return false;
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
            case LARGE -> 80;
            case MEDIUM -> 50;
            case SMALL -> 20;
        };
        if (nodeKind == AsteroidNodeKind.LORE) priority += 30;
        return priority;
    }

    public boolean canShowOreDetails() {
        return oreKnowledgeState == CelestialResourceKnowledgeState.PROFILE;
    }

    public boolean shouldCullAtNaturalRadius(float naturalRadius, float minimumRenderedDiameter) {
        if (naturalRadius * 2.0f < minimumRenderedDiameter) return true;
        float minimumRadius = switch (sizeClass) {
            case LARGE -> 0.45f;
            case MEDIUM -> 0.40f;
            case SMALL -> 0.35f;
        };
        return naturalRadius < minimumRadius;
    }
}
