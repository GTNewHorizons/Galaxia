package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class RocketEditorUI {

    private static final int CELL_SIZE = 20;
    private static final int CANVAS_PADDING = 8;

    public static ModularPanel build(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        TileEntitySilo silo = getSilo(data);
        if (silo == null) {
            return buildErrorPanel("Silo not found!");
        }

        RocketBlueprint workingBlueprint = silo.getBlueprint()
            .copy();

        final int[] selectedId = { -1 };
        IntSyncValue selectedPartId = new IntSyncValue(() -> selectedId[0], val -> selectedId[0] = val);
        syncManager.syncValue("selected_part_id", selectedPartId);

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_editor")
            .size(720, 440);

        ParentWidget<?> canvas = createCanvas(workingBlueprint, selectedPartId, silo);
        panel.child(canvas.pos(12, 12));
        panel.child(createPalette(selectedPartId).pos(540, 12));
        panel.child(createStatusLabel(selectedPartId).pos(540, 340));
        panel.child(createClearSelectionButton(selectedPartId).pos(540, 370));
        panel.child(createClearBlueprintButton(workingBlueprint, selectedPartId, canvas, silo).pos(540, 398));

        panel.onCloseAction(() -> {
            silo.setBlueprint(workingBlueprint);
            silo.sync();
        });

        return panel;
    }

    private static TileEntitySilo getSilo(PosGuiData data) {
        if (data == null) return null;
        World world = data.getWorld();
        if (world == null) return null;
        TileEntity te = world.getTileEntity(data.getX(), data.getY(), data.getZ());
        return te instanceof TileEntitySilo s ? s : null;
    }

    private static ModularPanel buildErrorPanel(String text) {
        ModularPanel panel = ModularPanel.defaultPanel("rocket_editor_error");
        panel.size(280, 120);
        panel.child(
            IKey.str(EnumChatFormatting.RED + text)
                .asWidget()
                .pos(10, 10));
        return panel;
    }

    private static ParentWidget<?> createCanvas(RocketBlueprint blueprint, IntSyncValue selectedPartId,
        TileEntitySilo silo) {
        int w = blueprint.getWidth() * CELL_SIZE + CANVAS_PADDING * 2;
        int h = blueprint.getHeight() * CELL_SIZE + CANVAS_PADDING * 2;

        ParentWidget<?> canvas = new ParentWidget<>().size(w, h)
            .background(EnumTextures.SELECTION_FRAME.getImage());

        ParentWidget<?> partLayer = new ParentWidget<>().size(w, h);

        for (int y = 0; y < blueprint.getHeight(); y++) {
            for (int x = 0; x < blueprint.getWidth(); x++) {
                canvas.child(createCellButton(x, y, blueprint, selectedPartId, partLayer, silo));
            }
        }

        canvas.child(partLayer);
        rebuildPartLayer(partLayer, blueprint, silo);

        return canvas;
    }

    private static void rebuildPartLayer(ParentWidget<?> partLayer, RocketBlueprint blueprint, TileEntitySilo silo) {
        partLayer.removeAll();

        for (RocketPartInstance part : blueprint.getParts()) {
            partLayer.child(createPartButton(part, blueprint, partLayer, silo));
        }
    }

    private static ButtonWidget<?> createCellButton(int cellX, int cellY, RocketBlueprint blueprint,
        IntSyncValue selectedPartId, ParentWidget<?> partLayer, TileEntitySilo silo) {
        return new ButtonWidget<>().pos(CANVAS_PADDING + cellX * CELL_SIZE, CANVAS_PADDING + cellY * CELL_SIZE)
            .size(CELL_SIZE, CELL_SIZE)
            .background(EnumTextures.OVERWORLD.getImage())
            .overlay(IKey.dynamic(() -> cellLabel(blueprint, cellX, cellY)))
            .tooltipDynamic(t -> {
                RocketPartInstance part = blueprint.partAt(cellX, cellY, 0);
                if (part == null) {
                    t.addLine(EnumChatFormatting.GRAY + "Empty");
                    return;
                }
                t.addLine(
                    EnumChatFormatting.WHITE + part.def()
                        .name());
                t.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        "%.1fm | %.0fkg",
                        part.def()
                            .height(),
                        part.def()
                            .weight()));
            })
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0) {
                    int id = selectedPartId.getValue();
                    if (id < 0) return false;

                    IRocketPartDef def = RocketPartRegistry.instance()
                        .get(id);
                    if (def == null) return false;

                    RocketPartInstance candidate = new RocketPartInstance(def, cellX, cellY, 0, false);
                    if (!blueprint.canPlacePart(candidate)) return false;

                    blueprint.addPart(candidate);
                    rebuildPartLayer(partLayer, blueprint, silo);
                    silo.sync();
                    return true;
                }

                if (mouseButton == 1) {
                    blueprint.removePartAt(cellX, cellY, 0);
                    rebuildPartLayer(partLayer, blueprint, silo);
                    silo.sync();
                    return true;
                }
                return false;
            });
    }

    private static ButtonWidget<?> createPartButton(RocketPartInstance part, RocketBlueprint blueprint,
        ParentWidget<?> partLayer, TileEntitySilo silo) {
        int w = part.def()
            .getWidthCells();
        int h = part.def()
            .getHeightCells();

        return new ButtonWidget<>().pos(CANVAS_PADDING + part.x() * CELL_SIZE, CANVAS_PADDING + part.y() * CELL_SIZE)
            .size(w * CELL_SIZE, h * CELL_SIZE)
            .background(
                part.def()
                    .textureLocation() != null ? UITexture.fullImage(
                        part.def()
                            .textureLocation())
                        : EnumTextures.ICON_MISSING.getImage())
            .overlay(IKey.str(partLabel(part)))
            .tooltip(t -> {
                t.addLine(
                    EnumChatFormatting.WHITE + part.def()
                        .name());
                t.addLine(
                    EnumChatFormatting.GRAY + String.format(
                        "%.1fm | %.0fkg",
                        part.def()
                            .height(),
                        part.def()
                            .weight()));
            })
            .onMousePressed(mouseButton -> {

                if (mouseButton == 1) {
                    blueprint.removePartAt(part.x(), part.y(), 0);
                    rebuildPartLayer(partLayer, blueprint, silo);
                    silo.sync();
                    return true;
                }

                return false;
            });
    }

    private static String cellLabel(RocketBlueprint bp, int x, int y) {
        RocketPartInstance p = bp.partAt(x, y, 0);
        return p == null ? "" : partLabel(p);
    }

    private static String partLabel(RocketPartInstance part) {
        String name = part.def()
            .name();
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, Math.min(2, name.length()))
            .toUpperCase();
    }

    private static ParentWidget<?> createPalette(IntSyncValue selectedPartId) {
        ParentWidget<?> panel = new ParentWidget<>().size(160, 300)
            .background(EnumTextures.SELECTION_FRAME.getImage());

        Flow flow = Flow.column()
            .coverChildren()
            .padding(4)
            .margin(4);

        for (IRocketPartDef def : RocketPartRegistry.instance()
            .getAll()) {
            flow.child(createPaletteButton(def, selectedPartId));
        }

        panel.child(flow);
        return panel;
    }

    private static ButtonWidget<?> createPaletteButton(IRocketPartDef def, IntSyncValue selectedPartId) {
        return new ButtonWidget<>().size(150, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(IKey.str(def.name()))
            .tooltip(
                t -> t.addLine(EnumChatFormatting.GRAY + String.format("%.1fm | %.0fkg", def.height(), def.weight())))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0) {
                    selectedPartId.setValue(def.id());
                    return true;
                }

                if (mouseButton == 1 && selectedPartId.getValue() == def.id()) {
                    selectedPartId.setValue(-1);
                    return true;
                }
                return false;
            });
    }

    private static ButtonWidget<?> createClearSelectionButton(IntSyncValue selectedPartId) {
        return new ButtonWidget<>().size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(
                IKey.str("Clear Selection")
                    .alignment(Alignment.Center))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0) {
                    selectedPartId.setValue(-1);
                    return true;
                }
                return false;
            });
    }

    private static ButtonWidget<?> createClearBlueprintButton(RocketBlueprint blueprint, IntSyncValue selectedPartId,
        ParentWidget<?> partLayer, TileEntitySilo silo) {
        return new ButtonWidget<>().size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(
                IKey.str("Clear Blueprint")
                    .alignment(Alignment.Center))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0) {
                    blueprint.clear();
                    selectedPartId.setValue(-1);
                    rebuildPartLayer(partLayer, blueprint, silo);
                    silo.sync();
                    return true;
                }
                return false;
            });
    }

    private static ParentWidget<?> createStatusLabel(IntSyncValue selectedPartId) {
        return new ParentWidget<>().size(160, 20)
            .child(IKey.dynamic(() -> {
                int id = selectedPartId.getValue();
                if (id < 0) {
                    return "Selected: none";
                }
                IRocketPartDef def = RocketPartRegistry.instance()
                    .get(id);
                return "Selected: " + (def != null ? def.name() : "Unknown");
            })
                .asWidget());
    }
}
