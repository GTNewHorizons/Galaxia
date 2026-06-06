package com.gtnewhorizons.galaxia.registry.items.tether;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

public final class KineticTetherState {

    private static final Map<UUID, TetherData> SERVER_DATA = new HashMap<>();

    private static final TetherData CLIENT_DATA = new TetherData();

    private KineticTetherState() {}

    public static TetherData get(EntityPlayer player) {
        UUID uuid = player.getUniqueID();

        TetherData data = SERVER_DATA.get(uuid);

        if (data == null) {
            data = new TetherData();
            SERVER_DATA.put(uuid, data);
        }

        return data;
    }

    public static TetherData getClient() {
        return CLIENT_DATA;
    }
}
