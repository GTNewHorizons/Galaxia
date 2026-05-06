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
    private static final Path STATION_SCREEN_SOURCE = Path
        .of("src/main/java/com/gtnewhorizons/galaxia/client/gui/station/StationManagementScreen.java");
    private static final Path MODAL_CONTROLLER_SOURCE = Path
        .of("src/main/java/com/gtnewhorizons/galaxia/client/gui/station/ModuleConfigModalController.java");
    private static final Path CLIENT_PROXY_SOURCE = Path
        .of("src/main/java/com/gtnewhorizons/galaxia/core/ClientProxy.java");

    @Test
    void sidePanelDoesNotOwnInlineModuleConfigurationWidgets() throws IOException {
        String source = Files.readString(PANEL_SOURCE);

        assertFalse(source.contains("TextFieldWidget"), "side panel must not own editable module config fields");
        assertFalse(source.contains("showHammerConfig"), "hammer config belongs to a dedicated screen");
        assertFalse(source.contains("showMinerVoidConfig"), "miner config belongs to a dedicated screen");
        assertTrue(
            source.contains("configController.openHammer"),
            "hammer button should open the embedded hammer modal");
        assertTrue(
            source.contains("configController.openMinerVoid"),
            "miner button should open the embedded miner modal");
    }

    @Test
    void moduleConfigurationUsesEmbeddedStationModalsInsteadOfNewGuiScreens() throws IOException {
        String stationScreen = Files.readString(STATION_SCREEN_SOURCE);
        String modalController = Files.readString(MODAL_CONTROLLER_SOURCE);
        String clientProxy = Files.readString(CLIENT_PROXY_SOURCE);

        assertTrue(
            stationScreen.contains("new ModuleConfigModalController("),
            "station screen must install a modal lifecycle controller");
        assertTrue(
            modalController.contains("new HammerConfigModalWidget"),
            "controller must create hammer modal on demand");
        assertTrue(
            modalController.contains("new MinerVoidConfigModalWidget"),
            "controller must create miner modal on demand");
        assertTrue(
            modalController.contains("host.child(widget)"),
            "controller must add opened modal to the station panel");
        assertTrue(
            modalController.contains("host.remove(modal)"),
            "controller must remove closed modal from the station panel");
        assertFalse(
            stationScreen.contains("new HammerConfigModalWidget"),
            "station screen must not keep a hidden hammer modal widget");
        assertFalse(
            stationScreen.contains("new MinerVoidConfigModalWidget"),
            "station screen must not keep a hidden miner modal widget");
        assertFalse(
            clientProxy.contains("HammerConfigScreen.FACTORY"),
            "config must not register a replacing GuiScreen");
        assertFalse(
            clientProxy.contains("MinerVoidConfigScreen.FACTORY"),
            "config must not register a replacing GuiScreen");
    }
}
