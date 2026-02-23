package com.gtnewhorizons.galaxia.modules;

/**
 * Enum for module types
 */
public enum ModuleTypes {

    HUB_3X3(ModuleType.builder("hub_3x3")
        .internalSize(0, 0, 0)
        .wallThickness(0)
        .model("textures/model/modules/hub_3x3/model.obj")
        .texture("textures/model/modules/hub_3x3/texture.png")
        .offset(0, 0, 0)
        .modelBounds(-1.5, 0, -1.5, 1.5, 5, 1.5)
        .build()),

    CAPSULE_3X3(ModuleType.builder("capsule_3x3")
        .internalSize(0, 0, 0)
        .wallThickness(0)
        .model("textures/model/modules/capsule_3x3/model.obj")
        .texture("textures/model/modules/capsule_3x3/texture.png")
        .offset(0, 0, 0)
        .modelBounds(-1.5, 0, -1.5, 1.5, 5, 1.5)
        .build()),

    ;

    public final ModuleType data;

    ModuleTypes(ModuleType data) {
        this.data = data;
    }

    /**
     * Get ID of ENUM
     * 
     * @return ID String
     */
    public String getId() {
        return data.getId();
    }

    /**
     * Get the module type by ID
     * 
     * @param id The ID string
     * @return Module Type defined by ID
     */
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
