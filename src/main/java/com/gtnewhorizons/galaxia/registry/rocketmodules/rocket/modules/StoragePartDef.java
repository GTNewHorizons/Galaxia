package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules;

/**
 * Cargo hold. Loaded from the silo's input buses and unloaded into the destination silo's output buses
 */
public class StoragePartDef extends FunctionalPartDef {

    private final int slots;

    public StoragePartDef(String key, int width, int height, int weight, int slots, String assetFolder) {
        super(key, width, height, weight, assetFolder);
        this.slots = slots;
    }

    /** Number of item slots in the hold */
    public int slots() {
        return slots;
    }
}
