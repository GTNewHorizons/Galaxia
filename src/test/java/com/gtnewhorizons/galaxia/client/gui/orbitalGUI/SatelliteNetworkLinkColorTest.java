package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SatelliteNetworkLinkColorTest {

    @Test
    void linkLoadUsesFiveOrderedColorBands() {
        assertEquals(SatelliteNetworkLinkColor.GREEN, SatelliteNetworkLinkColor.forLoad(0L, 100L));
        assertEquals(SatelliteNetworkLinkColor.LIME, SatelliteNetworkLinkColor.forLoad(25L, 100L));
        assertEquals(SatelliteNetworkLinkColor.YELLOW, SatelliteNetworkLinkColor.forLoad(50L, 100L));
        assertEquals(SatelliteNetworkLinkColor.ORANGE, SatelliteNetworkLinkColor.forLoad(75L, 100L));
        assertEquals(SatelliteNetworkLinkColor.RED, SatelliteNetworkLinkColor.forLoad(100L, 100L));
    }

    @Test
    void loadColorClampsMissingAndOverloadedCapacity() {
        assertEquals(SatelliteNetworkLinkColor.GREEN, SatelliteNetworkLinkColor.forLoad(10L, 0L));
        assertEquals(SatelliteNetworkLinkColor.RED, SatelliteNetworkLinkColor.forLoad(150L, 100L));
    }
}
