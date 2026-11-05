package com.gtnewhorizons.galaxia.registry.celestial.asteroid;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.MinorBodyOrbitSlot;

final class AsteroidNodeMaterializer {

    private static final long AUTHORED_ANGLE_SALT = 1L;
    private static final long AUTHORED_DEPTH_SALT = 2L;
    private static final long ORE_PROFILE_SALT = 3L;
    private static final long APPEARANCE_SALT = 4L;
    private static final String GENERATED_APPEARANCE = "generated_asteroid_tiles";

    private AsteroidNodeMaterializer() {}

    static AsteroidFieldNode resolveAuthoredNode(CelestialObjectId beltId, AsteroidFieldProfile profile,
        AuthoredAsteroidDefinition definition) {
        int index = definition.index();
        AsteroidFieldDeterminism nodeSeed = AsteroidFieldDeterminism.forNode(beltId, profile, index);
        double angleOffsetDeg = definition.angleOffsetDeg() != null ? definition.angleOffsetDeg()
            : nodeSeed.degrees(AUTHORED_ANGLE_SALT);
        double orbitalDepth01 = definition.orbitalDepth01() != null ? definition.orbitalDepth01()
            : nodeSeed.unit(AUTHORED_DEPTH_SALT);
        return new AsteroidFieldNode(
            new MinorCelestialBodyId(beltId, index),
            beltId,
            index,
            definition.displayName(),
            definition.kind(),
            definition.sizeClass(),
            definition.initialDetectionState(),
            definition.initialOreKnowledgeState(),
            new MinorBodyOrbitSlot(angleOffsetDeg, orbitalDepth01),
            definition.oreProfileId() != null ? profile.requireOreProfile(definition.oreProfileId())
                : profile.selectOreProfile(nodeSeed.unit(ORE_PROFILE_SALT)),
            definition.appearance() != null ? definition.appearance()
                : new AsteroidAppearanceProfile(GENERATED_APPEARANCE, nodeSeed.seed(APPEARANCE_SALT)));
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
            AsteroidFieldResolver.defaultInitialDetectionState(sizeClass),
            null,
            new MinorBodyOrbitSlot(angleOffsetDeg, orbitalDepth01),
            profile.selectOreProfile(nodeSeed.unit(ORE_PROFILE_SALT)),
            new AsteroidAppearanceProfile(GENERATED_APPEARANCE, nodeSeed.seed(APPEARANCE_SALT)));
    }

    private static String displayName(CelestialObjectId beltId, int index) {
        int displayNumber = AsteroidSlotRanges.isGeneratedSlot(index) ? AsteroidSlotRanges.generatedOrdinal(index) + 1
            : index + 1;
        return beltId.name() + " " + displayNumber;
    }
}
