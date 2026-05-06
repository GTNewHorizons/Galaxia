package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;

final class ModuleMinerTest {

    @BeforeAll
    static void initRegistries() {
        CelestialRegistry.freezeAndBake();
        FacilityModuleRegistry.init();
    }

    @Test
    void ungroupedMinerBlacklistIsSparseAndValidated() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();

        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));

        facility.setMinerOreBlacklisted(miner, "ore:iron", true);
        assertTrue(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertTrue(
            ((ModuleMiner) miner.component()).requireLocalSettings()
                .blacklistedOreKeys()
                .contains("ore:iron"));

        facility.setMinerOreBlacklisted(miner, "ore:iron", false);
        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertFalse(
            ((ModuleMiner) miner.component()).requireLocalSettings()
                .blacklistedOreKeys()
                .contains("ore:iron"));
    }

    @Test
    void bulkBlacklistLoadCrashesOnMalformedOreKey() {
        org.junit.jupiter.api.Assertions
            .assertThrows(IllegalArgumentException.class, () -> new MinerSettings(Set.of("")));
    }

    @Test
    void bulkBlacklistLoadReplacesSparseSet() {
        ModuleInstance miner = createMiner();
        ModuleMiner component = (ModuleMiner) miner.component();
        component.requireLocalSettings()
            .setOreBlacklisted("ore:iron", true);

        component.setLocalSettings(new MinerSettings(Set.of("ore:copper")));

        assertFalse(
            component.requireLocalSettings()
                .isOreBlacklisted("ore:iron"));
        assertTrue(
            component.requireLocalSettings()
                .isOreBlacklisted("ore:copper"));
    }

    @Test
    void blacklistVoidsOreAfterRoll() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.setMinerOreBlacklisted(miner, "ore:iron", true);

        assertTrue(ModuleMiner.shouldVoidOre(miner, facility, "ore:iron"));
        assertFalse(ModuleMiner.shouldVoidOre(miner, facility, "ore:copper"));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance createMiner() {
        ModuleInstance miner = FacilityModuleRegistry.create(
            ModuleInstance.ID.create(),
            FacilityModuleKind.MINER,
            StationTileCoord.of(1, 0),
            ModuleShape.SINGLE,
            ModuleTier.EV);
        miner.updateStatus(Buildable.Status.OPERATIONAL);
        return miner;
    }
}
