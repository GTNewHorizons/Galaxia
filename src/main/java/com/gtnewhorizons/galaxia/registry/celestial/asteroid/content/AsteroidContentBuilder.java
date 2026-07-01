package com.gtnewhorizons.galaxia.registry.celestial.asteroid.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidAppearanceProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidDetectionState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodeKind;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidNodePreset;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreKnowledgeState;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidOreProfile;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSizeClass;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;

/**
 * Authoring facade for asteroid field content.
 *
 * Field entries define generated pools and counts. Lore/unique entries are
 * authored presets that reserve stable slots inside those fields.
 */
public final class AsteroidContentBuilder {

    private final Map<CelestialObjectId, FieldBuilder> fieldsByBelt = new LinkedHashMap<>();
    private final List<AuthoredAsteroidBuilder> authoredAsteroids = new ArrayList<>();

    public AsteroidContentBuilder field(CelestialObjectId beltId, Consumer<FieldBuilder> config) {
        Objects.requireNonNull(beltId, "beltId cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        FieldBuilder builder = fieldsByBelt.computeIfAbsent(beltId, ignored -> new FieldBuilder());
        config.accept(builder);
        return this;
    }

    public AsteroidContentBuilder lore(String contentId, Consumer<AuthoredAsteroidBuilder> config) {
        return authored(contentId, AsteroidNodeKind.LORE, config);
    }

    public AsteroidContentBuilder unique(String contentId, Consumer<AuthoredAsteroidBuilder> config) {
        return authored(contentId, AsteroidNodeKind.UNIQUE, config);
    }

    private AsteroidContentBuilder authored(String contentId, AsteroidNodeKind kind,
        Consumer<AuthoredAsteroidBuilder> config) {
        Objects.requireNonNull(config, "config cannot be null");
        AuthoredAsteroidBuilder builder = new AuthoredAsteroidBuilder(contentId, kind);
        config.accept(builder);
        authoredAsteroids.add(builder);
        return this;
    }

    public Map<CelestialObjectId, AsteroidFieldProfile> buildProfiles() {
        Map<CelestialObjectId, AsteroidFieldProfile.Builder> profileBuilders = new LinkedHashMap<>();
        for (Map.Entry<CelestialObjectId, FieldBuilder> entry : fieldsByBelt.entrySet()) {
            profileBuilders.put(
                entry.getKey(),
                entry.getValue()
                    .toProfileBuilder());
        }

        Map<String, AuthoredAsteroidBuilder> contentIds = new LinkedHashMap<>();
        Map<CelestialObjectId, Map<Integer, String>> slotsByBelt = new LinkedHashMap<>();
        for (AuthoredAsteroidBuilder asteroid : authoredAsteroids) {
            asteroid.validate();
            if (contentIds.put(asteroid.contentId, asteroid) != null) {
                throw new IllegalStateException("Duplicate asteroid content id: " + asteroid.contentId);
            }
            String previousContent = slotsByBelt.computeIfAbsent(asteroid.beltId, ignored -> new LinkedHashMap<>())
                .put(asteroid.slot, asteroid.contentId);
            if (previousContent != null) {
                throw new IllegalStateException(
                    "Duplicate asteroid slot " + asteroid.slot
                        + " in "
                        + asteroid.beltId
                        + ": "
                        + previousContent
                        + " and "
                        + asteroid.contentId);
            }
            AsteroidFieldProfile.Builder profileBuilder = profileBuilders.get(asteroid.beltId);
            if (profileBuilder == null) {
                throw new IllegalStateException(
                    "Authored asteroid references belt without generated field: " + asteroid.beltId);
            }
            profileBuilder.nodePreset(asteroid.toPreset());
        }

        Map<CelestialObjectId, AsteroidFieldProfile> profiles = new LinkedHashMap<>();
        for (Map.Entry<CelestialObjectId, AsteroidFieldProfile.Builder> entry : profileBuilders.entrySet()) {
            profiles.put(
                entry.getKey(),
                entry.getValue()
                    .build());
        }
        return Collections.unmodifiableMap(profiles);
    }

    public static final class FieldBuilder {

        private long seedSalt;
        private int generationVersion = 1;
        private int largeCount;
        private int mediumCount;
        private int smallCount;
        private double innerOrbitalRadius;
        private double outerOrbitalRadius;
        private double satelliteScanRadius = Double.NaN;
        private final List<AsteroidOreProfile> oreProfiles = new ArrayList<>();

        public FieldBuilder seedSalt(long value) {
            this.seedSalt = value;
            return this;
        }

        public FieldBuilder generationVersion(int value) {
            this.generationVersion = value;
            return this;
        }

        public FieldBuilder sizeCounts(int large, int medium, int small) {
            this.largeCount = large;
            this.mediumCount = medium;
            this.smallCount = small;
            return this;
        }

        public FieldBuilder radialBand(double innerRadius, double outerRadius) {
            this.innerOrbitalRadius = innerRadius;
            this.outerOrbitalRadius = outerRadius;
            return this;
        }

        public FieldBuilder satelliteScanRadius(double value) {
            this.satelliteScanRadius = value;
            return this;
        }

        public FieldBuilder oreProfile(AsteroidOreProfile value) {
            this.oreProfiles.add(Objects.requireNonNull(value, "ore profile cannot be null"));
            return this;
        }

        private AsteroidFieldProfile.Builder toProfileBuilder() {
            AsteroidFieldProfile.Builder builder = AsteroidFieldProfile.builder()
                .seedSalt(seedSalt)
                .generationVersion(generationVersion)
                .sizeCounts(largeCount, mediumCount, smallCount)
                .radialBand(innerOrbitalRadius, outerOrbitalRadius)
                .satelliteScanRadius(satelliteScanRadius);
            for (AsteroidOreProfile oreProfile : oreProfiles) {
                builder.oreProfile(oreProfile);
            }
            return builder;
        }
    }

    public static final class AuthoredAsteroidBuilder {

        private final String contentId;
        private final AsteroidNodeKind kind;
        private CelestialObjectId beltId;
        private Integer slot;
        private String displayName;
        private AsteroidSizeClass sizeClass = AsteroidSizeClass.MEDIUM;
        private AsteroidDetectionState detectionState = AsteroidDetectionState.HIDDEN;
        private AsteroidOreKnowledgeState oreKnowledgeState;
        private Double angleOffsetDeg;
        private Double orbitalDepth01;
        private String oreProfileId;
        private AsteroidAppearanceProfile appearance;

        private AuthoredAsteroidBuilder(String contentId, AsteroidNodeKind kind) {
            if (contentId == null || contentId.isBlank()) {
                throw new IllegalArgumentException("contentId is required");
            }
            this.contentId = contentId;
            this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        }

        public AuthoredAsteroidBuilder belt(CelestialObjectId value) {
            this.beltId = Objects.requireNonNull(value, "beltId cannot be null");
            return this;
        }

        public AuthoredAsteroidBuilder slot(int value) {
            this.slot = value;
            return this;
        }

        public AuthoredAsteroidBuilder autoSlot() {
            UUID stableId = UUID.nameUUIDFromBytes(contentId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Unique asteroids may opt into deterministic placement without a
            // manually assigned slot. Collisions are still checked during build.
            this.slot = AsteroidSlotRanges.UNIQUE_SLOT_MIN + Math.floorMod(
                stableId.hashCode(),
                AsteroidSlotRanges.UNIQUE_SLOT_MAX - AsteroidSlotRanges.UNIQUE_SLOT_MIN + 1);
            return this;
        }

        public AuthoredAsteroidBuilder name(String value) {
            this.displayName = value;
            return this;
        }

        public AuthoredAsteroidBuilder size(AsteroidSizeClass value) {
            this.sizeClass = Objects.requireNonNull(value, "sizeClass cannot be null");
            return this;
        }

        public AuthoredAsteroidBuilder detected() {
            this.detectionState = AsteroidDetectionState.DETECTED;
            return this;
        }

        public AuthoredAsteroidBuilder hidden() {
            this.detectionState = AsteroidDetectionState.HIDDEN;
            this.oreKnowledgeState = AsteroidOreKnowledgeState.UNKNOWN;
            return this;
        }

        public AuthoredAsteroidBuilder oreProfileKnown() {
            this.oreKnowledgeState = AsteroidOreKnowledgeState.PROFILE;
            return this;
        }

        public AuthoredAsteroidBuilder oreSignatureKnown() {
            this.oreKnowledgeState = AsteroidOreKnowledgeState.SIGNATURE;
            return this;
        }

        public AuthoredAsteroidBuilder oreProfile(String value) {
            this.oreProfileId = value;
            return this;
        }

        public AuthoredAsteroidBuilder useBeltOrePool() {
            this.oreProfileId = null;
            return this;
        }

        public AuthoredAsteroidBuilder position(double angleOffsetDeg, double orbitalDepth01) {
            this.angleOffsetDeg = angleOffsetDeg;
            this.orbitalDepth01 = orbitalDepth01;
            return this;
        }

        public AuthoredAsteroidBuilder autoPosition() {
            this.angleOffsetDeg = null;
            this.orbitalDepth01 = null;
            return this;
        }

        public AuthoredAsteroidBuilder appearance(AsteroidAppearanceProfile value) {
            this.appearance = Objects.requireNonNull(value, "appearance cannot be null");
            return this;
        }

        private void validate() {
            if (beltId == null) throw new IllegalStateException("Authored asteroid requires a belt: " + contentId);
            if (slot == null) throw new IllegalStateException("Authored asteroid requires a slot: " + contentId);
            if (kind == AsteroidNodeKind.LORE && !AsteroidSlotRanges.isLoreSlot(slot)) {
                throw new IllegalStateException("Lore asteroid slot must be in 0..1999: " + contentId);
            }
            if (kind == AsteroidNodeKind.UNIQUE && !AsteroidSlotRanges.isUniqueSlot(slot)) {
                throw new IllegalStateException("Unique asteroid slot must be in 2000..3999: " + contentId);
            }
        }

        private AsteroidNodePreset toPreset() {
            validate();
            return new AsteroidNodePreset(
                slot,
                kind,
                contentId,
                displayName == null ? contentId : displayName,
                sizeClass,
                detectionState,
                oreKnowledgeState,
                angleOffsetDeg,
                orbitalDepth01,
                oreProfileId,
                appearance);
        }
    }
}
