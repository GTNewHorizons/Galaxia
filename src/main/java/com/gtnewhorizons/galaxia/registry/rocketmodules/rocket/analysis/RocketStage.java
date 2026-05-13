package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;

public class RocketStage {

    private final int number;
    private final List<RocketPartInstance> parts = new ArrayList<>();
    private double totalMass = 0;
    private double totalFuel = 0;
    private double totalThrust = 0;

    public RocketStage(int number) {
        this.number = number;
    }

    public void addPart(RocketPartInstance part) {
        parts.add(part);
        totalMass += part.def()
            .weight();
        if (part.def() instanceof EnginePartDef def) totalThrust += def.thrust();
        if (part.def() instanceof FuelTankPartDef def) totalFuel += def.fuelCapacity();
    }

    public double getDeltaV() {
        if (totalThrust == 0) return 0;
        double dryMass = totalMass - totalFuel;
        if (dryMass <= 0) return 0;
        double isp = 300.0;
        return isp * 9.8 * Math.log(totalMass / dryMass);
    }

    public boolean canLaunch(double payloadMass) {
        double totalMassWithPayload = totalMass + payloadMass;
        return totalThrust > totalMassWithPayload * 1.5;
    }

    public List<RocketPartInstance> getParts() {
        return parts;
    }

    public int getNumber() {
        return number;
    }

    public double getTotalMass() {
        return totalMass;
    }
}
