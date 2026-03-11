package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.rocketmodules.utility.TransitModule;

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
        if (endpoints.size() == 1) return false;
        for (TileEntityGantry teg : endpoints) {
            if (!isTerminal(teg)) {
                return false;
            }
        }
        return true;
    }

    public static void injectModule(RocketModule module, TileEntityModuleAssembler ma, TileEntitySilo silo,
        boolean returning) {
        TileEntityGantryTerminal start, dest;
        if (returning) {
            start = silo.getGantryTerminal();
            dest = ma.getGantryTerminal();
        } else {
            start = ma.getGantryTerminal();
            dest = silo.getGantryTerminal();
        }

        start.acceptModule(new TransitModule(module, dest));
        start.sync();
    }

    public static void findPathUpdateGantries(TileEntityGantryTerminal start, TileEntityGantryTerminal end) {
        List<TileEntityGantry> nodes = findPath(start, end);
        for (int i = 0; i < nodes.size() - 1; i++) {
            TileEntityGantry current = nodes.get(i);
            TileEntityGantry next = nodes.get(i + 1);
            current.setDirection(
                Vec3.createVectorHelper(
                    next.xCoord - current.xCoord,
                    next.yCoord - current.yCoord,
                    next.zCoord - current.zCoord));
        }
    }

    public static List<TileEntityGantryTerminal> findEndpointTerminals(TileEntityGantryTerminal start) {
        List<TileEntityGantry> ends = new ArrayList<>();
        dfsEndpoints(start, start, new HashSet<>(), ends, 0);
        return ends.stream()
            .filter(TileEntityGantryTerminal.class::isInstance)
            .map(TileEntityGantryTerminal.class::cast)
            .collect(Collectors.toList());
    }

    private static void dfsEndpoints(TileEntityGantry current, TileEntityGantry start, Set<TileEntityGantry> visited,
        List<TileEntityGantry> endpoints, int depth) {
        visited.add(current);

        if (depth >= MAX_CHAIN_SIZE) {
            endpoints.add(current);
            return;
        }

        if (current.neighbours.isEmpty() || isEndpoint(current)) {
            endpoints.add(current);
            if (current != start) return;
        }

        for (TileEntityGantry neighbour : current.neighbours) {
            if (!visited.contains(neighbour)) {
                dfsEndpoints(neighbour, start, visited, endpoints, depth + 1);
            }
        }
    }

    private static List<TileEntityGantry> findPath(TileEntityGantry start, TileEntityGantryTerminal end) {
        List<TileEntityGantry> path = new ArrayList<>();
        Set<TileEntityGantry> visited = new HashSet<>();

        if (dfsPath(start, end, visited, path, 0)) {
            return path;
        }

        return Collections.emptyList();
    }

    public static Vec3 getDirectionTo(TileEntityGantry start, TileEntityGantryTerminal end) {

        List<TileEntityGantry> nodes = findPath(start, end);
        if (nodes.size() == 1) {
            if (nodes.get(0) instanceof TileEntityGantryTerminal tegt) {
                if (tegt.getSilo() != null) {
                    TileEntitySilo s = tegt.getSilo();
                    return Vec3
                        .createVectorHelper(s.xCoord - start.xCoord, s.yCoord - start.yCoord, s.zCoord - start.zCoord);
                }

                if (tegt.getAssembler() != null) {
                    TileEntityModuleAssembler a = tegt.getAssembler();
                    return Vec3
                        .createVectorHelper(a.xCoord - start.xCoord, a.yCoord - start.yCoord, a.zCoord - start.zCoord);

                }
            }
            return Vec3.createVectorHelper(0, 0, 0);
        }
        TileEntityGantry next = nodes.get(1);
        return Vec3
            .createVectorHelper(next.xCoord - start.xCoord, next.yCoord - start.yCoord, next.zCoord - start.zCoord);
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
