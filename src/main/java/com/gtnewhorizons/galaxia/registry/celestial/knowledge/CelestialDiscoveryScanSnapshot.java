package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

public record CelestialDiscoveryScanSnapshot(@Nonnull UUID teamId, @Nonnull CelestialObjectKey anchorKey, double radius,
    long scopeRevision, @Nonnull CelestialDiscoveryCapability capability, @Nonnull Status status,
    @Nullable CelestialObjectKey targetKey, @Nullable CelestialDiscoveryStep step, long elapsedTicks) {

    public enum Status {
        ACTIVE,
        COMPLETE
    }

    public CelestialDiscoveryScanSnapshot {
        if (teamId == null || anchorKey == null || capability == null || status == null) {
            throw new IllegalArgumentException("discovery scan identity is required");
        }
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("scan radius must be finite and non-negative");
        }
        if (status == Status.ACTIVE) {
            if (targetKey == null || step == null) {
                throw new IllegalArgumentException("active discovery scan requires target and step");
            }
            if (elapsedTicks < 0 || elapsedTicks >= step.durationTicks()) {
                throw new IllegalArgumentException("active elapsed ticks must be within step duration");
            }
        } else if (targetKey != null || step != null || elapsedTicks != 0) {
            throw new IllegalArgumentException("complete discovery scan cannot contain active progress");
        }
    }

    public static CelestialDiscoveryScanSnapshot complete(@Nonnull UUID teamId,
        @Nonnull CelestialDiscoveryScanScope scope, @Nonnull CelestialDiscoveryCapability capability) {
        return new CelestialDiscoveryScanSnapshot(
            teamId,
            scope.anchorKey(),
            scope.radius(),
            scope.revision(),
            capability,
            Status.COMPLETE,
            null,
            null,
            0);
    }

    public CelestialDiscoveryScanScope scope() {
        return new CelestialDiscoveryScanScope(anchorKey, radius, scopeRevision);
    }
}
