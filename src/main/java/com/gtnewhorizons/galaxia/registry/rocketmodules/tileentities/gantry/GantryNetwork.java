package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;

/**
 * One connected set of gantries. Terminals are the only places modules can enter or leave, so the network keeps them
 * indexed by role: a silo looking for an assembler only scans the terminals that can send modules, never the whole
 * rail graph.
 * <p>
 * Networks are rebuilt lazily: placing or breaking a gantry only invalidates the affected network, and the next query
 * floods it again. Routes are cached per destination until the network changes
 */
public final class GantryNetwork {

    /** Upper bound on gantries in one network, protects against runaway floods */
    public static final int MAX_SIZE = 4096;

    private static final float HORIZONTAL_COST = 1f;
    private static final float VERTICAL_COST = 1.5f;

    private final Set<TileEntityGantry> members = new LinkedHashSet<>();
    private final List<TileEntityGantryTerminal> terminals = new ArrayList<>();
    private final Map<TileEntityGantryTerminal, Map<TileEntityGantry, TileEntityGantry>> routes = new HashMap<>();
    private boolean valid = true;

    private GantryNetwork() {}

    /**
     * Floods the rail graph from a gantry and binds every reached gantry to the new network
     *
     * @param start Any gantry of the network
     *
     * @return The freshly built network
     */
    static GantryNetwork build(TileEntityGantry start) {
        GantryNetwork network = new GantryNetwork();
        Deque<TileEntityGantry> open = new ArrayDeque<>();
        open.add(start);
        network.members.add(start);

        while (!open.isEmpty() && network.members.size() < MAX_SIZE) {
            TileEntityGantry current = open.poll();
            for (TileEntityGantry neighbour : current.getNeighbours()) {
                if (neighbour.isInvalid() || !network.members.add(neighbour)) continue;
                open.add(neighbour);
            }
        }

        for (TileEntityGantry member : network.members) {
            member.bindNetwork(network);
            if (member instanceof TileEntityGantryTerminal terminal) network.terminals.add(terminal);
        }
        return network;
    }

    /** Marks the network stale, members rebuild it on their next query */
    public void invalidate() {
        valid = false;
        routes.clear();
    }

    public boolean isValid() {
        return valid;
    }

    public int size() {
        return members.size();
    }

    public List<TileEntityGantryTerminal> getTerminals() {
        return Collections.unmodifiableList(terminals);
    }

    /** Terminals whose formed Module Assembler can send modules into the network */
    public List<TileEntityGantryTerminal> getProviders() {
        List<TileEntityGantryTerminal> result = new ArrayList<>();
        for (TileEntityGantryTerminal terminal : terminals) {
            TileEntityModuleAssembler assembler = terminal.getAssembler();
            if (terminal.getMode().accepts && assembler != null && assembler.isStructureValid()) result.add(terminal);
        }
        return result;
    }

    /** Terminals whose formed silo can receive modules from the network */
    public List<TileEntityGantryTerminal> getReceivers() {
        List<TileEntityGantryTerminal> result = new ArrayList<>();
        for (TileEntityGantryTerminal terminal : terminals) {
            TileEntitySilo silo = terminal.getSilo();
            if (terminal.getMode().delivers && silo != null && silo.isStructureValid()) result.add(terminal);
        }
        return result;
    }

    /** Terminals whose formed Module Assembler accepts returned modules */
    public List<TileEntityGantryTerminal> getReturnPoints() {
        List<TileEntityGantryTerminal> result = new ArrayList<>();
        for (TileEntityGantryTerminal terminal : terminals) {
            TileEntityModuleAssembler assembler = terminal.getAssembler();
            if (terminal.getMode().delivers && assembler != null && assembler.isStructureValid()) result.add(terminal);
        }
        return result;
    }

    /**
     * Next gantry on the cheapest path towards a terminal
     *
     * @param from        The gantry currently holding the module
     * @param destination The terminal the module is heading to
     *
     * @return The next gantry, {@code from} itself when already there, or null without a route
     */
    public TileEntityGantry nextHop(TileEntityGantry from, TileEntityGantryTerminal destination) {
        if (!members.contains(destination)) return null;
        return routes.computeIfAbsent(destination, this::buildRoutes)
            .get(from);
    }

    public boolean canReach(TileEntityGantry from, TileEntityGantryTerminal destination) {
        return nextHop(from, destination) != null;
    }

    /**
     * Dijkstra outward from the destination, so a single pass gives every member its next hop. Horizontal steps are
     * cheaper than vertical ones, which keeps modules on flat rails where possible
     */
    private Map<TileEntityGantry, TileEntityGantry> buildRoutes(TileEntityGantryTerminal destination) {
        Map<TileEntityGantry, TileEntityGantry> next = new HashMap<>();
        Map<TileEntityGantry, Float> cost = new HashMap<>();
        PriorityQueue<TileEntityGantry> open = new PriorityQueue<>(
            Comparator.comparingDouble(g -> cost.getOrDefault(g, Float.MAX_VALUE)));

        cost.put(destination, 0f);
        next.put(destination, destination);
        open.add(destination);

        while (!open.isEmpty()) {
            TileEntityGantry current = open.poll();
            float base = cost.get(current);
            for (TileEntityGantry neighbour : current.getNeighbours()) {
                if (!members.contains(neighbour)) continue;
                float step = neighbour.yCoord == current.yCoord ? HORIZONTAL_COST : VERTICAL_COST;
                float candidate = base + step;
                if (candidate < cost.getOrDefault(neighbour, Float.MAX_VALUE)) {
                    cost.put(neighbour, candidate);
                    next.put(neighbour, current);
                    // Re-insert to update priority, removal is linear but networks are small
                    open.remove(neighbour);
                    open.add(neighbour);
                }
            }
        }
        return next;
    }
}
