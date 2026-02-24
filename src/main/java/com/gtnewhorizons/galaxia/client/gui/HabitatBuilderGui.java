package com.gtnewhorizons.galaxia.client.gui;

import static com.gtnewhorizons.galaxia.core.Galaxia.channel;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.gtnewhorizons.galaxia.items.special.ItemHabitatBuilder;
import com.gtnewhorizons.galaxia.modules.ModuleType;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;
import com.gtnewhorizons.galaxia.utility.EnumColors;

// TODO: JAVADOC
public class HabitatBuilderGui {

    private final GuiData guiData;
    private final PanelSyncManager syncManager;

    public HabitatBuilderGui(GuiData guiData, PanelSyncManager syncManager) {
        this.guiData = guiData;
        this.syncManager = syncManager;
    }

    public ModularPanel build() {
        EntityPlayer player = guiData.getPlayer();
        int slot = player.inventory.currentItem;
        ItemStack held = player.inventory.getStackInSlot(slot);

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:habitat_builder")
            .size(250, 240);

        Column column = new Column();
        column.margin(15)
            .padding(6);

        IWidget text = new TextWidget(StatCollector.translateToLocal("galaxia.gui.select_module"));
        column.child(text)
            .align(Alignment.Center)
            .widthRel(1f);
        column.child(
            IDrawable.EMPTY.asWidget()
                .size(1, 6))
            .widthRel(1f);

        for (ModuleTypes mt : ModuleTypes.values()) {
            ModuleType type = mt.data;
            String id = type.getId();
            String name = StatCollector.translateToLocal("galaxia.gui.module." + id + ".name");
            boolean selected = id.equals(ItemHabitatBuilder.getSelectedModule(held));

            ButtonWidget button = new ButtonWidget<>().size(220, 36)
                .tooltip(tooltip -> tooltip.add(IKey.str(getModuleTooltip(type))))
                .onMousePressed((mouseButton) -> {
                    if (mouseButton == 0) {
                        channel.sendToServer(new PacketSetModule(id)); // Must call to server, not directly

                        player.inventory.setInventorySlotContents(slot, held);
                        player.inventory.markDirty();
                        if (player.inventoryContainer != null) {
                            player.inventoryContainer.detectAndSendChanges();
                        }
                        panel.closeIfOpen();
                    }
                    return true;
                });

            IDrawable markerDrawable = selected
                ? IDrawable.of(GuiTextures.PLAY.withColorOverride(EnumColors.IconGreen.getColor()))
                : IDrawable.EMPTY;
            IWidget marker = markerDrawable.asWidget()
                .size(8, 8);
            IWidget label = new TextWidget(IKey.str(name)).color(EnumColors.Value.getColor());

            Row content = new Row();
            content.children(Arrays.asList(marker, label));
            content.mainAxisAlignment(Alignment.MainAxis.CENTER);
            content.crossAxisAlignment(Alignment.CrossAxis.CENTER);
            content.childPadding(4);
            content.widthRel(1f)
                .heightRel(1f);

            button.child(content);
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
