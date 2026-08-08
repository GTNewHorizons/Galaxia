package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.SatelliteNetworkOverlay.Endpoint;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteNetworkGraph;

final class SatelliteNetworkOverlayTest {

    private static final SatelliteNetworkGraph.Edge EDGE = new SatelliteNetworkGraph.Edge(
        CelestialObjectKey.registered(CelestialObjectId.OVERWORLD),
        CelestialObjectKey.registered(CelestialObjectId.MARS));

    @Test
    void packetStyleUsesFourBandwidthBands() {
        assertEquals(SatelliteNetworkOverlay.KB_STYLE, SatelliteNetworkOverlay.signalStyle(0L));
        assertEquals(SatelliteNetworkOverlay.KB_STYLE, SatelliteNetworkOverlay.signalStyle(999L));
        assertEquals(SatelliteNetworkOverlay.MB_STYLE, SatelliteNetworkOverlay.signalStyle(1_000L));
        assertEquals(SatelliteNetworkOverlay.GB_STYLE, SatelliteNetworkOverlay.signalStyle(1_000_000L));
        assertEquals(SatelliteNetworkOverlay.TB_STYLE, SatelliteNetworkOverlay.signalStyle(1_000_000_000L));
    }

    @Test
    void packetHeadsGrowWithEachBandwidthBand() {
        assertTrue(SatelliteNetworkOverlay.KB_STYLE.headWidth() < SatelliteNetworkOverlay.MB_STYLE.headWidth());
        assertTrue(SatelliteNetworkOverlay.MB_STYLE.headWidth() < SatelliteNetworkOverlay.GB_STYLE.headWidth());
        assertTrue(SatelliteNetworkOverlay.GB_STYLE.headWidth() < SatelliteNetworkOverlay.TB_STYLE.headWidth());
    }

    @Test
    void idleLinkUsesTheBaseEmissionInterval() {
        assertEquals(6.0D, SatelliteNetworkOverlay.cooldownSeconds(0L), 1.0e-9);
    }

    @Test
    void busierLinksEmitPacketsMoreOften() {
        double previous = SatelliteNetworkOverlay.cooldownSeconds(0L);
        for (long usage : new long[] { 50L, 500L, 5_000L, 5_000_000L }) {
            double current = SatelliteNetworkOverlay.cooldownSeconds(usage);
            assertTrue(current < previous, "cooldown must shrink as usage grows, at " + usage + " kbps");
            assertTrue(current > 0.0D, "cooldown must stay positive, at " + usage + " kbps");
            previous = current;
        }
    }

    @Test
    void packetSeedIsStableForTheSamePacket() {
        assertEquals(
            SatelliteNetworkOverlay.signalSeed(EDGE, 0, 3, false),
            SatelliteNetworkOverlay.signalSeed(EDGE, 0, 3, false));
    }

    @Test
    void packetSeedSeparatesDirectionSequenceAndKeepAlive() {
        int forward = SatelliteNetworkOverlay.signalSeed(EDGE, 0, 3, false);
        assertNotEquals(forward, SatelliteNetworkOverlay.signalSeed(EDGE, 1, 3, false));
        assertNotEquals(forward, SatelliteNetworkOverlay.signalSeed(EDGE, 0, 4, false));
        assertNotEquals(forward, SatelliteNetworkOverlay.signalSeed(EDGE, 0, 3, true));
    }

    @Test
    void seedDerivedJitterStaysInsideItsConfiguredBand() {
        for (int packetIndex = 0; packetIndex < 64; packetIndex++) {
            int seed = SatelliteNetworkOverlay.signalSeed(EDGE, packetIndex % 2, packetIndex, false);
            double unit = SatelliteNetworkOverlay.signalUnit(seed, 8);
            assertTrue(unit >= 0.0D && unit <= 1.0D, "unit out of range: " + unit);
            double segment = SatelliteNetworkOverlay.signalSegmentLength(seed, 16);
            assertTrue(segment >= 16.0D && segment <= 24.0D, "segment length out of range: " + segment);
        }
    }

    @Test
    void threadStartsAndEndsAtTheBodyEdgesNotTheirCentres() {
        SatelliteNetworkOverlay overlay = new SatelliteNetworkOverlay();

        float[] ends = overlay.threadEndpoints(new Endpoint(0f, 0f, 5f), new Endpoint(100f, 0f, 10f));

        assertArrayEquals(new float[] { 7f, 0f, 88f, 0f }, ends, 0.001f);
    }

    @Test
    void overlappingBodiesFallBackToTheirCentres() {
        SatelliteNetworkOverlay overlay = new SatelliteNetworkOverlay();

        float[] ends = overlay.threadEndpoints(new Endpoint(50f, 50f, 5f), new Endpoint(50f, 50f, 5f));

        assertArrayEquals(new float[] { 50f, 50f, 50f, 50f }, ends, 0.001f);
    }
}
