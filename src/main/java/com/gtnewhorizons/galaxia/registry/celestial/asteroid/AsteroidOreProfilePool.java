package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

public record AsteroidOreProfilePool(@Nonnull List<Entry> entries) {

    public AsteroidOreProfilePool {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalStateException("Asteroid ore profile pool requires at least one profile");
        }
        entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<AsteroidOreProfile> profiles() {
        return entries.stream()
            .map(Entry::profile)
            .toList();
    }

    public AsteroidOreProfile select(double roll) {
        if (!Double.isFinite(roll)) throw new IllegalArgumentException("ore profile roll must be finite");
        double clampedRoll = Math.max(0.0, Math.min(Math.nextDown(1.0), roll));
        double totalWeight = 0.0;
        for (Entry entry : entries) {
            totalWeight += entry.weight();
        }
        double cursor = clampedRoll * totalWeight;
        for (Entry entry : entries) {
            cursor -= entry.weight();
            if (cursor < 0.0) return entry.profile();
        }
        return entries.get(entries.size() - 1)
            .profile();
    }

    public AsteroidOreProfile requireProfile(String id) {
        return entries.stream()
            .map(Entry::profile)
            .filter(
                profile -> profile.id()
                    .equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown asteroid ore profile: " + id));
    }

    public record Entry(@Nonnull AsteroidOreProfile profile, double weight) {

        public Entry {
            if (profile == null) {
                throw new IllegalArgumentException("ore profile cannot be null");
            }
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("ore profile weight must be finite and positive");
            }
        }
    }

    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();

        public Builder profile(@Nonnull AsteroidOreProfile profile, double weight) {
            entries.add(new Entry(profile, weight));
            return this;
        }

        public AsteroidOreProfilePool build() {
            return new AsteroidOreProfilePool(entries);
        }
    }
}
