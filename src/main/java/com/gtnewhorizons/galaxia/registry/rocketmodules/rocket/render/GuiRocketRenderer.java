package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.render;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;

public class GuiRocketRenderer {

    private static final int GRID_SIZE = 20;

    private final RocketBlueprint blueprint;
    private RocketPartDef selectedPart;

    public GuiRocketRenderer(RocketBlueprint blueprint) {
        this.blueprint = blueprint;
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
                    .onMousePressed(mb -> handleCellClick(mb, cellX, cellY));

                grid.child(cell);
            }
        }

        for (RocketPartInstance part : blueprint.getParts()) {
            RocketPartDef def = part.def();

            int px = part.x() * GRID_SIZE;
            int py = part.y() * GRID_SIZE;
            int pw = def.getWidthCells() * GRID_SIZE;
            int ph = def.getHeightCells() * GRID_SIZE;

            ParentWidget<?> partWidget = new ParentWidget<>().pos(px, py)
                .size(pw, ph);

            if (def.textureLocation() != null) {
                partWidget.background(UITexture.fullImage(def.textureLocation()));
            } else {
                partWidget.background(new Rectangle().color(0x88FF8800));
            }

            grid.child(partWidget);
        }

        return grid;
    }

    private boolean handleCellClick(int mouseButton, int cellX, int cellY) {
        if (mouseButton == 1) {
            blueprint.removePartAt(cellX, cellY, 0);
            return true;
        }

        if (mouseButton == 0 && selectedPart != null) {
            RocketPartInstance candidate = new RocketPartInstance(selectedPart, cellX, cellY, 0, false);
            return blueprint.addPart(candidate);
        }
        return false;
    }

    private IWidget buildPalette() {
        Flow flow = Flow.column()
            .pos(540, 8)
            .coverChildren()
            .margin(3);

        for (RocketPartDef def : RocketPartRegistry.instance()
            .getAll()) {
            flow.child(createPaletteButton(def));
        }

        flow.child(
            new ButtonWidget<>().size(140, 22)
                .overlay(IKey.str("Clear"))
                .background(new Rectangle().color(0xFF772222))
                .onMousePressed(mb -> {
                    if (mb == 0) blueprint.clear();
                    return true;
                }));

        return flow;
    }

    private ButtonWidget<?> createPaletteButton(RocketPartDef def) {
        return new ButtonWidget<>().size(140, 22)
            .overlay(IKey.str(def.name()))
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .tooltip(t -> {
                t.add(def.name());
                t.add(String.format("%.0f kg | %.1fm", def.weight(), def.height()));
            })
            .onMousePressed(mb -> {
                if (mb == 0) selectedPart = def;
                return true;
            });
    }
}
