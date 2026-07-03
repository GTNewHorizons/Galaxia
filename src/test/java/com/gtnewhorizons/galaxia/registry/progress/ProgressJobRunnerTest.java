package com.gtnewhorizons.galaxia.registry.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;

final class ProgressJobRunnerTest {

    @Test
    void runnerPersistsPartialProgressAndReportsIdleAfterLastWork() {
        ProgressJobRunner<String, TestWork, String> runner = new ProgressJobRunner<>();
        Queue<TestWork> work = new ArrayDeque<>(List.of(new TestWork("scan", 5), new TestWork("prospect", 3)));

        ProgressJobRunner.TickResult<String> partial = runner.tick(
            "satellite",
            4,
            () -> work.stream()
                .findFirst(),
            current -> {
                work.remove();
                return current.id();
            });

        assertTrue(
            partial.results()
                .isEmpty());
        assertEquals(
            4,
            runner.progress("satellite")
                .orElseThrow()
                .elapsedTicks());

        ProgressJobRunner.TickResult<String> completed = runner.tick(
            "satellite",
            4,
            () -> work.stream()
                .findFirst(),
            current -> {
                work.remove();
                return current.id();
            });

        assertEquals(List.of("scan", "prospect"), completed.results());
        assertTrue(completed.idle());
        assertTrue(
            runner.progress("satellite")
                .isEmpty());
    }

    private record TestWork(String id, int durationTicks) implements ProgressJobRunner.Work {}
}
