package com.gtnewhorizons.galaxia.registry.outpost.feature;

public enum PlanetaryFeatureLayer {

    TERRAIN(0),
    ENVIRONMENT(100),
    RESOURCE(200);

    private final int drawOrder;

    PlanetaryFeatureLayer(int drawOrder) {
        this.drawOrder = drawOrder;
    }

    public int drawOrder() {
        return drawOrder;
    }
}
