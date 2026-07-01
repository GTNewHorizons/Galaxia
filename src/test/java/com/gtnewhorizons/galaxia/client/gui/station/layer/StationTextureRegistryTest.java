package com.gtnewhorizons.galaxia.client.gui.station.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.client.gui.station.layer.StationTextureRegistry.ConnectorKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;

final class StationTextureRegistryTest {

    @Test
    void everyModuleTexturePathHasPackagedAsset() {
        ClassLoader classLoader = StationTextureRegistryTest.class.getClassLoader();
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            ResourceLocation texture = StationTextureRegistry.moduleTexture(kind);

            assertNotNull(texture, "missing module texture path for " + kind);
            assertNotNull(
                classLoader.getResource("assets/" + texture.getResourceDomain() + "/" + texture.getResourcePath()),
                "missing module texture asset for " + kind + ": " + texture);
        }
    }

    @Test
    void packagedModuleTextureFilesAreRegisteredAsBaseOrVariantAssets() throws Exception {
        Set<String> expectedPaths = new HashSet<>();
        for (FacilityModuleKind kind : FacilityModuleKind.values()) {
            expectedPaths.add(
                StationTextureRegistry.moduleTexture(kind)
                    .getResourcePath());
            for (ResourceLocation texture : StationTextureRegistry.moduleVariantTextures(kind)) {
                expectedPaths.add(texture.getResourcePath());
            }
        }

        Path moduleTextureDir = Path.of(
            StationTextureRegistryTest.class.getClassLoader()
                .getResource("assets/galaxia/textures/gui/station/modules")
                .toURI());
        Set<String> packagedPaths = new HashSet<>();
        try (var files = Files.list(moduleTextureDir)) {
            files.filter(
                path -> path.getFileName()
                    .toString()
                    .endsWith(".png"))
                .map(path -> "textures/gui/station/modules/" + path.getFileName())
                .forEach(packagedPaths::add);
        }

        assertEquals(expectedPaths, packagedPaths);
    }

    @Test
    void bigHammerTextureIsRegisteredAsHammerVariantAsset() {
        assertEquals(
            new ResourceLocation("galaxia", "textures/gui/station/modules/big_hammer.png"),
            StationTextureRegistry.moduleVariantTexture(FacilityModuleKind.HAMMER, HammerVariant.BIG.name()));
    }

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
