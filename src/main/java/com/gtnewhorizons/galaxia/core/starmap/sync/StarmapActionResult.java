package com.gtnewhorizons.galaxia.core.starmap.sync;

import com.gtnewhorizons.galaxia.core.network.AssetSyncPacket;

public record StarmapActionResult(boolean applied, AssetSyncPacket syncPacket, String errorKey) {

    public static StarmapActionResult applied(AssetSyncPacket syncPacket) {
        return new StarmapActionResult(true, syncPacket, null);
    }

    public static StarmapActionResult rejected(String errorKey) {
        return new StarmapActionResult(false, null, errorKey);
    }
}
