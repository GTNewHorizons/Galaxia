/**
 * Satellite communication is server-authoritative.
 *
 * <p>
 * Satellite counts live as normal {@code CelestialAsset} entries in the starmap asset store. This package derives
 * a per-team network snapshot from those assets, debug data generator endpoints, and buffered data waiting for
 * transfer.
 * The client receives the resulting snapshot and only renders the links, bandwidth usage, and pending data state.
 */
package com.gtnewhorizons.galaxia.registry.satellite;
