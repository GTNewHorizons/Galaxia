package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.List;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;

final class ButtonRect {

    final int left;
    final int top;
    final int right;
    final int bottom;

    ButtonRect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }
}

final class ConstructionSiteRow {

    final CelestialManagedAsset asset;
    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect nameClickArea;
    final ButtonRect actionButton;
    final ConstructionRowActionType actionType;

    ConstructionSiteRow(CelestialManagedAsset asset, int left, int top, int right, int bottom,
        ButtonRect nameClickArea, ButtonRect actionButton, ConstructionRowActionType actionType) {
        this.asset = asset;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.nameClickArea = nameClickArea;
        this.actionButton = actionButton;
        this.actionType = actionType;
    }
}

enum ConstructionRowActionType {
    CANCEL_BUILD("Cancel Build"),
    SEND_RESOURCES("Send To...");

    final String buttonLabel;

    ConstructionRowActionType(String buttonLabel) {
        this.buttonLabel = buttonLabel;
    }
}

final class AssetRow {

    final CelestialManagedAsset asset;
    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect nameClickArea;
    final ButtonRect manageButton;
    final ButtonRect destroyButton;

    AssetRow(CelestialManagedAsset asset, int left, int top, int right, int bottom, ButtonRect nameClickArea,
        ButtonRect manageButton, ButtonRect destroyButton) {
        this.asset = asset;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.nameClickArea = nameClickArea;
        this.manageButton = manageButton;
        this.destroyButton = destroyButton;
    }
}

final class AssetManagementLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final int contentLeft;
    final int contentTop;
    final int contentRight;
    final int contentBottom;
    final boolean showConstructionSection;
    final int constructionHeaderY;
    final int assetsHeaderY;
    final int noAssetsMessageY;
    final int totalContentHeight;
    final int maxScroll;
    final ButtonRect closeButton;
    final ButtonRect createStationButton;
    final ButtonRect createAutomatedStationButton;
    final ButtonRect createOutpostButton;
    final List<ConstructionSiteRow> siteRows;
    final List<AssetRow> assetRows;

    AssetManagementLayout(int left, int top, int right, int bottom, int contentLeft, int contentTop,
        int contentRight, int contentBottom, boolean showConstructionSection, int constructionHeaderY,
        int assetsHeaderY, int noAssetsMessageY, int totalContentHeight, int maxScroll, ButtonRect closeButton,
        ButtonRect createStationButton, ButtonRect createAutomatedStationButton, ButtonRect createOutpostButton,
        List<ConstructionSiteRow> siteRows, List<AssetRow> assetRows) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.contentLeft = contentLeft;
        this.contentTop = contentTop;
        this.contentRight = contentRight;
        this.contentBottom = contentBottom;
        this.showConstructionSection = showConstructionSection;
        this.constructionHeaderY = constructionHeaderY;
        this.assetsHeaderY = assetsHeaderY;
        this.noAssetsMessageY = noAssetsMessageY;
        this.totalContentHeight = totalContentHeight;
        this.maxScroll = maxScroll;
        this.closeButton = closeButton;
        this.createStationButton = createStationButton;
        this.createAutomatedStationButton = createAutomatedStationButton;
        this.createOutpostButton = createOutpostButton;
        this.siteRows = siteRows;
        this.assetRows = assetRows;
    }

    boolean isInScrollViewport(int x, int y) {
        return x >= contentLeft && x <= contentRight && y >= contentTop && y <= contentBottom;
    }
}

final class ModalBounds {

    final int left;
    final int top;
    final int right;
    final int bottom;

    ModalBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}

final class PendingAssetCreation {

    final String celestialObjectId;
    final String displayName;
    final CelestialAssetKind kind;
    final CelestialAssetLocation location;
    final List<CelestialAssetRequirement> requiredResources;

    PendingAssetCreation(String celestialObjectId, String displayName, CelestialAssetKind kind,
        CelestialAssetLocation location, List<CelestialAssetRequirement> requiredResources) {
        this.celestialObjectId = celestialObjectId;
        this.displayName = displayName;
        this.kind = kind;
        this.location = location;
        this.requiredResources = requiredResources;
    }
}

final class PendingAssetRename {

    final CelestialManagedAsset asset;

    PendingAssetRename(CelestialManagedAsset asset) {
        this.asset = asset;
    }
}

final class PendingAssetCreationLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect cancelButton;
    final ButtonRect confirmButton;

    PendingAssetCreationLayout(int left, int top, int right, int bottom, ButtonRect cancelButton,
        ButtonRect confirmButton) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.cancelButton = cancelButton;
        this.confirmButton = confirmButton;
    }
}

final class PendingAssetRenameLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect inputField;
    final ButtonRect cancelButton;
    final ButtonRect confirmButton;

    PendingAssetRenameLayout(int left, int top, int right, int bottom, ButtonRect inputField,
        ButtonRect cancelButton, ButtonRect confirmButton) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.inputField = inputField;
        this.cancelButton = cancelButton;
        this.confirmButton = confirmButton;
    }
}

final class PendingAssetDestruction {

    final CelestialManagedAsset asset;
    final boolean armed;

    PendingAssetDestruction(CelestialManagedAsset asset, boolean armed) {
        this.asset = asset;
        this.armed = armed;
    }
}

final class PendingAssetManagement {

    final CelestialManagedAsset asset;

    PendingAssetManagement(CelestialManagedAsset asset) {
        this.asset = asset;
    }
}

final class PendingAssetManagementLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect closeButton;

    PendingAssetManagementLayout(int left, int top, int right, int bottom, ButtonRect closeButton) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.closeButton = closeButton;
    }
}

final class PendingAssetDestructionLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect cancelButton;
    final ButtonRect destroyButton;

    PendingAssetDestructionLayout(int left, int top, int right, int bottom, ButtonRect cancelButton,
        ButtonRect destroyButton) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.cancelButton = cancelButton;
        this.destroyButton = destroyButton;
    }
}

final class PendingConstructionCancellation {

    final CelestialManagedAsset asset;

    PendingConstructionCancellation(CelestialManagedAsset asset) {
        this.asset = asset;
    }
}

final class PendingConstructionCancellationLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect cancelButton;
    final ButtonRect confirmButton;

    PendingConstructionCancellationLayout(int left, int top, int right, int bottom, ButtonRect cancelButton,
        ButtonRect confirmButton) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.cancelButton = cancelButton;
        this.confirmButton = confirmButton;
    }
}

final class PendingResourceTransfer {

    final CelestialManagedAsset asset;
    final List<StationTransferTarget> targets;

    PendingResourceTransfer(CelestialManagedAsset asset, List<StationTransferTarget> targets) {
        this.asset = asset;
        this.targets = targets;
    }
}

final class PendingResourceTransferLayout {

    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect closeButton;
    final List<TransferTargetRow> rows;

    PendingResourceTransferLayout(int left, int top, int right, int bottom, ButtonRect closeButton,
        List<TransferTargetRow> rows) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.closeButton = closeButton;
        this.rows = rows;
    }
}

final class StationTransferTarget {

    final String assetId;
    final String displayName;
    final String hostBodyName;

    StationTransferTarget(String assetId, String displayName, String hostBodyName) {
        this.assetId = assetId;
        this.displayName = displayName;
        this.hostBodyName = hostBodyName;
    }
}

final class TransferTargetRow {

    final StationTransferTarget target;
    final int left;
    final int top;
    final int right;
    final int bottom;
    final ButtonRect sendButton;

    TransferTargetRow(StationTransferTarget target, int left, int top, int right, int bottom, ButtonRect sendButton) {
        this.target = target;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.sendButton = sendButton;
    }
}
