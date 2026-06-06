package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public record CelestialHierarchy(Map<CelestialObjectId, CelestialObject> bodiesById,
    Map<CelestialObjectId, List<CelestialObject>> childrenByParentId, List<CelestialObject> roots) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Map<CelestialObjectId, CelestialObject> bodiesById = new HashMap<>();
        private Map<CelestialObjectId, List<CelestialObject>> childrenByParentId = new HashMap<>();
        private List<CelestialObject> roots = new ArrayList<>();

        private Builder() {}

        public Builder add(@Nonnull List<CelestialObject> bodies) {
            for (CelestialObject body : bodies) {
                this.add(body);
            }

            return this;
        }

        public Builder add(@Nonnull CelestialObject body) {
            bodiesById.put(body.id(), body);
            if (body.parentId() != null) {
                childrenByParentId.computeIfAbsent(body.parentId(), k -> new ArrayList<>())
                    .add(body);
            } else {
                roots.add(body);
                List<CelestialObject> childs = childrenByParentId.get(body.id());
                if (childs != null) {
                    for (CelestialObject childReg : childs) {
                        add(childReg);
                    }
                }
            }

            return this;
        }

        public CelestialHierarchy build() {
            return new CelestialHierarchy(bodiesById, childrenByParentId, roots);
        }
    }
}
