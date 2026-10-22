package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.Comparator;
import java.util.List;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntityModuleAssembler;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.TransitModule;

/**
 * API helper class for sending modules between terminals of a gantry network
 */
public final class GantryAPI {

    private GantryAPI() {}

    /**
     * Puts a module on the rails
     *
     * @param module The rocket module to send
     * @param from   The terminal it enters the network at
     * @param to     The terminal it should leave the network at
     *
     * @return Boolean : True => the module is on its way
     */
    public static boolean send(RocketPartInstance module, TileEntityGantryTerminal from, TileEntityGantryTerminal to) {
        if (from == null || to == null || !from.getMode().accepts) return false;
        boolean accepted = from.acceptModule(new TransitModule(module, to));
        if (accepted) from.sync();
        return accepted;
    }

    /**
     * Picks the assembler that should build a module for a terminal: one that already has it in stock if possible,
     * otherwise the one with the shortest queue. Only terminals that can send modules are considered, so a network
     * with one assembler and a hundred silos only ever looks at that one assembler
     *
     * @param receiver The terminal the module is for
     * @param def      The module type
     *
     * @return The provider terminal, or null if no reachable assembler is connected
     */
    public static TileEntityGantryTerminal findProvider(TileEntityGantryTerminal receiver, IRocketPartDef def) {
        if (receiver == null) return null;
        GantryNetwork network = receiver.getNetwork();
        return network.getProviders()
            .stream()
            .filter(provider -> network.canReach(provider, receiver))
            .min(
                Comparator.comparingInt(
                    (TileEntityGantryTerminal provider) -> provider.getAssembler()
                        .getStockCount(def) > 0 ? 0 : 1)
                    .thenComparingInt(
                        provider -> provider.getAssembler()
                            .getQueueSize()))
            .orElse(null);
    }

    /**
     * Finds an assembler that can take back a module from a terminal
     *
     * @param from The terminal the module leaves from
     *
     * @return The return terminal, or null if none is reachable
     */
    public static TileEntityGantryTerminal findReturnPoint(TileEntityGantryTerminal from) {
        if (from == null) return null;
        GantryNetwork network = from.getNetwork();
        for (TileEntityGantryTerminal terminal : network.getReturnPoints()) {
            if (network.canReach(from, terminal)) return terminal;
        }
        return null;
    }

    /**
     * Every assembler a terminal can draw modules from
     *
     * @param receiver The terminal modules should reach
     *
     * @return Assemblers of reachable provider terminals
     */
    public static List<TileEntityModuleAssembler> findAssemblers(TileEntityGantryTerminal receiver) {
        if (receiver == null) return List.of();
        GantryNetwork network = receiver.getNetwork();
        return network.getProviders()
            .stream()
            .filter(provider -> network.canReach(provider, receiver))
            .map(TileEntityGantryTerminal::getAssembler)
            .toList();
    }
}
