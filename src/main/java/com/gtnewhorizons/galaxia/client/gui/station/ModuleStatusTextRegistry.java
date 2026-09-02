package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.BlockingReason;
import com.gtnewhorizons.galaxia.registry.outpost.module.MinerFocusTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationState;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleStatusTextRegistry {

    private ModuleStatusTextRegistry() {}

    static Lines collect(Context context) {
        Lines lines = new Lines();
        appendSelectedTileText(context, lines);
        appendModuleRuntimeText(context, lines);
        appendMinerStatusText(context, lines);
        appendHammerStatusText(context, lines);
        appendOperationStatusText(context, lines);
        return lines;
    }

    private static void appendSelectedTileText(Context context, Lines lines) {
        StationTileCoord selected = context.selected();
        lines.line("Selected " + selected.dx() + ", " + selected.dy(), EnumColors.MAP_COLOR_TEXT_SECTION.getColor());

        PlacedTile tile = context.tile();
        if (tile == null) {
            lines.line("Expansion slot", EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            return;
        }

        ModuleInstance module = tile.module();
        if (module == null) {
            lines.line("Station Core", EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        } else {
            lines.line(moduleDisplayName(module), EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
        lines.line(
            tile.state()
                .name(),
            EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
    }

    private static void appendHammerStatusText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;

        lines.line(
            "Buffer: " + formatEu(hammer.energyStored()) + "/" + formatEu(hammer.energyCapacity()) + " EU",
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lines.line(
            "Cooldown: " + cooldownText(hammer.shotCooldownTicks()),
            hammer.shotCooldownTicks() > 0 ? EnumColors.MAP_COLOR_TEXT_WARNING.getColor()
                : EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        HammerDispatchStatus.Status status = context.hammerDispatchStatus();
        if (status == null) return;
        lines.line(
            hammerDispatchStatusLine(status),
            status.code() == HammerDispatchStatus.Code.READY ? EnumColors.MAP_COLOR_TEXT_BODY.getColor()
                : EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
    }

    private static void appendModuleRuntimeText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (module == null) return;

        long basePower = module.powerDrawEuPerTick();
        if (basePower > 0L) {
            long effectivePower = context.facility()
                .effectivePowerDrawEuPerTick(module);
            String powerLine = "Power draw: " + formatEu(effectivePower) + " EU/t";
            if (effectivePower != basePower) {
                powerLine += " (base " + formatEu(basePower) + ")";
            }
            lines.line(powerLine, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }

        if (!module.enabled()) {
            lines.line("Runtime: disabled", EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            return;
        }
        if (module.blocking() != BlockingReason.NONE) {
            lines.line(
                "Blocked: " + module.blocking()
                    .name(),
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        }
    }

    private static void appendMinerStatusText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (module == null || !(module.component() instanceof ModuleMiner miner)) return;

        String focusLine = "Focus: " + miner.focusTier()
            .name();
        if (miner.focusOreKeyOrNull() != null) {
            focusLine += " " + miner.focusOreKeyOrNull();
            focusLine += " (+" + currentFocusBonusPercent(miner) + "%)";
        }
        lines.line(focusLine, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        if (miner.focusOreKeyOrNull() != null) {
            lines.line("Alignment: " + focusAlignmentPercent(miner) + "%", EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
    }

    private static void appendOperationStatusText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (module == null) return;

        ModuleOperationState operation = module.operationOrNull();
        if (operation == null || operation.phase()
            .isTerminal()) {
            return;
        }

        String operationLine = context.facility()
            .isItemInventoryFull() && operation.phase() == ModuleOperationPhase.REFUNDING ? "Operation: refund paused"
                : "Operation: " + operation.phase()
                    .name();
        lines.line(operationLine, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

        lines.line(
            operationTargetLine(
                operation.plan()
                    .spec()),
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private static String moduleDisplayName(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return hammer.variant()
                .name() + " Hammer "
                + module.tier()
                    .name();
        }
        return module.kind()
            .getDisplayName();
    }

    private static String formatEu(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    private static String cooldownText(int ticks) {
        if (ticks <= 0) return "ready";
        return ((ticks + 19) / 20) + "s";
    }

    private static int focusAlignmentPercent(ModuleMiner miner) {
        return miner.focusAlignmentProgress() * 100 / MinerFocusTier.ALIGNMENT_REQUIRED_TICKS;
    }

    private static int currentFocusBonusPercent(ModuleMiner miner) {
        return miner.focusTier()
            .bonusPercent() * miner.focusAlignmentProgress()
            / MinerFocusTier.ALIGNMENT_REQUIRED_TICKS;
    }

    private static String operationTargetLine(IModuleOperation spec) {
        if (spec instanceof IModuleOperation.Hammer hammerSpec) {
            return "Target: " + hammerSpec.targetVariantKey()
                + " "
                + hammerSpec.targetTier()
                    .name();
        }
        if (spec instanceof IModuleOperation.MinerFocus minerSpec) {
            String line = "Target focus: " + minerSpec.targetFocusTierKey();
            if (minerSpec.targetFocusOreKey() != null) {
                line += " " + minerSpec.targetFocusOreKey();
            }
            return line;
        }
        if (spec instanceof IModuleOperation.Tier tierSpec) {
            return "Target tier: " + tierSpec.targetTier()
                .name();
        }
        if (spec == IModuleOperation.DECONSTRUCTION) {
            return StatCollector.translateToLocal("galaxia.module.operation.deconstruction_pending");
        }
        return "Operation pending";
    }

    private static String hammerDispatchStatusLine(HammerDispatchStatus.Status status) {
        return switch (status.code()) {
            case READY -> "Dispatch: ready";
            case WAITING_FOR_REQUEST -> "Dispatch: waiting for request";
            case NO_EXPORT_CONFIG -> "Dispatch: export disabled";
            case NO_SURPLUS_AFTER_RESERVE -> "Dispatch: no surplus after reserve";
            case DESTINATION_LACKS_PACKAGE_SPACE -> "Dispatch: destination lacks package space " + status.sendAmount()
                + "/"
                + status.orderSize();
            case DESTINATION_CAPACITY_BLOCKED -> "Dispatch: destination full, " + status.sendAmount() + " arrived";
            case NEED_BIG_HAMMER -> "Dispatch: need BIG Hammer";
            case ROUTE_UNAVAILABLE -> "Dispatch: route unavailable";
            case BLOCKED_BY_DV_LIMIT -> "Dispatch: blocked by dV limit";
            case BLOCKED_BY_TOF_LIMIT -> "Dispatch: blocked by TOF limit";
            case NEED_ENERGY -> "Dispatch: need " + formatEu(status.requiredEnergy())
                + " EU, buffer "
                + formatEu(status.storedEnergy());
        };
    }

    record Context(AutomatedFacility facility, StationTileCoord selected, PlacedTile tile, ModuleInstance module,
        @javax.annotation.Nullable HammerDispatchStatus.Status hammerDispatchStatus) {}

    static final class Lines {

        private final List<TextEntry> entries = new ArrayList<>();

        void line(String text, int color) {
            if (text == null || text.isBlank()) return;
            entries.add(new TextEntry(text, color));
        }

        int draw(int x, int y) {
            int lineY = y;
            for (TextEntry entry : entries) {
                lineY = ModuleConfigModalSupport.drawLine(entry.text(), x, lineY, entry.color());
            }
            return lineY;
        }
    }

    private record TextEntry(String text, int color) {}
}
