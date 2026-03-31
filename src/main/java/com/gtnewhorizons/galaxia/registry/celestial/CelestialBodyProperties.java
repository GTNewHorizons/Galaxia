package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CelestialBodyProperties(boolean visitable, boolean canCreateStation, boolean canCreateOutpost,
    String oreProfile, double radiation, double temperature, Map<String, String> metadata) {

    public CelestialBodyProperties {
        metadata = metadata == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean visitable;
        private boolean canCreateStation;
        private boolean canCreateOutpost;
        private String oreProfile = "";
        private double radiation;
        private double temperature;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        public Builder visitable(boolean value) {
            this.visitable = value;
            return this;
        }

        public Builder canCreateStation(boolean value) {
            this.canCreateStation = value;
            return this;
        }

        public Builder canCreateOutpost(boolean value) {
            this.canCreateOutpost = value;
            return this;
        }

        public Builder oreProfile(String value) {
            this.oreProfile = value;
            return this;
        }

        public Builder radiation(double value) {
            this.radiation = value;
            return this;
        }

        public Builder temperature(double value) {
            this.temperature = value;
            return this;
        }

        public Builder metadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public CelestialBodyProperties build() {
            return new CelestialBodyProperties(
                visitable,
                canCreateStation,
                canCreateOutpost,
                oreProfile,
                radiation,
                temperature,
                metadata);
        }
    }
}
