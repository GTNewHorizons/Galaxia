package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;

final class StationTextureRegistryTest {

    @Test
    void capacityConnectorTexturesAreNamedByModuleKindAndDirection() {
        assertEquals(
            new ResourceLocation("galaxia", "textures/gui/station/connectors/battery_connector_horizontal.png"),
            StationTextureRegistry.capacityConnectorTexture(FacilityModuleKind.BATTERY, ConnectorKind.HORIZONTAL));
        assertEquals(
            new ResourceLocation("galaxia", "textures/gui/station/connectors/storage_connector_vertical.png"),
            StationTextureRegistry.capacityConnectorTexture(FacilityModuleKind.STORAGE, ConnectorKind.VERTICAL));
        assertEquals(
            new ResourceLocation("galaxia", "textures/gui/station/connectors/tank_connector_horizontal.png"),
            StationTextureRegistry.capacityConnectorTexture(FacilityModuleKind.TANK, ConnectorKind.HORIZONTAL));
    }
}
