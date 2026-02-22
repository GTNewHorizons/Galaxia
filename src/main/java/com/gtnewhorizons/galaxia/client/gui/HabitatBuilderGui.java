package com.gtnewhorizons.galaxia.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.gtnewhorizons.galaxia.items.ItemHabitatBuilder;
import com.gtnewhorizons.galaxia.modules.ModuleType;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;

public class HabitatBuilderGui {

    private final GuiData guiData;
    private final PanelSyncManager syncManager;

    public HabitatBuilderGui(GuiData guiData, PanelSyncManager syncManager) {
        this.guiData = guiData;
        this.syncManager = syncManager;
    }

    public ModularPanel build() {
        EntityPlayer player = guiData.getPlayer();
        ItemStack held = guiData.getMainHandItem();

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:habitat_builder")
            .size(250, 240);

        Column column = new Column();
        column.margin(15)
            .padding(6);

        IWidget text = new TextWidget(IKey.lang("galaxia.gui.select_module"));
        column.child(text)
            .align(Alignment.Center)
            .widthRel(1f);

        for (ModuleTypes mt : ModuleTypes.values()) {
            ModuleType type = mt.data;
            String id = type.getId();
            String name = StatCollector.translateToLocal("galaxia.module." + id + ".name");
            boolean selected = id.equals(ItemHabitatBuilder.getSelectedModule(held));

            ButtonWidget button = new ButtonWidget<>().size(220, 36)
                .tooltip(tooltip -> tooltip.add(IKey.str(getModuleTooltip(type))))
                .onMousePressed((mouseButton) -> {
                    if (mouseButton == 0) {
                        ItemHabitatBuilder.setSelectedModule(held, id);
                        player.inventory.markDirty();
                        if (player instanceof EntityPlayerMP mp) {
                            mp.inventoryContainer.detectAndSendChanges();
                        }
                        panel.closeIfOpen();
                    }
                    return true;
                });

            if (selected) {
                button.background(IKey.str("§a"));
            }

            Widget label = IKey.str((selected ? "§a▶ §f" : "§7  ") + name)
                .asWidget()
                .pos(16, 10);

            button.child(label);
            column.child(button);
        }

        panel.child(column);
        return panel;
    }

    private String getModuleTooltip(ModuleType type) {
        return "§7Module: §f" + type.getId()
            .replace("_", " ")
            .toUpperCase()
            + "\n\n"
            + "§eBuilding Cost (Placeholder):\n"
            + "§f• 24× Steel Plate\n"
            + "§f• 12× Glass Pane\n"
            + "§f• 8× Advanced Circuit\n"
            + "§f• 4× Advanced Alloy\n\n"
            + "§aYou have enough resources";
    }
}
