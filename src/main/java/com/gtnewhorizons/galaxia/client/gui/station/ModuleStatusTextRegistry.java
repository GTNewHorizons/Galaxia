package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
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
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileState;

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
        PlacedTile tile = context.tile();
        if (tile == null) {
            lines.line(
                StatCollector.translateToLocal("galaxia.gui.station.status.expansion_slot"),
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            return;
        }

        ModuleInstance module = tile.module();
        if (module == null) {
            lines.line(
                StatCollector.translateToLocal("galaxia.gui.station.status.core"),
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        } else {
            lines.line(moduleDisplayName(module), EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
        // A working module needs no word about it, anything else says what is going on
        if (tile.state() != StationTileState.OCCUPIED_OPERATIONAL) {
            lines.line(
                StatCollector.translateToLocal(
                    "galaxia.gui.station.tile_state." + tile.state()
                        .name()
                        .toLowerCase()),
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        }
    }

    private static void appendHammerStatusText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;

        lines.line(
            StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.status.buffer",
                formatEu(hammer.energyStored()),
                formatEu(hammer.energyCapacity())),
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lines.line(
            cooldownText(hammer.shotCooldownTicks()),
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
            String powerLine = effectivePower == basePower
                ? StatCollector.translateToLocalFormatted("galaxia.gui.station.status.power", formatEu(effectivePower))
                : StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.status.power_base",
                    formatEu(effectivePower),
                    formatEu(basePower));
            lines.line(powerLine, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }

        if (!module.enabled()) {
            lines.line(
                StatCollector.translateToLocal("galaxia.gui.station.blocking.player_disabled"),
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
            return;
        }
        if (module.blocking() != BlockingReason.NONE) {
            lines.line(
                StatCollector.translateToLocal(
                    "galaxia.gui.station.blocking." + module.blocking()
                        .name()
                        .toLowerCase()),
                EnumColors.MAP_COLOR_TEXT_WARNING.getColor());
        }
    }

    private static void appendMinerStatusText(Context context, Lines lines) {
        ModuleInstance module = context.module();
        if (module == null || !(module.component() instanceof ModuleMiner miner)) return;

        String focusLine = miner.focusOreKeyOrNull() == null
            ? StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.status.focus",
                miner.focusTier()
                    .name())
            : StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.status.focus_ore",
                miner.focusTier()
                    .name(),
                miner.focusOreKeyOrNull(),
                currentFocusBonusPercent(miner));
        lines.line(focusLine, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        if (miner.focusOreKeyOrNull() != null) {
            lines.line(
                StatCollector
                    .translateToLocalFormatted("galaxia.gui.station.status.alignment", focusAlignmentPercent(miner)),
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
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
            .isItemInventoryFull() && operation.phase() == ModuleOperationPhase.REFUNDING
                ? StatCollector.translateToLocal("galaxia.gui.station.operation.refund_paused")
                : StatCollector.translateToLocal("galaxia.gui.station.upgrade.state." + switch (operation.phase()) {
                case WAITING_FOR_MATERIALS -> "waiting";
                case BUILDING -> "building";
                case REFUNDING -> "refunding";
                case COMPLETE -> "complete";
                case CANCELLED -> "cancelled";
                });
        lines.line(operationLine, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

        lines.line(
            operationTargetLine(
                operation.plan()
                    .spec()),
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
    }

    private static String moduleDisplayName(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.status.hammer_name",
                hammer.variant()
                    .name(),
                module.tier()
                    .name());
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
        if (ticks <= 0) return StatCollector.translateToLocal("galaxia.gui.station.status.cooldown_ready");
        return StatCollector.translateToLocalFormatted("galaxia.gui.station.status.cooldown", (ticks + 19) / 20);
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
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.status.target_hammer",
                hammerSpec.targetVariant(),
                hammerSpec.targetTier()
                    .name());
        }
        if (spec instanceof IModuleOperation.MinerFocus minerSpec) {
            return minerSpec.targetFocusOreKey() == null
                ? StatCollector
                    .translateToLocalFormatted("galaxia.gui.station.status.target_focus", minerSpec.targetFocusTier())
                : StatCollector.translateToLocalFormatted(
                    "galaxia.gui.station.status.target_focus_ore",
                    minerSpec.targetFocusTier(),
                    minerSpec.targetFocusOreKey());
        }
        if (spec instanceof IModuleOperation.Tier tierSpec) {
            return StatCollector.translateToLocalFormatted(
                "galaxia.gui.station.status.target_tier",
                tierSpec.targetTier()
                    .name());
        }
        if (spec == IModuleOperation.DECONSTRUCTION) {
            return StatCollector.translateToLocal("galaxia.module.operation.deconstruction_pending");
        }
        if (spec == IModuleOperation.CONSTRUCTION) {
            return StatCollector.translateToLocal("galaxia.module.operation.construction_pending");
        }
        return StatCollector.translateToLocal("galaxia.gui.station.status.operation_pending");
    }

    static String hammerDispatchStatusLine(HammerDispatchStatus.Status status) {
        String key = "galaxia.gui.station.dispatch." + status.code()
            .name()
            .toLowerCase();
        return switch (status.code()) {
            case DESTINATION_LACKS_PACKAGE_SPACE -> StatCollector
                .translateToLocalFormatted(key, status.sendAmount(), status.orderSize());
            case DESTINATION_CAPACITY_BLOCKED -> StatCollector.translateToLocalFormatted(key, status.sendAmount());
            case NEED_ENERGY -> StatCollector
                .translateToLocalFormatted(key, formatEu(status.requiredEnergy()), formatEu(status.storedEnergy()));
            default -> StatCollector.translateToLocal(key);
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

        List<GalaxiaUi.Line> lines() {
            List<GalaxiaUi.Line> lines = new ArrayList<>(entries.size());
            for (TextEntry entry : entries) lines.add(new GalaxiaUi.Line(entry.text(), entry.color()));
            return lines;
        }
    }

    private record TextEntry(String text, int color) {}
}
