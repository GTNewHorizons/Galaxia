package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeAccess;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;

public final class AsteroidProspectingDataHandler implements SatelliteDataJobService.ProductionListener {

    private final AsteroidFieldKnowledgeAccess knowledgeAccess;
    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;

    public AsteroidProspectingDataHandler(@Nonnull AsteroidFieldKnowledgeAccess knowledgeAccess) {
        this(knowledgeAccess, AsteroidProspectingDataHandler::liveProfile);
    }

    AsteroidProspectingDataHandler(@Nonnull AsteroidFieldKnowledgeAccess knowledgeAccess,
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        this.knowledgeAccess = knowledgeAccess;
        this.profileResolver = profileResolver;
    }

    public static AsteroidProspectingDataHandler live(@Nonnull AsteroidFieldKnowledgeAccess knowledgeAccess) {
        return new AsteroidProspectingDataHandler(knowledgeAccess);
    }

    @Override
    public void onProductionComplete(SatelliteDataJobService.ProductionEvent event) {
        handle(event);
    }

    public Optional<AsteroidFieldNode> handle(@Nonnull SatelliteDataJobService.ProductionEvent event) {
        SatelliteDataKey key = event.key();
        if (key.type() != SatelliteDataType.PROSPECTING) return Optional.empty();
        if (!key.hasOrigin()) {
            throw new IllegalStateException("Prospecting production event must include an origin body");
        }
        if (key.origin() != event.bodyId()) {
            throw new IllegalStateException("Prospecting production event origin does not match source body");
        }

        Optional<AsteroidFieldProfile> profile = profileResolver.apply(event.bodyId());
        if (profile == null) {
            throw new IllegalStateException("profileResolver cannot return null");
        }
        if (profile.isEmpty()) return Optional.empty();

        if (knowledgeAccess.hasDetectionWork(event.teamId(), event.bodyId(), profile.get())) {
            return knowledgeAccess.detectNext(event.teamId(), event.bodyId(), profile.get());
        }
        return knowledgeAccess.prospectNext(event.teamId(), event.bodyId(), profile.get());
    }

    private static Optional<AsteroidFieldProfile> liveProfile(CelestialObjectId bodyId) {
        return GalaxiaCelestialAPI.get(bodyId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile());
    }
}
