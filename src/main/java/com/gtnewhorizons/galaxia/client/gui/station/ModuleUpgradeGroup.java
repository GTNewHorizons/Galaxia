package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;

record ModuleUpgradeGroup(String id, String title, List<ModuleUpgradeOption> options) {

    ModuleUpgradeGroup {
        options = List.copyOf(options);
    }
}
