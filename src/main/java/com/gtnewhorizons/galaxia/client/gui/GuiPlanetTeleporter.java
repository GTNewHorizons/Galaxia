package com.gtnewhorizons.galaxia.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.utility.Colors;

public class GuiPlanetTeleporter extends GuiScreen {

    private GuiTextField xField;
    private GuiTextField yField;
    private GuiTextField zField;

    private DimensionEnum selectedPlanet = DimensionEnum.THEIA;
    private final DimensionEnum[] planets = DimensionEnum.values();

    private GuiButton teleportButton;

    /**
     * Initializes the GUI by creating the widget and buttons etc.
     */
    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        this.buttonList.clear();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = 40;
        int spacing = 25;

        // Add options for all Galaxia Dimensions and create buttons
        for (int i = 0; i < planets.length; i++) {
            DimensionEnum planet = planets[i];
            GuiButton planetButton = new GuiButton(
                i,
                this.width / 2 - buttonWidth / 2,
                startY + i * spacing,
                buttonWidth,
                buttonHeight,
                planet.name());
            this.buttonList.add(planetButton);
        }

        // Create text fields for desired coordinates
        int fieldY = startY + planets.length * spacing + 20;

        this.xField = new GuiTextField(this.fontRendererObj, this.width / 2 - 50, fieldY, 100, 20);
        this.xField.setText("0");
        this.xField.setMaxStringLength(12);

        this.yField = new GuiTextField(this.fontRendererObj, this.width / 2 - 50, fieldY + 25, 100, 20);
        this.yField.setText("100");
        this.yField.setMaxStringLength(12);

        this.zField = new GuiTextField(this.fontRendererObj, this.width / 2 - 50, fieldY + 50, 100, 20);
        this.zField.setText("0");
        this.zField.setMaxStringLength(12);

        // Add the teleport button
        this.teleportButton = new GuiButton(
            200,
            this.width / 2 - 100,
            fieldY + 80,
            200,
            20,
            StatCollector.translateToLocal("galaxia.gui.planet_teleporter.teleport"));
        this.buttonList.add(teleportButton);
    }

    /**
     * Configures the user inputs after GUI closure
     */
    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    /**
     * Defines the behaviour on button presses
     * 
     * @param button The button pressed
     */
    @Override
    protected void actionPerformed(GuiButton button) {
        // If button is a planet name, select that planet
        if (button.id < planets.length) {
            selectedPlanet = planets[button.id];
            for (GuiButton obj : this.buttonList) {
                if (obj.id < planets.length) {
                    obj.enabled = planets[obj.id] != selectedPlanet;
                }
            }
            // If button is teleporter, set the desired coordinates and send a teleport request packet
        } else if (button.id == 200) {
            try {
                double x = Double.parseDouble(xField.getText());
                double y = Double.parseDouble(yField.getText());
                double z = Double.parseDouble(zField.getText());

                Galaxia.channel.sendToServer(new TeleportRequestPacket(selectedPlanet.getId(), x, y, z));
                this.mc.displayGuiScreen(null);
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * Draws the screen of the GUI
     * 
     * @param mouseX       Current cursor x position
     * @param mouseY       Current cursor y position
     * @param partialTicks The current partial tick (how far user is between current game tick and next)
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(
            this.fontRendererObj,
            StatCollector.translateToLocal("galaxia.gui.planet_teleporter.title"),
            this.width / 2,
            15,
            Colors.Title.getColor());

        this.drawCenteredString(
            this.fontRendererObj,
            StatCollector.translateToLocalFormatted("galaxia.gui.planet_teleporter.selected", selectedPlanet.name()),
            this.width / 2,
            30,
            Colors.SubTitle.getColor());

        this.drawString(this.fontRendererObj, "X:", this.width / 2 - 80, xField.yPosition + 6, Colors.Value.getColor());
        this.drawString(this.fontRendererObj, "Y:", this.width / 2 - 80, yField.yPosition + 6, Colors.Value.getColor());
        this.drawString(this.fontRendererObj, "Z:", this.width / 2 - 80, zField.yPosition + 6, Colors.Value.getColor());

        xField.drawTextBox();
        yField.drawTextBox();
        zField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * Handles keyboard presses for textfield entry
     * 
     * @param typedChar The character typed in the field
     * @param keyCode   The keycode of non-alphanumeric commands (enter, return etc.)
     */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Update coordinate fields
        xField.textboxKeyTyped(typedChar, keyCode);
        yField.textboxKeyTyped(typedChar, keyCode);
        zField.textboxKeyTyped(typedChar, keyCode);

        // If return pressed, activate teleport button
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            actionPerformed(teleportButton);
        }

        super.keyTyped(typedChar, keyCode);
    }

    /**
     * Handles mouse click events
     * 
     * @param mouseX      Current cursor x position
     * @param mouseY      Current cursor y position
     * @param mouseButton The mouse button pressed (right click, left click etc)
     */
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Update coordinate fields
        xField.mouseClicked(mouseX, mouseY, mouseButton);
        yField.mouseClicked(mouseX, mouseY, mouseButton);
        zField.mouseClicked(mouseX, mouseY, mouseButton);

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /**
     * Updates the screen to reflect current choices
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        xField.updateCursorCounter();
        yField.updateCursorCounter();
        zField.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
