package com.gtnewhorizons.galaxia.rocketmodules.rules;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.IStackableModule;
import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class ClusteredPlacementRule implements IPlacementRule {

    @Override
    public List<RocketAssembly.ModulePlacement> apply(List<RocketModule> modules, double startY) {
        List<RocketAssembly.ModulePlacement> placements = new ArrayList<>();
        double y = startY;
        int idx = 0;

        while (idx < modules.size()) {
            RocketModule centre = modules.get(idx++);
            if (!(centre instanceof IStackableModule stackable)) {
                placements.add(new RocketAssembly.ModulePlacement(centre, 0, y, 0));
                y += centre.getHeight();
                continue;
            }

            int maxInCluster = stackable.getMaxStackSize();
            int orbitalCount = Math.min(modules.size() - idx, maxInCluster - 1);

            double clusterY = y;

            placements.add(new RocketAssembly.ModulePlacement(centre, 0, clusterY, 0));

            double radius = calculateOrbitRadius(centre, orbitalCount);

            for (int o = 0; o < orbitalCount; o++) {
                double angle = (2 * Math.PI / orbitalCount) * o;
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;

                RocketModule orb = modules.get(idx++);
                placements.add(new RocketAssembly.ModulePlacement(orb, ox, clusterY, oz));
            }

            y = clusterY + centre.getHeight();
        }
        return placements;
    }

    private double calculateOrbitRadius(RocketModule centre, int count) {
        if (count == 0) return 0;
        double r1 = centre.getWidth();
        double r2 = count > 1 ? centre.getWidth() / (2 * Math.sin(Math.PI / count)) : 0;
        return Math.max(r1, r2) + 0.1;
    }
}
