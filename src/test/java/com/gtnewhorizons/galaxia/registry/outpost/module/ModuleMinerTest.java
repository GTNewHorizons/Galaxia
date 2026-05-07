package com.gtnewhorizons.galaxia.registry.outpost.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

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
        facility.addModule(miner);

        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));

        facility.setMinerOreBlacklisted(miner, "ore:iron", true);
        assertTrue(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertTrue(
            facility.minerSettings(miner)
                .blacklistedOreKeys()
                .contains("ore:iron"));

        facility.setMinerOreBlacklisted(miner, "ore:iron", false);
        assertFalse(facility.isMinerOreBlacklisted(miner, "ore:iron"));
        assertFalse(
            facility.minerSettings(miner)
                .blacklistedOreKeys()
                .contains("ore:iron"));
    }

    @Test
    void blacklistVoidsOreAfterRoll() {
        AutomatedFacility facility = createFacility();
        ModuleInstance miner = createMiner();
        facility.addModule(miner);
        facility.setMinerOreBlacklisted(miner, "ore:iron", true);

        assertTrue(ModuleMiner.shouldVoidOre(miner, facility, "ore:iron"));
        assertFalse(ModuleMiner.shouldVoidOre(miner, facility, "ore:copper"));
    }

    @Test
    void minerSettingsGroupSharesAndCopiesSettingsOnLeave() {
        AutomatedFacility facility = createFacility();
        ModuleInstance first = createMiner(StationTileCoord.of(1, 0));
        ModuleInstance second = createMiner(StationTileCoord.of(2, 0));
        facility.addModule(first);
        facility.addModule(second);
        facility.setMinerOreBlacklisted(first, "ore:iron", true);

        SettingsGroup group = facility.createSettingsGroupForModule(first, "Tin line");
        facility.assignSettingsGroup(second, group.id());

        assertTrue(facility.isMinerOreBlacklisted(second, "ore:iron"));

        facility.setMinerOreBlacklisted(second, "ore:copper", true);
        assertTrue(facility.isMinerOreBlacklisted(first, "ore:copper"));

        facility.leaveSettingsGroup(second);
        facility.setMinerOreBlacklisted(first, "ore:gold", true);

        assertTrue(facility.isMinerOreBlacklisted(second, "ore:copper"));
        assertFalse(facility.isMinerOreBlacklisted(second, "ore:gold"));
    }

    private static AutomatedFacility createFacility() {
        return new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.PANSPIRA,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
    }

    private static ModuleInstance createMiner() {
        return createMiner(StationTileCoord.of(1, 0));
    }

    private static ModuleInstance createMiner(StationTileCoord anchor) {
        ModuleInstance miner = FacilityModuleRegistry
            .create(ModuleInstance.ID.create(), FacilityModuleKind.MINER, anchor, ModuleShape.SINGLE, ModuleTier.EV);
        miner.updateStatus(Buildable.Status.OPERATIONAL);
        return miner;
    }
}
