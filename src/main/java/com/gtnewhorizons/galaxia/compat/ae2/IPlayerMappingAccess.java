package com.gtnewhorizons.galaxia.compat.ae2;

import appeng.core.worlddata.IWorldPlayerMapping;

/**
 * Gives access to AE2's map from its own player ids to UUIDs, which also covers offline players
 */
public interface IPlayerMappingAccess {

    IWorldPlayerMapping galaxia$getPlayerMapping();
}
