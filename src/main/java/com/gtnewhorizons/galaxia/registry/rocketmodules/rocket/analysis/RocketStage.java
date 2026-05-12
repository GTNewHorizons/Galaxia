package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartType;
import java.util.ArrayList;
import java.util.List;

public class RocketStage {
    private final int number;
    private final List<RocketPartInstance> parts = new ArrayList<>();
    private double totalFuel = 0;
    private double totalMass = 0;
    private double totalThrust = 0;

    public RocketStage(int number) { this.number = number; }

    public void addPart(RocketPartInstance part) {
        parts.add(part);
        totalMass += part.def().weight();
        if (part.def().type() == RocketPartType.ENGINE)
            totalThrust += part.def().thrust();
        if (part.def().type() == RocketPartType.FUEL_TANK)
            totalFuel += part.def().fuelCapacity();
    }

    public double getDeltaV() {
        if (totalThrust == 0) return 0;
        double dryMass = totalMass - totalFuel;
        if (dryMass <= 0) return 0;
        double isp = 300.0;
        return isp * 9.81 * Math.log(totalMass / dryMass);
    }

    public boolean canLaunch(double payloadMass) {
        double totalMassWithPayload = totalMass + payloadMass;
        return totalThrust > totalMassWithPayload * 3.0;
    }

    public List<RocketPartInstance> getParts() { return parts; }
    public int getNumber() { return number; }
    public double getTotalMass() { return totalMass; }
}
