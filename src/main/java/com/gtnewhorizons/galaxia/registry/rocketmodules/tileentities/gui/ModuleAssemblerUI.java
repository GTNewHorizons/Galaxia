package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.render.rockets.ModuleDrawable;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartCategory;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.PartTooltips;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.editor.RocketGuiStyle;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.FuelTankFrame;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.fuel.RocketFuel;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.ModuleRecipe;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;

/**
 * Module Assembler screen: stock of every module, the recipe of the selected one with what the input buses hold, and
 * the craft queue
 */
public final class ModuleAssemblerUI {

    private static final int COLUMNS = 6;
    private static final int CELL = 28;
    private static final int MAX_INPUTS_SHOWN = 6;

    private ModuleAssemblerUI() {}

    /**
     * Client copy of the assembler state, filled from one synced tag
     */
    private static final class View {

        NBTTagCompound stock = new NBTTagCompound();
        NBTTagCompound queued = new NBTTagCompound();
        long[] have = new long[0];
        String activeCraft = "";
        float progress;
        int status;
        int deliveries;
        int craftQueue;
        int inputBuses;
        boolean formed;
        boolean infinite;

        void read(NBTTagCompound tag) {
            stock = tag.getCompoundTag("stock");
            queued = tag.getCompoundTag("queued");
            int[] haveInts = tag.getIntArray("have");
            have = new long[haveInts.length];
            for (int i = 0; i < haveInts.length; i++) have[i] = haveInts[i];
            activeCraft = tag.getString("active");
            progress = tag.getFloat("progress");
            status = tag.getInteger("status");
            deliveries = tag.getInteger("deliveries");
            craftQueue = tag.getInteger("craftQueue");
            inputBuses = tag.getInteger("inputBuses");
            formed = tag.getBoolean("formed");
            infinite = tag.getBoolean("infinite");
        }

        int stockOf(IRocketPartDef def) {
            return infinite ? TileEntityModuleAssembler.INFINITE_STOCK : stock.getInteger(def.key());
        }

        /** Stock as shown to the player, infinite stock reads as a symbol instead of a number */
        String stockText(int count) {
            return infinite ? "∞" : Integer.toString(count);
        }

        int queuedOf(IRocketPartDef def) {
            return queued.getInteger(def.key());
        }
    }

    private static NBTTagCompound write(TileEntityModuleAssembler assembler, IRocketPartDef selected) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound stock = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : assembler.getModuleStock()
            .entrySet()) {
            stock.setInteger(entry.getKey(), entry.getValue());
        }
        tag.setTag("stock", stock);

        NBTTagCompound queued = new NBTTagCompound();
        for (IRocketPartDef def : RocketPartRegistry.instance()
            .getAll()) {
            int count = assembler.countQueued(def);
            if (count > 0) queued.setInteger(def.key(), count);
        }
        tag.setTag("queued", queued);

        if (selected != null) {
            List<ModuleRecipe.Input> inputs = selected.recipe()
                .inputs();
            int[] have = new int[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) {
                have[i] = (int) Math.min(Integer.MAX_VALUE, assembler.countInput(inputs.get(i)));
            }
            tag.setIntArray("have", have);
        }

        tag.setString("active", assembler.getActiveCraft() == null ? "" : assembler.getActiveCraft());
        tag.setFloat("progress", assembler.getCraftProgress());
        tag.setInteger(
            "status",
            assembler.getStatus()
                .ordinal());
        tag.setInteger("deliveries", assembler.getDeliveryCount());
        tag.setInteger("craftQueue", assembler.getCraftQueueSize());
        tag.setInteger(
            "inputBuses",
            assembler.getHatches()
                .inputBusCount());
        tag.setBoolean("formed", assembler.isStructureValid());
        tag.setBoolean("infinite", assembler.isInfiniteStock());
        return tag;
    }

    public static ModularPanel build(TileEntityModuleAssembler assembler, PosGuiData data, PanelSyncManager syncManager,
        UISettings settings) {
        List<IRocketPartDef> defs = RocketPartRegistry.instance()
            .getAll();

        final int[] selectedId = { defs.isEmpty() ? -1
            : defs.getFirst()
                .id() };
        IntSyncValue selected = new IntSyncValue(() -> selectedId[0], v -> selectedId[0] = v).allowC2S();
        syncManager.syncValue("selected_module", selected);

        View view = new View();
        NBTTagCompound[] received = { new NBTTagCompound() };
        syncManager.syncValue(
            "assembler_state",
            GenericSyncValue.builder(NBTTagCompound.class)
                .getter(
                    () -> assembler.getWorldObj().isRemote ? received[0]
                        : write(
                            assembler,
                            RocketPartRegistry.instance()
                                .get(selectedId[0])))
                .setter(tag -> {
                    received[0] = tag;
                    view.read(tag);
                })
                .adapter(ByteBufAdapters.NBT)
                .build());

        ModularPanel panel = RocketGuiStyle.panel("galaxia:module_assembler", 358, 216);

        panel.child(
            RocketGuiStyle.title("galaxia.gui.module_assembler.title")
                .pos(8, 7));
        panel.child(
            new Widget<>().size(6, 6)
                .pos(344, 9)
                .background(
                    new DynamicDrawable(
                        () -> !view.formed ? EnumTextures.ROCKET_LED_OFF.getImage()
                            : view.status == TileEntityModuleAssembler.Status.MISSING_INPUTS.ordinal()
                                ? EnumTextures.ROCKET_LED_WAIT.getImage()
                                : EnumTextures.ROCKET_LED_ON.getImage())));
        panel.child(RocketGuiStyle.label(() -> {
            if (!view.formed) return StatCollector.translateToLocal("galaxia.gui.module_assembler.not_formed");
            return StatCollector.translateToLocal(
                "galaxia.gui.module_assembler.status." + TileEntityModuleAssembler.Status.values()[view.status].name()
                    .toLowerCase());
        }, () -> view.formed ? RocketGuiStyle.TEXT_DIM : RocketGuiStyle.BAD, 0.7f)
            .alignment(Alignment.CenterRight)
            .size(200, 10)
            .pos(140, 7));

        Browser browser = new Browser();
        panel.child(createCategoryColumn(browser, selected, view).pos(8, 20));
        panel.child(createModuleGrid(browser, selected, view).pos(36, 20));
        panel.child(createDetails(assembler, data, selected, view).pos(218, 20));
        panel.child(createFooter(view).pos(8, 188));
        return panel;
    }

    /**
     * Client-side browsing state of the module grid: the open category and which tank groups are unfolded
     */
    private static final class Browser {

        int category = 0;
        final Set<FuelTankFrame> expanded = EnumSet.noneOf(FuelTankFrame.class);
        ListWidget<IWidget, ?> list;
    }

    private static ParentWidget<?> createCategoryColumn(Browser browser, IntSyncValue selected, View view) {
        Flow column = Flow.column()
            .coverChildren()
            .childPadding(2);
        RocketPartCategory[] categories = RocketPartCategory.values();
        for (int i = 0; i < categories.length; i++) {
            final int index = i;
            RocketPartCategory category = categories[i];
            column.child(
                new ButtonWidget<>().size(24, 24)
                    .name(
                        "assembler.category." + category.name()
                            .toLowerCase())
                    .background(
                        new DynamicDrawable(
                            () -> browser.category == index ? EnumTextures.SILO_CATEGORY_BASE_SELECTED.getImage()
                                : EnumTextures.SILO_CATEGORY_BASE.getImage()))
                    .overlay(category.icon.getImage())
                    .tooltip(t -> t.addLine(IKey.lang(category.langKey)))
                    .onMousePressed(b -> {
                        if (b != 0) return false;
                        browser.category = index;
                        fillGrid(browser, selected, view);
                        return true;
                    }));
        }
        return new ParentWidget<>().size(24, 164)
            .child(column);
    }

    private static ListWidget<IWidget, ?> createModuleGrid(Browser browser, IntSyncValue selected, View view) {
        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.size(COLUMNS * CELL + 8, 164)
            .background(EnumTextures.ROCKET_WELL.getImage())
            .padding(3);
        browser.list = list;
        fillGrid(browser, selected, view);
        return list;
    }

    /**
     * Lays out the open category. Fuel tanks are grouped by frame like NEI item groups: a folded group shows one
     * tank, unfolding it shows every core in a framed row
     */
    private static void fillGrid(Browser browser, IntSyncValue selected, View view) {
        ListWidget<IWidget, ?> list = browser.list;
        list.removeAll();
        RocketPartCategory category = RocketPartCategory.values()[browser.category];

        RowFiller rows = new RowFiller(list);
        if (category == RocketPartCategory.FUEL_TANKS) {
            for (FuelTankFrame frame : FuelTankFrame.values()) {
                List<IRocketPartDef> group = tanksOf(frame);
                if (group.isEmpty()) continue;
                if (browser.expanded.contains(frame)) {
                    rows.flush();
                    list.child(
                        new ParentWidget<>().size(COLUMNS * CELL, CELL + 4)
                            .child(expandedGroup(browser, frame, group, selected, view)));
                } else {
                    rows.add(collapsedGroup(browser, frame, group, selected, view));
                }
            }
        } else {
            for (IRocketPartDef def : RocketPartRegistry.instance()
                .getByCategory(category)) {
                rows.add(moduleCell(def, selected, view));
            }
        }
        rows.flush();
        list.scheduleResize();
    }

    /** Packs cells into rows of {@link #COLUMNS} */
    private static final class RowFiller {

        private final ListWidget<IWidget, ?> list;
        private Flow row;
        private int count;

        RowFiller(ListWidget<IWidget, ?> list) {
            this.list = list;
        }

        void add(IWidget cell) {
            if (row == null) {
                // Full width rows keep a short last row on the left instead of centred
                row = Flow.row()
                    .size(COLUMNS * CELL, CELL)
                    .mainAxisAlignment(Alignment.MainAxis.START)
                    .childPadding(0);
            }
            row.child(cell);
            if (++count == COLUMNS) flush();
        }

        void flush() {
            if (row != null) list.child(row);
            row = null;
            count = 0;
        }
    }

    private static List<IRocketPartDef> tanksOf(FuelTankFrame frame) {
        List<IRocketPartDef> tanks = new ArrayList<>();
        for (RocketFuel fuel : RocketPartRegistry.instance()
            .getFuelsFor(frame)) {
            tanks.add(
                RocketPartRegistry.instance()
                    .getFuelTank(frame, fuel));
        }
        return tanks;
    }

    private static ParentWidget<?> collapsedGroup(Browser browser, FuelTankFrame frame, List<IRocketPartDef> group,
        IntSyncValue selected, View view) {
        IRocketPartDef first = group.get(0);
        ParentWidget<?> cell = new ParentWidget<>().size(CELL, CELL);
        cell.child(
            new ButtonWidget<>().size(CELL, CELL)
                .name("assembler.group." + frame.label)
                .background(EnumTextures.ROCKET_BUTTON_SELECTED.getImage())
                .hoverBackground(EnumTextures.ROCKET_BUTTON_HOVERED.getImage())
                .overlay(new ModuleDrawable(first, 3))
                .tooltip(t -> {
                    t.addLine(
                        IKey.str(
                            StatCollector
                                .translateToLocalFormatted("galaxia.gui.module_assembler.group", frame.label)));
                    t.addLine(IKey.lang("galaxia.gui.module_assembler.group_hint"));
                })
                .onMousePressed(b -> {
                    if (b != 0) return false;
                    browser.expanded.add(frame);
                    selected.setValue(first.id());
                    fillGrid(browser, selected, view);
                    return true;
                }));
        cell.child(
            RocketGuiStyle.label(() -> "+", () -> RocketGuiStyle.ACCENT, 0.6f)
                .pos(3, 2));
        cell.child(RocketGuiStyle.label(() -> {
            int stock = 0;
            for (IRocketPartDef def : group) stock += view.stockOf(def);
            return stock > 0 ? view.stockText(stock) : "";
        }, () -> RocketGuiStyle.TEXT_TITLE, 0.6f)
            .alignment(Alignment.BottomRight)
            .size(CELL - 3, CELL - 3)
            .pos(0, 0));
        return cell;
    }

    private static ParentWidget<?> expandedGroup(Browser browser, FuelTankFrame frame, List<IRocketPartDef> group,
        IntSyncValue selected, View view) {
        int fold = 10;
        ParentWidget<?> frameBox = new ParentWidget<>().size(fold + group.size() * CELL + 4, CELL + 4)
            .background(EnumTextures.ROCKET_BUTTON_SELECTED.getImage());
        frameBox.child(
            new ButtonWidget<>().size(fold, CELL)
                .pos(2, 2)
                .name("assembler.group." + frame.label + ".fold")
                .background(EnumTextures.ROCKET_BUTTON.getImage())
                .hoverBackground(EnumTextures.ROCKET_BUTTON_HOVERED.getImage())
                .overlay(
                    IKey.str("-")
                        .color(RocketGuiStyle.ACCENT))
                .tooltip(t -> t.addLine(IKey.lang("galaxia.gui.module_assembler.group_fold")))
                .onMousePressed(b -> {
                    if (b != 0) return false;
                    browser.expanded.remove(frame);
                    fillGrid(browser, selected, view);
                    return true;
                }));
        for (int i = 0; i < group.size(); i++) {
            frameBox.child(moduleCell(group.get(i), selected, view).pos(2 + fold + i * CELL, 2));
        }
        return frameBox;
    }

    private static ParentWidget<?> moduleCell(IRocketPartDef def, IntSyncValue selected, View view) {
        ParentWidget<?> cell = new ParentWidget<>().size(CELL, CELL);
        cell.child(
            new ButtonWidget<>().size(CELL, CELL)
                .background(
                    new DynamicDrawable(
                        () -> selected.getValue() == def.id() ? EnumTextures.SILO_PART_BACKGROUND_SELECTED.getImage()
                            : EnumTextures.SILO_PART_BACKGROUND.getImage()))
                .overlay(new ModuleDrawable(def, 3))
                .tooltip(t -> {
                    t.addLine(IKey.str(def.name()));
                    PartTooltips.describe(def, t);
                })
                .onMousePressed(b -> {
                    if (b != 0) return false;
                    selected.setValue(def.id());
                    return true;
                }));
        cell.child(RocketGuiStyle.label(() -> {
            int stock = view.stockOf(def);
            return stock > 0 ? view.stockText(stock) : "";
        }, () -> RocketGuiStyle.TEXT_TITLE, 0.6f)
            .alignment(Alignment.BottomRight)
            .size(CELL - 3, CELL - 3)
            .pos(0, 0));
        return cell;
    }

    private static ParentWidget<?> createDetails(TileEntityModuleAssembler assembler, PosGuiData data,
        IntSyncValue selected, View view) {
        ParentWidget<?> details = new ParentWidget<>().size(132, 164)
            .background(EnumTextures.ROCKET_WELL.getImage());

        details.child(
            new Widget<>().size(40, 40)
                .pos(4, 4)
                .background(new ModuleDrawable(() -> selectedDef(selected), 2)));
        details.child(RocketGuiStyle.label(() -> {
            IRocketPartDef def = selectedDef(selected);
            return def == null ? "" : def.name();
        }, () -> RocketGuiStyle.TEXT, 0.65f)
            .width(82)
            .pos(46, 6));
        details.child(RocketGuiStyle.label(() -> {
            IRocketPartDef def = selectedDef(selected);
            if (def == null) return "";
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.module_assembler.stock",
                view.stockText(view.stockOf(def)),
                view.queuedOf(def));
        }, () -> RocketGuiStyle.TEXT_DIM, 0.6f)
            .name("assembler.stock")
            .pos(46, 30));

        details.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.gui.module_assembler.recipe"),
                    () -> RocketGuiStyle.ACCENT,
                    0.6f)
                .pos(4, 48));

        for (int i = 0; i < MAX_INPUTS_SHOWN; i++) {
            final int index = i;
            int y = 57 + i * 14;
            details.child(
                new Widget<>().size(12, 12)
                    .pos(4, y)
                    .background(new DynamicDrawable(() -> {
                        ModuleRecipe.Input input = inputAt(selected, index);
                        if (input == null) return null;
                        List<ItemStack> stacks = input.displayStacks();
                        if (stacks.isEmpty()) return null;
                        // Cycle through ore dictionary alternatives
                        ItemStack shown = stacks.get((int) (System.currentTimeMillis() / 1000 % stacks.size()));
                        return new ItemDrawable(shown);
                    }))
                    .tooltipDynamic(t -> {
                        ModuleRecipe.Input input = inputAt(selected, index);
                        if (input == null) return;
                        List<ItemStack> stacks = input.displayStacks();
                        t.addLine(
                            IKey.str(
                                stacks.isEmpty() ? input.oreName()
                                    : stacks.get(0)
                                        .getDisplayName()));
                        if (input.oreName() != null) t.addLine(IKey.str("§8" + input.oreName()));
                    })
                    .tooltipAutoUpdate(true)
                    .setEnabledIf(w -> inputAt(selected, index) != null));
            details.child(RocketGuiStyle.label(() -> {
                ModuleRecipe.Input input = inputAt(selected, index);
                if (input == null) return "";
                long have = index < view.have.length ? view.have[index] : 0;
                return have + " / " + input.amount();
            }, () -> {
                ModuleRecipe.Input input = inputAt(selected, index);
                long have = index < view.have.length ? view.have[index] : 0;
                return input != null && have >= input.amount() ? RocketGuiStyle.GOOD : RocketGuiStyle.BAD;
            }, 0.6f)
                .pos(20, y + 3));
        }

        // Craft controls
        details.child(craftButton(assembler, selected, 1, "galaxia.gui.module_assembler.craft_one").pos(4, 144));
        details.child(craftButton(assembler, selected, 5, "galaxia.gui.module_assembler.craft_five").pos(46, 144));
        details.child(
            RocketGuiStyle.textButton(
                IKey.lang("galaxia.gui.module_assembler.cancel")
                    .scale(0.6f))
                .size(40, 16)
                .pos(88, 144)
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                    IRocketPartDef def = selectedDef(selected);
                    if (md.mouseButton == 0 && def != null && !assembler.getWorldObj().isRemote) {
                        assembler.cancelCraft(def);
                    }
                })));

        // Free stock for testing, creative only
        details.child(infiniteStockButton(assembler, data, view).pos(90, 44));
        details.child(
            debugButton(assembler, data, selected, 1, "+", "galaxia.gui.module_assembler.debug_add").pos(104, 44));
        details.child(
            debugButton(assembler, data, selected, -1, "-", "galaxia.gui.module_assembler.debug_remove").pos(118, 44));
        return details;
    }

    private static ButtonWidget<?> craftButton(TileEntityModuleAssembler assembler, IntSyncValue selected, int count,
        String langKey) {
        return RocketGuiStyle.textButton(
            IKey.lang(langKey)
                .scale(0.6f))
            .size(40, 16)
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                IRocketPartDef def = selectedDef(selected);
                if (md.mouseButton == 0 && def != null && !assembler.getWorldObj().isRemote) {
                    assembler.queueCraft(def, count);
                }
            }));
    }

    private static ButtonWidget<?> debugButton(TileEntityModuleAssembler assembler, PosGuiData data,
        IntSyncValue selected, int delta, String text, String langKey) {
        ButtonWidget<?> button = RocketGuiStyle.textButton(IKey.str(text))
            .size(12, 12)
            .tooltip(t -> t.addLine(IKey.lang(langKey)))
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                IRocketPartDef def = selectedDef(selected);
                if (md.mouseButton != 0 || def == null || assembler.getWorldObj().isRemote) return;
                if (data.getPlayer().capabilities.isCreativeMode) assembler.debugAdjustStock(def, delta);
            }));
        button.setEnabledIf(w -> data.getPlayer().capabilities.isCreativeMode);
        return button;
    }

    /**
     * Creative switch that makes every module count as in stock, lit green while on
     */
    private static ButtonWidget<?> infiniteStockButton(TileEntityModuleAssembler assembler, PosGuiData data,
        View view) {
        // The glyph sits a pixel left of centre, the margin moves it clear of the frame
        ButtonWidget<?> button = RocketGuiStyle.button()
            .overlay(
                IKey.dynamic(() -> (view.infinite ? EnumChatFormatting.GREEN : "") + "∞")
                    .color(RocketGuiStyle.TEXT)
                    .shadow(true)
                    .asIcon()
                    .marginLeft(2))
            .size(12, 12)
            .tooltipDynamic(t -> {
                t.addLine(IKey.lang("galaxia.gui.module_assembler.infinite_stock"));
                t.addLine(
                    IKey.lang(
                        view.infinite ? "galaxia.gui.module_assembler.infinite_stock.on"
                            : "galaxia.gui.module_assembler.infinite_stock.off"));
            })
            .tooltipAutoUpdate(true)
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(md -> {
                if (md.mouseButton != 0 || assembler.getWorldObj().isRemote) return;
                if (data.getPlayer().capabilities.isCreativeMode) assembler.toggleInfiniteStock();
            }));
        button.name("assembler.infinite_stock");
        button.setEnabledIf(w -> data.getPlayer().capabilities.isCreativeMode);
        return button;
    }

    private static ParentWidget<?> createFooter(View view) {
        ParentWidget<?> footer = new ParentWidget<>().size(342, 20);
        footer.child(RocketGuiStyle.label(() -> {
            IRocketPartDef active = RocketPartRegistry.instance()
                .getByKey(view.activeCraft);
            return active == null ? StatCollector.translateToLocal("galaxia.gui.module_assembler.no_craft")
                : StatCollector.translateToLocalFormatted("galaxia.gui.module_assembler.crafting", active.name());
        }, () -> RocketGuiStyle.TEXT, 0.6f)
            .pos(0, 0));
        footer.child(
            RocketGuiStyle.bar(() -> view.progress, () -> RocketGuiStyle.ACCENT)
                .size(202, 6)
                .pos(0, 9));
        footer.child(
            RocketGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.module_assembler.queue",
                        view.craftQueue,
                        view.deliveries,
                        view.inputBuses),
                    () -> RocketGuiStyle.TEXT_DIM,
                    0.6f)
                .alignment(Alignment.CenterRight)
                .size(132, 16)
                .pos(210, 0));
        return footer;
    }

    private static IRocketPartDef selectedDef(IntSyncValue selected) {
        return RocketPartRegistry.instance()
            .get(selected.getValue());
    }

    private static ModuleRecipe.Input inputAt(IntSyncValue selected, int index) {
        IRocketPartDef def = selectedDef(selected);
        if (def == null) return null;
        List<ModuleRecipe.Input> inputs = def.recipe()
            .inputs();
        return index < inputs.size() ? inputs.get(index) : null;
    }
}
