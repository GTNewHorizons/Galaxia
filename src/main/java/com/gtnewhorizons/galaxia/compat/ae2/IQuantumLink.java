package com.gtnewhorizons.galaxia.compat.ae2;

/**
 * Added to AE2 quantum bridges so their link can be checked again after relays come or go
 */
public interface IQuantumLink {

    /** Runs the bridge's link logic again, connecting or cutting it as the relays now allow */
    void galaxia$recheckLink();

    /** False once the bridge is broken or unloaded */
    boolean galaxia$isAlive();
}
