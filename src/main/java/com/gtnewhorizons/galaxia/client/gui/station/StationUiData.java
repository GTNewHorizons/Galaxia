package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.operation.ModuleOperationPhase;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;

/** What the station screens read about a station and its modules, shared by the station window and its windows */
final class StationUiData {

    private StationUiData() {}

    static @Nullable AutomatedFacility facility(CelestialAsset.ID assetId) {
        return assetId != null && CelestialClient.getByAssetId(assetId) instanceof AutomatedFacility facility ? facility
            : null;
    }

    static @Nullable CelestialAsset asset(CelestialAsset.ID assetId) {
        return assetId != null ? CelestialClient.getByAssetId(assetId) : null;
    }

    static @Nullable ModuleInstance module(CelestialAsset.ID assetId, ModuleInstance.ID moduleId) {
        AutomatedFacility facility = facility(assetId);
        return facility == null ? null : facility.moduleById(moduleId);
    }

    static boolean refundBlockedByFullInventory(CelestialAsset.ID assetId, ModuleInstance module) {
        AutomatedFacility facility = facility(assetId);
        return facility != null && module != null
            && module.operationOrNull() != null
            && module.operationOrNull()
                .phase() == ModuleOperationPhase.REFUNDING
            && facility.isItemInventoryFull();
    }

    /** What a module's running build, upgrade or refund is doing, or the prompt to confirm cancelling it */
    static String operationLabel(CelestialAsset.ID assetId, ModuleInstance module, boolean cancelArmed) {
        if (module.operationOrNull() == null) return "";
        boolean cancellable = !module.operationOrNull()
            .phase()
            .isTerminal();
        if (cancellable && cancelArmed)
            return StatCollector.translateToLocal("galaxia.gui.station.operation.cancel_confirm");
        return StatCollector.translateToLocal(
            switch (module.operationOrNull()
                .phase()) {
            case WAITING_FOR_MATERIALS -> "galaxia.gui.station.operation.waiting";
            case BUILDING -> "galaxia.gui.station.operation.building";
            case REFUNDING -> refundBlockedByFullInventory(assetId, module)
                ? "galaxia.gui.station.operation.refund_paused"
                : "galaxia.gui.station.operation.refunding";
            case COMPLETE -> "galaxia.gui.station.operation.complete";
            case CANCELLED -> "galaxia.gui.station.operation.cancelled";
            });
    }

    static String formatEu(long amount) {
        if (amount < 1_000L) return Long.toString(amount);
        if (amount < 1_000_000L) return (amount / 1_000L) + "k";
        return (amount / 1_000_000L) + "M";
    }

    /** Title of a module window: the module, where it stands and what the window is for */
    static String moduleTitle(ModuleInstance module, String purposeKey) {
        StationTileCoord anchor = module.anchor();
        return StatCollector.translateToLocalFormatted(
            "galaxia.gui.station.config.module_title",
            moduleDisplayName(module),
            (int) anchor.dx(),
            (int) anchor.dy(),
            StatCollector.translateToLocal(purposeKey));
    }

    static String moduleDisplayName(ModuleInstance module) {
        if (module.component() instanceof ModuleHammer hammer) {
            return hammer.variant()
                .name() + " Hammer";
        }
        String name = module.kind()
            .name()
            .toLowerCase();
        StringBuilder title = new StringBuilder(name.length());
        boolean nextUpper = true;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '_') {
                title.append(' ');
                nextUpper = true;
                continue;
            }
            title.append(nextUpper ? Character.toUpperCase(ch) : ch);
            nextUpper = false;
        }
        return title.toString();
    }
}
