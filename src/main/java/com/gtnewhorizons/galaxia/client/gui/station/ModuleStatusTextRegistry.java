package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.HammerDispatchStatus;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.HammerModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.IModuleOperation;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.PlacedTile;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

final class ModuleStatusTextRegistry {

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    static {
        register(ModuleStatusTextRegistry::appendSelectedTileText);
        register(ModuleStatusTextRegistry::appendHammerStatusText);
    }

    private ModuleStatusTextRegistry() {}

    static void register(Provider provider) {
        if (provider == null) throw new IllegalArgumentException("Module status text provider must not be null");
        PROVIDERS.add(provider);
    }

    static Lines collect(Context context) {
        Lines lines = new Lines();
        for (Provider provider : PROVIDERS) {
            provider.append(context, lines);
        }
        return lines;
    }

    static List<Provider> providers() {
        return Collections.unmodifiableList(PROVIDERS);
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

        HammerDispatchStatus.Status status = HammerDispatchStatus.evaluate(
            context.facility(),
            module,
            CelestialClient.allOutposts(),
            CelestialClient.clientDeliveries(),
            GalaxiaCelestialAPI.currentOrbitalTime());
        lines.line(
            hammerDispatchStatusLine(status),
            status.code() == HammerDispatchStatus.Code.READY ? EnumColors.MAP_COLOR_TEXT_BODY.getColor()
                : EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

        if (module.operationOrNull() == null || module.operationOrNull()
            .phase()
            .isTerminal()) {
            return;
        }

        String operationLine = context.facility()
            .isItemInventoryFull()
            && module.operationOrNull()
                .phase() == ModuleOperationPhase.REFUNDING
                    ? "Operation: refund paused"
                    : "Operation: " + module.operationOrNull()
                        .phase()
                        .name();
        lines.line(operationLine, EnumColors.MAP_COLOR_TEXT_WARNING.getColor());

        IModuleOperation activeSpec = module.operationOrNull()
            .plan()
            .spec();
        if (activeSpec instanceof HammerModuleOperation hammerSpec) {
            lines.line(
                "Target: " + hammerSpec.targetVariantKey()
                    + " "
                    + hammerSpec.targetTier()
                        .name(),
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        }
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

    private static String hammerDispatchStatusLine(HammerDispatchStatus.Status status) {
        return switch (status.code()) {
            case READY -> "Dispatch: ready";
            case WAITING_FOR_REQUEST -> "Dispatch: waiting for request";
            case NO_EXPORT_CONFIG -> "Dispatch: export disabled";
            case NO_SURPLUS_AFTER_RESERVE -> "Dispatch: no surplus after reserve";
            case ORDER_BELOW_PACKAGE_SIZE -> "Dispatch: order below package size " + status.sendAmount()
                + "/"
                + status.orderSize();
            case NEED_BIG_HAMMER -> "Dispatch: need BIG Hammer";
            case ROUTE_UNAVAILABLE -> "Dispatch: route unavailable";
            case BLOCKED_BY_DV_LIMIT -> "Dispatch: blocked by dV limit";
            case BLOCKED_BY_TOF_LIMIT -> "Dispatch: blocked by TOF limit";
            case NEED_ENERGY -> "Dispatch: need " + formatEu(status.requiredEnergy())
                + " EU, buffer "
                + formatEu(status.storedEnergy());
        };
    }

    interface Provider {

        void append(Context context, Lines lines);
    }

    record Context(AutomatedFacility facility, StationTileCoord selected, PlacedTile tile, ModuleInstance module) {}

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
