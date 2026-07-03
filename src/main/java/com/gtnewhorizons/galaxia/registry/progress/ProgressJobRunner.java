package com.gtnewhorizons.galaxia.registry.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

/**
 * Small state machine for long-running jobs that advance one deterministic work
 * item at a time.
 *
 * The caller owns domain selection and completion side effects. The runner only
 * remembers elapsed ticks for the current work item and reports when a complete
 * pass found no more work, letting callers permanently idle expensive scans.
 */
public final class ProgressJobRunner<K, W extends ProgressJobRunner.Work, R> {

    private final Map<K, Progress<W>> progressByKey = new LinkedHashMap<>();

    public TickResult<R> tick(@Nonnull K key, int elapsedTicks, @Nonnull Supplier<Optional<W>> nextWork,
        @Nonnull Function<W, R> completeWork) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        if (elapsedTicks == 0) return new TickResult<>(List.of(), false);

        List<R> results = new ArrayList<>();
        int remainingTicks = elapsedTicks;
        while (remainingTicks > 0) {
            Optional<W> work = nextWork.get();
            if (work == null) throw new IllegalStateException("nextWork cannot return null");
            if (work.isEmpty()) {
                progressByKey.remove(key);
                return new TickResult<>(List.copyOf(results), true);
            }

            W currentWork = work.get();
            int durationTicks = currentWork.durationTicks();
            if (durationTicks <= 0) throw new IllegalStateException("work duration must be positive");
            Progress<W> progress = currentProgress(key, currentWork);
            int requiredTicks = durationTicks - progress.elapsedTicks();
            if (remainingTicks < requiredTicks) {
                progressByKey.put(key, progress.advance(remainingTicks));
                return new TickResult<>(List.copyOf(results), false);
            }

            remainingTicks -= requiredTicks;
            results.add(completeWork.apply(currentWork));
            progressByKey.remove(key);
            Optional<W> next = nextWork.get();
            if (next == null) throw new IllegalStateException("nextWork cannot return null");
            if (next.isEmpty()) return new TickResult<>(List.copyOf(results), true);
        }
        return new TickResult<>(List.copyOf(results), false);
    }

    public Optional<Progress<W>> progress(@Nonnull K key) {
        return Optional.ofNullable(progressByKey.get(key));
    }

    public Map<K, Progress<W>> progressByKey() {
        return Map.copyOf(progressByKey);
    }

    public void restore(@Nonnull K key, @Nonnull W work, int elapsedTicks) {
        progressByKey.put(key, new Progress<>(work, elapsedTicks));
    }

    public void remove(@Nonnull K key) {
        progressByKey.remove(key);
    }

    public void removeIf(@Nonnull java.util.function.Predicate<K> predicate) {
        progressByKey.keySet()
            .removeIf(predicate);
    }

    public void clear() {
        progressByKey.clear();
    }

    private Progress<W> currentProgress(K key, W work) {
        Progress<W> current = progressByKey.get(key);
        if (current == null || !current.work()
            .equals(work)) return new Progress<>(work, 0);
        return current;
    }

    public interface Work {

        int durationTicks();
    }

    public record Progress<W extends Work> (@Nonnull W work, int elapsedTicks) {

        public Progress {
            if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        }

        private Progress<W> advance(int ticks) {
            if (ticks < 0) throw new IllegalArgumentException("ticks must be non-negative");
            return new Progress<>(work, elapsedTicks + ticks);
        }
    }

    public record TickResult<R> (@Nonnull List<R> results, boolean idle) {

        public TickResult {
            results = List.copyOf(results == null ? List.of() : results);
        }
    }
}
