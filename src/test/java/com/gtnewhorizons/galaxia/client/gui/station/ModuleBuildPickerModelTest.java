package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.feature.PlanetaryFeatureRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleBuildPickerModelTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void compatibleBuildTargetMustBeEmptyAdjacentTile() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        assertTrue(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.of(1, 0)));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.CORE));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.of(5, 5)));
    }

    @Test
    void incompatibleModuleKindOrTierCannotBePicked() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.POWER,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                StationTileCoord.of(1, 0)));
    }

    @Test
    void selectedBuildTargetsUnlockAdjacentTiles() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord chained = StationTileCoord.of(2, 0);

        assertFalse(
            ModuleBuildPickerModel
                .isCompatibleTarget(facility, FacilityModuleKind.STORAGE, ModuleShape.SINGLE, ModuleTier.HV, chained));
        assertTrue(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                chained,
                List.of(first)));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.STORAGE,
                ModuleShape.SINGLE,
                ModuleTier.HV,
                first,
                List.of(first)));
    }

    @Test
    void selectedMultiTileBuildTargetsUnlockAdjacentFootprints() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_OUTPOST,
            Buildable.Status.OPERATIONAL);

        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord chained = StationTileCoord.of(3, 0);
        StationTileCoord overlapping = StationTileCoord.of(2, 0);

        assertFalse(
            ModuleBuildPickerModel
                .isCompatibleTarget(facility, FacilityModuleKind.MINER, ModuleShape.QUAD_2x2, ModuleTier.EV, chained));
        assertTrue(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.MINER,
                ModuleShape.QUAD_2x2,
                ModuleTier.EV,
                chained,
                List.of(first)));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.MINER,
                ModuleShape.QUAD_2x2,
                ModuleTier.EV,
                overlapping,
                List.of(first)));
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.MINER,
                ModuleShape.QUAD_2x2,
                ModuleTier.EV,
                first,
                List.of(first)));
        assertEquals(
            List.of(first, chained),
            ModuleBuildPickerModel.connectedTargets(facility, List.of(first, chained), ModuleShape.QUAD_2x2));
    }

    @Test
    void twoByTwoAnchorFollowsSelectedRotation() {
        StationTileCoord tile = StationTileCoord.of(5, 5);

        assertEquals(
            StationTileCoord.of(5, 5),
            ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 0));
        assertEquals(
            StationTileCoord.of(4, 5),
            ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 1));
        assertEquals(
            StationTileCoord.of(4, 5),
            ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 5));
        assertEquals(
            StationTileCoord.of(4, 4),
            ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 2));
        assertEquals(
            StationTileCoord.of(5, 4),
            ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 3));
    }

    @Test
    void twoByTwoClickTileFollowsSelectedAnchorRotation() {
        StationTileCoord anchor = StationTileCoord.of(5, 5);

        assertEquals(
            StationTileCoord.of(5, 5),
            ModuleBuildPickerModel.tileForAnchorRotation(anchor, ModuleShape.QUAD_2x2, 0));
        assertEquals(
            StationTileCoord.of(6, 5),
            ModuleBuildPickerModel.tileForAnchorRotation(anchor, ModuleShape.QUAD_2x2, 1));
        assertEquals(
            StationTileCoord.of(6, 6),
            ModuleBuildPickerModel.tileForAnchorRotation(anchor, ModuleShape.QUAD_2x2, 2));
        assertEquals(
            StationTileCoord.of(5, 6),
            ModuleBuildPickerModel.tileForAnchorRotation(anchor, ModuleShape.QUAD_2x2, 3));
    }

    @Test
    void twoByTwoAnchorReturnsNullWhenRotationWouldLeaveMap() {
        StationTileCoord tile = StationTileCoord.of(StationTileCoord.MIN, StationTileCoord.MIN);

        assertNull(ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 1));
        assertNull(ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 2));
        assertNull(ModuleBuildPickerModel.anchorForRotation(tile, ModuleShape.QUAD_2x2, 3));
    }

    @Test
    void disconnectedBuildTargetsArePrunedAfterSelectionChanges() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);

        StationTileCoord first = StationTileCoord.of(1, 0);
        StationTileCoord second = StationTileCoord.of(2, 0);
        StationTileCoord third = StationTileCoord.of(3, 0);

        assertEquals(
            List.of(first, second, third),
            ModuleBuildPickerModel.connectedTargets(facility, List.of(first, second, third), ModuleShape.SINGLE));
        assertEquals(
            List.of(),
            ModuleBuildPickerModel.connectedTargets(facility, List.of(second, third), ModuleShape.SINGLE));
        assertEquals(
            List.of(first),
            ModuleBuildPickerModel.connectedTargets(facility, List.of(first, third), ModuleShape.SINGLE));
    }

    @Test
    void geothermalGeneratorRequiresMagmaPoolUnderCenterAnchor() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        StationTileCoord center = StationTileCoord.of(2, 0);

        setSaltWithFeatureAt(facility, center, true);
        assertTrue(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.GEOTHERMAL_GENERATOR,
                ModuleShape.BLOCK_3x3,
                ModuleTier.HV,
                center));

        setSaltWithFeatureAt(facility, center, false);
        assertFalse(
            ModuleBuildPickerModel.isCompatibleTarget(
                facility,
                FacilityModuleKind.GEOTHERMAL_GENERATOR,
                ModuleShape.BLOCK_3x3,
                ModuleTier.HV,
                center));
    }

    private static void setSaltWithFeatureAt(AutomatedFacility facility, StationTileCoord coord, boolean required) {
        for (long salt = 0; salt < 100_000L; salt++) {
            facility.setStationFeatureSalt(salt);
            boolean hasVent = facility.planetaryFeaturesAt(coord)
                .contains(PlanetaryFeatureRegistry.MAGMA_POOL.key());
            if (hasVent == required) return;
        }
        throw new AssertionError("Could not find magma pool salt for " + coord);
    }
}
