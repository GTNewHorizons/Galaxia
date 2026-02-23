package com.gtnewhorizons.galaxia.dimension.sky;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class SkyBuilder {

    private final List<CelestialBody> bodies = new ArrayList<>();

    public static SkyBuilder builder() {
        return new SkyBuilder();
    }

    /**
     * Add any celestial body
     *
     * @param config Settings for the body
     * @return this builder
     */
    public SkyBuilder addBody(Consumer<CelestialBodyBuilder> config) {
        CelestialBodyBuilder b = new CelestialBodyBuilder();
        config.accept(b);
        bodies.add(b.build());
        return this;
    }

    public List<CelestialBody> build() {
        return Collections.unmodifiableList(bodies);
    }
}
