package com.gtnewhorizons.galaxia.client.gui.station;

import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;

/** Content of the window a station module is configured in */
interface ModuleConfigWindow {

    /** Title of the window, read again every frame so it follows the selected module */
    String title();

    /** Fills the window once; anything that changes is given as suppliers */
    void build(GalaxiaUi.Column column);

    /** Runs every tick while the window is open */
    default void update() {}
}
