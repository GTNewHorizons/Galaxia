package com.gtnewhorizons.galaxia.client.gui.rocket;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.PacketAddModule;
import com.gtnewhorizons.galaxia.registry.block.tileentities.TileSiloController;
import com.gtnewhorizons.galaxia.registry.entity.rocket.ModuleType;
import com.gtnewhorizons.galaxia.registry.entity.rocket.RocketEntity;

public class GuiSilo extends GuiScreen {

    private final ContainerSilo container;

    public GuiSilo(ContainerSilo c) {
        this.container = c;
    }

    @Override
    public void initGui() {
        buttonList.add(new GuiButton(0, width / 2 - 100, 50, 200, 20, "Добавить капсулу"));
        buttonList.add(new GuiButton(1, width / 2 - 100, 80, 200, 20, "Добавить хранилище"));
        buttonList.add(new GuiButton(2, width / 2 - 100, 110, 200, 20, "Добавить бак"));
        buttonList.add(new GuiButton(3, width / 2 - 100, 140, 200, 20, "Добавить двигатель"));
        buttonList.add(new GuiButton(10, width / 2 - 100, height - 40, 200, 20, "Запустить"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        TileSiloController tile = container.tile;
        int x = tile.xCoord;
        int y = tile.yCoord;
        int z = tile.zCoord;

        System.out.println("[GuiSilo CLIENT] button clicked " + button.id + " | coords=" + x + "," + y + "," + z);

        if (button.id == 0) {
            Galaxia.GALAXIA_NETWORK.sendToServer(new PacketAddModule(x, y, z, ModuleType.CAPSULE));
            System.out.println("[GuiSilo CLIENT] CAPSULE");
        } else if (button.id == 1) {
            Galaxia.GALAXIA_NETWORK.sendToServer(new PacketAddModule(x, y, z, ModuleType.STORAGE));
            System.out.println("[GuiSilo CLIENT] STORAGE");
        } else if (button.id == 2) {
            Galaxia.GALAXIA_NETWORK.sendToServer(new PacketAddModule(x, y, z, ModuleType.FUEL_TANK));
            System.out.println("[GuiSilo CLIENT] FUEL_TANK");
        } else if (button.id == 3) {
            Galaxia.GALAXIA_NETWORK.sendToServer(new PacketAddModule(x, y, z, ModuleType.ENGINE));
            System.out.println("[GuiSilo CLIENT] ENGINE");
        } else if (button.id == 10) {
            RocketEntity r = tile.getOrCreateRocket();
            if (r != null) {
                r.launch();
            } else {
                System.out.println("[GuiSilo CLIENT] Ошибка: не удалось получить ракету!");
            }
        }
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawDefaultBackground();
        super.drawScreen(mx, my, pt);
    }
}
