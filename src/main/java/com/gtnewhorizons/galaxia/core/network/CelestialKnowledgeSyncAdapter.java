package com.gtnewhorizons.galaxia.core.network;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

public interface CelestialKnowledgeSyncAdapter {

    CelestialKnowledgeSyncType type();

    void write(ByteBuf buf, UUID teamId);

    CelestialKnowledgeSyncPayload read(ByteBuf buf);
}
