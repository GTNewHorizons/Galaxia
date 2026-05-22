package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.ItemStackWrapper;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleUpgradeModalWidget extends ParentWidget<ModuleUpgradeModalWidget> {

    static final int WIDTH = 390;
    static final int HEIGHT = 284;

    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + 10;
    private static final int OPTION_TOP = 118;
    private static final int OPTION_BUTTONS = 8;
    private static final int OPTION_COLUMNS = 2;
    private static final int OPTION_BUTTON_WIDTH = 84;
    private static final int OPTION_BUTTON_HEIGHT = 18;
    private static final int OPTION_COLUMN_GAP = 4;
    private static final int OPTION_ROW_GAP = 4;
    private static final int CONTROL_GAP = 8;
    static final int CONTROL_GAP_FOR_TEST = CONTROL_GAP;
    private static final int FLAG_TOP = 214;
    private static final int FOOTER_TOP = HEIGHT - 32;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CONFIRM_BUTTON_WIDTH = 72;
    private static final int MULTIPLE_BUTTON_WIDTH = 78;
    private static final int CANCEL_BUTTON_WIDTH = 72;
    private static final int BACK_BUTTON_WIDTH = 54;
    private static final int RESERVE_BUTTON_WIDTH = 84;
    private static final int VOID_REFUND_BUTTON_WIDTH = 98;
    private static final int BODY_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final @Nullable StationTilePickerController tilePickerController;

    ModuleUpgradeModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        @Nullable StationTilePickerController tilePickerController) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
        for (int slot = 0; slot < OPTION_BUTTONS; slot++) {
            int col = slot % OPTION_COLUMNS;
            int row = slot / OPTION_COLUMNS;
            int optionSlot = slot;
            child(
                createOptionButton(optionSlot)
                    .pos(
                        ModuleConfigModalSupport.PANEL_PADDING + col * (OPTION_BUTTON_WIDTH + OPTION_COLUMN_GAP),
                        OPTION_TOP + row * (OPTION_BUTTON_HEIGHT + OPTION_ROW_GAP))
                    .size(OPTION_BUTTON_WIDTH, OPTION_BUTTON_HEIGHT));
        }
        child(
            ModuleConfigModalSupport
                .button(this::canUseHammerFlags, this::reserveLabel, controller::toggleHammerUpgradeReserveItems)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FLAG_TOP)
                .size(RESERVE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport
                .button(this::canUseHammerFlags, this::voidLabel, controller::toggleHammerUpgradeVoidRefund)
                .pos(ModuleConfigModalSupport.PANEL_PADDING + RESERVE_BUTTON_WIDTH + CONTROL_GAP, FLAG_TOP)
                .size(VOID_REFUND_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfirm, "Confirm", this::confirm)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, FOOTER_TOP)
                .size(CONFIRM_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::canConfirmMultiple, "Multiple...", this::startMultiplePicker)
                .pos(ModuleConfigModalSupport.PANEL_PADDING + CONFIRM_BUTTON_WIDTH + CONTROL_GAP, FOOTER_TOP)
                .size(MULTIPLE_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(this::hasCancellableBuild, this::cancelLabel, this::cancelBuild)
                .pos(
                    ModuleConfigModalSupport.PANEL_PADDING + CONFIRM_BUTTON_WIDTH
                        + CONTROL_GAP
                        + MULTIPLE_BUTTON_WIDTH
                        + CONTROL_GAP,
                    FOOTER_TOP)
                .size(CANCEL_BUTTON_WIDTH, BUTTON_HEIGHT));
        child(
            ModuleConfigModalSupport.button(controller::isModuleUpgradeOpen, "Back", controller::close)
                .pos(WIDTH - ModuleConfigModalSupport.PANEL_PADDING - BACK_BUTTON_WIDTH, FOOTER_TOP)
                .size(BACK_BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isModuleUpgradeOpen()) return;
        ModuleConfigModalSupport.drawFrame(title(), WIDTH, HEIGHT);
        ModuleInstance module = selectedModule();
        if (module == null || !ModuleUpgradeUiModel.supports(module)) {
            ModuleConfigModalSupport.drawLine(
                "No upgradeable module selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        if (ModuleUpgradeUiModel.hasActiveBuild(module)) {
            drawActiveBuild(module);
            return;
        }
        drawPlan(module);
    }

    private void drawPlan(ModuleInstance module) {
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        int lineY = BODY_TOP;
        lineY = ModuleConfigModalSupport
            .drawLine(currentLine(module), x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport
            .drawLine(targetLine(module), x, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        lineY = ModuleConfigModalSupport
            .drawLine(effectLine(module), x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY += 3;
        if (!targetChanged(module)) {
            ModuleConfigModalSupport
                .drawLine("No upgrade selected", x, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            drawGroupLabels(module);
            return;
        }
        lineY = ModuleConfigModalSupport
            .drawLine(buildLine(module), x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport.drawLine("Cost:", x, lineY, EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        drawCost(module, lineY);
        drawGroupLabels(module);
    }

    private void drawActiveBuild(ModuleInstance module) {
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        int lineY = BODY_TOP;
        lineY = ModuleConfigModalSupport
            .drawLine(currentLine(module), x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        ModuleConfigModalSupport
            .drawLine(operationLabel(module), x, lineY, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
    }

    private void drawGroupLabels(ModuleInstance module) {
        List<ModuleUpgradeGroup> groups = ModuleUpgradeUiModel.groups(module, controller.moduleUpgradeSelection());
        int x = ModuleConfigModalSupport.PANEL_PADDING + OPTION_COLUMNS * (OPTION_BUTTON_WIDTH + OPTION_COLUMN_GAP) + 8;
        int lineY = OPTION_TOP;
        for (ModuleUpgradeGroup group : groups) {
            lineY = ModuleConfigModalSupport.drawTrimmedLine(
                group.title(),
                x,
                lineY,
                WIDTH - x - ModuleConfigModalSupport.PANEL_PADDING,
                EnumColors.MAP_COLOR_TEXT_SECTION.getColor());
        }
    }

    private ButtonWidget<?> createOptionButton(int slot) {
        return new ButtonWidget<>()
            .background(
                ModuleConfigModalSupport
                    .drawable((ctx, x, y, w, h) -> drawOptionButton(x, y, w, h, optionRef(slot), false)))
            .hoverBackground(
                ModuleConfigModalSupport
                    .drawable((ctx, x, y, w, h) -> drawOptionButton(x, y, w, h, optionRef(slot), true)))
            .overlay(
                ModuleConfigModalSupport.drawable((ctx, x, y, w, h) -> drawOptionLabel(x, y, w, h, optionRef(slot))))
            .onMousePressed(mouseButton -> {
                OptionRef ref = optionRef(slot);
                if (mouseButton != 0 || ref == null
                    || !ref.option()
                        .enabled()
                    || ModuleUpgradeUiModel.hasActiveBuild(selectedModule())) return true;
                controller.selectModuleUpgradeOption(
                    ref.group()
                        .id(),
                    ref.option()
                        .id());
                return true;
            })
            .setEnabledIf(w -> optionRef(slot) != null);
    }

    private void drawOptionButton(int x, int y, int w, int h, @Nullable OptionRef ref, boolean hovered) {
        if (ref == null) return;
        boolean enabled = ref.option()
            .enabled() && !ModuleUpgradeUiModel.hasActiveBuild(selectedModule());
        int bg = enabled
            ? (hovered ? EnumColors.MAP_COLOR_BTN_ENABLED_HOVERED.getColor()
                : EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor())
            : EnumColors.MAP_COLOR_BTN_DISABLED.getColor();
        int border = enabled ? EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor()
            : EnumColors.MAP_COLOR_BTN_BORDER_DISABLED.getColor();
        BorderedRect.draw(x, y, w, h, bg, border);
    }

    private void drawOptionLabel(int x, int y, int w, int h, @Nullable OptionRef ref) {
        if (ref == null) return;
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        String label = ref.option()
            .selected()
                ? "* " + ref.option()
                    .label()
                : ref.option()
                    .label();
        String trimmed = fr.trimStringToWidth(label, w - 4);
        int color = ref.option()
            .enabled() ? EnumColors.MAP_COLOR_TEXT_BTN_ENABLED.getColor()
                : EnumColors.MAP_COLOR_TEXT_BTN_DISABLED.getColor();
        fr.drawStringWithShadow(
            trimmed,
            x + (w - fr.getStringWidth(trimmed)) / 2,
            y + (h - fr.FONT_HEIGHT) / 2 + 1,
            color);
    }

    private @Nullable OptionRef optionRef(int slot) {
        ModuleInstance module = selectedModule();
        if (module == null) return null;
        List<OptionRef> options = optionRefs(module);
        return slot >= 0 && slot < options.size() ? options.get(slot) : null;
    }

    private List<OptionRef> optionRefs(ModuleInstance module) {
        List<OptionRef> refs = new ArrayList<>();
        for (ModuleUpgradeGroup group : ModuleUpgradeUiModel.groups(module, controller.moduleUpgradeSelection())) {
            for (ModuleUpgradeOption option : group.options()) {
                refs.add(new OptionRef(group, option));
            }
        }
        return refs;
    }

    private boolean canConfirm() {
        ModuleInstance module = selectedModule();
        return module != null && !ModuleUpgradeUiModel.hasActiveBuild(module) && targetChanged(module);
    }

    private boolean canConfirmMultiple() {
        ModuleInstance module = selectedModule();
        return tilePickerController != null && module != null
            && module.component() instanceof ModuleHammer
            && canConfirm();
    }

    private boolean canUseHammerFlags() {
        ModuleInstance module = selectedModule();
        return module != null && module.component() instanceof ModuleHammer
            && !ModuleUpgradeUiModel.hasActiveBuild(module);
    }

    private void confirm() {
        ModuleInstance module = selectedModule();
        if (module == null || !canConfirm()) return;
        if (module.component() instanceof ModuleHammer) {
            CelestialClient.planHammerUpgrade(
                assetId,
                controller.moduleIndex(),
                ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection()),
                ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection()),
                controller.hammerUpgradeReserveItems(),
                controller.hammerUpgradeVoidRefund());
        } else if (module.component() instanceof ModuleMiner) {
            CelestialClient.planMinerFocusTier(
                assetId,
                controller.moduleIndex(),
                ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection()));
        }
        controller.close();
    }

    private void startMultiplePicker() {
        AutomatedFacility facility = ModuleConfigModalSupport.facility(assetId);
        ModuleInstance source = selectedModule();
        int sourceModuleIndex = controller.moduleIndex();
        if (facility == null || source == null
            || tilePickerController == null
            || !(source.component() instanceof ModuleHammer)
            || sourceModuleIndex < 0) return;
        ModuleTier targetTier = ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection());
        HammerVariant targetVariant = ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection());
        boolean reserveItems = controller.hammerUpgradeReserveItems();
        boolean voidCompletionRefund = controller.hammerUpgradeVoidRefund();
        controller.close();
        tilePickerController.start(
            "Upgrade modules",
            "Upgrade",
            coord -> ModuleUpgradePickerModel.isCompatibleTarget(facility, source, targetTier, targetVariant, coord),
            coord -> ModuleUpgradePickerModel.normalizeTarget(facility, coord),
            targets -> {
                List<StationTileCoord> confirmedTargets = ModuleUpgradePickerModel
                    .confirmedTargets(facility, source, targetTier, targetVariant, targets);
                if (confirmedTargets.isEmpty()) return;
                CelestialClient.planModuleUpgradeTargets(
                    assetId,
                    sourceModuleIndex,
                    targetTier,
                    targetVariant,
                    reserveItems,
                    voidCompletionRefund,
                    confirmedTargets);
            });
    }

    private boolean targetChanged(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return hammer.variant() != ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection())
                || module.tier() != ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection());
        }
        if (module.component() instanceof ModuleMiner) {
            return MinerFocusUiModel
                .canPlanTier(module, ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection()));
        }
        return false;
    }

    private String currentLine(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return "Current: " + hammer.variant()
                .name()
                + " "
                + module.tier()
                    .name();
        }
        if (module.component() instanceof ModuleMiner miner) {
            return "Current focus tier: " + focusTierLabel(miner.focusTier());
        }
        return "Current: -";
    }

    private String targetLine(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer) {
            return "Target: " + ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection())
                .name()
                + " "
                + ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection())
                    .name();
        }
        if (module.component() instanceof ModuleMiner) {
            return "Target focus tier: "
                + focusTierLabel(ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection()));
        }
        return "Target: -";
    }

    private String effectLine(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer) {
            HammerVariant targetVariant = ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection());
            ModuleTierData data = FacilityModuleRegistry.get(module.kind())
                .getTierData(ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection()));
            int chargeTicks = ModuleHammer.chargeTicks(targetVariant, data);
            return "Effect: cooldown " + chargeTicks / 20
                + "s, buffer "
                + ModuleConfigModalSupport.formatEu(targetVariant.shotEnergyEu())
                + " EU";
        }
        if (module.component() instanceof ModuleMiner miner) {
            MinerFocusTier target = ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection());
            return "Effect: focus bonus " + miner.focusTier()
                .bonusPercent() + "% -> " + target.bonusPercent() + "%";
        }
        return "Effect: -";
    }

    private String buildLine(ModuleInstance module) {
        ModuleTierData data = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        return "Build: " + data.buildTicks() + " ticks (" + data.buildTicks() / 20 + "s)";
    }

    private void drawCost(ModuleInstance module, int lineY) {
        Map<ItemStackWrapper, Long> cost = upgradeCost(module);
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        if (cost.isEmpty()) {
            ModuleConfigModalSupport.drawLine("None", x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            return;
        }
        int shown = 0;
        for (Map.Entry<ItemStackWrapper, Long> entry : cost.entrySet()) {
            if (shown >= 3) {
                ModuleConfigModalSupport.drawLine("...", x, lineY, EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
                break;
            }
            lineY = ModuleConfigModalSupport.drawTrimmedLine(
                entry.getValue() + "x "
                    + entry.getKey()
                        .toStack(1)
                        .getDisplayName(),
                x,
                lineY,
                BODY_WIDTH,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            shown++;
        }
    }

    private Map<ItemStackWrapper, Long> upgradeCost(ModuleInstance module) {
        ModuleTierData data;
        if (module.component() instanceof ModuleHammer) {
            data = FacilityModuleRegistry.get(module.kind())
                .getTierData(ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection()));
        } else {
            data = FacilityModuleRegistry.get(module.kind())
                .getTierData(module.tier());
        }
        return FacilityModuleRegistry.operationCost(data.constructionCost());
    }

    private boolean hasCancellableBuild() {
        return ModuleUpgradeUiModel.hasActiveBuild(selectedModule());
    }

    private String cancelLabel() {
        return controller.isModuleOperationCancelArmed() ? "Confirm" : "Cancel";
    }

    private void cancelBuild() {
        if (!hasCancellableBuild()) return;
        if (!controller.isModuleOperationCancelArmed()) {
            controller.armModuleOperationCancel();
            return;
        }
        CelestialClient.cancelModuleOperation(assetId, controller.moduleIndex());
        controller.clearModuleOperationCancel();
    }

    private String operationLabel(ModuleInstance module) {
        if (controller.isModuleOperationCancelArmed()) return "Click Confirm to cancel build";
        return switch (module.operationOrNull()
            .phase()) {
            case WAITING_FOR_MATERIALS -> "Build is waiting for materials";
            case BUILDING -> "Build in progress";
            case REFUNDING -> "Build is refunding";
            case COMPLETE -> "Build complete";
            case CANCELLED -> "Build cancelled";
        };
    }

    private String reserveLabel() {
        return controller.hammerUpgradeReserveItems() ? "Reserve On" : "Reserve Off";
    }

    private String voidLabel() {
        return controller.hammerUpgradeVoidRefund() ? "Void refund On" : "Void refund Off";
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleId());
    }

    private String title() {
        ModuleInstance module = selectedModule();
        return module == null ? "Module Upgrade" : ModuleConfigModalSupport.moduleTitle(module, "Upgrade");
    }

    private static String focusTierLabel(MinerFocusTier tier) {
        return tier == MinerFocusTier.NONE ? "None" : tier.name();
    }

    static List<ControlRect> controlRectsForTest() {
        int left = ModuleConfigModalSupport.PANEL_PADDING;
        return List.of(
            new ControlRect("reserve", left, FLAG_TOP, RESERVE_BUTTON_WIDTH, BUTTON_HEIGHT),
            new ControlRect(
                "voidRefund",
                left + RESERVE_BUTTON_WIDTH + CONTROL_GAP,
                FLAG_TOP,
                VOID_REFUND_BUTTON_WIDTH,
                BUTTON_HEIGHT),
            new ControlRect("confirm", left, FOOTER_TOP, CONFIRM_BUTTON_WIDTH, BUTTON_HEIGHT),
            new ControlRect(
                "multiple",
                left + CONFIRM_BUTTON_WIDTH + CONTROL_GAP,
                FOOTER_TOP,
                MULTIPLE_BUTTON_WIDTH,
                BUTTON_HEIGHT),
            new ControlRect(
                "cancel",
                left + CONFIRM_BUTTON_WIDTH + CONTROL_GAP + MULTIPLE_BUTTON_WIDTH + CONTROL_GAP,
                FOOTER_TOP,
                CANCEL_BUTTON_WIDTH,
                BUTTON_HEIGHT),
            new ControlRect(
                "back",
                WIDTH - ModuleConfigModalSupport.PANEL_PADDING - BACK_BUTTON_WIDTH,
                FOOTER_TOP,
                BACK_BUTTON_WIDTH,
                BUTTON_HEIGHT));
    }

    static ControlRect optionRectForTest(int slot) {
        int col = slot % OPTION_COLUMNS;
        int row = slot / OPTION_COLUMNS;
        return new ControlRect(
            "option" + slot,
            ModuleConfigModalSupport.PANEL_PADDING + col * (OPTION_BUTTON_WIDTH + OPTION_COLUMN_GAP),
            OPTION_TOP + row * (OPTION_BUTTON_HEIGHT + OPTION_ROW_GAP),
            OPTION_BUTTON_WIDTH,
            OPTION_BUTTON_HEIGHT);
    }

    record ControlRect(String name, int x, int y, int width, int height) {

        int bottom() {
            return y + height;
        }

        boolean overlaps(ControlRect other) {
            return x < other.x + other.width && x + width > other.x
                && y < other.y + other.height
                && y + height > other.y;
        }
    }

    private record OptionRef(ModuleUpgradeGroup group, ModuleUpgradeOption option) {}
}
