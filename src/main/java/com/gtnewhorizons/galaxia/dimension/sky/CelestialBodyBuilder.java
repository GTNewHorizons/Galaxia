package com.gtnewhorizons.galaxia.dimension.sky;

import net.minecraft.util.ResourceLocation;

public abstract class CelestialBodyBuilder<T extends CelestialBodyBuilder<T>> {

    protected ResourceLocation texture;
    protected ResourceLocation phaseTexture;
    protected float size = 20f;
    protected double distance = 100.0;
    protected float inclination = 0f;
    protected long orbitalPeriodTicks = 24000L;

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public T texture(ResourceLocation texture) {
        this.texture = texture;
        return self();
    }

    public T texture(String texture) {
        return texture(new ResourceLocation(texture));
    }

    public T size(float size) {
        this.size = size;
        return self();
    }

    public T distance(double distance) {
        this.distance = distance;
        return self();
    }

    public T inclination(float inclination) {
        this.inclination = inclination;
        return self();
    }

    public T period(long orbitalPeriodTicks) {
        this.orbitalPeriodTicks = orbitalPeriodTicks;
        return self();
    }

    protected void validate() {
        if (texture == null) throw new IllegalStateException("Texture is required");
    }

    public abstract CelestialBody build();
}
