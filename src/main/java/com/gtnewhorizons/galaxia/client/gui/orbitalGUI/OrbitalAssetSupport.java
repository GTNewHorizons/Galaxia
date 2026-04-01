package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.galaxia.orbitalGUI.Hierarchy.OrbitalCelestialBody;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetKind;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetLocation;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetRequirement;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStatus;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialBodyAssetState;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialManagedAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectClass;

final class OrbitalAssetSupport {

    boolean hasStoredConstructionResources(CelestialManagedAsset asset) {
        if (asset == null) {
            return false;
        }
        for (CelestialAssetRequirement entry : asset.constructionInventory()) {
            if (entry.amount() > 0) {
                return true;
            }
        }
        return false;
    }

    boolean isManageableStationAsset(CelestialManagedAsset asset) {
        if (asset == null || asset.status() != CelestialAssetStatus.OPERATIONAL) {
            return false;
        }
        return asset.kind() == CelestialAssetKind.STATION || asset.kind() == CelestialAssetKind.AUTOMATED_STATION;
    }

    String formatAssetDisplayName(CelestialManagedAsset asset) {
        return switch (asset.status()) {
            case CONSTRUCTION_SITE -> asset.displayName() + " (In construction)";
            case DECONSTRUCTION -> asset.displayName() + " (Deconstruction)";
            default -> asset.displayName();
        };
    }

    String buildConstructionInventorySummary(CelestialManagedAsset asset) {
        if (asset.status() == CelestialAssetStatus.DECONSTRUCTION) {
            return buildStoredInventorySummary(asset.constructionInventory());
        }
        if (asset.requiredResources().isEmpty()) {
            return "Empty";
        }
        List<String> parts = new ArrayList<>();
        for (CelestialAssetRequirement required : asset.requiredResources()) {
            long storedAmount = 0;
            for (CelestialAssetRequirement stored : asset.constructionInventory()) {
                if (required.matches(stored.stack())) {
                    storedAmount += stored.amount();
                }
            }
            parts.add(storedAmount + "/" + required.amount() + " " + required.displayName());
        }
        return String.join(", ", parts);
    }

    List<StationTransferTarget> getTransferTargetsInSystem(OrbitalCelestialBody root, OrbitalCelestialBody body) {
        List<StationTransferTarget> targets = new ArrayList<>();
        if (body == null) {
            return targets;
        }

        OrbitalCelestialBody hostStar = findHostStar(root, body, null);
        if (hostStar == null) {
            return targets;
        }

        List<OrbitalCelestialBody> systemBodies = new ArrayList<>();
        collectBodies(hostStar, systemBodies);
        for (OrbitalCelestialBody systemBody : systemBodies) {
            CelestialBodyAssetState state = CelestialAssetStore.getState(systemBody.id());
            for (CelestialManagedAsset asset : state.assets()) {
                boolean isStationTarget = asset.status() == CelestialAssetStatus.OPERATIONAL
                    && asset.location() == CelestialAssetLocation.ORBIT
                    && (asset.kind() == CelestialAssetKind.STATION
                        || asset.kind() == CelestialAssetKind.AUTOMATED_STATION);
                if (isStationTarget) {
                    targets.add(new StationTransferTarget(asset.assetId(), asset.displayName(), systemBody.displayName()));
                }
            }
        }
        return targets;
    }

    String formatAssetKind(CelestialAssetKind kind) {
        return switch (kind) {
            case STATION -> "Station";
            case AUTOMATED_STATION -> "Automated Station";
            case AUTOMATED_OUTPOST -> "Automated Outpost";
        };
    }

    String formatAssetLocation(CelestialAssetLocation location) {
        return switch (location) {
            case ORBIT -> "Orbit";
            case SURFACE -> "Surface";
        };
    }

    private String buildStoredInventorySummary(List<CelestialAssetRequirement> storedResources) {
        if (storedResources.isEmpty()) {
            return "Empty";
        }
        List<String> parts = new ArrayList<>();
        for (CelestialAssetRequirement stored : storedResources) {
            parts.add(stored.amount() + " " + stored.displayName());
        }
        return String.join(", ", parts);
    }

    private OrbitalCelestialBody findHostStar(OrbitalCelestialBody current, OrbitalCelestialBody target,
        OrbitalCelestialBody currentStar) {
        OrbitalCelestialBody nextStar = current.objectClass() == CelestialObjectClass.STAR ? current : currentStar;
        if (current == target) {
            return nextStar;
        }
        for (OrbitalCelestialBody child : current.children()) {
            OrbitalCelestialBody found = findHostStar(child, target, nextStar);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void collectBodies(OrbitalCelestialBody current, List<OrbitalCelestialBody> out) {
        out.add(current);
        for (OrbitalCelestialBody child : current.children()) {
            collectBodies(child, out);
        }
    }
}
