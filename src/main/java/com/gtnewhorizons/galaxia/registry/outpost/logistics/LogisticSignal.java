package com.gtnewhorizons.galaxia.registry.outpost.logistics;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;

public record LogisticSignal(CelestialAsset.ID outpostAssetId, CelestialObjectId systemId, ItemStackWrapper resourceId,
    long amount, Scope scope, CelestialObjectId bodyId, CelestialObjectId planetaryAnchorBodyId) {

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
