package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.render;

import java.util.List;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;

public class GuiRocketRenderer {

    private static final int GRID_SIZE = 20;

    private final RocketBlueprint blueprint;
    private final List<RocketPartDef> availableParts;

    private RocketPartDef selectedPart;

    public GuiRocketRenderer(RocketBlueprint blueprint, List<RocketPartDef> availableParts) {
        this.blueprint = blueprint;
        this.availableParts = availableParts;
    }

    public IWidget getWidget() {

        ParentWidget<?> root = new ParentWidget<>().size(700, 420);

        root.child(buildGrid());
        root.child(buildPalette());

        return root;
    }

    private IWidget buildGrid() {

        ParentWidget<?> grid = new ParentWidget<>().pos(8, 8)
            .size(blueprint.getWidth() * GRID_SIZE, blueprint.getHeight() * GRID_SIZE)
            .background(new Rectangle().color(0xFF2B2B2B));

        for (int y = 0; y < blueprint.getHeight(); y++) {
            for (int x = 0; x < blueprint.getWidth(); x++) {

                final int cellX = x;
                final int cellY = y;

                ButtonWidget<?> cell = new ButtonWidget<>().pos(x * GRID_SIZE, y * GRID_SIZE)
                    .size(GRID_SIZE, GRID_SIZE)
                    .background(
                        new Rectangle().color(0xFF444444)
                            .hollow())
                    .onMousePressed(mouseButton -> {

                        if (mouseButton == 1) {
                            blueprint.removePartAt(cellX, cellY);
                            return true;
                        }

                        if (mouseButton != 0) {
                            return false;
                        }

                        if (selectedPart == null) {
                            return false;
                        }

                        RocketPartInstance instance = new RocketPartInstance(selectedPart, cellX, cellY, 0, false);

                        if (!blueprint.canPlacePart(instance)) {
                            return false;
                        }

                        blueprint.addPart(instance);
                        return true;
                    });

                grid.child(cell);
            }
        }

        for (RocketPartInstance part : blueprint.getParts()) {

            int width = part.def()
                .getWidthCells() * GRID_SIZE;
            int height = part.def()
                .getHeightCells() * GRID_SIZE;

            ParentWidget<?> partWidget = new ParentWidget<>().pos(part.x() * GRID_SIZE, part.y() * GRID_SIZE)
                .size(width, height);

            if (part.def()
                .texture() != null) {
                partWidget.background(
                    UITexture.fullImage(
                        Galaxia.MODID,
                        (part.def()
                            .texture()
                            .getResourcePath())));
            } else {
                partWidget.background(new Rectangle().color(0x88FF8800));
            }

            grid.child(partWidget);
        }

        return grid;
    }

    private IWidget buildPalette() {

        Flow flow = Flow.column()
            .pos(540, 8)
            .coverChildren()
            .margin(3);

        for (RocketPartDef def : availableParts) {

            ButtonWidget<?> button = new ButtonWidget<>().size(140, 22)
                .overlay(IKey.str(def.name()))
                .background(EnumTextures.SELECTION_FRAME.getImage())
                .tooltip(tooltip -> {
                    tooltip.add(def.name());
                    tooltip.add(String.format("%.0f kg", def.weight()));
                })
                .onMousePressed(mouseButton -> {

                    if (mouseButton != 0) {
                        return false;
                    }

                    selectedPart = def;
                    return true;
                });

            flow.child(button);
        }

        ButtonWidget<?> clearButton = new ButtonWidget<>().size(140, 22)
            .overlay(IKey.str("Clear"))
            .background(new Rectangle().color(0xFF772222))
            .onMousePressed(mouseButton -> {

                if (mouseButton != 0) {
                    return false;
                }

                blueprint.clear();
                return true;
            });

        flow.child(clearButton);

        return flow;
    }
}
