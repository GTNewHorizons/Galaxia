package com.gtnewhorizons.galaxia.rocketmodules.rules;

import java.util.List;

import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.RocketModule;

public interface IPlacementRule {

    List<RocketAssembly.ModulePlacement> apply(List<RocketModule> modules, double startY);
}
