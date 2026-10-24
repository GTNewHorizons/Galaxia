package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import net.minecraft.util.EnumChatFormatting;

/**
 * Which way modules may pass through a gantry terminal
 */
public enum TerminalMode {

    /** Both directions, the default */
    BOTH(true, true, EnumChatFormatting.AQUA),
    /** Modules from the attached machine enter the network here */
    INPUT(true, false, EnumChatFormatting.GREEN),
    /** Modules leave the network here into the attached machine */
    OUTPUT(false, true, EnumChatFormatting.GOLD);

    public final boolean accepts;
    public final boolean delivers;
    public final EnumChatFormatting color;

    TerminalMode(boolean accepts, boolean delivers, EnumChatFormatting color) {
        this.accepts = accepts;
        this.delivers = delivers;
        this.color = color;
    }

    public String langKey() {
        return "galaxia.gantry.mode." + name().toLowerCase();
    }

    public TerminalMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
