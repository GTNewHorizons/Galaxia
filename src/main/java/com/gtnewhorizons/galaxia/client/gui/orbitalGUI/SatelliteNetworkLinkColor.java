package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

final class SatelliteNetworkLinkColor {

    static final int GREEN = 0xFF40D96B;
    static final int LIME = 0xFFA3E635;
    static final int YELLOW = 0xFFFFD84D;
    static final int ORANGE = 0xFFFF9F43;
    static final int RED = 0xFFFF5A5A;

    private static final int[] BANDS = { GREEN, LIME, YELLOW, ORANGE, RED };

    private SatelliteNetworkLinkColor() {}

    static int forLoad(long usedKbps, long capacityKbps) {
        if (usedKbps <= 0L || capacityKbps <= 0L) return GREEN;
        double load = Math.min(1.0D, usedKbps / (double) capacityKbps);
        int band = Math.max(0, Math.min(BANDS.length - 1, (int) Math.ceil(load * BANDS.length) - 1));
        return BANDS[band];
    }
}
