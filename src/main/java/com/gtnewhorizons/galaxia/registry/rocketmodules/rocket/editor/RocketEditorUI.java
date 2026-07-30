package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.SingleChildWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.core.network.CommitBlueprintAndOrderPacket;
import com.gtnewhorizons.galaxia.core.network.DestinationSetPacket;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

public class RocketEditorUI {

    public static ModularPanel build(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        TileEntitySilo silo = getSilo(data);
        if (silo == null) {
            return buildErrorPanel();
        }

        RocketBlueprint workingBlueprint = silo.getDesignBlueprint()
            .copy();

        final int[] selectedId = { -1 };
        IntSyncValue selectedPartId = new IntSyncValue(() -> selectedId[0], v -> selectedId[0] = v).allowC2S();
        syncManager.syncValue("selected_part_id", selectedPartId);

        final int[] selectedCat = { 0 };
        IntSyncValue selectedCategory = new IntSyncValue(() -> selectedCat[0], v -> selectedCat[0] = v).allowC2S();
        syncManager.syncValue("selected_category", selectedCategory);

        IntSyncValue buildStatusSync = new IntSyncValue(
            () -> silo.getBuildStatus()
                .ordinal(),
            v -> {});
        syncManager.syncValue("build_status", buildStatusSync);

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_editor")
            .size(483, 248)
            .background(EnumTextures.SILO_GUI_BASE.getImage());

        RocketCanvasWidget canvas = new RocketCanvasWidget(workingBlueprint, silo);
        canvas.resetView();
        canvas.setSelectedPartSupplier(() -> {
            int id = selectedPartId.getValue();
            return id >= 0 ? RocketPartRegistry.instance()
                .get(id) : null;
        });

        panel.child(createCategoryColumn(selectedCategory, selectedPartId).margin(6, 6));
        panel.child(createModuleTabs(selectedCategory, selectedPartId).margin(62, 7));
        panel.child(createCanvasContainer(canvas).margin(153, 6));
        panel.child(createNameFieldLayer().margin(156, 9));
        panel.child(createStagesPanel().margin(403, 2));
        panel
            .child(createBottomToolbar(silo, workingBlueprint, selectedPartId, buildStatusSync, data).margin(258, 219));

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

    private static ModularPanel buildErrorPanel() {
        ModularPanel panel = ModularPanel.defaultPanel("rocket_editor_error");
        panel.size(200, 60);
        panel.child(
            IKey.lang("galaxia.rocket_editor.silo_not_found")
                .style(EnumChatFormatting.RED)
                .asWidget()
                .margin(10, 20));
        return panel;
    }

    private static ParentWidget<?> createCategoryColumn(IntSyncValue selectedCategory, IntSyncValue selectedPartId) {
        Flow col = Flow.column()
            .coverChildren()
            .childPadding(2);

        RocketPartCategory[] cats = RocketPartCategory.values();
        for (int i = 0; i < cats.length; i++) {
            final int idx = i;
            RocketPartCategory cat = cats[i];

            col.child(
                new ButtonWidget<>().size(24, 24)
                    .background(new DynamicDrawable(() -> {
                        if (selectedCategory.getValue() == idx)
                            return EnumTextures.SILO_CATEGORY_BASE_SELECTED.getImage();
                        return EnumTextures.SILO_CATEGORY_BASE.getImage();
                    }))
                    .overlay(cat.icon.getImage())
                    .tooltip(t -> t.addLine(IKey.lang(cat.langKey)))
                    .onMousePressed(btn -> {
                        if (btn == 0) {
                            selectedCategory.setValue(idx);
                            List<IRocketPartDef> filtered = RocketPartRegistry.instance()
                                .getByCategory(cat);
                            if (!filtered.isEmpty()) {
                                selectedPartId.setValue(
                                    filtered.getFirst()
                                        .id());
                            } else {
                                selectedPartId.setValue(-1);
                            }
                            return true;
                        }
                        return false;
                    }));
        }

        return new ParentWidget<>().size(24, 200)
            .child(col);
    }

    private static ParentWidget<?> createModuleTabs(IntSyncValue selectedCategory, IntSyncValue selectedPartId) {
        ParentWidget<?> container = new ParentWidget<>().size(34, 240);

        RocketPartCategory[] cats = RocketPartCategory.values();
        for (int i = 0; i < cats.length; i++) {
            final int catIdx = i;
            RocketPartCategory cat = cats[i];

            Flow col = Flow.column()
                .coverChildren()
                .childPadding(2);

            for (IRocketPartDef part : RocketPartRegistry.instance()
                .getByCategory(cat)) {
                final int partId = part.id();
                col.child(
                    new ButtonWidget<>().size(34, 34)
                        .background(new DynamicDrawable(() -> {
                            if (selectedPartId.getValue() == partId)
                                return EnumTextures.SILO_PART_BACKGROUND_SELECTED.getImage();
                            return EnumTextures.SILO_PART_BACKGROUND.getImage();
                        }))
                        .overlay(UITexture.fullImage(part.siloIconLocation()))
                        .tooltip(t -> {
                            t.addLine(IKey.str(part.name()));
                            t.add(
                                UITexture.fullImage(part.spriteLocation())
                                    .asIcon()
                                    .size(48, 48 * part.height() / part.width()));
                        })
                        .onMousePressed(b -> {
                            if (b == 0) {
                                selectedPartId.setValue(partId);
                                return true;
                            }
                            return false;
                        }));
            }

            col.setEnabledIf(w -> selectedCategory.getValue() == catIdx);
            container.child(col);
        }

        return container;
    }

    private static TextFieldWidget createNameField() {
        return new TextFieldWidget().width(160)
            .background(EnumTextures.SILO_FIELD_TEXT_MIDDLE.getImage())
            .setMaxLength(24)
            .setTextColor(0xFFFFFFFF)
            .hintText(StatCollector.translateToLocal("galaxia.gui.orbital.search.placeholder"))
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .tooltip(_ -> {});
    }

    // workaround for proper layers in mui2 so that canvas doesn't overwrite it
    private static ParentWidget<?> createNameFieldLayer() {
        return new ParentWidget<>().size(160, 18)
            .child(createNameField().size(160, 18));
    }

    private static ParentWidget<?> createCanvasContainer(RocketCanvasWidget canvas) {
        ParentWidget<?> container = new ParentWidget<>().size(208, 208)
            .background(EnumTextures.SILO_BLUEPRINT.getImage());
        container.child(
            canvas.size(202, 202)
                .margin(3));

        return container;
    }

    private static ParentWidget<?> createStagesPanel() {
        ParentWidget<?> container = new ParentWidget<>().size(56, 240);

        int[] stageParts = { 2, 3, 1, 0 };

        int stageWidth = 72;
        int stageGap = 2;
        int rightMargin = 4;
        int bottomMargin = 4;
        int addBtnWidth = 12;
        int addBtnHeight = 11;
        int addBtnYOffset = 2;
        int btnGap = 2;

        int stageX = 56 - rightMargin - stageWidth;
        int currentY = 240 - bottomMargin;

        for (int partsCount : stageParts) {
            if (partsCount == 0) {
                int h = 28;
                currentY -= h;
                addStagePart(container, stageX, currentY, stageWidth, h, EnumTextures.SILO_STAGE_EMPTY, false, 0);
            } else if (partsCount == 1) {
                int h = 32;
                currentY -= h;
                addStagePart(container, stageX, currentY, stageWidth, h, EnumTextures.SILO_STAGE_SINGLE, true, 0);
            } else {
                int hBot = 28;
                currentY -= hBot;
                addStagePart(
                    container,
                    stageX,
                    currentY,
                    stageWidth,
                    hBot,
                    EnumTextures.SILO_STAGE_MULTIPLE_BOTTOM,
                    true,
                    -2);

                int hMid = 24;
                for (int j = 0; j < partsCount - 2; j++) {
                    currentY -= hMid;
                    addStagePart(
                        container,
                        stageX,
                        currentY,
                        stageWidth,
                        hMid,
                        EnumTextures.SILO_STAGE_MULTIPLE_MIDDLE,
                        true,
                        0);
                }

                int hTop = 28;
                currentY -= hTop;
                addStagePart(
                    container,
                    stageX,
                    currentY,
                    stageWidth,
                    hTop,
                    EnumTextures.SILO_STAGE_MULTIPLE_TOP,
                    true,
                    2);
            }

            int blockTopY = currentY;
            container.child(
                new ButtonWidget<>().size(addBtnWidth, addBtnHeight)
                    .pos(stageX - addBtnWidth, blockTopY + addBtnYOffset)
                    .background(EnumTextures.SILO_STAGE_ADD.getImage())
                    .onMousePressed(_ -> true));
            container.child(
                new ButtonWidget<>().size(addBtnWidth, addBtnHeight)
                    .pos(stageX - addBtnWidth, blockTopY + addBtnYOffset + addBtnHeight + btnGap)
                    .background(EnumTextures.SILO_STAGE_REMOVE.getImage())
                    .onMousePressed(_ -> true));

            currentY -= stageGap;
        }

        return container;
    }

    private static void addStagePart(ParentWidget<?> container, int x, int y, int w, int h, EnumTextures bg,
        boolean renderIcon, int iconYOffset) {
        container.child(
            new ButtonWidget<>().size(w, h)
                .pos(x, y)
                .background(bg.getImage())
                .onMousePressed(b -> true));

        if (renderIcon) {
            int iconSize = 22;
            int iconY = y + (h - iconSize) / 2 + iconYOffset;
            container.child(
                new ButtonWidget<>().size(iconSize, iconSize)
                    .pos(x + 3, iconY)
                    .background(EnumTextures.SILO_STAGE_BURN_LIQUID.getImage())
                    .onMousePressed(b -> true));
        }
    }

    private static ParentWidget<?> createBottomToolbar(TileEntitySilo silo, RocketBlueprint blueprint,
        IntSyncValue selectedPartId, IntSyncValue buildStatusSync, PosGuiData data) {

        Flow row = Flow.row()
            .coverChildren()
            .childPadding(0);

        row.child(
            new SingleChildWidget<>().size(18, 20)
                .background(EnumTextures.SILO_FIELD_ICON_LEFT.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .margin(2, 2)
                        .background(EnumTextures.SILO_CLEAR_ICON.getImage())
                        .tooltip(t -> t.addLine(IKey.lang("galaxia.rocket_editor.clear_blueprint")))
                        .onMousePressed(btn -> {
                            if (btn == 0 && silo.getBuildStatus()
                                .canEdit()) {
                                blueprint.clear();
                                selectedPartId.setValue(-1);
                                silo.sync();
                                return true;
                            }
                            return false;
                        })));

        row.child(
            new SingleChildWidget<>().size(16, 20)
                .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .margin(0, 2)
                        .background(EnumTextures.SILO_SAVE_BLUEPRINT_ICON.getImage())
                        .tooltip(t -> t.addLine(IKey.lang("galaxia.rocket_editor.save_schematic")))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                            if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) {
                                silo.captureSchematic(data.getPlayer());
                            }
                        }))));

        row.child(
            new SingleChildWidget<>().size(16, 20)
                .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .margin(0, 2)
                        .background(EnumTextures.SILO_SAVE_BLUEPRINT_ICON.getImage())
                        .tooltip(t -> t.addLine(IKey.lang("galaxia.rocket_editor.return_modules")))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                            if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) {
                                silo.returnModules();
                            }
                        }))));

        row.child(
            new SingleChildWidget<>().size(16, 20)
                .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .margin(0, 2)
                        .background(EnumTextures.SILO_ORDER_MODULES_ICON.getImage())
                        .tooltip(t -> {
                            RocketBuildStatus status = RocketBuildStatus.values()[buildStatusSync.getValue()];
                            String key = switch (status) {
                                case IDLE -> blueprint.isEmpty() ? "galaxia.rocket_editor.order_modules_empty"
                                    : "galaxia.rocket_editor.order_modules";
                                case DESIGNED -> "galaxia.rocket_editor.order_modules";
                                case ASSEMBLING -> "galaxia.rocket_editor.assembling";
                                case READY -> "galaxia.rocket_editor.ready_launch";
                            };
                            t.addLine(IKey.lang(key));
                        })
                        .onMousePressed(btn -> {
                            if (btn != 0) return false;
                            GALAXIA_NETWORK.sendToServer(
                                new CommitBlueprintAndOrderPacket(
                                    silo.xCoord,
                                    silo.yCoord,
                                    silo.zCoord,
                                    blueprint.serializeNBT()));
                            return true;
                        })));

        row.child(
            new SingleChildWidget<>().size(16, 20)
                .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .margin(0, 2)
                        .background(EnumTextures.SILO_DESTINATION_ICON.getImage())
                        .tooltip(t -> {
                            DimensionEnum current = null;
                            for (DimensionEnum d : DimensionEnum.values()) {
                                if (d.getId() == silo.getDestination()) {
                                    current = d;
                                    break;
                                }
                            }
                            t.addLine(
                                IKey.str(
                                    StatCollector.translateToLocalFormatted(
                                        "galaxia.rocket_editor.destination",
                                        current != null ? current.getName() : "?")));
                            t.addLine(IKey.lang("galaxia.rocket_editor.click_cycle"));
                        })
                        .onMousePressed(btn -> {
                            if (btn != 0) return false;
                            DimensionEnum[] dims = DimensionEnum.values();
                            int cur = 0;
                            for (int i = 0; i < dims.length; i++) {
                                if (dims[i].getId() == silo.getDestination()) {
                                    cur = i;
                                    break;
                                }
                            }
                            int next = (cur + 1) % dims.length;
                            GALAXIA_NETWORK.sendToServer(
                                new DestinationSetPacket(silo.xCoord, silo.yCoord, silo.zCoord, dims[next].getId()));
                            return true;
                        })));

        row.child(
            new SingleChildWidget<>().size(18, 20)
                .background(EnumTextures.SILO_FIELD_ICON_RIGHT.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .margin(-2, 2)
                        .background(EnumTextures.SILO_ENTER_ROCKET_ICON.getImage())
                        .tooltip(
                            t -> t.addLine(
                                IKey.lang(
                                    silo.getBuildStatus()
                                        .canLaunch() ? "galaxia.rocket_editor.enter_rocket"
                                            : "galaxia.rocket_editor.enter_rocket_not_ready")))
                        .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                            if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) {
                                silo.enterRocket(data);
                            }
                        }))));

        return new ParentWidget<>().size(100, 20)
            .child(row);
    }
}
