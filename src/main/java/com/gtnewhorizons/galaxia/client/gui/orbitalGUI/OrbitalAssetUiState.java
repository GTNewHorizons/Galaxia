package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;

final class OrbitalAssetUiState {

    OrbitalCelestialBody assetManagementBody;
    PendingAssetCreation pendingAssetCreation;
    PendingAssetDestruction pendingAssetDestruction;
    PendingConstructionCancellation pendingConstructionCancellation;
    PendingResourceTransfer pendingResourceTransfer;
    PendingAssetManagement pendingAssetManagement;
    PendingAssetRename pendingAssetRename;

    boolean isAssetManagementOpen() {
        return assetManagementBody != null;
    }

    boolean hasBlockingModal() {
        return pendingAssetCreation != null
            || pendingAssetDestruction != null
            || pendingConstructionCancellation != null
            || pendingResourceTransfer != null
            || pendingAssetManagement != null
            || pendingAssetRename != null;
    }

    void openAssetManagement(OrbitalCelestialBody body) {
        assetManagementBody = body;
        clearTransientState();
    }

    void closeAssetManagement() {
        assetManagementBody = null;
        clearTransientState();
    }

    void clearTransientState() {
        pendingAssetCreation = null;
        pendingAssetDestruction = null;
        pendingConstructionCancellation = null;
        pendingResourceTransfer = null;
        pendingAssetManagement = null;
        pendingAssetRename = null;
    }
}
