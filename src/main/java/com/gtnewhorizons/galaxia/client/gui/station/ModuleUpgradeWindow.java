package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.station.ModuleUpgradeUiModel.Group;
import com.gtnewhorizons.galaxia.client.gui.station.ModuleUpgradeUiModel.Option;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
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

/** Upgrade of a hammer or miner: the target tier and variant, what it changes and costs, and a running build */
final class ModuleUpgradeWindow implements ModuleConfigWindow {

    private static final int GROUP_LABEL_WIDTH = 74;
    private static final int OPTION_WIDTH = 42;
    private static final int COST_LINES = 3;
    private static final int LINE_WIDTH = 300;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;
    private final @Nullable StationTilePickerController tilePickerController;
    private String builtShape = "";

    ModuleUpgradeWindow(CelestialAsset.ID assetId, ModuleConfigModalController controller,
        @Nullable StationTilePickerController tilePickerController) {
        this.assetId = assetId;
        this.controller = controller;
        this.tilePickerController = tilePickerController;
    }

    @Override
    public String title() {
        ModuleInstance module = selectedModule();
        return module == null ? StatCollector.translateToLocal("galaxia.gui.station.upgrade.title")
            : StationUiData.moduleTitle(module, "galaxia.gui.station.config.purpose.upgrade");
    }

    @Override
    public void update() {
        // The options on offer follow the selection, a bigger hammer offers other tiers
        if (!optionShape().equals(builtShape)) controller.rebuild();
    }

    private String optionShape() {
        ModuleInstance module = selectedModule();
        if (module == null || !ModuleUpgradeUiModel.supports(module)) return "";
        StringBuilder shape = new StringBuilder();
        for (Group group : ModuleUpgradeUiModel.groups(module, controller.moduleUpgradeSelection())) {
            shape.append(group.id())
                .append(':');
            for (Option option : group.options()) shape.append(option.id())
                .append(',');
            shape.append(';');
        }
        return shape.toString();
    }

    @Override
    public void build(GalaxiaUi.Column column) {
        builtShape = optionShape();
        ModuleInstance module = selectedModule();
        if (module == null || !ModuleUpgradeUiModel.supports(module)) {
            column.dim(StatCollector.translateToLocal("galaxia.gui.station.upgrade.none"));
            return;
        }
        column.text(() -> currentLine(selectedModule()));

        GalaxiaUi.Column building = GalaxiaUi.columnOf(GalaxiaUi.column());
        building.add(GalaxiaUi.colored(() -> buildStatus(selectedModule()), GalaxiaGuiStyle.WARN))
            .add(
                GalaxiaUi.button(
                    this::cancelLabel,
                    GalaxiaUi.width(StatCollector.translateToLocal("galaxia.gui.common.cancel")) + 10,
                    () -> true,
                    this::cancelBuild));
        building.flow()
            .setEnabledIf(w -> hasActiveBuild());
        column.add(building.flow());

        GalaxiaUi.Column plan = GalaxiaUi.columnOf(GalaxiaUi.column());
        plan.add(GalaxiaUi.colored(() -> targetLine(selectedModule()), GalaxiaGuiStyle.TEXT_DIM))
            .text(() -> effectLine(selectedModule()))
            .add(
                GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.station.upgrade.nothing"))
                    .setEnabledIf(w -> !targetChanged()))
            .add(
                GalaxiaUi.text(() -> buildLine(selectedModule()))
                    .setEnabledIf(w -> targetChanged()))
            .add(
                GalaxiaUi.dim(StatCollector.translateToLocal("galaxia.gui.station.upgrade.cost"))
                    .setEnabledIf(w -> targetChanged()));
        for (int line = 0; line < COST_LINES + 1; line++) {
            int index = line;
            plan.add(
                GalaxiaUi.text(() -> costLine(index))
                    .setEnabledIf(w -> targetChanged() && !costLine(index).isEmpty()));
        }
        for (Group group : ModuleUpgradeUiModel.groups(module, controller.moduleUpgradeSelection())) {
            Flow row = GalaxiaUi.row()
                .child(
                    GalaxiaUi.clipped(group.title(), GROUP_LABEL_WIDTH, GalaxiaGuiStyle.TEXT_DIM)
                        .width(GROUP_LABEL_WIDTH));
            for (Option option : group.options()) row.child(optionButton(group.id(), option));
            plan.add(row);
        }
        Flow flags = GalaxiaUi.row()
            .child(
                GalaxiaUi
                    .toggle(
                        () -> StatCollector.translateToLocal("galaxia.gui.station.upgrade.reserve"),
                        GalaxiaUi.width(StatCollector.translateToLocal("galaxia.gui.station.upgrade.reserve")) + 10,
                        controller::hammerUpgradeReserveItems,
                        controller::toggleHammerUpgradeReserveItems)
                    .tooltip(
                        t -> t.addLine(StatCollector.translateToLocal("galaxia.gui.station.upgrade.reserve.tooltip"))))
            .child(
                GalaxiaUi
                    .toggle(
                        () -> StatCollector.translateToLocal("galaxia.gui.station.upgrade.void_refund"),
                        GalaxiaUi.width(StatCollector.translateToLocal("galaxia.gui.station.upgrade.void_refund")) + 10,
                        controller::hammerUpgradeVoidRefund,
                        controller::toggleHammerUpgradeVoidRefund)
                    .tooltip(
                        t -> t.addLine(
                            StatCollector.translateToLocal("galaxia.gui.station.upgrade.void_refund.tooltip"))));
        flags.setEnabledIf(w -> selectedModule() != null && selectedModule().component() instanceof ModuleHammer);
        plan.add(flags);
        plan.row(
            GalaxiaUi
                .button(
                    StatCollector.translateToLocal("galaxia.gui.station.upgrade.confirm"),
                    this::canConfirm,
                    this::confirm)
                .name("module.upgrade.confirm"),
            GalaxiaUi.button(
                StatCollector.translateToLocal("galaxia.gui.station.upgrade.multiple"),
                this::canConfirmMultiple,
                this::startMultiplePicker));
        plan.flow()
            .setEnabledIf(w -> !hasActiveBuild());
        column.add(plan.flow());
    }

    private com.cleanroommc.modularui.widgets.ButtonWidget<?> optionButton(String groupId, Option built) {
        return GalaxiaUi.choice(() -> {
            Option option = option(groupId, built.id());
            return option == null ? built.label() : option.label();
        }, OPTION_WIDTH, () -> {
            Option option = option(groupId, built.id());
            return option != null && option.selected();
        }, () -> {
            Option option = option(groupId, built.id());
            return option != null && option.enabled() && !hasActiveBuild();
        }, () -> controller.selectModuleUpgradeOption(groupId, built.id()))
            .name("module.upgrade." + groupId + "." + built.id());
    }

    /** The option as it stands now, its flags follow the current selection */
    private @Nullable Option option(String groupId, String optionId) {
        ModuleInstance module = selectedModule();
        if (module == null) return null;
        for (Group group : ModuleUpgradeUiModel.groups(module, controller.moduleUpgradeSelection())) {
            if (!group.id()
                .equals(groupId)) continue;
            for (Option option : group.options()) {
                if (option.id()
                    .equals(optionId)) return option;
            }
        }
        return null;
    }

    private boolean hasActiveBuild() {
        return ModuleUpgradeUiModel.hasActiveBuild(selectedModule());
    }

    private boolean targetChanged() {
        ModuleInstance module = selectedModule();
        return module != null && targetChanged(module);
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

    private void confirm() {
        ModuleInstance module = selectedModule();
        if (module == null || !canConfirm()) return;
        if (module.component() instanceof ModuleHammer) {
            CelestialClient.planHammerUpgrade(
                assetId,
                module.id,
                ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection()),
                ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection()),
                controller.hammerUpgradeReserveItems(),
                controller.hammerUpgradeVoidRefund());
        } else if (module.component() instanceof ModuleMiner) {
            ModuleTier targetTier = ModuleUpgradeUiModel.minerTier(controller.moduleUpgradeSelection());
            MinerFocusTier targetFocusTier = ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection());
            if (MinerFocusUiModel.canPlanTier(module, targetFocusTier)) {
                CelestialClient.planMinerFocusTier(
                    assetId,
                    module.id,
                    targetTier == ModuleTier.NONE ? module.tier() : targetTier,
                    targetFocusTier);
            } else if (module.tier() != targetTier) {
                CelestialClient.planModuleTierUpgrade(assetId, module.id, targetTier, false);
            }
        }
        controller.close();
    }

    private void startMultiplePicker() {
        AutomatedFacility facility = StationUiData.facility(assetId);
        ModuleInstance source = selectedModule();
        if (facility == null || source == null
            || tilePickerController == null
            || !(source.component() instanceof ModuleHammer)) return;
        ModuleTier targetTier = ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection());
        HammerVariant targetVariant = ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection());
        boolean reserveItems = controller.hammerUpgradeReserveItems();
        boolean voidCompletionRefund = controller.hammerUpgradeVoidRefund();
        controller.close();
        tilePickerController.start(
            StatCollector.translateToLocal("galaxia.gui.station.upgrade.multiple_title"),
            StatCollector.translateToLocal("galaxia.gui.station.upgrade.multiple_confirm"),
            coord -> ModuleUpgradeUiModel.isCompatibleTarget(facility, source, targetTier, targetVariant, coord),
            coord -> StationTilePickerController.normalizeModuleTarget(facility, coord),
            targets -> {
                List<ModuleInstance.ID> confirmedTargets = ModuleUpgradeUiModel
                    .confirmedTargets(facility, source, targetTier, targetVariant, targets);
                if (confirmedTargets.isEmpty()) return;
                CelestialClient.planModuleUpgradeTargets(
                    assetId,
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
            return module.tier() != ModuleUpgradeUiModel.minerTier(controller.moduleUpgradeSelection())
                || MinerFocusUiModel
                    .canPlanTier(module, ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection()));
        }
        return false;
    }

    private String currentLine(ModuleInstance module) {
        if (module == null) return "";
        if (module.component() instanceof ModuleHammer hammer) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.upgrade.current",
                hammer.variant()
                    .name() + " "
                    + module.tier()
                        .name());
        }
        if (module.component() instanceof ModuleMiner miner) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.upgrade.current_miner",
                module.tier()
                    .name(),
                focusTierLabel(miner.focusTier()));
        }
        return StatCollector.translateToLocalFormatted("galaxia.gui.station.upgrade.current", "-");
    }

    private String targetLine(ModuleInstance module) {
        if (module == null) return "";
        if (module.component() instanceof ModuleHammer) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.upgrade.target",
                ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection())
                    .name() + " "
                    + ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection())
                        .name());
        }
        if (module.component() instanceof ModuleMiner) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.upgrade.target_miner",
                ModuleUpgradeUiModel.minerTier(controller.moduleUpgradeSelection())
                    .name(),
                focusTierLabel(ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection())));
        }
        return StatCollector.translateToLocalFormatted("galaxia.gui.station.upgrade.target", "-");
    }

    private String effectLine(ModuleInstance module) {
        if (module == null) return "";
        if (module.component() instanceof ModuleHammer) {
            HammerVariant targetVariant = ModuleUpgradeUiModel.hammerVariant(controller.moduleUpgradeSelection());
            ModuleTierData data = FacilityModuleRegistry.get(module.kind())
                .getTierData(ModuleUpgradeUiModel.hammerTier(controller.moduleUpgradeSelection()));
            int chargeTicks = ModuleHammer.chargeTicks(targetVariant, data);
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.upgrade.effect_hammer",
                chargeTicks / 20,
                StationUiData.formatEu(targetVariant.shotEnergyEu()));
        }
        if (module.component() instanceof ModuleMiner miner) {
            MinerFocusTier target = ModuleUpgradeUiModel.minerFocusTier(controller.moduleUpgradeSelection());
            ModuleTier targetTier = ModuleUpgradeUiModel.minerTier(controller.moduleUpgradeSelection());
            if (module.tier() == targetTier) {
                return StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.upgrade.effect_focus",
                    miner.focusTier()
                        .bonusPercent(),
                    target.bonusPercent());
            }
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.upgrade.effect_miner",
                module.tier()
                    .name(),
                targetTier.name(),
                miner.focusTier()
                    .bonusPercent(),
                target.bonusPercent());
        }
        return StatCollector.translateToLocalFormatted("galaxia.gui.station.upgrade.effect", "-");
    }

    private String buildLine(ModuleInstance module) {
        if (module == null) return "";
        ModuleTierData data = FacilityModuleRegistry.get(module.kind())
            .getTierData(module.tier());
        return StatCollector
            .translateToLocalFormatted("galaxia.gui.station.upgrade.build", data.buildTicks(), data.buildTicks() / 20);
    }

    /** One line of the cost, the last one standing for whatever does not fit */
    private String costLine(int index) {
        ModuleInstance module = selectedModule();
        if (module == null) return "";
        Map<ItemStackWrapper, Long> cost = ModuleUpgradeUiModel
            .upgradeMaterials(module, controller.moduleUpgradeSelection());
        if (cost.isEmpty())
            return index == 0 ? StatCollector.translateToLocal("galaxia.gui.station.upgrade.cost_none") : "";
        List<Map.Entry<ItemStackWrapper, Long>> entries = new ArrayList<>(cost.entrySet());
        if (index == COST_LINES) return entries.size() > COST_LINES ? "..." : "";
        if (index >= entries.size()) return "";
        Map.Entry<ItemStackWrapper, Long> entry = entries.get(index);
        return GalaxiaUi.clip(
            StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.picker.cost_line",
                entry.getValue(),
                entry.getKey()
                    .toStack(1)
                    .getDisplayName()),
            LINE_WIDTH);
    }

    private String cancelLabel() {
        return StatCollector.translateToLocal(
            controller.isModuleOperationCancelArmed() ? "galaxia.gui.station.upgrade.confirm"
                : "galaxia.gui.common.cancel");
    }

    private void cancelBuild() {
        if (!hasActiveBuild()) return;
        if (!controller.isModuleOperationCancelArmed()) {
            controller.armModuleOperationCancel();
            return;
        }
        CelestialClient.cancelModuleOperation(assetId, controller.moduleId());
        controller.clearModuleOperationCancel();
    }

    private String buildStatus(ModuleInstance module) {
        if (module == null || module.operationOrNull() == null) return "";
        if (controller.isModuleOperationCancelArmed())
            return StatCollector.translateToLocal("galaxia.gui.station.upgrade.cancel_confirm");
        return StatCollector.translateToLocal(
            switch (module.operationOrNull()
                .phase()) {
            case WAITING_FOR_MATERIALS -> "galaxia.gui.station.upgrade.state.waiting";
            case BUILDING -> "galaxia.gui.station.upgrade.state.building";
            case REFUNDING -> "galaxia.gui.station.upgrade.state.refunding";
            case COMPLETE -> "galaxia.gui.station.upgrade.state.complete";
            case CANCELLED -> "galaxia.gui.station.upgrade.state.cancelled";
            });
    }

    private ModuleInstance selectedModule() {
        return StationUiData.module(assetId, controller.moduleId());
    }

    private static String focusTierLabel(MinerFocusTier tier) {
        return tier == MinerFocusTier.NONE ? StatCollector.translateToLocal("galaxia.gui.station.picker.none")
            : tier.name();
    }

}
