package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

public class RocketEditorUI implements IGuiHolder<GuiData> {
    private final RocketBlueprint targetBlueprint;
    private final RocketBlueprint workingBlueprint;
    private RocketPartDef selectedPart;

    private final int cellSize = 20;
    private final int canvasPadding = 8;

    public RocketEditorUI(RocketBlueprint targetBlueprint) {
        this.targetBlueprint = targetBlueprint;
        this.workingBlueprint = targetBlueprint.copy();
    }

    public RocketEditorUI(PosGuiData data) {
        this(new RocketBlueprint());
    }

    public void open(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP mp)) {
            return;
        }
        GuiFactories.createSimple("galaxia:rocket_editor", () -> new RocketEditorUI(targetBlueprint))
            .open(mp);
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = ModularPanel.defaultPanel("rocket_editor");
        panel.size(700, 420);

        panel.child(createCanvas().pos(8, 8));
        panel.child(createPalette().pos(520, 8));
        panel.child(createStatusLabel().pos(520, 330));
        panel.child(createClearSelectionButton().pos(520, 360));
        panel.child(createClearBlueprintButton().pos(520, 388));

        panel.onCloseAction(() -> targetBlueprint.replaceWith(workingBlueprint));

        return panel;
    }

    private ParentWidget<?> createCanvas() {
        int w = workingBlueprint.getWidth() * cellSize + canvasPadding * 2;
        int h = workingBlueprint.getHeight() * cellSize + canvasPadding * 2;

        ParentWidget<?> canvas = new ParentWidget<>()
            .size(w, h)
            .background(EnumTextures.SELECTION_FRAME.getImage());

        for (int y = 0; y < workingBlueprint.getHeight(); y++) {
            for (int x = 0; x < workingBlueprint.getWidth(); x++) {
                canvas.child(createCellButton(x, y));
            }
        }

        return canvas;
    }

    private ButtonWidget<?> createCellButton(final int cellX, final int cellY) {
        final int px = canvasPadding + cellX * cellSize;
        final int py = canvasPadding + cellY * cellSize;

        return new ButtonWidget<>()
            .pos(px, py)
            .size(cellSize, cellSize)
            .background(EnumTextures.OVERWORLD.getImage())
            .overlay(IKey.dynamic(() -> cellLabel(cellX, cellY)))
            .tooltipDynamic(t -> {
                RocketPartInstance part = workingBlueprint.partAt(cellX, cellY);
                if (part == null) {
                    t.addLine(EnumChatFormatting.GRAY + "Empty");
                } else {
                    t.addLine(EnumChatFormatting.WHITE + part.def().name());
                    t.addLine(EnumChatFormatting.GRAY + String.format(
                        "%.1fm | %.0fkg",
                        part.def().height(),
                        part.def().weight()
                    ));
                }
            })
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    if (selectedPart != null) {
                        RocketPartInstance candidate = new RocketPartInstance(selectedPart, cellX, cellY, 0, false);
                        if (workingBlueprint.canPlacePart(candidate)) {
                            workingBlueprint.addPart(candidate);
                        }
                    }

                    RocketPartInstance existing = workingBlueprint.partAt(cellX, cellY);
                    if (existing != null) {
                        selectedPart = existing.def();
                    }
                }

                if (md.mouseButton == 1) {
                    workingBlueprint.removePartAt(cellX, cellY);
                }

            }));
    }

    private String cellLabel(int cellX, int cellY) {
        RocketPartInstance part = workingBlueprint.partAt(cellX, cellY);
        if (part == null) {
            return "";
        }
        String name = part.def().name();
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private ParentWidget<?> createPalette() {
        ParentWidget<?> panel = new ParentWidget<>()
            .size(160, 300)
            .background(EnumTextures.SELECTION_FRAME.getImage());

        Flow flow = Flow.column().coverChildren().padding(4).margin(4);

        List<RocketPartDef> parts = RocketPartRegistry.instance().getAll();
        for (RocketPartDef def : parts) {
            flow.child(createPaletteButton(def));
        }

        panel.child(flow);
        return panel;
    }

    private ButtonWidget<?> createPaletteButton(final RocketPartDef def) {
        return new ButtonWidget<>()
            .size(150, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(IKey.str(def.name()))
            .tooltip(t -> t.addLine(EnumChatFormatting.GRAY + String.format(
                "%.1fm | %.0fkg",
                def.height(),
                def.weight()
            )))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    selectedPart = def;
                }
                if (md.mouseButton == 1 && selectedPart == def) {
                    selectedPart = null;
                }
            }));
    }

    private ButtonWidget<?> createClearSelectionButton() {
        return new ButtonWidget<>()
            .size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(IKey.str("Clear Selection").alignment(Alignment.Center))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    selectedPart = null;
                }
            }));
    }

    private ButtonWidget<?> createClearBlueprintButton() {
        return new ButtonWidget<>()
            .size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(IKey.str("Clear Blueprint").alignment(Alignment.Center))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton == 0) {
                    workingBlueprint.clear();
                }
            }));
    }

    private ParentWidget<?> createStatusLabel() {
        return new ParentWidget<>()
            .size(160, 20)
            .child(
                IKey.dynamic(() -> {
                    if (selectedPart == null) {
                        return "Selected: none";
                    }
                    return "Selected: " + selectedPart.name();
                }).asWidget()
            );
    }
}
