package com.gtnewhorizons.galaxia.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizons.galaxia.Galaxia;
import com.gtnewhorizons.galaxia.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.dimension.PlanetEnum;

public class GuiPlanetTeleporter extends GuiScreen {

    private GuiTextField xField;
    private GuiTextField yField;
    private GuiTextField zField;

    private PlanetEnum selectedPlanet = PlanetEnum.CALX;
    private final PlanetEnum[] planets = PlanetEnum.values();

    private GuiButton teleportButton;

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        this.buttonList.clear();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = 40;
        int spacing = 25;

        for (int i = 0; i < planets.length; i++) {
            PlanetEnum planet = planets[i];
            GuiButton planetButton = new GuiButton(
                i,
                this.width / 2 - buttonWidth / 2,
                startY + i * spacing,
                buttonWidth,
                buttonHeight,
                planet.name());
            this.buttonList.add(planetButton);
        }

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

        this.teleportButton = new GuiButton(
            200,
            this.width / 2 - 100,
            fieldY + 80,
            200,
            20,
            I18n.format("gui.teleport"));
        this.buttonList.add(teleportButton);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id < planets.length) {
            selectedPlanet = planets[button.id];
            for (GuiButton obj : this.buttonList) {
                if (obj.id < planets.length) {
                    obj.enabled = planets[obj.id] != selectedPlanet;
                }
            }
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(this.fontRendererObj, "Planet Teleporter", this.width / 2, 15, 0xFFFFFF);

        this.drawCenteredString(
            this.fontRendererObj,
            "Selected: " + selectedPlanet.name(),
            this.width / 2,
            30,
            0xAAAAFF);

        this.drawString(this.fontRendererObj, "X:", this.width / 2 - 80, xField.yPosition + 6, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "Y:", this.width / 2 - 80, yField.yPosition + 6, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "Z:", this.width / 2 - 80, zField.yPosition + 6, 0xFFFFFF);

        xField.drawTextBox();
        yField.drawTextBox();
        zField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        xField.textboxKeyTyped(typedChar, keyCode);
        yField.textboxKeyTyped(typedChar, keyCode);
        zField.textboxKeyTyped(typedChar, keyCode);

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            actionPerformed(teleportButton);
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        xField.mouseClicked(mouseX, mouseY, mouseButton);
        yField.mouseClicked(mouseX, mouseY, mouseButton);
        zField.mouseClicked(mouseX, mouseY, mouseButton);

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

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
