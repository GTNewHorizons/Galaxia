package com.gtnewhorizons.galaxia.registry.celestial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

public record CelestialHierarchy(Map<CelestialObjectKey, CelestialObject> bodiesById,
    Map<CelestialObjectKey, List<CelestialObject>> childrenByParentId, List<CelestialObject> roots) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Map<CelestialObjectKey, CelestialObject> bodiesById = new HashMap<>();
        private Map<CelestialObjectKey, List<CelestialObject>> childrenByParentId = new HashMap<>();
        private List<CelestialObject> roots = new ArrayList<>();

        private Builder() {}

        public Builder add(@Nonnull List<CelestialObject> bodies) {
            for (CelestialObject body : bodies) {
                this.add(body);
            }

            return this;
        }

        public Builder add(@Nonnull CelestialObject body) {
            bodiesById.put(body.key(), body);
            if (body.parentKey() != null) {
                childrenByParentId.computeIfAbsent(body.parentKey(), k -> new ArrayList<>())
                    .add(body);
            } else {
                roots.add(body);
                List<CelestialObject> childs = childrenByParentId.get(body.key());
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
