package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.rocketmodules.utility.TransitModule;

/**
 * API helper class for graph pathing and injection of modules
 */
public final class GantryAPI {

    // Costs and limit for dfs/dijkstra
    private static final float HORIZONTAL_COST = 1f;
    private static final float DIAGONAL_COST = 1.5f;
    private static int MAX_CHAIN_SIZE = 256;

    // Valid connection vectors
    public static final Vec3[] CHECK_OFFSETS = { Vec3.createVectorHelper(1, 0, 0), Vec3.createVectorHelper(-1, 0, 0),
        Vec3.createVectorHelper(0, 0, 1), Vec3.createVectorHelper(0, 0, -1), Vec3.createVectorHelper(1, 1, 0),
        Vec3.createVectorHelper(1, -1, 0), Vec3.createVectorHelper(-1, 1, 0), Vec3.createVectorHelper(-1, -1, 0),

        Vec3.createVectorHelper(0, 1, 1), Vec3.createVectorHelper(0, -1, 1), Vec3.createVectorHelper(0, 1, -1),
        Vec3.createVectorHelper(0, -1, -1) };

    /**
     * Checks that a graph of gantries always terminates with actual gantry
     * terminals
     *
     * @param world The world the TileEntity is in
     * @param x     X coordinate of TileEntity
     * @param y     Y coordinate of TileEntity
     * @param z     Z coordinate of TileEntity
     */
    public static boolean terminatesWithTerminals(World world, int x, int y, int z) {
        TileEntity tegantry = world.getTileEntity(x, y, z);
        if (tegantry == null || !(tegantry instanceof TileEntityGantry)) {
            return false;
        }

        List<TileEntityGantry> endpoints = new ArrayList<>();
        TileEntityGantry start = (TileEntityGantry) tegantry;
        // Get all endpoints with DFS
        dfsEndpoints(start, start, new HashSet<>(), endpoints, 0);
        if (endpoints.size() == 1) return false;
        // Ensure all are terminal instances
        for (TileEntityGantry instance : endpoints) {
            if (!isTerminal(instance)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Injects a module into a gantry
     *
     * @param module          The rocket module to inject
     * @param moduleAssembler The assembler for this journey
     * @param silo            The silo for this journey
     * @param returning       Determines which consumer is the start point (True =>
     *                        Silo start)
     */
    public static void injectModule(RocketModule module, TileEntityModuleAssembler moduleAssembler, TileEntitySilo silo,
        boolean returning) {
        TileEntityGantryTerminal start, dest;
        // Determine start point and accept
        if (returning) {
            start = silo.getGantryTerminal();
            dest = moduleAssembler.getGantryTerminal();
        } else {
            start = moduleAssembler.getGantryTerminal();
            dest = silo.getGantryTerminal();
        }

        start.acceptModule(new TransitModule(module, dest));
        start.sync();
    }

    /**
     * Finds endpoint terminals of the gantry graph
     *
     * @param start The start terminal of the graph
     *
     * @return The list of terminals at endpoints
     */
    public static List<TileEntityGantryTerminal> findEndpointTerminals(TileEntityGantryTerminal start) {
        List<TileEntityGantry> ends = new ArrayList<>();
        dfsEndpoints(start, start, new HashSet<>(), ends, 0);
        return ends.stream()
            .filter(TileEntityGantryTerminal.class::isInstance)
            .map(TileEntityGantryTerminal.class::cast)
            .collect(Collectors.toList());
    }

    /**
     * Depth-First Search (DFS) to find all endpoints of a gantry graph
     *
     * @param current   The current gantry being checked
     * @param start     The original start gantry
     * @param visited   The set of all visited nodes on the graph
     * @param endpoints The list of endpoints found so far
     * @param depth     The current depth of this search relative to original search
     *                  being 0
     */
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

    /**
     * Performs a Dijkstra Search algorithm to find the shortest path from a gantry
     * to a terminal along a grapy, prioritising horizontal paths first
     *
     * @see <a href="https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm"Dijkstra's
     *      Algorithm</a>
     *
     * @param start The start point to search from
     * @param end   The ending terminal to search for
     *
     * @return The list of gantries in the shortest path, null if end not found
     */
    private static List<TileEntityGantry> dijkstraPath(TileEntityGantry start, TileEntityGantryTerminal end) {
        Map<TileEntityGantry, Float> dist = new HashMap<>();
        Map<TileEntityGantry, TileEntityGantry> prev = new HashMap<>();
        PriorityQueue<TileEntityGantry> queue = new PriorityQueue<>(
            Comparator.comparingDouble(n -> dist.getOrDefault(n, Float.MAX_VALUE)));

        dist.put(start, 0f);
        queue.add(start);
        int visited = 0;

        while (!queue.isEmpty()) {
            TileEntityGantry current = queue.poll();
            if (++visited > MAX_CHAIN_SIZE) break;
            if (current == end) break;

            float currentDist = dist.getOrDefault(current, Float.MAX_VALUE);

            for (TileEntityGantry neighbour : current.getNeighbours()) {
                float stepCost = isHorizontalStep(current, neighbour) ? HORIZONTAL_COST : DIAGONAL_COST;
                float newDist = currentDist + stepCost;

                if (newDist < dist.getOrDefault(neighbour, Float.MAX_VALUE)) {
                    dist.put(neighbour, newDist);
                    prev.put(neighbour, current);
                    // Update priority in queue
                    queue.remove(neighbour);
                    queue.add(neighbour);
                }
            }
        }

        if (!prev.containsKey(end) && start != end) return null;
        List<TileEntityGantry> path = new ArrayList<>();
        for (TileEntityGantry step = end; step != null; step = prev.get(step)) {
            path.add(0, step);
        }
        return path.isEmpty() ? null : path;
    }

    /**
     * Helper method to determine if a step is horizontal
     *
     * @param from The gantry at start of step
     * @param to   The gantry stepping to
     *
     * @return Boolean : True => is horizontal step
     */
    private static boolean isHorizontalStep(TileEntityGantry from, TileEntityGantry to) {
        return (to.yCoord - from.yCoord) == 0 ? true : false;

    }

    /**
     * Gets the direction vector from one gantry to another
     *
     * @param start The starting gantry
     * @param end   The ending gantry
     *
     * @return The vector from start to end
     */
    public static Vec3 getDirectionTo(TileEntityGantry start, TileEntityGantryTerminal end) {

        List<TileEntityGantry> nodes = dijkstraPath(start, end);
        if (nodes.size() == 1) {
            if (nodes.get(0) instanceof TileEntityGantryTerminal terminal) {
                if (terminal.getSilo() != null) {
                    TileEntitySilo silo = terminal.getSilo();
                    return Vec3.createVectorHelper(
                        silo.xCoord - start.xCoord,
                        silo.yCoord - start.yCoord,
                        silo.zCoord - start.zCoord);
                }

                if (terminal.getAssembler() != null) {
                    TileEntityModuleAssembler assembler = terminal.getAssembler();
                    return Vec3.createVectorHelper(
                        assembler.xCoord - start.xCoord,
                        assembler.yCoord - start.yCoord,
                        assembler.zCoord - start.zCoord);

                }
            }
            return Vec3.createVectorHelper(0, 0, 0);
        }
        TileEntityGantry next = nodes.get(1);
        return Vec3
            .createVectorHelper(next.xCoord - start.xCoord, next.yCoord - start.yCoord, next.zCoord - start.zCoord);
    }

    /**
     * Determines if a gantry is an endpoint in its graph
     *
     * @param gantry The gantry to check
     *
     * @return Boolean : True => is an endpoint
     */
    private static boolean isEndpoint(TileEntityGantry gantry) {
        return gantry.neighbours.size() == 1;
    }

    /**
     * Determines if a gantry is a terminal
     *
     * @param gantry The gantry to check
     *
     * @return Boolean : True => is a terminal
     */
    public static boolean isTerminal(TileEntityGantry gantry) {
        return gantry instanceof TileEntityGantryTerminal;
    }
}
