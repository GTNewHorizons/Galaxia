package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;

final class AsteroidNodeMaterializer {

    private AsteroidNodeMaterializer() {}

    static AsteroidFieldNode resolveNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        long baseSeed = AsteroidFieldDeterminism.nodeSeed(beltId, profile, index);
        MinorCelestialBodyId id = new MinorCelestialBodyId(beltId, index);
        AuthoredAsteroidDefinition definition = profile.authoredAsteroid(index)
            .orElse(null);
        AsteroidSizeClass sizeClass = definition == null
            ? AsteroidGeneratedSlotAllocator.generatedSizeClass(profile, index)
            : definition.sizeClass();
        return new AsteroidFieldNode(
            id,
            beltId,
            index,
            definition == null ? displayName(beltId, index) : definition.displayName(),
            definition == null ? AsteroidNodeKind.GENERATED : definition.kind(),
            sizeClass,
            definition == null ? AsteroidInitialKnowledgeRules.defaultInitialDetectionState(sizeClass)
                : definition.initialDetectionState(),
            definition == null ? null : definition.initialOreKnowledgeState(),
            definition == null
                ? AsteroidGeneratedSlotAllocator.generatedAngleOffsetDeg(profile, index, baseSeed, sizeClass)
                : definition.angleOffsetDeg() != null ? definition.angleOffsetDeg()
                    : AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 1L)) * 360.0,
            definition != null && definition.orbitalDepth01() != null ? definition.orbitalDepth01()
                : AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 2L)),
            definition != null && definition.oreProfileId() != null
                ? selectOreProfile(profile, definition.oreProfileId())
                : selectOreProfile(
                    profile,
                    AsteroidFieldDeterminism.unitDouble(AsteroidFieldDeterminism.mix(baseSeed, 3L))),
            definition != null && definition.appearance() != null ? definition.appearance()
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
        return profile.oreProfilePool()
            .select(roll);
    }

    private static AsteroidOreProfile selectOreProfile(AsteroidFieldProfile profile, String oreProfileId) {
        return profile.oreProfilePool()
            .requireProfile(oreProfileId);
    }

    private static String displayName(CelestialObjectId beltId, int index) {
        int displayNumber = AsteroidSlotRanges.isGeneratedSlot(index) ? AsteroidSlotRanges.generatedOrdinal(index) + 1
            : index + 1;
        return beltId.name() + " " + displayNumber;
    }
}
