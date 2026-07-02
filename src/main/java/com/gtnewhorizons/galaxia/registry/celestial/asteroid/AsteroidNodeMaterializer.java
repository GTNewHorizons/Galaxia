package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidNodeMaterializer {

    private AsteroidNodeMaterializer() {}

    static AsteroidFieldNode resolveNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
        MinorCelestialBodyId id = new MinorCelestialBodyId(beltId, index);
        AsteroidNodePreset preset = profile.nodePreset(index)
            .orElse(null);
        AsteroidSizeClass sizeClass = preset == null ? AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index)
            : preset.sizeClass();
        return new AsteroidFieldNode(
            id,
            beltId,
            index,
            preset == null ? displayName(beltId, index) : preset.displayName(),
            preset == null ? AsteroidNodeKind.GENERATED : preset.kind(),
            sizeClass,
            preset == null ? AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass)
                : preset.initialDetectionState(),
            preset == null ? null : preset.initialOreKnowledgeState(),
            preset == null ? AsteroidGeneratedSlotAllocator.generatedAngleOffsetDeg(profile, index, baseSeed, sizeClass)
                : preset.angleOffsetDeg() != null ? preset.angleOffsetDeg()
                    : AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) * 360.0,
            preset != null && preset.orbitalDepth01() != null ? preset.orbitalDepth01()
                : AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 2L)),
            preset != null && preset.oreProfileId() != null ? selectOreProfile(profile, preset.oreProfileId())
                : selectOreProfile(
                    profile,
                    AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            preset != null && preset.appearance() != null ? preset.appearance()
                : new AsteroidAppearanceProfile(
                    "generated_asteroid_tiles",
                    AsteroidFieldDeterminism.mix(baseSeed, 4L)));
    }

    static AsteroidFieldNode resolveGeneratedNodeAtPosition(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index, double angleOffsetDeg, double orbitalDepth01) {
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
        AsteroidSizeClass sizeClass = AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index);
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName(beltId, index),
            AsteroidNodeKind.GENERATED,
            sizeClass,
            AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass),
            null,
            angleOffsetDeg,
            orbitalDepth01,
            selectOreProfile(profile, AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", AsteroidFieldDeterminism.mix(baseSeed, 4L)));
    }

    static AsteroidFieldNode resolveUnregisteredSavedNode(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index) {
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
        // Save data may reference a node that no longer exists in the current
        // profile. Keep the body addressable so player assets are not orphaned.
        AsteroidSizeClass sizeClass = AsteroidSizeClass.SMALL;
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            displayName(beltId, index),
            savedNodeKind(index),
            sizeClass,
            AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass),
            AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) * 360.0,
            AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 2L)),
            selectOreProfile(profile, AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", AsteroidFieldDeterminism.mix(baseSeed, 4L)));
    }

    private static AsteroidNodeKind savedNodeKind(int index) {
        if (AsteroidSlotRanges.isLoreSlot(index)) return AsteroidNodeKind.LORE;
        if (AsteroidSlotRanges.isUniqueSlot(index)) return AsteroidNodeKind.UNIQUE;
        return AsteroidNodeKind.GENERATED;
    }

    private static AsteroidOreProfile selectOreProfile(AsteroidFieldProfile profile, double roll) {
        double totalWeight = 0.0;
        for (AsteroidOreProfile oreProfile : profile.oreProfiles()) {
            totalWeight += oreProfile.weight();
        }
        double cursor = roll * totalWeight;
        for (AsteroidOreProfile oreProfile : profile.oreProfiles()) {
            cursor -= oreProfile.weight();
            if (cursor <= 0.0) return oreProfile;
        }
        return profile.oreProfiles()
            .get(
                profile.oreProfiles()
                    .size() - 1);
    }

    private static AsteroidOreProfile selectOreProfile(AsteroidFieldProfile profile, String oreProfileId) {
        return profile.oreProfiles()
            .stream()
            .filter(
                oreProfile -> oreProfile.id()
                    .equals(oreProfileId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown asteroid ore profile: " + oreProfileId));
    }

    private static String displayName(CelestialObjectId beltId, int index) {
        int displayNumber = AsteroidSlotRanges.isGeneratedSlot(index) ? AsteroidSlotRanges.generatedOrdinal(index) + 1
            : index + 1;
        return beltId.name() + " " + displayNumber;
    }
}
