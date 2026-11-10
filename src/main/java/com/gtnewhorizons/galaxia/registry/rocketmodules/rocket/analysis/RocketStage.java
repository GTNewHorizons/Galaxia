package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.EnginePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;

import lombok.Getter;

/**
 * A group of modules burned and dropped together. Stage 0 is the top of the rocket, the highest number is the first
 * to fire
 */
public class RocketStage {

    @Getter
    private final int number;
    @Getter
    private final List<RocketPartInstance> parts = new ArrayList<>();
    @Getter
    private final List<RocketPartInstance> tanks = new ArrayList<>();
    @Getter
    private final List<RocketPartInstance> engines = new ArrayList<>();
    @Getter
    private double dryMass = 0;
    @Getter
    private double totalThrust = 0;

    public RocketStage(int number) {
        this.number = number;
    }

    public void addPart(RocketPartInstance part) {
        parts.add(part);
        dryMass += part.def()
            .weight();
        if (part.def() instanceof EnginePartDef def) {
            engines.add(part);
            totalThrust += def.thrust();
        }
        if (part.def() instanceof FuelTankPartDef) tanks.add(part);
    }

    /** Fuels at least one engine of this stage can burn */
    public Set<RocketFuel> getBurnableFuels() {
        Set<RocketFuel> fuels = new LinkedHashSet<>();
        for (RocketPartInstance engine : engines) {
            fuels.add(((EnginePartDef) engine.def()).fuel());
        }
        return fuels;
    }

    /**
     * Checks whether a tank of this stage can feed one of its engines
     *
     * @param tank The tank to check
     *
     * @return Boolean : True => an engine here burns the tank's fuel
     */
    public boolean canBurn(RocketPartInstance tank) {
        return tank.def() instanceof FuelTankPartDef def && getBurnableFuels().contains(def.fuel());
    }

    /**
     * Thrust-weighted specific impulse of the engines that have fuel to burn
     *
     * @param fuels Fuels currently available to this stage
     *
     * @return Specific impulse in seconds, 0 if nothing can burn
     */
    public double getEffectiveIsp(Set<RocketFuel> fuels) {
        double thrust = 0;
        double weighted = 0;
        for (RocketPartInstance engine : engines) {
            EnginePartDef def = (EnginePartDef) engine.def();
            if (!fuels.contains(def.fuel())) continue;
            thrust += def.thrust();
            weighted += def.thrust() * def.isp();
        }
        return thrust <= 0 ? 0 : weighted / thrust;
    }

    public boolean hasEngines() {
        return !engines.isEmpty();
    }

    public boolean hasTanks() {
        return !tanks.isEmpty();
    }
}
