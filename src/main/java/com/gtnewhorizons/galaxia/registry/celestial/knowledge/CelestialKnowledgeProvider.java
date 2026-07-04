package com.gtnewhorizons.galaxia.registry.celestial.knowledge;

/**
 * Server-side owner for one family of team knowledge.
 */
public interface CelestialKnowledgeProvider extends CelestialDiscoveryProvider {

    default void clear() {}
}
