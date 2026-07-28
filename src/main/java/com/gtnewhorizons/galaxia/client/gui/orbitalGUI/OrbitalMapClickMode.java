package com.gtnewhorizons.galaxia.client.gui.orbitalGUI;

import com.google.gson.JsonElement;

enum OrbitalMapClickMode {

    HIERARCHY,
    FOLLOW;

    static OrbitalMapClickMode fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return HIERARCHY;
        try {
            return valueOf(element.getAsString());
        } catch (IllegalArgumentException e) {
            return HIERARCHY;
        }
    }
}
