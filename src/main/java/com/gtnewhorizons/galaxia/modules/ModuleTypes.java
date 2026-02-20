package com.gtnewhorizons.galaxia.modules;

public enum ModuleTypes {

    HUB_3X3(ModuleType.builder("hub_3x3")
        .internalSize(3, 3, 3)
        .wallThickness(1)
        .model("models/modules/hub_3x3/hub_3x3.obj")
        .build()),

    ;

    public final ModuleType data;

    ModuleTypes(ModuleType data) {
        this.data = data;
    }

    public String getId() {
        return data.getId();
    }

    public static ModuleType byId(String id) {
        if (id == null) return null;
        for (ModuleTypes m : values()) {
            if (m.getId()
                .equals(id)) {
                return m.data;
            }
        }
        return null;
    }
}
