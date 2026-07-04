package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.MinorCelestialBodyId;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidSlotRanges;
import com.gtnewhorizons.galaxia.testing.GalaxiaTestBootstrap;

final class OrbitalContextMenuWidgetTest {

    @BeforeAll
    static void init() {
        GalaxiaTestBootstrap.ensureCelestialRegistry();
    }

    @Test
    void contextMenuActionsExposeLocalizationKeys() {
        CelestialObject body = CelestialRegistry.get(CelestialObjectId.MARS)
            .orElseThrow();

        List<String> labelKeys = OrbitalContextMenuWidget.buildActions(body, true)
            .stream()
            .map(ContextMenuAction::labelKey)
            .toList();

        assertEquals(
            List.of(
                "galaxia.gui.orbital.context_menu.manage_assets",
                "galaxia.satellite.action.add_communication",
                "galaxia.satellite.action.delete_communication",
                "galaxia.satellite.action.add_prospecting",
                "galaxia.satellite.action.delete_prospecting"),
            labelKeys);
    }

    @Test
    void asteroidContextMenuOffersCommunicationAndProspectingSatelliteActions() {
        CelestialObject asteroid = CelestialRegistry
            .get(
                CelestialObjectKey.minorBody(
                    new MinorCelestialBodyId(CelestialObjectId.FROZEN_BELT, AsteroidSlotRanges.GENERATED_SLOT_MIN)))
            .orElseThrow();

        List<String> labelKeys = OrbitalContextMenuWidget.buildActions(asteroid, true)
            .stream()
            .map(ContextMenuAction::labelKey)
            .toList();

        assertEquals(
            List.of(
                "galaxia.gui.orbital.context_menu.manage_assets",
                "galaxia.satellite.action.add_communication",
                "galaxia.satellite.action.delete_communication",
                "galaxia.satellite.action.add_prospecting",
                "galaxia.satellite.action.delete_prospecting"),
            labelKeys);
    }
}
