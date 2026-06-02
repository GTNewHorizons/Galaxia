package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.IRecipeModule;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;

final class StationItemInteractionModalWidget extends ParentWidget<StationItemInteractionModalWidget> {

    static final int WIDTH = 292;
    static final int HEIGHT = 204;

    private static final int ROW_HEIGHT = 18;
    private static final int CONTENT_X = 8;
    private static final int CONTENT_Y = 52;
    private static final int CONTENT_WIDTH = WIDTH - 16;
    private static final int CONTENT_HEIGHT = HEIGHT - CONTENT_Y - 8;
    private static final int CONTENT_TEXT_X = 8;
    private static final int ENTRY_ICON_X = CONTENT_TEXT_X + 2;
    private static final int ENTRY_TEXT_X = CONTENT_TEXT_X + 26;
    private static final int ACTION_BUTTON_WIDTH = 20;
    private static final int ACTION_BUTTON_X = CONTENT_WIDTH - ACTION_BUTTON_WIDTH - 2;
    private static final int ENTRY_TEXT_WIDTH = ACTION_BUTTON_X - ENTRY_TEXT_X - 4;
    private static final ItemStack ACTION_ICON = new ItemStack(Items.book);
    private static final ItemStack CLOSE_ICON = new ItemStack(Items.redstone);

    private final @Nullable CelestialAsset.ID assetId;
    private final @Nullable ModuleConfigModalController configController;
    private final ItemStack displayStack;
    private final List<Line> lines;
    private final VerticalScrollData scrollData = new VerticalScrollData();
    private final ParentWidget<?> scrollContent = new ParentWidget<>().width(CONTENT_WIDTH);
    private final Runnable onClose;

    StationItemInteractionModalWidget(@Nullable CelestialAsset.ID assetId,
        @Nullable ModuleConfigModalController configController, AutomatedFacility facility, ItemStackWrapper item,
        Runnable onClose) {
        this.assetId = assetId;
        this.configController = configController;
        this.displayStack = item.toStack(1);
        this.lines = layoutLines(StationItemInteractionModel.forItem(facility, item));
        this.onClose = onClose;
        size(WIDTH, HEIGHT);
        overlay(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> drawModal(x, y)));
        child(
            ModuleConfigModalSupport.iconButton(() -> true, CLOSE_ICON, "Close", onClose)
                .pos(WIDTH - 24, 4)
                .size(18, 18));
        int contentHeight = Math.max(
            CONTENT_HEIGHT,
            lines.isEmpty() ? ROW_HEIGHT
                : lines.get(lines.size() - 1)
                    .y() + ROW_HEIGHT);
        scrollData.setScrollSize(contentHeight);
        scrollContent.height(contentHeight)
            .overlay(ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> drawContent(x, y)));
        ScrollWidget<?> scroll = new ScrollWidget<>(scrollData).pos(CONTENT_X, CONTENT_Y)
            .size(CONTENT_WIDTH, CONTENT_HEIGHT);
        scroll.child(scrollContent);
        child(scroll);
        for (Line line : lines) {
            if (line.entry() == null) continue;
            scrollContent.child(
                ModuleConfigModalSupport
                    .iconButton(() -> canOpen(line.entry()), ACTION_ICON, "Open config", () -> openEntry(line.entry()))
                    .pos(ACTION_BUTTON_X, line.y() - 2)
                    .size(ACTION_BUTTON_WIDTH, 18));
        }
    }

    private void drawModal(int x, int y) {
        ModuleConfigModalSupport.drawFrameAt("Item Interactions", x, y, WIDTH, HEIGHT);
        ModuleConfigModalSupport.renderItemIcon(displayStack, x + 10, y + 28);
        ModuleConfigModalSupport.drawTrimmedLine(
            displayStack.getDisplayName(),
            x + 30,
            y + 32,
            WIDTH - 46,
            EnumColors.MAP_COLOR_TEXT_TITLE.getColor());
    }

    private void drawContent(int x, int y) {
        if (lines.isEmpty()) {
            ModuleConfigModalSupport
                .drawLine("No interactions.", x + CONTENT_TEXT_X, y + 2, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        for (Line line : lines) {
            int rowY = y + line.y();
            if (line.header() != null) {
                ModuleConfigModalSupport
                    .drawLine(line.header(), x + CONTENT_TEXT_X, rowY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                continue;
            }
            StationItemInteractionModel.Entry entry = line.entry();
            if (entry == null) continue;
            ModuleConfigModalSupport.renderItemIcon(iconFor(entry), x + ENTRY_ICON_X, rowY - 4);
            ModuleConfigModalSupport.drawTrimmedLine(
                textFor(entry),
                x + ENTRY_TEXT_X,
                rowY,
                ENTRY_TEXT_WIDTH,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
    }

    private static List<Line> layoutLines(List<StationItemInteractionModel.Entry> entries) {
        List<Line> lines = new ArrayList<>();
        StationItemInteractionModel.Section section = null;
        int y = 2;
        for (StationItemInteractionModel.Entry entry : entries) {
            if (entry.section() != section) {
                section = entry.section();
                lines.add(new Line(headerFor(section), null, y));
                y += ROW_HEIGHT;
            }
            lines.add(new Line(null, entry, y));
            y += ROW_HEIGHT;
        }
        return lines;
    }

    private static String headerFor(StationItemInteractionModel.Section section) {
        return switch (section) {
            case LOGISTICS -> "Logistics:";
            case MACHINES -> "Machines:";
            case UPKEEP -> "Upkeep:";
        };
    }

    private static String textFor(StationItemInteractionModel.Entry entry) {
        return switch (entry.role()) {
            case CORE_IMPORT -> "Core import reserve " + entry.reserve();
            case HAMMER_EXPORT -> "Hammer export reserve " + entry.reserve();
            case CONSUMES -> rolePrefix("Consumes", entry);
            case PRODUCES -> rolePrefix("Produces", entry);
            case UPKEEP -> entry.label() + countSuffix(entry)
                + " - "
                + entry.amountPerMinute()
                    .toDisplayString()
                + "/min";
        };
    }

    private static String rolePrefix(String role, StationItemInteractionModel.Entry entry) {
        return role + ": " + entry.label() + countSuffix(entry);
    }

    private static String countSuffix(StationItemInteractionModel.Entry entry) {
        return entry.count() > 1 ? " x" + entry.count() : "";
    }

    private static ItemStack iconFor(StationItemInteractionModel.Entry entry) {
        if (entry.role() == StationItemInteractionModel.Role.CORE_IMPORT) return new ItemStack(Items.compass);
        if (entry.role() == StationItemInteractionModel.Role.HAMMER_EXPORT) return new ItemStack(Items.iron_pickaxe);
        FacilityModuleKind kind = entry.kind();
        if (kind == null) return new ItemStack(Items.compass);
        return switch (kind) {
            case HAMMER -> new ItemStack(Items.iron_pickaxe);
            case MINER -> new ItemStack(Items.diamond_pickaxe);
            case POWER, BATTERY, GEOTHERMAL_GENERATOR -> new ItemStack(Items.redstone);
            case STORAGE -> new ItemStack(Blocks.chest);
            case TANK -> new ItemStack(Items.bucket);
            case MAINTENANCE_BAY -> new ItemStack(Blocks.anvil);
            case MACERATOR, CENTRIFUGE, ELECTROLYZER, CHEMICAL_REACTOR, ASSEMBLER, DISTILLERY -> new ItemStack(
                Blocks.furnace);
        };
    }

    private boolean canOpen(StationItemInteractionModel.Entry entry) {
        return configController != null
            && (entry.role() == StationItemInteractionModel.Role.CORE_IMPORT || entry.targetModuleId() != null);
    }

    private void openEntry(StationItemInteractionModel.Entry entry) {
        if (configController == null) return;
        onClose.run();
        if (entry.role() == StationItemInteractionModel.Role.CORE_IMPORT) {
            configController.openCoreLogistics();
            return;
        }
        int moduleIndex = ModuleConfigModalSupport.moduleIndex(assetId, entry.targetModuleId());
        if (moduleIndex < 0) return;
        ModuleInstance module = ModuleConfigModalSupport.module(assetId, moduleIndex);
        if (module == null) return;
        if (module.component() instanceof ModuleHammer) {
            configController.openHammer(moduleIndex);
        } else if (module.component() instanceof IRecipeModule) {
            configController.openRecipeConfig(moduleIndex);
        } else if (module.component() instanceof ModuleMiner) {
            configController.openMinerBlacklist(moduleIndex);
        } else {
            configController.openUpgrade(moduleIndex);
        }
    }

    private record Line(@Nullable String header, @Nullable StationItemInteractionModel.Entry entry, int y) {}
}
