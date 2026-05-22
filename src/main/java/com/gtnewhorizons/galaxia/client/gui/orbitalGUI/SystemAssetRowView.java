package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.WarningPriority;

/**
 * Snapshot of one asset row's display state. Built once per sort/filter pass and reused by the
 * widget across frames; never holds derived gameplay state.
 */
public final class SystemAssetRowView {

    public final CelestialAsset.ID assetId;
    public final CelestialAsset.Kind kind;
    public final CelestialObjectId hostBodyId;
    public final String displayName;
    public final Buildable.Status status;
    public final boolean underConstruction;
    public final boolean underDeconstruction;
    public final boolean hasMining;
    public final boolean hasProduction;
    public final WarningPriority warning;
    public final float constructionProgress;

    public SystemAssetRowView(CelestialAsset asset) {
        this.assetId = asset.assetId;
        this.kind = asset.kind;
        this.hostBodyId = asset.celestialObjectId;
        this.displayName = asset.displayName();
        this.status = asset.status();
        this.underConstruction = asset.isInConstruction();
        this.underDeconstruction = asset.isUnderDeconstruction();
        this.hasMining = asset.hasMiningCapability();
        this.hasProduction = asset.hasProductionCapability();
        this.warning = asset.warningPriority();
        this.constructionProgress = asset.getConstructionProgress();
    }
}
