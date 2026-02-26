package com.gtnewhorizons.galaxia.orbitalGUI.flightplan;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

@Desugar
public record Maneuver(
    double universalTime,
    double deltaVX,
    double deltaVY,
    OrbitalCelestialBody referenceBody
) {}
