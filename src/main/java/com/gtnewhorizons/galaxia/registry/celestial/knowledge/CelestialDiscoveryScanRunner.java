package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.progress.ProgressJobRunner;

/**
 * Generic progress runner for active scans that uncover celestial knowledge.
 *
 * Domain code owns candidate selection and reveal side effects through
 * {@link CelestialDiscoveryKnowledge}; this runner only persists progress for
 * the current work item and returns generic scan results.
 */
public final class CelestialDiscoveryScanRunner<K, C> {

    private final ProgressJobRunner<K, CelestialDiscoveryWork, ScanResult> progressRunner = new ProgressJobRunner<>();

    public TickResult tick(@Nonnull K key, int elapsedTicks, @Nonnull CelestialDiscoveryKnowledge<C> knowledge,
        @Nonnull C context) {
        if (knowledge == null) throw new IllegalArgumentException("discovery knowledge is required");
        return toTickResult(progressRunner.tick(key, elapsedTicks, () -> knowledge.nextDiscoveryWork(context), work -> {
            knowledge.revealDiscovery(work, context);
            return new ScanResult(work.targetKey(), work.step());
        }));
    }

    public Optional<ProgressJobRunner.Progress<CelestialDiscoveryWork>> progress(@Nonnull K key) {
        return progressRunner.progress(key);
    }

    public Map<K, ProgressJobRunner.Progress<CelestialDiscoveryWork>> progressByKey() {
        return progressRunner.progressByKey();
    }

    public void restore(@Nonnull K key, @Nonnull CelestialDiscoveryWork work, int elapsedTicks) {
        progressRunner.restore(key, work, elapsedTicks);
    }

    public void remove(@Nonnull K key) {
        progressRunner.remove(key);
    }

    public void removeIf(@Nonnull Predicate<K> predicate) {
        progressRunner.removeIf(predicate);
    }

    public void clear() {
        progressRunner.clear();
    }

    private static TickResult toTickResult(@Nonnull ProgressJobRunner.TickResult<ScanResult> result) {
        return new TickResult(result.results(), result.idle());
    }

    public record ScanResult(@Nonnull CelestialObjectKey targetKey, @Nonnull CelestialDiscoveryStep step) {}

    public record TickResult(@Nonnull List<ScanResult> results, boolean idle) {

        public TickResult {
            results = List.copyOf(results == null ? List.of() : results);
        }
    }
}
