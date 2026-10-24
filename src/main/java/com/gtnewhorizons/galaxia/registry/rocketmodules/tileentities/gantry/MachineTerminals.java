package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import java.util.ArrayList;
import java.util.List;

/**
 * Gantry terminals built into one multiblock. A structure can have several terminal spots, only the ones rails are
 * attached to matter
 */
public final class MachineTerminals {

    private final List<TileEntityGantryTerminal> terminals = new ArrayList<>();

    /** Called before every structure check, the check adds back what is still there */
    public void clear() {
        terminals.clear();
    }

    public void add(TileEntityGantryTerminal terminal) {
        if (!terminals.contains(terminal)) terminals.add(terminal);
    }

    public List<TileEntityGantryTerminal> all() {
        return terminals.stream()
            .filter(terminal -> !terminal.isInvalid())
            .toList();
    }

    /**
     * Picks the terminal to use for sending and receiving
     *
     * @return The first terminal with rails attached, any terminal otherwise, or null without terminals
     */
    public TileEntityGantryTerminal best() {
        TileEntityGantryTerminal fallback = null;
        for (TileEntityGantryTerminal terminal : terminals) {
            if (terminal.isInvalid()) continue;
            if (terminal.getNetwork()
                .size() > 1) return terminal;
            if (fallback == null) fallback = terminal;
        }
        return fallback;
    }

    /**
     * Picks the terminal that can reach another one
     *
     * @param target The terminal modules should go to
     *
     * @return A terminal on the same rails as the target, or null
     */
    public TileEntityGantryTerminal receivingFrom(TileEntityGantryTerminal target) {
        for (TileEntityGantryTerminal terminal : terminals) {
            if (!terminal.isInvalid() && terminal.getMode().delivers
                && terminal.getNetwork()
                    .canReach(terminal, target))
                return terminal;
        }
        return null;
    }
}
