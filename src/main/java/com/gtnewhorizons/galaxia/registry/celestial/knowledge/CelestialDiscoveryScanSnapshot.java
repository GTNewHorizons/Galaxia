package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
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

    public enum CelestialDiscoveryCapability {
        PROSPECTING
    }

    /**
     * Stable discovery tier/type for progressive scanning or research work.
     */
    public enum CelestialDiscoveryStep {

        DETECTION(1200),
        PROFILE(4800);

        private final int durationTicks;

        CelestialDiscoveryStep(int durationTicks) {
            if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks must be positive");
            this.durationTicks = durationTicks;
        }

        @Nonnull
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public int durationTicks() {
            return durationTicks;
        }
    }

    /**
     * Stable boundary of one discovery scan around an anchored celestial object.
     */
    public record CelestialDiscoveryScanScope(@Nonnull CelestialObjectKey anchorKey, double radius, long revision) {

        public CelestialDiscoveryScanScope {
            if (anchorKey == null) throw new IllegalArgumentException("anchor key is required");
            if (!Double.isFinite(radius) || radius < 0.0) {
                throw new IllegalArgumentException("scan radius must be finite and non-negative");
            }
        }
    }

    /**
     * One discoverable fact a scan can uncover about a celestial object.
     * <p>
     * TLDR: data-shaped work item ({@code Key + step}) so scan progress persists
     * and rebinds after load without feature-specific work types.
     */
    public record CelestialDiscoveryWork(@Nonnull CelestialObjectKey targetKey, @Nonnull CelestialDiscoveryStep step) {

        public CelestialDiscoveryWork {
            if (targetKey == null) throw new IllegalArgumentException("target key is required");
            if (step == null) throw new IllegalArgumentException("discovery step is required");
        }

        public int durationTicks() {
            return step.durationTicks();
        }
    }

    public record CelestialDiscoveryWorkerContribution(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
        @Nonnull CelestialDiscoveryCapability capability, int workerCount, double effectPerWorker) {

        public CelestialDiscoveryWorkerContribution {
            if (workerCount < 0) throw new IllegalArgumentException("worker count must be non-negative");
            if (!Double.isFinite(effectPerWorker) || effectPerWorker < 0.0) {
                throw new IllegalArgumentException("worker effect must be finite and non-negative");
            }
        }

        long effectiveTicks(int elapsedTicks) {
            double ticks = (double) elapsedTicks * workerCount * effectPerWorker;
            if (!Double.isFinite(ticks) || ticks > Long.MAX_VALUE) {
                throw new IllegalArgumentException("effective discovery ticks overflow");
            }
            return Math.round(ticks);
        }
    }

    /**
     * Domain rules for one family of celestial discovery work.
     */
    public interface CelestialDiscoveryDomain {

        boolean ownsDiscoveryAnchor(@Nonnull CelestialObjectKey anchorKey);

        boolean ownsDiscoveryScope(@Nonnull CelestialDiscoveryScanScope scope);

        OptionalLong discoveryScopeRevision(@Nonnull CelestialObjectKey anchorKey);

        Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull UUID teamId,
            @Nonnull CelestialDiscoveryScanScope scope);

        void completeDiscoveryWork(@Nonnull UUID teamId, @Nonnull CelestialDiscoveryScanScope scope,
            @Nonnull CelestialDiscoveryWork work);
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
