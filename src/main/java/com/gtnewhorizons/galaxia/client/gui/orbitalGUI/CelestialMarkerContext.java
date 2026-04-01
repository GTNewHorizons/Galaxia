package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyAssetState;

@Desugar
public record CelestialMarkerContext(OrbitalCelestialBody body, CelestialBodyAssetState assetState) {}
