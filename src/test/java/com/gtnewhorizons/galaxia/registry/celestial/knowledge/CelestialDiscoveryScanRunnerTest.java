package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

final class CelestialDiscoveryScanRunnerTest {

    private static final CelestialObjectKey TARGET = CelestialObjectKey.registered(CelestialObjectId.MARS);

    @Test
    void scanRunnerCompletesGenericDiscoveryWorkThroughKnowledgeInterface() {
        CelestialDiscoveryScanRunner<String, String> runner = new CelestialDiscoveryScanRunner<>();
        TestKnowledge knowledge = new TestKnowledge(new TestWork(TARGET, new TestStep("test_scan", 5)));

        assertTrue(
            runner.tick("satellite", 4, knowledge, "anchor-scope")
                .results()
                .isEmpty());

        CelestialDiscoveryScanRunner.TickResult result = runner.tick("satellite", 1, knowledge, "anchor-scope");

        assertEquals(
            List.of(new CelestialDiscoveryScanRunner.ScanResult(TARGET, knowledge.work.step())),
            result.results());
        assertEquals(List.of("anchor-scope:" + TARGET), knowledge.revealed);
    }

    private static final class TestKnowledge implements CelestialDiscoveryKnowledge<String> {

        private final TestWork work;
        private final List<String> revealed = new ArrayList<>();
        private boolean complete;

        private TestKnowledge(TestWork work) {
            this.work = work;
        }

        @Override
        public Optional<CelestialDiscoveryWork> nextDiscoveryWork(@Nonnull String context) {
            return complete ? Optional.empty() : Optional.of(work);
        }

        @Override
        public void revealDiscovery(@Nonnull CelestialDiscoveryWork work, @Nonnull String context) {
            complete = true;
            revealed.add(context + ":" + work.targetKey());
        }
    }

    private record TestWork(@Nonnull CelestialObjectKey targetKey, @Nonnull CelestialDiscoveryStep step)
        implements CelestialDiscoveryWork {}

    private record TestStep(@Nonnull String id, int durationTicks) implements CelestialDiscoveryStep {}
}
