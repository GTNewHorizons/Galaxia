package com.gtnewhorizons.galaxia.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;

import com.gtnewhorizons.galaxia.registry.celestial.CelestialHierarchy;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;

public final class GalaxiaCelestialAPI {

    private GalaxiaCelestialAPI() {}

    public static void register(CelestialObject registration) {
        CelestialRegistry.register(registration);
    }

    public static boolean isFrozen() {
        return CelestialRegistry.isFrozen();
    }

    public static Optional<CelestialObject> get(CelestialObjectId id) {
        return id == null ? Optional.empty() : get(CelestialObjectKey.registered(id));
    }

    public static Optional<CelestialObject> get(CelestialObjectKey key) {
        return CelestialRegistry.get(key);
    }

    public static List<CelestialObject> getAll() {
        return CelestialRegistry.getAll();
    }

    public static List<CelestialObject> getRoots() {
        return CelestialRegistry.getRoots();
    }

    public static CelestialObject getPrimaryRoot() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findByDimension(DimensionEnum dimension) {
        return CelestialRegistry.findByDimension(dimension);
    }

    public static Optional<CelestialObject> findByDimension(int dimensionId) {
        DimensionEnum dimension = DimensionEnum.fromId(dimensionId);
        return dimension == null ? Optional.empty() : findByDimension(dimension);
    }

    public static CelestialHierarchy getHierarchy() {
        return CelestialRegistry.hierarchy();
    }

    public static Optional<CelestialObject> findBodyById(CelestialObjectKey id) {
        return CelestialRegistry.get(id);
    }

    public static List<CelestialObject> getChildren(CelestialObject parent) {
        return parent == null ? List.of() : getChildren(parent.key());
    }

    public static List<CelestialObject> getChildren(CelestialObjectKey parentId) {
        return CelestialRegistry.getChildren(parentId);
    }

    public static Map<CelestialObjectKey, CelestialObject> getAllBodies() {
        return CelestialRegistry.getAllBodies();
    }

    public static CelestialObject root() {
        return CelestialRegistry.getPrimaryRoot();
    }

    public static Optional<CelestialObject> findCurrentStar(int dimensionId) {
        for (DimensionEnum dim : DimensionEnum.values()) {
            if (dim.getId() == dimensionId) {
                return findCurrentStar(dim);
            }
        }
        return getPrimaryStar();
    }

    public static Optional<CelestialObject> findCurrentStar(DimensionEnum dim) {
        return findByDimension(dim).flatMap(body -> findAncestorOfClass(root(), body, CelestialObject.Class.STAR));
    }

    public static Optional<CelestialObject> findCelestialAnchor(DimensionEnum dim) {
        return findByDimension(dim).flatMap(body -> findAncestorOfClass(root(), body, CelestialObject.Class.STAR));
    }

    public static Optional<CelestialObject> getPrimaryStar() {
        return findFirstByClass(root(), CelestialObject.Class.STAR);
    }

    private static Optional<CelestialObject> findAncestorOfClass(CelestialObject current, CelestialObject target,
        CelestialObject.Class objectClass) {
        return findAncestorOfClass(current, target, objectClass, new ArrayList<>());
    }

    private static Optional<CelestialObject> findAncestorOfClass(CelestialObject current, CelestialObject target,
        CelestialObject.Class objectClass, List<CelestialObject> ancestors) {
        if (current == target) {
            for (int i = ancestors.size() - 1; i >= 0; i--) {
                CelestialObject ancestor = ancestors.get(i);
                if (ancestor.objectClass() == objectClass) {
                    return Optional.of(ancestor);
                }
            }
            return Optional.empty();
        }

        for (CelestialObject child : getChildren(current)) {
            ArrayList<CelestialObject> nextAncestors = new ArrayList<>(ancestors);
            nextAncestors.add(current);
            Optional<CelestialObject> found = findAncestorOfClass(child, target, objectClass, nextAncestors);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    private static Optional<CelestialObject> findFirstByClass(CelestialObject current,
        CelestialObject.Class objectClass) {
        if (current.objectClass() == objectClass) {
            return Optional.of(current);
        }
        for (CelestialObject child : getChildren(current)) {
            Optional<CelestialObject> found = findFirstByClass(child, objectClass);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public static CelestialObject findBodyById(CelestialObject root, CelestialObjectKey needle) {
        if (root == null || needle == null) return null;
        CelestialObject found = findBodyByIdRec(root, needle);
        if (found != null) return found;

        CelestialObject dynamic = get(needle).orElse(null);
        if (dynamic == null || dynamic.parentKey() == null) return null;
        return findBodyByIdRec(root, dynamic.parentKey()) != null ? dynamic : null;
    }

    private static CelestialObject findBodyByIdRec(CelestialObject current, CelestialObjectKey needle) {
        for (CelestialObject child : getChildren(current)) {
            if (child.key()
                .equals(needle)) {
                return child;
            }

            CelestialObject found = findBodyByIdRec(child, needle);
            if (found != null) return found;
        }
        return null;
    }

    public static CelestialObject findStar(CelestialObjectKey targetId) {
        return findStar(getPrimaryRoot(), targetId);
    }

    public static CelestialObject findStar(CelestialObject root, CelestialObjectKey targetId) {
        if (root == null || targetId == null) return null;
        CelestialObject target = get(targetId).orElse(null);
        CelestialObject star = findStar(root, target);
        if (star != null || target == null || target.parentKey() == null) return star;
        return findStar(root, target.parentKey());
    }

    public static CelestialObject findStar(CelestialObject root, CelestialObject target) {
        if (root == null || target == null) return null;
        return findStarRec(root, target, null);
    }

    private static CelestialObject findStarRec(CelestialObject current, CelestialObject target,
        CelestialObject currentStar) {
        CelestialObject nextStar = current.objectClass() == CelestialObject.Class.STAR ? current : currentStar;
        if (current == target) return nextStar;
        for (CelestialObject child : getChildren(current)) {
            CelestialObject found = findStarRec(child, target, nextStar);
            if (found != null) return found;
        }
        return null;
    }

    public static CelestialObject findPlanetaryAnchor(CelestialObjectKey targetId) {
        return findPlanetaryAnchor(getPrimaryRoot(), targetId);
    }

    public static CelestialObject findPlanetaryAnchor(CelestialObject root, CelestialObjectKey targetId) {
        if (root == null || targetId == null) return null;
        CelestialObject target = get(targetId).orElse(null);
        CelestialObject anchor = findPlanetaryAnchor(root, target);
        if (anchor != null || target == null || target.parentKey() == null) return anchor;
        return target;
    }

    public static CelestialObject findPlanetaryAnchor(CelestialObject root, CelestialObject target) {
        if (root == null || target == null) return target;
        CelestialObject anchor = findPlanetaryAnchorRec(root, target, null);
        return anchor != null ? anchor : target;
    }

    private static CelestialObject findPlanetaryAnchorRec(CelestialObject current, CelestialObject target,
        CelestialObject currentPlanet) {
        CelestialObject.Class cls = current.objectClass();
        CelestialObject nextPlanet = (cls == CelestialObject.Class.PLANET || cls == CelestialObject.Class.GAS_GIANT)
            ? current
            : currentPlanet;
        if (current == target) return nextPlanet != null ? nextPlanet : current;
        for (CelestialObject child : getChildren(current)) {
            CelestialObject found = findPlanetaryAnchorRec(child, target, nextPlanet);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Returns {@code true} if both bodies share the same planetary anchor
     * (i.e. same planet/gas-giant or both on the same planet's moon system).
     * Used to gate HAMMER planetary transfer handling.
     */
    public static boolean sharesPlanetaryAnchor(CelestialObject root, CelestialObjectKey bodyIdA,
        CelestialObjectKey bodyIdB) {
        if (root == null || bodyIdA == null || bodyIdB == null) return false;
        CelestialObject a = GalaxiaCelestialAPI.findBodyById(root, bodyIdA);
        CelestialObject b = GalaxiaCelestialAPI.findBodyById(root, bodyIdB);
        if (a == null || b == null) return false;
        CelestialObject anchorA = GalaxiaCelestialAPI.findPlanetaryAnchor(root, a);
        CelestialObject anchorB = GalaxiaCelestialAPI.findPlanetaryAnchor(root, b);
        return anchorA != null && anchorA == anchorB;
    }

    /**
     * Returns the current orbital simulation time in OSU (orbital simulation units).
     * Server-side: based on world tick count, converted with 20 OSU/s at 20 TPS.
     */
    public static double currentOrbitalTime() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return 0.0;
        long totalWorldTime = server.getEntityWorld()
            .getTotalWorldTime();
        return totalWorldTime * OrbitalTransferPlanner.OSU_PER_TICK;
    }

}
