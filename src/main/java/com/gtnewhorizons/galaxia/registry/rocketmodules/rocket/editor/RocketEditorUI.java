package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
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
import com.gtnewhorizons.galaxia.core.network.CommitBlueprintAndOrderPacket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class RocketEditorUI {

    public static ModularPanel build(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        TileEntitySilo silo = getSilo(data);

        if (silo == null) {
            return buildErrorPanel("Silo not found!");
        }

        RocketBlueprint workingBlueprint = silo.getDesignBlueprint()
            .copy();

        final int[] selectedId = { -1 };
        IntSyncValue selectedPartId = new IntSyncValue(() -> selectedId[0], val -> selectedId[0] = val).allowC2S();
        syncManager.syncValue("selected_part_id", selectedPartId);

        IntSyncValue buildStatusSync = new IntSyncValue(
            () -> silo.getBuildStatus()
                .ordinal(),
            v -> {});
        syncManager.syncValue("build_status", buildStatusSync);
        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_editor")
            .size(720, 640);

        RocketCanvasWidget canvas = new RocketCanvasWidget(workingBlueprint, silo);
        canvas.resetView();
        canvas.setSelectedPartSupplier(() -> {
            int id = selectedPartId.getValue();
            return id >= 0 ? RocketPartRegistry.instance()
                .get(id) : null;
        });

        panel.child(createCanvasContainer(canvas).pos(12, 12));
        panel.child(createPalette(selectedPartId).pos(548, 12));
        panel.child(createStatusLabel(selectedPartId).pos(548, 340));
        panel.child(createResetViewButton(canvas).pos(548, 368));
        panel.child(createClearSelectionButton(selectedPartId).pos(548, 396));
        panel.child(createClearBlueprintButton(workingBlueprint, selectedPartId, silo).pos(548, 424));
        panel.child(createOrderModulesButton(silo, workingBlueprint, buildStatusSync).pos(548, 452));

        panel.onCloseAction(() -> {
            if (silo.getBuildStatus()
                .canEdit()) {
                silo.setDesignBlueprint(workingBlueprint);
            }
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

    private static ParentWidget<?> createCanvasContainer(RocketCanvasWidget canvas) {
        ParentWidget<?> container = new ParentWidget<>().size(536, 416)
            .background(EnumTextures.SELECTION_FRAME.getImage());
        container.child(
            canvas.size(520, 400)
                .pos(8, 8));
        return container;
    }

    private static ParentWidget<?> createPalette(IntSyncValue selectedPartId) {
        ParentWidget<?> panel = new ParentWidget<>().size(160, 320)
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
                t -> { t.addLine(EnumChatFormatting.GRAY + String.format("%sm | %skg", def.height(), def.weight())); })
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

    private static ButtonWidget<?> createResetViewButton(RocketCanvasWidget canvas) {
        return new ButtonWidget<>().size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(
                IKey.str("Reset View")
                    .alignment(Alignment.Center))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0) {
                    canvas.resetView();
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
        TileEntitySilo silo) {

        return new ButtonWidget<>().size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(
                IKey.str("Clear Blueprint")
                    .alignment(Alignment.Center))
            .onMousePressed(mouseButton -> {
                if (mouseButton == 0 && silo.getBuildStatus()
                    .canEdit()) {
                    blueprint.clear();
                    selectedPartId.setValue(-1);
                    silo.sync();
                    return true;
                }
                return false;
            });
    }

    private static ButtonWidget<?> createOrderModulesButton(TileEntitySilo silo, RocketBlueprint workingBlueprint,
        IntSyncValue buildStatusSync) {
        return new ButtonWidget<>().size(160, 20)
            .background(EnumTextures.SELECTION_FRAME.getImage())
            .overlay(IKey.dynamic(() -> {
                RocketBuildStatus status = RocketBuildStatus.values()[buildStatusSync.getValue()];

                return switch (status) {
                    case IDLE -> workingBlueprint.isEmpty() ? EnumChatFormatting.DARK_GRAY + "Order Modules"
                        : EnumChatFormatting.YELLOW + "Order Modules";

                    case DESIGNED -> EnumChatFormatting.YELLOW + "Order Modules";

                    case ASSEMBLING -> EnumChatFormatting.AQUA + "Assembling... ("
                        + (silo.getCurrentBuildOrder() != null ? Math.round(
                            silo.getCurrentBuildOrder()
                                .getProgress() * 100)
                            + "%" : "0%")
                        + ")";

                    case READY -> EnumChatFormatting.GREEN + "Ready to Launch";

                    case LAUNCHED -> EnumChatFormatting.GRAY + "Launched";
                };
            })
                .alignment(Alignment.Center))
            .onMousePressed(mouseButton -> {

                if (mouseButton != 0) {
                    return false;
                }

                GALAXIA_NETWORK.sendToServer(
                    new CommitBlueprintAndOrderPacket(
                        silo.xCoord,
                        silo.yCoord,
                        silo.zCoord,
                        workingBlueprint.serializeNBT()));

                return true;
            });
    }

    private static ParentWidget<?> createStatusLabel(IntSyncValue selectedPartId) {
        return new ParentWidget<>().size(160, 20)
            .child(IKey.dynamic(() -> {
                int id = selectedPartId.getValue();
                if (id < 0) return "Selected: none";
                IRocketPartDef def = RocketPartRegistry.instance()
                    .get(id);
                return "Selected: " + (def != null ? def.name() : "Unknown");
            })
                .asWidget());
    }
}
