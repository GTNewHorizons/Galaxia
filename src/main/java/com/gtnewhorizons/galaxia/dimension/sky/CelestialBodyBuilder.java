package com.gtnewhorizons.galaxia.dimension.sky;

import net.minecraft.util.ResourceLocation;

public class CelestialBodyBuilder {

    protected ResourceLocation texture;
    protected float size = 20f;
    protected double distance = 100.0;
    protected float inclination = 0f;
    protected long orbitalPeriodTicks = 24000L;
    protected long phaseOffsetTicks = 0L;
    protected boolean isMainLightSource = false;

    private boolean hasPhases = false;
    private int phaseCount = 8;

    /**
     * Sets the main texture file.
     *
     * @param texture The texture location (example: "galaxia:sun")
     * @return builder
     */
    public CelestialBodyBuilder texture(ResourceLocation texture) {
        this.texture = texture;
        return this;
    }

    /**
     * Sets the main texture file using a string.
     *
     * @param texture The texture path as string
     * @return builder
     */
    public CelestialBodyBuilder texture(String texture) {
        return texture(new ResourceLocation(texture));
    }

    /**
     * Sets the body as a main light source on a planet
     *
     * @return builder
     */
    public CelestialBodyBuilder mainLightSource() {
        this.isMainLightSource = true;
        return this;
    }

    /**
     * How big the body looks in the sky.
     *
     * @param size Size multiplier (bigger number = bigger body)
     * @return builder
     */
    public CelestialBodyBuilder size(float size) {
        this.size = size;
        return this;
    }

    /**
     * How far the body is from the player.
     *
     * @param distance Distance value (higher = farther away)
     * @return builder
     */
    public CelestialBodyBuilder distance(double distance) {
        this.distance = distance;
        return this;
    }

    /**
     * Tilt angle of the orbit.
     *
     * @param inclination Tilt in degrees
     * @return builder
     */
    public CelestialBodyBuilder inclination(float inclination) {
        this.inclination = inclination;
        return this;
    }

    /**
     * How long one full orbit takes.
     *
     * @param ticks Number of ticks for one full cycle (24000 = 1 Minecraft day)
     * @return builder
     */
    public CelestialBodyBuilder period(long ticks) {
        this.orbitalPeriodTicks = ticks;
        return this;
    }

    /**
     * Shifts when the phases start.
     *
     * @param ticks Offset in ticks
     * @return builder
     */
    public CelestialBodyBuilder phaseOffset(long ticks) {
        this.phaseOffsetTicks = ticks;
        return this;
    }

    /**
     * Should the body change appearance over time (like the moon).
     *
     * @param enabled true - uses phases
     * @return builder
     */
    public CelestialBodyBuilder hasPhases(boolean enabled) {
        this.hasPhases = enabled;
        return this;
    }

    /**
     * Shortcut to turn phases on.
     *
     * @return builder
     */
    public CelestialBodyBuilder hasPhases() {
        return hasPhases(true);
    }

    /**
     * How many different looks the body has.
     *
     * @param count Number of phases (usually 8 for moon)
     * @return builder
     */
    public CelestialBodyBuilder phaseCount(int count) {
        this.phaseCount = count;
        return this;
    }

    /**
     * Creates the final celestial body.
     *
     * @return ready CelestialBody object
     */
    public CelestialBody build() {
        validate();

        return new CelestialBody(
            texture,
            size,
            distance,
            inclination,
            orbitalPeriodTicks,
            hasPhases,
            hasPhases ? phaseCount : 0,
            phaseOffsetTicks,
            isMainLightSource);
    }

    protected void validate() {
        if (texture == null) {
            throw new IllegalStateException("Texture is required for celestial body");
        }
    }
}
