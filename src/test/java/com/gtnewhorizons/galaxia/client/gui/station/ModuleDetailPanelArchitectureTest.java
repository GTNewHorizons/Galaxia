package com.gtnewhorizons.galaxia.client.gui.station;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class ModuleDetailPanelArchitectureTest {

    private static final Path PANEL_SOURCE = Path
        .of("src/main/java/com/gtnewhorizons/galaxia/client/gui/station/ModuleDetailPanel.java");

    @Test
    void sidePanelDoesNotOwnInlineModuleConfigurationWidgets() throws IOException {
        String source = Files.readString(PANEL_SOURCE);

        assertFalse(source.contains("TextFieldWidget"), "side panel must not own editable module config fields");
        assertFalse(source.contains("showHammerConfig"), "hammer config belongs to a dedicated screen");
        assertFalse(source.contains("showMinerVoidConfig"), "miner config belongs to a dedicated screen");
        assertTrue(source.contains("HammerConfigScreen.open"), "hammer button should open the hammer config screen");
        assertTrue(source.contains("MinerVoidConfigScreen.open"), "miner button should open the miner config screen");
    }
}
