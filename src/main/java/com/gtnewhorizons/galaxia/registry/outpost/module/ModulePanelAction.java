package com.gtnewhorizons.galaxia.registry.outpost.module;

public enum ModulePanelAction {

    CONFIG("Configure"),
    UPGRADE("Upgrade"),
    COPY_MODULE("Copy Module");

    private final String label;

    ModulePanelAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
