package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class GantryAPI {

    private static int MAX_CHAIN_SIZE = 256;

    public static boolean terminatesWithTerminals(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !(te instanceof TileEntityGantry)) {
            return false;
        }

        List<TileEntityGantry> endpoints = new ArrayList<>();
        TileEntityGantry start = (TileEntityGantry) te;
        dfsEndpoints(start, new HashSet<>(), endpoints, 0);
        if (endpoints.size() == 1) return false;
        for (TileEntityGantry teg : endpoints) {
            if (isTerminal(teg)) {
                return false;
            }
        }
        return true;
    }

    private static void dfsEndpoints(TileEntityGantry current, Set<TileEntityGantry> visited,
        List<TileEntityGantry> endpoints, int depth) {
        visited.add(current);

        if (depth >= MAX_CHAIN_SIZE) {
            endpoints.add(current);
            return;
        }

        if (current.neighbours.isEmpty() || isEndpoint(current)) {
            endpoints.add(current);
            return;
        }

        for (TileEntityGantry neighbour : current.neighbours) {
            if (!visited.contains(neighbour)) {
                dfsEndpoints(neighbour, visited, endpoints, depth + 1);
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
