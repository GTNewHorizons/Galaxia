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

    public SkyBuilder sun(Consumer<SunBuilder> config) {
        SunBuilder builder = new SunBuilder();
        config.accept(builder);
        bodies.add(builder.build());
        return this;
    }

    public SkyBuilder moon(Consumer<MoonBuilder> config) {
        MoonBuilder builder = new MoonBuilder();
        config.accept(builder);
        bodies.add(builder.build());
        return this;
    }

    public SkyBuilder body(Consumer<CelestialBodyBuilder<?>> config) {
        return this;
    }

    public List<CelestialBody> build() {
        return Collections.unmodifiableList(bodies);
    }
}
