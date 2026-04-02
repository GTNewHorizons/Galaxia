package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;

final class OrbitalAssetActionController {

    interface Callbacks {

        boolean isCreativeBuildModeEnabled();

        void showActionStatus(String message);

        void beginRenameInput(String currentText);

        void endRenameInput();

        String getRenameInput();
    }

    private final OrbitalAssetSupport assetSupport;
    private final Callbacks callbacks;

    OrbitalAssetActionController(OrbitalAssetSupport assetSupport, Callbacks callbacks) {
        this.assetSupport = assetSupport;
        this.callbacks = callbacks;
    }

    void openAssetManagement(OrbitalAssetUiState state, OrbitalCelestialBody body) {
        if (body == null || body.objectClass() == CelestialObjectClass.GALAXY) {
            return;
        }
        state.openAssetManagement(body);
        closePendingAssetRename(state);
    }

    void closeAssetManagement(OrbitalAssetUiState state) {
        state.closeAssetManagement();
        closePendingAssetRename(state);
    }

    void createBaseStation(OrbitalCelestialBody body) {
        if (body == null) {
            return;
        }
        CelestialAssetStore.createOperationalAsset(
            body.id(),
            buildDefaultAssetDisplayName(body, CelestialAssetKind.STATION),
            CelestialAssetKind.STATION,
            getDefaultAssetLocation(CelestialAssetKind.STATION));
        callbacks.showActionStatus("Station created");
    }

    void triggerAssetCreation(OrbitalAssetUiState state, OrbitalCelestialBody body, CelestialAssetKind kind,
        boolean openManagementFirst) {
        if (body == null) {
            return;
        }
        if (openManagementFirst) {
            openAssetManagement(state, body);
        }
        CelestialAssetLocation location = getDefaultAssetLocation(kind);
        String displayName = buildDefaultAssetDisplayName(body, kind);
        if (callbacks.isCreativeBuildModeEnabled()) {
            CelestialAssetStore.createOperationalAsset(body.id(), displayName, kind, location);
            callbacks.showActionStatus(assetSupport.formatAssetKind(kind) + " created");
            return;
        }
        state.pendingAssetCreation = new PendingAssetCreation(
            body.id(),
            displayName,
            kind,
            location,
            CelestialAssetStore.previewRequirements(kind));
    }

    void confirmPendingAssetCreation(OrbitalAssetUiState state) {
        if (state.pendingAssetCreation == null) {
            return;
        }
        if (callbacks.isCreativeBuildModeEnabled()) {
            CelestialAssetStore.createOperationalAsset(
                state.pendingAssetCreation.celestialObjectId,
                state.pendingAssetCreation.displayName,
                state.pendingAssetCreation.kind,
                state.pendingAssetCreation.location);
            callbacks.showActionStatus(assetSupport.formatAssetKind(state.pendingAssetCreation.kind) + " created");
        } else {
            CelestialAssetStore.createAssetInConstruction(
                state.pendingAssetCreation.celestialObjectId,
                state.pendingAssetCreation.displayName,
                state.pendingAssetCreation.kind,
                state.pendingAssetCreation.location);
            callbacks.showActionStatus(
                assetSupport.formatAssetKind(state.pendingAssetCreation.kind) + " construction planned");
        }
        state.pendingAssetCreation = null;
    }

    void dismissPendingAssetCreation(OrbitalAssetUiState state) {
        state.pendingAssetCreation = null;
    }

    void openPendingAssetRename(OrbitalAssetUiState state, CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        state.pendingAssetRename = new PendingAssetRename(asset);
        callbacks.beginRenameInput(asset.displayName());
    }

    void closePendingAssetRename(OrbitalAssetUiState state) {
        state.pendingAssetRename = null;
        callbacks.endRenameInput();
    }

    void openPendingAssetDestruction(OrbitalAssetUiState state, CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        state.pendingAssetDestruction = new PendingAssetDestruction(asset, false);
    }

    void dismissPendingAssetDestruction(OrbitalAssetUiState state) {
        state.pendingAssetDestruction = null;
    }

    void advancePendingAssetDestruction(OrbitalAssetUiState state) {
        if (state.pendingAssetDestruction == null) {
            return;
        }
        if (!state.pendingAssetDestruction.armed) {
            state.pendingAssetDestruction = new PendingAssetDestruction(state.pendingAssetDestruction.asset, true);
            return;
        }
        CelestialAssetStore.destroyAsset(state.pendingAssetDestruction.asset.assetId());
        callbacks.showActionStatus("Asset destroyed");
        state.pendingAssetDestruction = null;
    }

    void openPendingAssetManagement(OrbitalAssetUiState state, CelestialManagedAsset asset) {
        if (asset == null || !assetSupport.isManageableStationAsset(asset)) {
            return;
        }
        state.pendingAssetManagement = new PendingAssetManagement(asset);
    }

    void closePendingAssetManagement(OrbitalAssetUiState state) {
        state.pendingAssetManagement = null;
    }

    void openPendingConstructionCancellation(OrbitalAssetUiState state, CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        state.pendingConstructionCancellation = new PendingConstructionCancellation(asset);
    }

    void dismissPendingConstructionCancellation(OrbitalAssetUiState state) {
        state.pendingConstructionCancellation = null;
    }

    void confirmPendingConstructionCancellation(OrbitalAssetUiState state) {
        if (state.pendingConstructionCancellation == null) {
            return;
        }
        CelestialAssetStore.startDeconstruction(state.pendingConstructionCancellation.asset.assetId());
        callbacks.showActionStatus("Construction site converted to deconstruction");
        state.pendingConstructionCancellation = null;
    }

    void openPendingResourceTransfer(OrbitalAssetUiState state, OrbitalCelestialBody root, CelestialManagedAsset asset) {
        if (asset == null) {
            return;
        }
        state.pendingResourceTransfer = new PendingResourceTransfer(
            asset,
            assetSupport.getTransferTargetsInSystem(root, state.assetManagementBody));
    }

    void dismissPendingResourceTransfer(OrbitalAssetUiState state) {
        state.pendingResourceTransfer = null;
    }

    void sendPendingResourceTransfer(OrbitalAssetUiState state, StationTransferTarget target) {
        callbacks.showActionStatus("Resource transfer planning is not implemented yet");
        state.pendingResourceTransfer = null;
    }

    void confirmPendingAssetRename(OrbitalAssetUiState state) {
        if (state.pendingAssetRename == null) {
            return;
        }
        String renamed = callbacks.getRenameInput().trim();
        if (renamed.isEmpty()) {
            callbacks.showActionStatus("Name cannot be empty");
            return;
        }
        if (renamed.equals(state.pendingAssetRename.asset.displayName())) {
            closePendingAssetRename(state);
            return;
        }
        if (CelestialAssetStore.renameAsset(state.pendingAssetRename.asset.assetId(), renamed)) {
            callbacks.showActionStatus("Asset renamed");
            closePendingAssetRename(state);
            return;
        }
        callbacks.showActionStatus("Rename failed");
    }

    void dismissPendingModalByOutsideClick(OrbitalAssetUiState state) {
        if (state.pendingAssetRename != null) {
            closePendingAssetRename(state);
            return;
        }
        if (state.pendingResourceTransfer != null) {
            dismissPendingResourceTransfer(state);
            return;
        }
        if (state.pendingAssetManagement != null) {
            closePendingAssetManagement(state);
            return;
        }
        if (state.pendingConstructionCancellation != null) {
            dismissPendingConstructionCancellation(state);
            return;
        }
        if (state.pendingAssetDestruction != null) {
            dismissPendingAssetDestruction(state);
            return;
        }
        if (state.pendingAssetCreation != null) {
            dismissPendingAssetCreation(state);
        }
    }

    private String buildDefaultAssetDisplayName(OrbitalCelestialBody body, CelestialAssetKind kind) {
        return body.displayName() + " " + assetSupport.formatAssetKind(kind);
    }

    private CelestialAssetLocation getDefaultAssetLocation(CelestialAssetKind kind) {
        return kind == CelestialAssetKind.AUTOMATED_OUTPOST ? CelestialAssetLocation.SURFACE : CelestialAssetLocation.ORBIT;
    }
}
