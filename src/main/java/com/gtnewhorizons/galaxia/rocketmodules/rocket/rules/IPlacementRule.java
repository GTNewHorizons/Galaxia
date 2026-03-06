package com.gtnewhorizons.galaxia.rocketmodules.rocket.rules;

import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;

public interface IPlacementRule {

    List<RocketAssembly.ModulePlacement> apply(List<RocketModule> modules, double startY);
}
