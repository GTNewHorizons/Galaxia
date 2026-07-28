package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.InventoryKey;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.satellite.SatelliteKind;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class SystemAssetPanelStressTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void filtersMultipleStationsAndOutpostsInOneSystem() {
        List<CelestialAsset> assets = stressAssets();

        assertEquals(
            List.of("Alpha Station", "Beta Station", "Delta Station Build"),
            namesAcceptedBy(assets, SystemAssetFilter.STATIONS));
        assertEquals(List.of("Gamma Outpost", "Epsilon Outpost"), namesAcceptedBy(assets, SystemAssetFilter.OUTPOSTS));
        assertEquals(
            List.of("Beta Station", "Delta Station Build"),
            namesAcceptedBy(assets, SystemAssetFilter.WARNINGS));
        assertEquals(List.of("Epsilon Outpost"), namesAcceptedBy(assets, SystemAssetFilter.MINING));
        assertEquals(List.of("Gamma Outpost"), namesAcceptedBy(assets, SystemAssetFilter.PRODUCTION));
        assertEquals(List.of("Delta Station Build"), namesAcceptedBy(assets, SystemAssetFilter.CONSTRUCTION));
    }

    @Test
    void satelliteRowsAreGroupedOutsideManageableAssetRows() {
        List<CelestialAsset> assets = List.of(
            asset(
                "Mars Outpost",
                CelestialAsset.Kind.AUTOMATED_OUTPOST,
                CelestialObjectId.MARS,
                Buildable.Status.OPERATIONAL,
                WarningPriority.NONE,
                true,
                false),
            satellite(CelestialObjectId.MARS, SatelliteKind.COMMUNICATION),
            satellite(CelestialObjectId.MARS, SatelliteKind.COMMUNICATION),
            satellite(CelestialObjectId.MARS, SatelliteKind.PROSPECTING),
            satellite(CelestialObjectId.MOON, SatelliteKind.COMMUNICATION));

        assertEquals(
            1,
            SolarSystemAssetPanelWidget.assetRows(assets, SystemAssetFilter.ALL, SystemAssetSort.BY_BODY)
                .size());
        assertEquals(
            List.of("MARS:COMMUNICATION:2", "MARS:PROSPECTING:1", "MOON:COMMUNICATION:1"),
            SolarSystemAssetPanelWidget.satelliteRows(assets)
                .stream()
                .map(
                    row -> row.bodyId()
                        .requireRegisteredBodyId() + ":"
                        + row.kind()
                        + ":"
                        + row.count())
                .collect(Collectors.toList()));
    }

    @Test
    void manageAssetsModalSeparatesDeployedAssetsFromAggregatedSatellites() {
        List<CelestialAsset> assets = List.of(
            asset(
                "Mars Outpost",
                CelestialAsset.Kind.AUTOMATED_OUTPOST,
                CelestialObjectId.MARS,
                Buildable.Status.OPERATIONAL,
                WarningPriority.NONE,
                true,
                false),
            satellite(CelestialObjectId.MARS, SatelliteKind.COMMUNICATION),
            satellite(CelestialObjectId.MARS, SatelliteKind.COMMUNICATION),
            satellite(CelestialObjectId.MARS, SatelliteKind.PROSPECTING));

        assertEquals(List.of("Mars Outpost"), namesOf(StarmapAssetActions.deployedAssetRows(assets)));
        assertEquals(
            List.of("COMMUNICATION:2", "PROSPECTING:1"),
            StarmapAssetActions.satelliteAssetRows(assets)
                .stream()
                .map(row -> row.kind() + ":" + row.count())
                .collect(Collectors.toList()));
    }

    @Test
    void warningsFirstSortKeepsHighestWarningsAheadOfConstructionAndNameFallbacks() {
        List<CelestialAsset> assets = stressAssets().stream()
            .sorted(SystemAssetSort.BY_WARNINGS_FIRST.comparator())
            .filter(SystemAssetFilter.ALL::accepts)
            .collect(Collectors.toList());

        assertEquals(
            List.of("Beta Station", "Delta Station Build", "Alpha Station", "Epsilon Outpost", "Gamma Outpost"),
            namesOf(assets));
        assertTrue(
            assets.get(0)
                .warningPriority().priority
                > assets.get(1)
                    .warningPriority().priority);
        assertFalse(
            assets.get(2)
                .warningPriority()
                .isWarning());
    }

    @Test
    void openPanelDoesNotRefreshRowsEveryUpdate() {
        CelestialObject star = star(CelestialObjectId.OVERWORLD, "Vael");
        AtomicInteger refreshes = new AtomicInteger();
        SolarSystemAssetPanelWidget panel = new SolarSystemAssetPanelWidget(
            star,
            () -> star,
            () -> true,
            id -> {},
            ignored -> {
                refreshes.incrementAndGet();
                return List.of();
            });

        panel.onUpdate();
        panel.onUpdate();

        assertEquals(1, refreshes.get());
    }

    private static List<CelestialAsset> stressAssets() {
        return List.of(
            asset(
                "Alpha Station",
                CelestialAsset.Kind.AUTOMATED_STATION,
                CelestialObjectId.OVERWORLD,
                Buildable.Status.OPERATIONAL,
                WarningPriority.NONE,
                false,
                false),
            asset(
                "Beta Station",
                CelestialAsset.Kind.AUTOMATED_STATION,
                CelestialObjectId.OVERWORLD,
                Buildable.Status.OPERATIONAL,
                WarningPriority.NO_POWER,
                false,
                false),
            asset(
                "Gamma Outpost",
                CelestialAsset.Kind.AUTOMATED_OUTPOST,
                CelestialObjectId.OVERWORLD,
                Buildable.Status.OPERATIONAL,
                WarningPriority.NONE,
                false,
                true),
            asset(
                "Delta Station Build",
                CelestialAsset.Kind.STATION,
                CelestialObjectId.OVERWORLD,
                Buildable.Status.IN_CONSTRUCTION,
                WarningPriority.IDLE,
                false,
                false),
            asset(
                "Epsilon Outpost",
                CelestialAsset.Kind.AUTOMATED_OUTPOST,
                CelestialObjectId.OVERWORLD,
                Buildable.Status.DISABLED,
                WarningPriority.NONE,
                true,
                false),
            asset(
                "Communication Satellite",
                CelestialAsset.Kind.SATELLITE,
                CelestialObjectId.OVERWORLD,
                Buildable.Status.OPERATIONAL,
                WarningPriority.NO_POWER,
                true,
                false));
    }

    private static CelestialObject star(CelestialObjectId id, String name) {
        return CelestialObject.builder()
            .id(id)
            .name(name)
            .objectClass(CelestialObject.Class.STAR)
            .build();
    }

    private static List<String> namesAcceptedBy(List<CelestialAsset> assets, SystemAssetFilter filter) {
        return namesOf(
            assets.stream()
                .filter(filter::accepts)
                .collect(Collectors.toList()));
    }

    private static List<String> namesOf(List<CelestialAsset> assets) {
        return assets.stream()
            .map(CelestialAsset::displayName)
            .collect(Collectors.toList());
    }

    private static CelestialAsset asset(String name, CelestialAsset.Kind kind, CelestialObjectId body,
        Buildable.Status status, WarningPriority warning, boolean mining, boolean production) {
        FakeAsset asset = new FakeAsset(kind, body, status, warning, mining, production);
        asset.setDisplayName(name);
        return asset;
    }

    private static CelestialAsset satellite(CelestialObjectId body, SatelliteKind kind) {
        return CelestialAsset.create(body, CelestialAsset.Kind.SATELLITE, Buildable.Status.OPERATIONAL, kind);
    }

    private static final class FakeAsset extends CelestialAsset {

        private final WarningPriority warning;
        private final boolean mining;
        private final boolean production;

        private FakeAsset(Kind kind, CelestialObjectId body, Buildable.Status status, WarningPriority warning,
            boolean mining, boolean production) {
            super(ID.create(), body, kind, status, Collections.emptyMap());
            this.warning = warning;
            this.mining = mining;
            this.production = production;
        }

        @Override
        public boolean hasMiningCapability() {
            return mining;
        }

        @Override
        public boolean hasProductionCapability() {
            return production;
        }

        @Override
        public WarningPriority warningPriority() {
            return warning;
        }

        @Override
        public void tick() {}

        @Override
        public long updateContents(InventoryKey item, long delta, boolean sync) {
            return updateContents(item, delta);
        }

        public String getInventoryName() {
            return "fake";
        }

        @Override
        public boolean tryConsumeEnergy(long powerDraw) {
            return false;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public Stream<ModuleInstance> forEachModule() {
            return Stream.of();
        }
    }
}
