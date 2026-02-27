package com.gtnewhorizons.galaxia.rocketmodules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.github.bsideup.jabel.Desugar;

public final class RocketAssembly {

    @Desugar
    public record ModulePlacement(ModuleType type, double x, double y, double z) {}

    private final List<ModuleType> modules;

    public RocketAssembly(List<Integer> moduleIds) {
        this.modules = moduleIds.stream()
            .map(ModuleType::fromId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<ModulePlacement> getPlacements() {
        List<ModulePlacement> placements = new ArrayList<>();

        List<ModuleType> fuelTanks = filter(ModuleType.FUEL_TANK);
        List<ModuleType> engines = filter(ModuleType.ENGINE);
        List<ModuleType> storages = filter(ModuleType.STORAGE);
        List<ModuleType> capsules = filter(ModuleType.CAPSULE);

        double yOff = 0.0;

        yOff += buildFuelAndEngines(placements, fuelTanks, engines, yOff);

        for (ModuleType s : storages) {
            placements.add(new ModulePlacement(s, 0, yOff, 0));
            yOff += s.getHeight();
        }

        for (ModuleType c : capsules) {
            placements.add(new ModulePlacement(c, 0, yOff, 0));
            yOff += c.getHeight();
        }

        return placements;
    }

    private List<ModuleType> filter(ModuleType type) {
        return modules.stream()
            .filter(m -> m == type)
            .collect(Collectors.toList());
    }

    private double buildFuelAndEngines(List<ModulePlacement> placements, List<ModuleType> tanks,
        List<ModuleType> engines, double startY) {
        double y = startY;
        int tankIdx = 0, engineIdx = 0;
        int remaining = tanks.size();

        if (remaining <= 2) {
            if (engineIdx < engines.size()) {
                ModuleType e = engines.get(engineIdx++);
                placements.add(new ModulePlacement(e, 0, y, 0));
                y += e.getHeight();
            }
            for (ModuleType t : tanks) {
                placements.add(new ModulePlacement(t, 0, y, 0));
                y += t.getHeight();
            }
        } else {
            while (remaining > 0) {
                int orbitalCount = Math.min(remaining - 1, 6);
                double radius = calculateOrbitRadius(tanks, tankIdx, orbitalCount);

                double tierEngineH = 0.0;
                if (engineIdx < engines.size()) {
                    ModuleType e = engines.get(engineIdx++);
                    placements.add(new ModulePlacement(e, 0, y, 0));
                    tierEngineH = e.getHeight();
                }

                ModuleType centre = tanks.get(tankIdx);
                placements.add(new ModulePlacement(centre, 0, y + tierEngineH, 0));
                tankIdx++;
                remaining--;

                for (int o = 0; o < orbitalCount; o++) {
                    double angle = (2 * Math.PI / orbitalCount) * o;
                    double ox = Math.cos(angle) * radius;
                    double oz = Math.sin(angle) * radius;

                    double orbEngineH = 0.0;
                    if (engineIdx < engines.size()) {
                        ModuleType e = engines.get(engineIdx++);
                        placements.add(new ModulePlacement(e, ox, y, oz));
                        orbEngineH = e.getHeight();
                    }

                    ModuleType orbTank = tanks.get(tankIdx);
                    placements.add(new ModulePlacement(orbTank, ox, y + orbEngineH, oz));
                    tankIdx++;
                    remaining--;
                }

                y += tierEngineH + centre.getHeight();
            }
        }
        return y - startY;
    }

    private double calculateOrbitRadius(List<ModuleType> tanks, int startIdx, int count) {
        if (count == 0) return 0;
        double r1 = tanks.get(startIdx)
            .getWidth() / 2.0
            + tanks.get(startIdx + 1)
                .getWidth() / 2.0;
        double r2 = count > 1 ? tanks.get(startIdx + 1)
            .getWidth() / (2 * Math.sin(Math.PI / count)) : 0;
        return Math.max(r1, r2) + 0.1;
    }

    public double getTotalHeight() {
        return getPlacements().stream()
            .mapToDouble(
                p -> p.y() + p.type()
                    .getHeight())
            .max()
            .orElse(0.0);
    }

    // capsule index is redundant as capsule is always on top
    public double getMountedYOffset() {
        double seatOffsetInCapsule = ModuleType.CAPSULE.getSitOffset();
        return getTotalHeight() + seatOffsetInCapsule;
    }

    public List<ModuleType> getModules() {
        return modules;
    }
}
