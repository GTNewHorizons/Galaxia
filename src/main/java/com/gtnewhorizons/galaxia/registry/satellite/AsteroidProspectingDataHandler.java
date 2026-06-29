package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledgeStore;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;

public final class AsteroidProspectingDataHandler implements SatelliteDataJobService.ProductionListener {

    private final AsteroidFieldKnowledgeStore store;
    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;

    public AsteroidProspectingDataHandler(AsteroidFieldKnowledgeStore store,
        Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.profileResolver = Objects.requireNonNull(profileResolver, "profileResolver cannot be null");
    }

    public static AsteroidProspectingDataHandler live(AsteroidFieldKnowledgeStore store) {
        return new AsteroidProspectingDataHandler(
            store,
            bodyId -> GalaxiaCelestialAPI.get(bodyId)
                .map(
                    body -> body.properties()
                        .asteroidFieldProfile()));
    }

    @Override
    public void onProductionComplete(SatelliteDataJobService.ProductionEvent event) {
        handle(event);
    }

    public Optional<AsteroidFieldNode> handle(SatelliteDataJobService.ProductionEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        SatelliteDataKey key = event.key();
        if (key.type() != SatelliteDataType.PROSPECTING) return Optional.empty();
        if (!key.hasOrigin()) {
            throw new IllegalStateException("Prospecting production event must include an origin body");
        }
        if (key.origin() != event.bodyId()) {
            throw new IllegalStateException("Prospecting production event origin does not match source body");
        }

        Optional<AsteroidFieldProfile> profile = Objects
            .requireNonNull(profileResolver.apply(event.bodyId()), "profileResolver cannot return null");
        if (profile.isEmpty()) return Optional.empty();

        if (store.getOrCreate(event.teamId(), event.bodyId(), profile.get())
            .hasDetectionWork()) {
            return store.detectNext(event.teamId(), event.bodyId(), profile.get());
        }
        return store.prospectNext(event.teamId(), event.bodyId(), profile.get());
    }
}
