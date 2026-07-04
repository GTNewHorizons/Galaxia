package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorBodyOrbitSlot;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;

final class AsteroidNodeMaterializer {

    private AsteroidNodeMaterializer() {}

    static AsteroidFieldNode resolveNode(CelestialObjectId beltId, AsteroidFieldProfile profile, int index) {
        AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
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
            orbitSlot(profile, index, nodeSeed, definition, sizeClass),
            definition != null && definition.oreProfileId() != null
                ? selectOreProfile(profile, definition.oreProfileId())
                : selectOreProfile(profile, nodeSeed.unit(3L)),
            definition != null && definition.appearance() != null ? definition.appearance()
                : new AsteroidAppearanceProfile("generated_asteroid_tiles", nodeSeed.seed(4L)));
    }

    static AsteroidFieldNode resolveGeneratedNodeAtPosition(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index, double angleOffsetDeg, double orbitalDepth01) {
        AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
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
            new MinorBodyOrbitSlot(angleOffsetDeg, orbitalDepth01),
            selectOreProfile(profile, nodeSeed.unit(3L)),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", nodeSeed.seed(4L)));
    }

    static AsteroidFieldNode resolveUnregisteredSavedNode(CelestialObjectId beltId, AsteroidFieldProfile profile,
        int index) {
        AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
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
            null,
            new MinorBodyOrbitSlot(nodeSeed.degrees(1L), nodeSeed.unit(2L)),
            selectOreProfile(profile, nodeSeed.unit(3L)),
            new AsteroidAppearanceProfile("generated_asteroid_tiles", nodeSeed.seed(4L)));
    }

    private static MinorBodyOrbitSlot orbitSlot(AsteroidFieldProfile profile, int index,
        AsteroidFieldDeterminism nodeSeed, AuthoredAsteroidDefinition definition, AsteroidSizeClass sizeClass) {
        double angleOffsetDeg = definition == null
            ? AsteroidGeneratedSlotAllocator.generatedAngleOffsetDeg(profile, index, nodeSeed, sizeClass)
            : definition.angleOffsetDeg() != null ? definition.angleOffsetDeg() : nodeSeed.degrees(1L);
        double orbitalDepth01 = definition != null && definition.orbitalDepth01() != null ? definition.orbitalDepth01()
            : nodeSeed.unit(2L);
        return new MinorBodyOrbitSlot(angleOffsetDeg, orbitalDepth01);
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
