package com.gtnewhorizons.galaxia.rocketmodules.rocket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.CapsuleModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.EngineModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.FuelTankModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.RocketCoreModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.rules.ClusteredPlacementRule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.rules.LinearPlacementRule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.rules.PropulsionPlacementRule;

public final class RocketAssembly {

    @Desugar
    public record ModulePlacement(RocketModule type, double x, double y, double z) {}

    private final List<RocketModule> modules;
    private List<ModulePlacement> placements;
    private int destination = 0;

    public RocketAssembly(List<Integer> moduleIds) {
        this.modules = moduleIds.stream()
            .map(ModuleRegistry::fromId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public void updateDestination(int dim) {
        this.destination = dim;
    }

    public int getDestination() {
        return this.destination;
    }

    public List<ModulePlacement> getPlacements() {
        if (placements == null) {
            placements = new ArrayList<>();
            double y = 0.0;

            // Tanks and Engines
            List<RocketModule> propulsion = modules.stream()
                .filter(m -> m instanceof EngineModule || m instanceof FuelTankModule)
                .collect(Collectors.toList());

            placements.addAll(new PropulsionPlacementRule().apply(propulsion, y));

            double afterPropulsion = placements.stream()
                .mapToDouble(
                    p -> p.y() + p.type()
                        .getHeight())
                .max()
                .orElse(0.0);

            List<RocketModule> otherStackables = modules.stream()
                .filter(
                    m -> m instanceof IStackableModule && !(m instanceof EngineModule)
                        && !(m instanceof FuelTankModule))
                .collect(Collectors.toList());

            placements.addAll(new ClusteredPlacementRule().apply(otherStackables, afterPropulsion));
            double afterClustered = placements.stream()
                .mapToDouble(
                    p -> p.y() + p.type()
                        .getHeight())
                .max()
                .orElse(afterPropulsion);

            // Rocket Core
            List<RocketModule> cores = modules.stream()
                .filter(RocketCoreModule.class::isInstance)
                .collect(Collectors.toList());

            placements.addAll(new LinearPlacementRule().apply(cores, afterClustered));

            // Non Capsule Linears
            List<RocketModule> linears = modules.stream()
                .filter(m -> !(m instanceof IStackableModule) && !(m instanceof CapsuleModule))
                .collect(Collectors.toList());

            placements.addAll(new LinearPlacementRule().apply(linears, afterClustered));
            double beforeCommand = placements.stream()
                .mapToDouble(
                    p -> p.y() + p.type()
                        .getHeight())
                .max()
                .orElse(afterClustered);

            // Capsule Module
            List<RocketModule> capsules = modules.stream()
                .filter(CapsuleModule.class::isInstance)
                .collect(Collectors.toList());

            placements.addAll(new LinearPlacementRule().apply(capsules, beforeCommand));
        }
        return placements;
    }

    public double getTotalHeight() {
        return getPlacements().stream()
            .mapToDouble(
                p -> p.y() + p.type()
                    .getHeight())
            .max()
            .orElse(0.0);
    }

    public double getTotalWidth() {
        return getPlacements().stream()
            .mapToDouble(
                p -> p.x() + p.type()
                    .getWidth())
            .max()
            .orElse(0.0);
    }

    public double getTotalWeight() {
        return modules.stream()
            .mapToDouble(RocketModule::getWeight)
            .sum();
    }

    public double getMountedYOffset() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            if (modules.get(i) instanceof CapsuleModule m) return m.getSitOffset() + getTotalHeight();

        }
        return

        getTotalHeight();

    }

    public List<RocketModule> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<EngineModule> getEngineModules() {
        return getModules().stream()
            .filter(EngineModule.class::isInstance)
            .map(EngineModule.class::cast)
            .collect(Collectors.toList());
    }

    public List<FuelTankModule> getFuelTankModules() {
        return getModules().stream()
            .filter(FuelTankModule.class::isInstance)
            .map(FuelTankModule.class::cast)
            .collect(Collectors.toList());
    }

    public List<CapsuleModule> getCapsuleModules() {
        return getModules().stream()
            .filter(CapsuleModule.class::isInstance)
            .map(CapsuleModule.class::cast)
            .collect(Collectors.toList());
    }

    public List<RocketCoreModule> getCoreModules() {
        return getModules().stream()
            .filter(RocketCoreModule.class::isInstance)
            .map(RocketCoreModule.class::cast)
            .collect(Collectors.toList());
    }

    public List<RocketModule> getFunctionalModules() {
        return new ArrayList<>();
    }

    public List<RocketModule> getStructuralModules() {
        return new ArrayList<>();
    }
}
