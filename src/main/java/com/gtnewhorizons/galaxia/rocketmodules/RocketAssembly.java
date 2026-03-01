package com.gtnewhorizons.galaxia.rocketmodules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.galaxia.rocketmodules.rules.ClusteredPlacementRule;
import com.gtnewhorizons.galaxia.rocketmodules.rules.LinearPlacementRule;
import com.gtnewhorizons.galaxia.rocketmodules.rules.PropulsionPlacementRule;

public final class RocketAssembly {

    @Desugar
    public record ModulePlacement(RocketModule type, double x, double y, double z) {}

    private final List<RocketModule> modules;
    private List<ModulePlacement> placements;

    public RocketAssembly(List<Integer> moduleIds) {
        this.modules = moduleIds.stream()
            .map(ModuleRegistry::fromId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<ModulePlacement> getPlacements() {
        if (placements == null) {
            placements = new ArrayList<>();
            double y = 0.0;

            List<RocketModule> propulsion = modules.stream()
                .filter(m -> m.getFuelCapacity() > 0 || m.getThrust() > 0)
                .collect(Collectors.toList());

            placements.addAll(new PropulsionPlacementRule().apply(propulsion, y));

            double afterPropulsion = placements.stream()
                .mapToDouble(
                    p -> p.y() + p.type()
                        .getHeight())
                .max()
                .orElse(0.0);

            List<RocketModule> otherStackables = modules.stream()
                .filter(m -> m instanceof IStackableModule && m.getFuelCapacity() == 0 && m.getThrust() == 0)
                .collect(Collectors.toList());

            placements.addAll(new ClusteredPlacementRule().apply(otherStackables, afterPropulsion));

            double afterClustered = placements.stream()
                .mapToDouble(
                    p -> p.y() + p.type()
                        .getHeight())
                .max()
                .orElse(afterPropulsion);

            List<RocketModule> linears = modules.stream()
                .filter(m -> !(m instanceof IStackableModule))
                .collect(Collectors.toList());

            placements.addAll(new LinearPlacementRule().apply(linears, afterClustered));
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

    public double getTotalWeight() {
        return modules.stream()
            .mapToDouble(RocketModule::getWeight)
            .sum();
    }

    public double getMountedYOffset() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            if (modules.get(i)
                .getPassengerCapacity() > 0) {
                return getTotalHeight() + modules.get(i)
                    .getSitOffset();
            }
        }
        return getTotalHeight();
    }

    public List<RocketModule> getModules() {
        return Collections.unmodifiableList(modules);
    }
}
