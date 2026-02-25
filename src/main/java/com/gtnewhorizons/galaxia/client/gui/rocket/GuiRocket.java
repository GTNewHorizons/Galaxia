package com.gtnewhorizons.galaxia.client.gui.rocket;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;

public class GuiRocket extends GuiScreen {

    private final RocketEntity rocket;

    public GuiRocket(RocketEntity rocket) {
        this.rocket = rocket;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 - 40, 200, 20, "sit"));
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 2 + 10, 200, 20, "launch"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.thePlayer.mountEntity(rocket);
        else if (button.id == 1) rocket.launch();
        mc.thePlayer.closeScreen();
    }

    @Override
    public void drawScreen(int x, int y, float p) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "rocket control", width / 2, 30, 0xFFFFFF);
        super.drawScreen(x, y, p);
    }
}
