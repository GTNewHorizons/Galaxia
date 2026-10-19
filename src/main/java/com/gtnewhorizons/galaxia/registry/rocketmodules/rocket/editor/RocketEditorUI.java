package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
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
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.render.rockets.ModuleDrawable;
import com.gtnewhorizons.galaxia.core.network.CommitBlueprintAndOrderPacket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketAssembly;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.analysis.RocketStage;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.FuelTankPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * The silo screen: module palette, design canvas, rocket stats and the toolbar opening the destination, automation
 * and cargo panels
 */
public class RocketEditorUI {

    /** Most stages the side panel shows */
    private static final int MAX_STAGES_SHOWN = 5;

    /** Fuel picked for each tank frame, remembered for the rest of the session */
    private static final Map<FuelTankFrame, RocketFuel> CHOSEN_FUEL = new EnumMap<>(FuelTankFrame.class);

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

        // Server-only knowledge (fuel, destinations, automation progress) arrives as one packed tag
        SiloGuiState state = new SiloGuiState();
        NBTTagCompound[] received = { new NBTTagCompound() };
        syncManager.syncValue(
            "silo_state",
            GenericSyncValue.builder(NBTTagCompound.class)
                .getter(() -> silo.getWorldObj().isRemote ? received[0] : SiloGuiState.write(silo))
                .setter(tag -> {
                    received[0] = tag;
                    state.read(tag);
                })
                .adapter(ByteBufAdapters.NBT)
                .build());

        DesignStats stats = new DesignStats(workingBlueprint);

        ModularPanel panel = ModularPanel.defaultPanel("galaxia:rocket_editor")
            .size(483, 248)
            .background(EnumTextures.SILO_GUI_BASE.getImage());

        IPanelHandler destinationPanel = syncManager.syncedPanel(
            "destination",
            true,
            (subSync, handler) -> SiloPanels.destination(silo, subSync, handler, state, stats));
        IPanelHandler automationPanel = syncManager.syncedPanel(
            "automation",
            true,
            (subSync, handler) -> SiloPanels.automation(silo, subSync, handler, state, workingBlueprint));
        IPanelHandler cargoPanel = syncManager
            .syncedPanel("cargo", true, (subSync, handler) -> SiloPanels.cargo(silo, subSync, handler, state));

        FuelTankFrame[] pickerFrame = { null };
        IPanelHandler fuelPicker = IPanelHandler
            .simple(panel, (parent, player) -> SiloPanels.fuelPicker(pickerFrame[0], CHOSEN_FUEL, fuel -> {
                CHOSEN_FUEL.put(pickerFrame[0], fuel);
                FuelTankPartDef tank = RocketPartRegistry.instance()
                    .getFuelTank(pickerFrame[0], fuel);
                if (tank != null) selectedPartId.setValue(tank.id());
            }), true);

        RocketCanvasWidget canvas = new RocketCanvasWidget(workingBlueprint, silo);
        canvas.resetView();
        canvas.setSelectedPartSupplier(() -> {
            int id = selectedPartId.getValue();
            return id >= 0 ? RocketPartRegistry.instance()
                .get(id) : null;
        });

        panel.child(createCategoryColumn(selectedCategory, selectedPartId).margin(6, 6));
        panel.child(createModuleTabs(selectedCategory, selectedPartId, pickerFrame, fuelPicker).margin(62, 7));
        panel.child(createCanvasContainer(canvas).margin(153, 6));
        panel.child(createNameFieldLayer(workingBlueprint).margin(156, 9));
        panel.child(createSidePanel(state, stats).pos(366, 7));
        panel.child(createStatusLine(state, stats).pos(158, 223));
        panel.child(
            createBottomToolbar(
                silo,
                workingBlueprint,
                selectedPartId,
                buildStatusSync,
                data,
                destinationPanel,
                automationPanel,
                cargoPanel).pos(227, 219));

        panel.onCloseAction(() -> {
            // Edits only exist on the client, send them so they survive without ordering
            if (!silo.getWorldObj().isRemote || !silo.getBuildStatus()
                .canEdit()) return;
            silo.setDesignBlueprint(workingBlueprint);
            GALAXIA_NETWORK.sendToServer(
                new CommitBlueprintAndOrderPacket(
                    silo.xCoord,
                    silo.yCoord,
                    silo.zCoord,
                    workingBlueprint.serializeNBT(),
                    false));
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

    /**
     * Analysis of the blueprint being edited, recomputed only when the design changes
     */
    static final class DesignStats {

        private final RocketBlueprint blueprint;
        private int lastHash = 0;
        private RocketAssembly assembly;

        DesignStats(RocketBlueprint blueprint) {
            this.blueprint = blueprint;
        }

        RocketAssembly get() {
            int hash = blueprint.getParts()
                .hashCode();
            if (assembly == null || hash != lastHash) {
                assembly = blueprint.analyze();
                lastHash = hash;
            }
            return assembly;
        }

        /** Delta-v of the design with full tanks */
        double deltaV() {
            return blueprint.isEmpty() ? 0 : get().getTotalDeltaV();
        }
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
                    .name(
                        "silo.category." + cat.name()
                            .toLowerCase())
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
                            selectedPartId.setValue(firstPartOf(cat));
                            return true;
                        }
                        return false;
                    }));
        }

        return new ParentWidget<>().size(24, 200)
            .child(col);
    }

    private static int firstPartOf(RocketPartCategory cat) {
        if (cat == RocketPartCategory.FUEL_TANKS) {
            FuelTankPartDef tank = tankFor(FuelTankFrame.values()[0]);
            return tank != null ? tank.id() : -1;
        }
        List<IRocketPartDef> filtered = RocketPartRegistry.instance()
            .getByCategory(cat);
        return filtered.isEmpty() ? -1
            : filtered.getFirst()
                .id();
    }

    /** The tank module a frame button places, using the fuel last picked for it */
    private static FuelTankPartDef tankFor(FuelTankFrame frame) {
        RocketPartRegistry registry = RocketPartRegistry.instance();
        RocketFuel fuel = CHOSEN_FUEL.get(frame);
        if (fuel == null || !frame.canHold(fuel)) {
            List<RocketFuel> fuels = registry.getFuelsFor(frame);
            if (fuels.isEmpty()) return null;
            fuel = fuels.getFirst();
        }
        return registry.getFuelTank(frame, fuel);
    }

    private static ParentWidget<?> createModuleTabs(IntSyncValue selectedCategory, IntSyncValue selectedPartId,
        FuelTankFrame[] pickerFrame, IPanelHandler fuelPicker) {
        ParentWidget<?> container = new ParentWidget<>().size(70, 240);

        RocketPartCategory[] cats = RocketPartCategory.values();
        for (int i = 0; i < cats.length; i++) {
            final int catIdx = i;
            RocketPartCategory cat = cats[i];

            ParentWidget<?> grid = new ParentWidget<>().size(70, 240);
            if (cat == RocketPartCategory.FUEL_TANKS) {
                FuelTankFrame[] frames = FuelTankFrame.values();
                for (int f = 0; f < frames.length; f++) {
                    grid.child(frameButton(frames[f], selectedPartId, pickerFrame, fuelPicker).pos(gridX(f), gridY(f)));
                }
            } else {
                List<IRocketPartDef> parts = RocketPartRegistry.instance()
                    .getByCategory(cat);
                for (int p = 0; p < parts.size(); p++) {
                    grid.child(partButton(parts.get(p), selectedPartId).pos(gridX(p), gridY(p)));
                }
            }

            grid.setEnabledIf(w -> selectedCategory.getValue() == catIdx);
            container.child(grid);
        }

        return container;
    }

    private static int gridX(int index) {
        return (index % 2) * 35;
    }

    private static int gridY(int index) {
        return (index / 2) * 35;
    }

    private static ButtonWidget<?> partButton(IRocketPartDef part, IntSyncValue selectedPartId) {
        final int partId = part.id();
        return new ButtonWidget<>().size(34, 34)
            .name("silo.part." + part.key())
            .background(new DynamicDrawable(() -> {
                if (selectedPartId.getValue() == partId) return EnumTextures.SILO_PART_BACKGROUND_SELECTED.getImage();
                return EnumTextures.SILO_PART_BACKGROUND.getImage();
            }))
            .overlay(new ModuleDrawable(part, 4))
            .tooltip(t -> {
                t.addLine(IKey.str(part.name()));
                PartTooltips.describe(part, t);
            })
            .onMousePressed(b -> {
                if (b == 0) {
                    selectedPartId.setValue(partId);
                    return true;
                }
                return false;
            });
    }

    /**
     * Button of one tank frame. Left click places it with the last picked core, right click opens the core picker
     */
    private static ButtonWidget<?> frameButton(FuelTankFrame frame, IntSyncValue selectedPartId,
        FuelTankFrame[] pickerFrame, IPanelHandler fuelPicker) {
        return new ButtonWidget<>().size(34, 34)
            .name("silo.frame." + frame.label)
            .background(new DynamicDrawable(() -> {
                IRocketPartDef selected = RocketPartRegistry.instance()
                    .get(selectedPartId.getValue());
                if (selected instanceof FuelTankPartDef tank && tank.frame() == frame)
                    return EnumTextures.SILO_PART_BACKGROUND_SELECTED.getImage();
                return EnumTextures.SILO_PART_BACKGROUND.getImage();
            }))
            .overlay(new ModuleDrawable(() -> tankFor(frame), 4))
            .tooltipDynamic(t -> {
                FuelTankPartDef tank = tankFor(frame);
                if (tank == null) return;
                t.addLine(IKey.str(tank.name()));
                PartTooltips.describe(tank, t);
                t.addLine(IKey.lang("galaxia.rocket_editor.pick_core_hint"));
            })
            .tooltipAutoUpdate(true)
            .onMousePressed(b -> {
                FuelTankPartDef tank = tankFor(frame);
                if (b == 0 && tank != null) {
                    selectedPartId.setValue(tank.id());
                    return true;
                }
                if (b == 1) {
                    pickerFrame[0] = frame;
                    fuelPicker.deleteCachedPanel();
                    fuelPicker.openPanel();
                    return true;
                }
                return false;
            });
    }

    private static TextFieldWidget createNameField(RocketBlueprint blueprint) {
        return new TextFieldWidget().width(160)
            .background(EnumTextures.SILO_FIELD_TEXT_MIDDLE.getImage())
            .setMaxLength(24)
            .setTextColor(0xFFFFFFFF)
            .value(new StringValue.Dynamic(blueprint::getName, blueprint::setName))
            .hintText(StatCollector.translateToLocal("galaxia.rocket_editor.name_placeholder"))
            .hintColor(EnumColors.MapSidebaSearchLabel.getColor())
            .tooltip(_ -> {});
    }

    // workaround for proper layers in mui2 so that canvas doesn't overwrite it
    private static ParentWidget<?> createNameFieldLayer(RocketBlueprint blueprint) {
        return new ParentWidget<>().size(160, 18)
            .child(createNameField(blueprint).size(160, 18));
    }

    private static ParentWidget<?> createCanvasContainer(RocketCanvasWidget canvas) {
        ParentWidget<?> container = new ParentWidget<>().size(208, 208)
            .background(EnumTextures.SILO_BLUEPRINT.getImage());
        container.child(
            canvas.size(202, 202)
                .margin(3));

        return container;
    }

    /**
     * Rocket numbers on the right: build state, delta-v against the destination, mass and the stage stack
     */
    private static ParentWidget<?> createSidePanel(SiloGuiState state, DesignStats stats) {
        ParentWidget<?> side = new ParentWidget<>().size(90, 234);

        side.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal(
                        "galaxia.rocket_editor.status." + state.buildStatus.name()
                            .toLowerCase()),
                    () -> state.buildStatus == RocketBuildStatus.READY ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.ACCENT,
                    0.7f)
                .pos(2, 2));

        side.child(GalaxiaGuiStyle.label(() -> {
            double available = state.buildStatus == RocketBuildStatus.READY ? state.availableDeltaV : stats.deltaV();
            return StatCollector
                .translateToLocalFormatted("galaxia.rocket_editor.delta_v", GalaxiaGuiStyle.deltaV(available));
        }, () -> GalaxiaGuiStyle.TEXT, 0.6f)
            .pos(2, 13));

        side.child(
            GalaxiaGuiStyle.label(
                () -> state.destination.isSet()
                    ? StatCollector.translateToLocalFormatted(
                        "galaxia.rocket_editor.delta_v_needed",
                        GalaxiaGuiStyle.deltaV(state.requiredDeltaV))
                    : StatCollector.translateToLocal("galaxia.rocket_editor.no_destination"),
                () -> {
                    if (!state.destination.isSet()) return GalaxiaGuiStyle.TEXT_DIM;
                    double available = state.buildStatus == RocketBuildStatus.READY ? state.availableDeltaV
                        : stats.deltaV();
                    return available >= state.requiredDeltaV ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.BAD;
                },
                0.6f)
                .pos(2, 21));

        side.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.rocket_editor.mass",
                        String.format(
                            "%,.0f",
                            stats.get()
                                .getTotalMass())),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    0.6f)
                .pos(2, 29));

        side.child(GalaxiaGuiStyle.label(() -> {
            List<String> problems = stats.get()
                .problems();
            if (problems.isEmpty() || stats.blueprint.isEmpty()) return "";
            return StatCollector.translateToLocal(problems.getFirst());
        }, () -> GalaxiaGuiStyle.BAD, 0.6f)
            .width(88)
            .pos(2, 39));

        // Stage stack, bottom stage first like the rocket itself
        for (int i = 0; i < MAX_STAGES_SHOWN; i++) {
            final int slot = i;
            int y = 234 - 34 * (i + 1);
            ParentWidget<?> stage = new ParentWidget<>().size(72, 32)
                .pos(10, y)
                .background(EnumTextures.SILO_STAGE_SINGLE.getImage());
            stage.child(
                new SingleChildWidget<>().size(22, 22)
                    .pos(4, 5)
                    .background(new DynamicDrawable(() -> {
                        RocketStage s = stageAt(stats, slot);
                        if (s == null) return null;
                        return s.hasEngines() ? EnumTextures.SILO_STAGE_BURN_LIQUID.getImage()
                            : EnumTextures.SILO_STAGE_PARACHUTE.getImage();
                    })));
            stage.child(GalaxiaGuiStyle.label(() -> {
                RocketStage s = stageAt(stats, slot);
                return s == null ? ""
                    : StatCollector.translateToLocalFormatted("galaxia.rocket_editor.stage", slot + 1);
            }, () -> GalaxiaGuiStyle.TEXT, 0.6f)
                .pos(29, 8));
            stage.child(GalaxiaGuiStyle.label(() -> {
                RocketStage s = stageAt(stats, slot);
                return s == null ? "" : String.format("%,.0f kgf", s.getTotalThrust());
            }, () -> GalaxiaGuiStyle.TEXT_DIM, 0.55f)
                .pos(29, 17));
            stage.tooltipDynamic(t -> {
                RocketStage s = stageAt(stats, slot);
                if (s == null) return;
                PartTooltips.describeStage(s, t);
            })
                .tooltipAutoUpdate(true);
            stage.setEnabledIf(w -> stageAt(stats, slot) != null);
            side.child(stage);
        }
        return side;
    }

    /** Stage shown in a slot of the side panel, counting from the bottom of the rocket */
    private static RocketStage stageAt(DesignStats stats, int slotFromBottom) {
        if (stats.blueprint.isEmpty()) return null;
        List<RocketStage> stages = stats.get()
            .stages();
        int index = stages.size() - 1 - slotFromBottom;
        return index >= 0 ? stages.get(index) : null;
    }

    private static ParentWidget<?> createStatusLine(SiloGuiState state, DesignStats stats) {
        return new ParentWidget<>().size(66, 12)
            .child(GalaxiaGuiStyle.label(() -> {
                if (!state.problem.isEmpty()) return StatCollector.translateToLocal(state.problem);
                if (state.automationEnabled) return StatCollector.translateToLocal(state.automationStatus.langKey());
                return "";
            }, () -> state.problem.isEmpty() ? GalaxiaGuiStyle.TEXT_DIM : GalaxiaGuiStyle.BAD, 0.55f)
                .width(66));
    }

    private static ParentWidget<?> createBottomToolbar(TileEntitySilo silo, RocketBlueprint blueprint,
        IntSyncValue selectedPartId, IntSyncValue buildStatusSync, PosGuiData data, IPanelHandler destinationPanel,
        IPanelHandler automationPanel, IPanelHandler cargoPanel) {

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
            toolbarButton(
                EnumTextures.SILO_SAVE_BLUEPRINT_ICON,
                "galaxia.rocket_editor.save_schematic",
                new InteractionSyncHandler().setOnMousePressed(md -> {
                    if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) {
                        silo.captureSchematic(data.getPlayer());
                    }
                })));

        row.child(
            toolbarButton(
                EnumTextures.SILO_RETURN_ICON,
                "galaxia.rocket_editor.return_modules",
                new InteractionSyncHandler().setOnMousePressed(md -> {
                    if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) {
                        silo.returnModules();
                    }
                })));

        row.child(
            new SingleChildWidget<>().size(16, 20)
                .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
                .child(
                    new ButtonWidget<>().size(16, 16)
                        .name("silo.toolbar.order")
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
            panelButton(
                EnumTextures.SILO_DESTINATION_ICON,
                "galaxia.rocket_editor.destination",
                destinationPanel,
                "silo.toolbar.destination"));
        row.child(
            panelButton(
                EnumTextures.ICON_CYCLE,
                "galaxia.rocket_editor.automation",
                automationPanel,
                "silo.toolbar.automation"));
        row.child(
            panelButton(EnumTextures.SILO_CARGO_ICON, "galaxia.rocket_editor.cargo", cargoPanel, "silo.toolbar.cargo"));

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

        return new ParentWidget<>().size(146, 20)
            .child(row);
    }

    private static SingleChildWidget<?> toolbarButton(EnumTextures icon, String tooltip,
        InteractionSyncHandler handler) {
        return new SingleChildWidget<>().size(16, 20)
            .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
            .child(
                new ButtonWidget<>().size(16, 16)
                    .margin(0, 2)
                    .background(icon.getImage())
                    .tooltip(t -> t.addLine(IKey.lang(tooltip)))
                    .syncHandler(handler));
    }

    private static SingleChildWidget<?> panelButton(EnumTextures icon, String tooltip, IPanelHandler target,
        String name) {
        return new SingleChildWidget<>().size(16, 20)
            .background(EnumTextures.SILO_FIELD_ICON_MIDDLE.getImage())
            .child(
                new ButtonWidget<>().size(16, 16)
                    .name(name)
                    .margin(0, 2)
                    .background(icon.getImage())
                    .tooltip(t -> t.addLine(IKey.lang(tooltip)))
                    .onMousePressed(btn -> {
                        if (btn != 0) return false;
                        if (target.isPanelOpen()) target.closePanel();
                        else target.openPanel();
                        return true;
                    }));
    }
}
