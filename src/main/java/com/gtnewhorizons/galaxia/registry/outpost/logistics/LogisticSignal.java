package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record LogisticSignal(CelestialAsset.ID outpostAssetId, CelestialObjectKey systemKey,
    ItemStackWrapper resourceId, long amount, Scope scope, CelestialObjectKey bodyKey,
    CelestialObjectKey planetaryAnchorBodyKey) {

    public enum Scope {
        PLANETARY,
        SYSTEM,
        GALACTIC
    }

    public boolean isSupply() {
        return amount > 0;
    }

    public boolean isRequest() {
        return amount < 0;
    }

    public long magnitude() {
        return Math.abs(amount);
    }
}
