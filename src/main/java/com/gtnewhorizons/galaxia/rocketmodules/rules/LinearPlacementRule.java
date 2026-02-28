package com.gtnewhorizons.galaxia.rocketmodules.rules;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public class LinearPlacementRule implements IPlacementRule {

    @Override
    public List<RocketAssembly.ModulePlacement> apply(List<RocketModule> modules, double startY) {
        List<RocketAssembly.ModulePlacement> placements = new ArrayList<>();
        double y = startY;
        for (RocketModule m : modules) {
            placements.add(new RocketAssembly.ModulePlacement(m, 0, y, 0));
            y += m.getHeight();
        }
        return placements;
    }
}
