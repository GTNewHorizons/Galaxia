package com.gtnewhorizons.galaxia.registry.outpost.module.types;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.satellite.SatelliteDataType;

final class ModuleDebugDataGeneratorTest {

    @Test
    void configClampsDebugAmountsToFormatterSafeRange() {
        ModuleDebugDataGenerator.Config config = ModuleDebugDataGenerator.Config
            .produce(SatelliteDataType.RESEARCH, Long.MAX_VALUE, 20);

        assertEquals(ModuleDebugDataGenerator.MAX_AMOUNT_KB, config.amountKb());
    }
}
