package com.gtnewhorizons.galaxia.rocketmodules.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.gtnewhorizons.galaxia.rocketmodules.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class PropulsionPlacementRule implements IPlacementRule {

    @Override
    public List<RocketAssembly.ModulePlacement> apply(List<RocketModule> allModules, double startY) {
        List<RocketModule> tanks = allModules.stream()
            .filter(m -> m.getFuelCapacity() > 0)
            .collect(Collectors.toList());

        List<RocketModule> engines = allModules.stream()
            .filter(m -> m.getThrust() > 0)
            .collect(Collectors.toList());

        List<RocketAssembly.ModulePlacement> placements = new ArrayList<>();
        double y = startY;
        int engineIdx = 0;
        int tankIdx = 0;

        while (tankIdx < tanks.size()) {
            RocketModule centreTank = tanks.get(tankIdx++);
            int orbitalCount = Math
                .min(tanks.size() - tankIdx, (centreTank instanceof IStackableModule s) ? s.getMaxStackSize() - 1 : 0);

            double clusterBaseY = y;

            List<RocketModule> clusterEngines = new ArrayList<>();
            if (engineIdx < engines.size()) {
                clusterEngines.add(engines.get(engineIdx++));
            }
            for (int o = 0; o < orbitalCount; o++) {
                if (engineIdx < engines.size()) {
                    clusterEngines.add(engines.get(engineIdx++));
                }
            }

            double maxEngineH = 0.0;
            for (RocketModule e : clusterEngines) {
                maxEngineH = Math.max(maxEngineH, e.getHeight());
            }

            RocketModule centreEngine = clusterEngines.isEmpty() ? null : clusterEngines.remove(0);
            double centreEngineOffset = centreEngine != null ? maxEngineH - centreEngine.getHeight() : 0;
            if (centreEngine != null) {
                placements
                    .add(new RocketAssembly.ModulePlacement(centreEngine, 0, clusterBaseY + centreEngineOffset, 0));
            }

            double tankY = clusterBaseY + maxEngineH;
            placements.add(new RocketAssembly.ModulePlacement(centreTank, 0, tankY, 0));

            double radius = calculateOrbitRadius(centreTank, orbitalCount);

            for (int o = 0; o < orbitalCount; o++) {
                double angle = (2 * Math.PI / orbitalCount) * o;
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;

                RocketModule orbTank = tanks.get(tankIdx++);

                RocketModule orbEngine = o < clusterEngines.size() ? clusterEngines.get(o) : null;
                double orbEngineOffset = orbEngine != null ? maxEngineH - orbEngine.getHeight() : 0;
                if (orbEngine != null) {
                    placements
                        .add(new RocketAssembly.ModulePlacement(orbEngine, ox, clusterBaseY + orbEngineOffset, oz));
                }

                placements.add(new RocketAssembly.ModulePlacement(orbTank, ox, tankY, oz));
            }

            y = tankY + centreTank.getHeight();
        }

        while (engineIdx < engines.size()) {
            RocketModule e = engines.get(engineIdx++);
            placements.add(new RocketAssembly.ModulePlacement(e, 0, y, 0));
            y += e.getHeight();
        }

        return placements;
    }

    private double calculateOrbitRadius(RocketModule m, int count) {
        if (count == 0) return 0;
        double r1 = m.getWidth();
        double r2 = count > 1 ? m.getWidth() / (2 * Math.sin(Math.PI / count)) : 0;
        return Math.max(r1, r2) + 0.1;
    }
}
