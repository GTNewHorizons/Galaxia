package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import static com.gtnewhorizons.galaxia.core.Galaxia.LOG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class GantryAPI {

    public static final Vec3[] CHECK_OFFSETS = { Vec3.createVectorHelper(1, 0, 0), Vec3.createVectorHelper(-1, 0, 0),
        Vec3.createVectorHelper(0, 1, 0), Vec3.createVectorHelper(0, -1, 0), Vec3.createVectorHelper(0, 0, 1),
        Vec3.createVectorHelper(0, 0, -1), Vec3.createVectorHelper(1, 1, 0), Vec3.createVectorHelper(1, -1, 0),
        Vec3.createVectorHelper(-1, 1, 0), Vec3.createVectorHelper(-1, -1, 0),

        Vec3.createVectorHelper(0, 1, 1), Vec3.createVectorHelper(0, -1, 1), Vec3.createVectorHelper(0, 1, -1),
        Vec3.createVectorHelper(0, -1, -1) };
    private static int MAX_CHAIN_SIZE = 256;

    public static boolean terminatesWithTerminals(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !(te instanceof TileEntityGantry)) {
            return false;
        }

        List<TileEntityGantry> endpoints = new ArrayList<>();
        TileEntityGantry start = (TileEntityGantry) te;
        dfsEndpoints(start, start, new HashSet<>(), endpoints, 0);
        LOG.info(endpoints.get(0).zCoord);
        if (endpoints.size() == 1) return false;
        for (TileEntityGantry teg : endpoints) {
            LOG.info(teg);
            if (!isTerminal(teg)) {
                return false;
            }
        }
        return true;
    }

    private static void dfsEndpoints(TileEntityGantry current, TileEntityGantry start, Set<TileEntityGantry> visited,
        List<TileEntityGantry> endpoints, int depth) {
        visited.add(current);

        if (depth >= MAX_CHAIN_SIZE) {
            endpoints.add(current);
            return;
        }

        if (current.neighbours.isEmpty() || isEndpoint(current)) {
            LOG.info("Adding new endpoint");
            endpoints.add(current);
            if (current != start) return;
        }

        for (TileEntityGantry neighbour : current.neighbours) {
            if (!visited.contains(neighbour)) {
                dfsEndpoints(neighbour, start, visited, endpoints, depth + 1);
            }
        }
    }

    private static List<TileEntityGantry> findPath(TileEntityGantryTerminal start, TileEntityGantryTerminal end) {
        List<TileEntityGantry> path = new ArrayList<>();
        Set<TileEntityGantry> visited = new HashSet<>();

        if (dfsPath(start, end, visited, path, 0)) {
            return path;
        }

        return Collections.emptyList();
    }

    private static boolean dfsPath(TileEntityGantry current, TileEntityGantryTerminal end,
        Set<TileEntityGantry> visited, List<TileEntityGantry> path, int depth) {
        visited.add(current);
        path.add(current);

        if (depth >= MAX_CHAIN_SIZE) {
            path.add(current);
            return false;
        }

        if (current == end) {
            return true;
        }

        for (TileEntityGantry neighbour : current.neighbours) {
            if (!visited.contains(neighbour)) {
                if (dfsPath(neighbour, end, visited, path, depth + 1)) {
                    return true;
                }
            }
        }

        path.remove(path.size() - 1);
        return false;
    }

    private static boolean isEndpoint(TileEntityGantry teg) {
        return teg.neighbours.size() == 1;
    }

    public static boolean isTerminal(TileEntityGantry te) {
        return te instanceof TileEntityGantryTerminal;
    }
}
