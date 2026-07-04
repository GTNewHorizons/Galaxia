package com.gtnewhorizons.galaxia.registry.satellite;

import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldKnowledge;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldNode;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldScanOrder;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialDiscoveryWork;
import com.gtnewhorizons.galaxia.registry.celestial.knowledge.CelestialKnowledgeService;

public final class AsteroidProspectingDataHandler implements SatelliteDataJobService.ProductionListener {

    private final Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver;

    public AsteroidProspectingDataHandler() {
        this(AsteroidProspectingDataHandler::liveProfile);
    }

    AsteroidProspectingDataHandler(
        @Nonnull Function<CelestialObjectId, Optional<AsteroidFieldProfile>> profileResolver) {
        this.profileResolver = profileResolver;
    }

    public static AsteroidProspectingDataHandler live() {
        return new AsteroidProspectingDataHandler();
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

        AsteroidFieldKnowledge knowledge = CelestialKnowledgeService
            .asteroidFieldKnowledge(event.teamId(), event.bodyId(), profile.get());
        Optional<CelestialDiscoveryWork> work = knowledge
            .nextDiscoveryWork(node -> true, AsteroidFieldScanOrder.byIndex());
        work.ifPresent(discovery -> knowledge.revealDiscovery(discovery, node -> true));
        return work.map(AsteroidProspectingDataHandler::asteroidNode)
            .map(
                asteroidId -> knowledge.nodes()
                    .stream()
                    .filter(
                        node -> node.id()
                            .equals(asteroidId))
                    .findFirst()
                    .orElseThrow(
                        () -> new IllegalStateException("Discovery work targeted unknown asteroid: " + asteroidId)));
    }

    private static com.gtnewhorizons.galaxia.registry.celestial.asteroid.MinorCelestialBodyId asteroidNode(
        CelestialDiscoveryWork work) {
        if (work instanceof AsteroidFieldDiscoveryWork asteroidWork) return asteroidWork.asteroidId();
        throw new IllegalArgumentException("Expected asteroid field discovery work");
    }

    private static Optional<AsteroidFieldProfile> liveProfile(CelestialObjectId bodyId) {
        return GalaxiaCelestialAPI.get(bodyId)
            .map(
                body -> body.properties()
                    .asteroidFieldProfile());
    }
}
