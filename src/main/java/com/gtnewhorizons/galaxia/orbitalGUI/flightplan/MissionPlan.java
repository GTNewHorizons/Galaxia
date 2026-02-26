package com.gtnewhorizons.galaxia.orbitalGUI.flightplan;

public class MissionPlan {
    private final Spacecraft spacecraft;

    public MissionPlan(Spacecraft spacecraft) {
        this.spacecraft = spacecraft;
    }

    public void addManeuver(Maneuver maneuver) {
        spacecraft.addManeuver(maneuver);
    }

    public Spacecraft getSpacecraft() {
        return spacecraft;
    }
}
