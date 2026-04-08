package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.rules;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.RocketModule;

public interface IPlacementRule {

    List<RocketAssembly.ModulePlacement> apply(List<RocketModule> modules, double startY);
}
