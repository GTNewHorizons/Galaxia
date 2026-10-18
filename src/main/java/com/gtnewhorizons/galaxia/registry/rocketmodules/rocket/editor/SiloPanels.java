package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.mui.SafePhantomItemSlot;
import com.gtnewhorizons.galaxia.client.render.rockets.ModuleDrawable;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.items.special.ItemRocketSchematic;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.assembly.RocketBuildStatus;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketDestination;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuels;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.SiloAutomation;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gui.CargoItemHandler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gui.RequestTemplateHandler;

/**
 * Popups of the silo screen
 */
final class SiloPanels {

    /** Silo rows kept ready in the destination list */
    private static final int MAX_SILO_ROWS = 24;

    private SiloPanels() {}

    private static ButtonWidget<?> closeButton(IPanelHandler handler) {
        return RocketGuiStyle.textButton(IKey.str("x"))
            .size(12, 12)
            .onMousePressed(b -> {
                handler.closePanel();
                return true;
            });
    }

    // Destination

    static ModularPanel destination(TileEntitySilo silo, PanelSyncManager sync, IPanelHandler handler,
        SiloGuiState state, RocketEditorUI.DesignStats stats) {
        StringSyncValue destination = new StringSyncValue(
            () -> SiloGuiState.encode(silo.getDestination()),
            value -> { if (!silo.getWorldObj().isRemote) silo.setDestination(SiloGuiState.decode(value)); }).allowC2S();
        sync.syncValue("destination", destination);

        ModularPanel panel = RocketGuiStyle.panel("galaxia:silo_destination", 250, 214);
        panel.child(
            RocketGuiStyle.title("galaxia.silo.destination.title")
                .pos(8, 7));
        panel.child(closeButton(handler).pos(232, 6));
        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.silo.destination.current",
                        describe(state.destination, state.destinationName)),
                    () -> RocketGuiStyle.TEXT_DIM,
                    0.7f)
                .pos(8, 20));

        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.size(234, 168)
            .pos(8, 32)
            .background(EnumTextures.ROCKET_WELL.getImage())
            .padding(2);

        list.child(sectionHeader("galaxia.silo.destination.silos"));
        for (int i = 0; i < MAX_SILO_ROWS; i++) {
            final int index = i;
            Widget<?> row = destinationRow(
                () -> index < state.silos.size() ? state.silos.get(index)
                    .destination() : RocketDestination.NONE,
                () -> index < state.silos.size() ? state.silos.get(index)
                    .name() : "",
                () -> index < state.silos.size() ? StatCollector.translateToLocal(
                    state.silos.get(index)
                        .body())
                    : "",
                () -> index < state.silos.size() ? state.silos.get(index)
                    .deltaV() : 0,
                destination,
                state,
                stats);
            row.setEnabledIf(w -> index < state.silos.size());
            list.child(row);
        }
        list.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.silo.destination.no_silos"),
                    () -> RocketGuiStyle.TEXT_DIM,
                    0.7f)
                .height(14)
                .setEnabledIf(w -> state.silos.isEmpty()));

        list.child(sectionHeader("galaxia.silo.destination.surfaces"));
        for (DimensionEnum dimension : DimensionEnum.values()) {
            RocketDestination target = RocketDestination.ofDimension(dimension.getId());
            list.child(
                destinationRow(
                    () -> target,
                    () -> StatCollector.translateToLocal(dimension.getTranslationKey()),
                    () -> StatCollector.translateToLocal("galaxia.silo.destination.crewed_only"),
                    () -> -1,
                    destination,
                    state,
                    stats));
        }
        panel.child(list);
        return panel;
    }

    private static String describe(RocketDestination destination, String siloName) {
        if (!destination.isSet()) return StatCollector.translateToLocal("galaxia.silo.destination.none");
        if (destination.isSilo()) return siloName.isEmpty() ? "?" : siloName;
        DimensionEnum dimension = DimensionEnum.fromId(destination.dimension());
        return dimension != null ? StatCollector.translateToLocal(dimension.getTranslationKey()) : "?";
    }

    private static Widget<?> sectionHeader(String langKey) {
        return IKey.lang(langKey)
            .color(RocketGuiStyle.ACCENT)
            .asWidget()
            .scale(0.75f)
            .height(12);
    }

    private static Widget<?> destinationRow(Supplier<RocketDestination> target, Supplier<String> name,
        Supplier<String> body, DoubleSupplier deltaV, StringSyncValue destination, SiloGuiState state,
        RocketEditorUI.DesignStats stats) {
        ParentWidget<?> row = new ParentWidget<>().size(228, 20);
        row.child(
            new ButtonWidget<>().size(228, 20)
                .background(
                    new DynamicDrawable(
                        () -> target.get()
                            .equals(state.destination) ? EnumTextures.ROCKET_BUTTON_SELECTED.getImage()
                                : EnumTextures.ROCKET_BUTTON.getImage()))
                .hoverBackground(EnumTextures.ROCKET_BUTTON_HOVERED.getImage())
                .onMousePressed(b -> {
                    if (b != 0) return false;
                    destination.setStringValue(SiloGuiState.encode(target.get()));
                    return true;
                }));
        row.child(
            RocketGuiStyle.label(name, () -> RocketGuiStyle.TEXT, 0.8f)
                .pos(6, 3));
        row.child(
            RocketGuiStyle.label(body, () -> RocketGuiStyle.TEXT_DIM, 0.6f)
                .pos(6, 12));
        row.child(RocketGuiStyle.label(() -> {
            double needed = deltaV.getAsDouble();
            return needed < 0 ? "" : RocketGuiStyle.deltaV(needed);
        }, () -> {
            double available = state.buildStatus == RocketBuildStatus.READY ? state.availableDeltaV : stats.deltaV();
            return available >= deltaV.getAsDouble() ? RocketGuiStyle.GOOD : RocketGuiStyle.BAD;
        }, 0.7f)
            .alignment(Alignment.CenterRight)
            .size(80, 20)
            .pos(144, 0));
        return row;
    }

    // Automation

    static ModularPanel automation(TileEntitySilo silo, PanelSyncManager sync, IPanelHandler handler,
        SiloGuiState state, RocketBlueprint editorBlueprint) {
        SiloAutomation automation = silo.getAutomation();
        BooleanSyncValue enabled = new BooleanSyncValue(automation::isEnabled, value -> {
            automation.setEnabled(value);
            if (!silo.getWorldObj().isRemote) silo.sync();
        }).allowC2S();
        BooleanSyncValue returnArrivals = new BooleanSyncValue(automation::isReturnArrivals, value -> {
            automation.setReturnArrivals(value);
            silo.markDirty();
        }).allowC2S();
        sync.syncValue("automation_enabled", enabled);
        sync.syncValue("automation_return", returnArrivals);

        ModularPanel panel = RocketGuiStyle.panel("galaxia:silo_automation", 184, 318);
        panel.child(
            RocketGuiStyle.title("galaxia.silo.automation.title")
                .pos(8, 7));
        panel.child(closeButton(handler).pos(166, 6));

        panel.child(
            toggle(enabled, "galaxia.silo.automation.enabled").name("silo.automation.enabled")
                .pos(8, 20));
        panel.child(toggle(returnArrivals, "galaxia.silo.automation.return_arrivals").pos(8, 34));

        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocal(state.automationStatus.langKey()),
                    () -> statusColor(state.automationStatus),
                    0.7f)
                .pos(8, 50));

        // Schematic the automation keeps building
        panel.child(
            new ItemSlot()
                .slot(new ModularSlot(automation.getSchematicSlot(), 0).filter(ItemRocketSchematic::hasBlueprint))
                .background(EnumTextures.ROCKET_SLOT.getImage())
                .pos(8, 62));
        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.silo.automation.schematic"),
                    () -> RocketGuiStyle.TEXT,
                    0.7f)
                .pos(30, 64));
        panel.child(
            RocketGuiStyle.textButton(
                IKey.lang("galaxia.silo.automation.view_schematic")
                    .scale(0.6f))
                .size(62, 12)
                .pos(30, 72)
                .tooltip(t -> t.addLine(IKey.lang("galaxia.silo.automation.view_schematic.tooltip")))
                .onMousePressed(b -> {
                    RocketBlueprint schematic = ItemRocketSchematic.getBlueprint(
                        automation.getSchematicSlot()
                            .getStackInSlot(0));
                    if (b != 0 || schematic == null
                        || !silo.getBuildStatus()
                            .canEdit())
                        return false;
                    editorBlueprint.clear();
                    editorBlueprint.setName(schematic.getName());
                    for (RocketPartInstance part : schematic.getParts()) editorBlueprint.addPartUnchecked(part);
                    return true;
                }));

        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.silo.automation.requests"),
                    () -> RocketGuiStyle.ACCENT,
                    0.7f)
                .pos(8, 86));

        RequestTemplateHandler templates = new RequestTemplateHandler(silo);
        for (int i = 0; i < SiloAutomation.MAX_REQUESTS; i++) {
            final int index = i;
            int y = 96 + i * 20;
            IntSyncValue amount = new IntSyncValue(
                () -> automation.getRequests()
                    .get(index)
                    .getAmount(),
                value -> {
                    automation.getRequests()
                        .get(index)
                        .setAmount(Math.max(0, value));
                    silo.markDirty();
                }).allowC2S();
            sync.syncValue("request_amount_" + i, amount);

            panel.child(
                SafePhantomItemSlot.create()
                    .slot(new ModularSlot(templates, i))
                    .background(EnumTextures.ROCKET_SLOT.getImage())
                    .pos(8, y));
            panel.child(
                new TextFieldWidget().size(44, 14)
                    .pos(29, y + 2)
                    .setNumbers(0, 1_000_000)
                    .value(amount)
                    .background(EnumTextures.ROCKET_WELL.getImage())
                    .setTextColor(RocketGuiStyle.TEXT));
            panel.child(
                RocketGuiStyle
                    .bar(
                        () -> state.requestAmount[index] <= 0 ? 0
                            : (double) state.requestDelivered[index] / state.requestAmount[index],
                        () -> RocketGuiStyle.GOOD)
                    .size(90, 6)
                    .pos(78, y + 2));
            panel.child(
                RocketGuiStyle
                    .label(
                        () -> state.requestAmount[index] <= 0 ? ""
                            : StatCollector.translateToLocalFormatted(
                                "galaxia.silo.automation.progress",
                                state.requestDelivered[index],
                                state.requestAmount[index],
                                state.requestInFlight[index]),
                        () -> RocketGuiStyle.TEXT_DIM,
                        0.55f)
                    .pos(78, y + 10));
        }

        panel.child(
            RocketGuiStyle.textButton(
                IKey.lang("galaxia.silo.automation.reset")
                    .scale(0.7f))
                .size(70, 12)
                .pos(106, 218)
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                    if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) {
                        automation.resetProgress();
                        silo.sync();
                    }
                })));

        panel.child(
            RocketGuiStyle.playerInventory()
                .pos(11, 222 + 12));
        return panel;
    }

    private static int statusColor(SiloAutomation.Status status) {
        return switch (status) {
            case COMPLETE, IN_FLIGHT -> RocketGuiStyle.GOOD;
            case BUILDING, FUELING, LOADING -> RocketGuiStyle.ACCENT;
            case DISABLED -> RocketGuiStyle.TEXT_DIM;
            case DESTINATION_BUSY -> RocketGuiStyle.WARN;
            default -> RocketGuiStyle.BAD;
        };
    }

    /** LED toggle with a label, in the silo style */
    private static ParentWidget<?> toggle(BooleanSyncValue value, String langKey) {
        ParentWidget<?> row = new ParentWidget<>().size(160, 12);
        row.child(
            new ButtonWidget<>().size(160, 12)
                .background(EnumTextures.ROCKET_BUTTON.getImage())
                .hoverBackground(EnumTextures.ROCKET_BUTTON_HOVERED.getImage())
                .onMousePressed(b -> {
                    if (b != 0) return false;
                    value.setBoolValue(!value.getBoolValue());
                    return true;
                }));
        row.child(
            new Widget<>().size(6, 6)
                .pos(4, 3)
                .background(
                    new DynamicDrawable(
                        () -> value.getBoolValue() ? EnumTextures.ROCKET_LED_ON.getImage()
                            : EnumTextures.ROCKET_LED_OFF.getImage())));
        row.child(
            RocketGuiStyle.label(() -> StatCollector.translateToLocal(langKey), () -> RocketGuiStyle.TEXT, 0.7f)
                .pos(14, 3));
        return row;
    }

    // Cargo and fuel

    static ModularPanel cargo(TileEntitySilo silo, PanelSyncManager sync, IPanelHandler handler, SiloGuiState state) {
        ModularPanel panel = RocketGuiStyle.panel("galaxia:silo_cargo", 300, 250);
        panel.child(
            RocketGuiStyle.title("galaxia.silo.cargo.title")
                .pos(8, 7));
        panel.child(closeButton(handler).pos(282, 6));

        sync.registerSlotGroup(new SlotGroup("cargo", 9, true));
        CargoItemHandler cargo = new CargoItemHandler(silo);
        for (int i = 0; i < CargoItemHandler.MAX_SLOTS; i++) {
            final int index = i;
            // Hidden through a parent, slots only switch their container slot off when an ancestor is disabled
            ParentWidget<?> holder = new ParentWidget<>().size(18, 18)
                .pos(8 + (i % 9) * 18, 22 + (i / 9) * 18);
            holder.child(
                new ItemSlot().slot(new ModularSlot(cargo, i).slotGroup("cargo"))
                    .background(EnumTextures.ROCKET_SLOT.getImage()));
            holder.setEnabledIf(w -> index < cargo.getUsableSlots());
            panel.child(holder);
        }
        panel
            .child(
                RocketGuiStyle
                    .label(
                        () -> state.cargoSlots == 0 ? StatCollector.translateToLocal("galaxia.silo.cargo.no_holds")
                            : "",
                        () -> RocketGuiStyle.TEXT_DIM,
                        0.7f)
                    .pos(10, 26));

        panel.child(
            RocketGuiStyle.playerInventory()
                .pos(8, 140 + 24));

        // Fuel and launch column
        int x = 176;
        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.silo.cargo.fuel"),
                    () -> RocketGuiStyle.ACCENT,
                    0.7f)
                .pos(x, 22));
        for (int i = 0; i < 6; i++) {
            final int index = i;
            int y = 32 + i * 18;
            panel.child(RocketGuiStyle.label(() -> {
                if (index >= state.tanks.size()) return "";
                SiloGuiState.TankEntry tank = state.tanks.get(index);
                return tank.name();
            }, () -> RocketGuiStyle.TEXT, 0.55f)
                .pos(x, y));
            panel.child(RocketGuiStyle.bar(() -> {
                if (index >= state.tanks.size()) return 0;
                SiloGuiState.TankEntry tank = state.tanks.get(index);
                return tank.capacity() <= 0 ? 0 : (double) tank.amount() / tank.capacity();
            }, () -> {
                if (index >= state.tanks.size()) return RocketGuiStyle.BAR_BACK;
                RocketFuel fuel = RocketFuels.byKey(
                    state.tanks.get(index)
                        .fuel());
                return fuel == null ? RocketGuiStyle.ACCENT : 0xFF000000 | fuel.color();
            })
                .name("silo.cargo.tank." + i)
                .size(114, 6)
                .pos(x, y + 7)
                .tooltipDynamic(t -> {
                    if (index >= state.tanks.size()) return;
                    SiloGuiState.TankEntry tank = state.tanks.get(index);
                    t.addLine(IKey.str(tank.name()));
                    t.addLine(
                        IKey.str(RocketGuiStyle.fluid(tank.amount()) + " / " + RocketGuiStyle.fluid(tank.capacity())));
                })
                .tooltipAutoUpdate(true)
                .setEnabledIf(w -> index < state.tanks.size()));
        }
        panel
            .child(
                RocketGuiStyle
                    .label(
                        () -> state.tanks.isEmpty() ? StatCollector.translateToLocal("galaxia.silo.cargo.no_tanks")
                            : "",
                        () -> RocketGuiStyle.TEXT_DIM,
                        0.6f)
                    .pos(x, 34));

        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.silo.cargo.delta_v",
                        RocketGuiStyle.deltaV(state.availableDeltaV),
                        state.destination.isSet() ? RocketGuiStyle.deltaV(state.requiredDeltaV) : "-"),
                    () -> !state.destination.isSet() || state.availableDeltaV >= state.requiredDeltaV
                        ? RocketGuiStyle.TEXT
                        : RocketGuiStyle.BAD,
                    0.6f)
                .pos(x, 144));
        panel.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.silo.cargo.hatches",
                        state.inputBuses,
                        state.outputBuses,
                        state.inputHatches),
                    () -> RocketGuiStyle.TEXT_DIM,
                    0.55f)
                .pos(x, 154));

        panel.child(
            RocketGuiStyle.button()
                .name("silo.cargo.launch")
                .size(114, 20)
                .pos(x, 164)
                .overlay(
                    IKey.lang("galaxia.silo.cargo.launch")
                        .color(RocketGuiStyle.TEXT)
                        .shadow(true))
                .tooltip(t -> t.addLine(IKey.lang("galaxia.silo.cargo.launch.tooltip")))
                .syncHandler(
                    new InteractionSyncHandler().setOnMousePressed(
                        md -> { if (md.mouseButton == 0 && !silo.getWorldObj().isRemote) silo.launchUncrewed(); })));
        panel.child(
            RocketGuiStyle
                .label(
                    () -> state.problem.isEmpty() ? "" : StatCollector.translateToLocal(state.problem),
                    () -> RocketGuiStyle.BAD,
                    0.6f)
                .width(114)
                .pos(x, 188));
        return panel;
    }

    // Fuel core picker

    /**
     * Small client-side menu listing every fuel a tank frame can hold
     *
     * @param frame    The frame whose core is picked
     * @param chosen   Fuel currently picked per frame
     * @param onPicked Called with the picked fuel
     *
     * @return The menu panel
     */
    static ModularPanel fuelPicker(FuelTankFrame frame, Map<FuelTankFrame, RocketFuel> chosen,
        Consumer<RocketFuel> onPicked) {
        List<RocketFuel> fuels = RocketPartRegistry.instance()
            .getFuelsFor(frame);
        ModularPanel panel = RocketGuiStyle.panel("galaxia:silo_fuel_picker", 150, 24 + fuels.size() * 26);
        panel.child(
            IKey.str(StatCollector.translateToLocalFormatted("galaxia.rocket_editor.pick_core", frame.label))
                .color(RocketGuiStyle.TEXT_TITLE)
                .shadow(true)
                .asWidget()
                .pos(8, 7));

        for (int i = 0; i < fuels.size(); i++) {
            RocketFuel fuel = fuels.get(i);
            int y = 20 + i * 26;
            panel.child(
                new ButtonWidget<>().size(134, 24)
                    .name("silo.fuel_picker." + fuel.key())
                    .pos(8, y)
                    .background(
                        new DynamicDrawable(
                            () -> fuel == chosen.get(frame) ? EnumTextures.ROCKET_BUTTON_SELECTED.getImage()
                                : EnumTextures.ROCKET_BUTTON.getImage()))
                    .hoverBackground(EnumTextures.ROCKET_BUTTON_HOVERED.getImage())
                    .onMousePressed(b -> {
                        if (b != 0) return false;
                        onPicked.accept(fuel);
                        panel.closeIfOpen();
                        return true;
                    }));
            panel.child(
                new Widget<>().size(20, 20)
                    .pos(10, y + 2)
                    .background(
                        new ModuleDrawable(
                            RocketPartRegistry.instance()
                                .getFuelTank(frame, fuel),
                            1)));
            panel.child(
                RocketGuiStyle.label(fuel::name, () -> RocketGuiStyle.TEXT, 0.75f)
                    .pos(34, y + 5));
            panel.child(
                RocketGuiStyle
                    .label(
                        () -> StatCollector.translateToLocal(
                            "galaxia.rocket_fuel.containment." + fuel.containment()
                                .name()
                                .toLowerCase()),
                        () -> 0xFF000000 | fuel.color(),
                        0.55f)
                    .pos(34, y + 14));
        }
        return panel;
    }
}
