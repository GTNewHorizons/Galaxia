package com.gtnewhorizons.galaxia.registry.outpost.station;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;

public final class CapacityClusterBuilder {

    private CapacityClusterBuilder() {}

    /**
     * Builds capacity clusters for a given kind by BFS over orthogonally adjacent same-kind modules.
     *
     * @param layout the station layout to scan
     * @param kind   the module kind (must be a CAPACITY_KIND, otherwise returns empty list)
     * @return list of clusters, each containing connected same-kind members and their total effective capacity
     */
    public static List<CapacityCluster> build(StationLayout layout, FacilityModuleKind kind) {
        if (!kind.isCapacityModule()) {
            return Collections.emptyList();
        }

        Set<StationTileCoord> globalVisited = new HashSet<>();
        List<CapacityCluster> clusters = new ArrayList<>();

        layout.forEachAnchor((coord, module) -> {
            if (module.kind() != kind) return;
            if (globalVisited.contains(coord)) return;

            // BFS to collect all connected members
            Set<StationTileCoord> members = new HashSet<>();
            Deque<StationTileCoord> queue = new ArrayDeque<>();
            queue.addLast(coord);
            globalVisited.add(coord);
            members.add(coord);

            while (!queue.isEmpty()) {
                StationTileCoord current = queue.removeFirst();
                for (int i = 0; i < StationLayout.DX.length; i++) {
                    int ndx = current.dx() + StationLayout.DX[i];
                    int ndy = current.dy() + StationLayout.DY[i];
                    if (ndx < StationTileCoord.MIN || ndx > StationTileCoord.MAX
                        || ndy < StationTileCoord.MIN
                        || ndy > StationTileCoord.MAX) {
                        continue;
                    }
                    StationTileCoord ncoord = StationTileCoord.of(ndx, ndy);
                    ModuleInstance neighborModule = layout.moduleAt(ncoord);
                    if (neighborModule == null || neighborModule.kind() != kind) continue;
                    StationTileCoord nAnchor = neighborModule.anchor();
                    if (globalVisited.add(nAnchor)) {
                        members.add(nAnchor);
                        queue.addLast(nAnchor);
                    }
                }
            }

            // Compute total effective capacity for the cluster
            long totalEffective = 0;
            for (StationTileCoord memberCoord : members) {
                ModuleInstance mi = layout.moduleAt(memberCoord);
                if (mi == null) {
                    throw new IllegalStateException(
                        "CapacityClusterBuilder: null module at cluster member " + memberCoord
                            + " for kind "
                            + kind
                            + " — layout invariant violated: forEachAnchor yielded a coord with no module");
                }
                IModuleComponent comp = mi.component();
                if (comp == null) {
                    throw new IllegalStateException(
                        "CapacityClusterBuilder: null component for module " + mi.kind()
                            + " (id="
                            + mi.id
                            + ") at "
                            + memberCoord
                            + " — capacity module was created without a component");
                }
                long baseCap = mi.baseCapacity();
                int neighborCount = StationLayout.countOrthogonalNeighbors(layout, memberCoord, kind);
                totalEffective += Math.round(baseCap * (1.0 + 0.5 * neighborCount));
            }

            clusters.add(new CapacityCluster(kind, Collections.unmodifiableSet(members), totalEffective));
        });

        return Collections.unmodifiableList(clusters);
    }

}
