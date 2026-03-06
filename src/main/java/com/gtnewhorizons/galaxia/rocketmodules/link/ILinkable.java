package com.gtnewhorizons.galaxia.rocketmodules.link;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

/**
 * Implement this on any TileEntity that can participate in a link (as master or slave).
 * <p>
 * MASTER — the "source" side (e.g. ModuleAssembler that holds modules)
 * <p>
 * SLAVE — the "consumer" side (e.g. Silo that reads from the master)
 * <p>
 * A single TE can implement both roles simultaneously if needed.
 */
public interface ILinkable {

    /**
     * Name shown in chat when binding, e.g. "Module Assembler".
     */
    String getLinkableName();

    /**
     * @return true if this TE can act as a link master.
     */
    default boolean canBeMaster() {
        return false;
    }

    /**
     * Called when a slave successfully links to this master.
     *
     * @param slave  The slave TileEntity that linked to us.
     * @param player The player who performed the link
     */
    default void onSlaveLinked(TileEntity slave, EntityPlayer player) {}

    /**
     * Called when a slave unlinks from this master.
     *
     * @param slave The slave TileEntity that unlinked.
     */
    default void onSlaveUnlinked(TileEntity slave) {}

    /**
     * @return true if this TE can act as a link slave.
     */
    default boolean canBeSlave() {
        return false;
    }

    /**
     * @return The class of master this slave accepts, used by LinkRegistry
     *         to validate compatibility before linking.
     */
    default Class<? extends TileEntity> acceptedMasterClass() {
        return TileEntity.class;
    }

    /**
     * Stores the master position into this slave's state.
     * Implementation should persist this in NBT and call markDirty().
     *
     * @param pos Position of the master, or null to clear the link.
     */
    void setMasterPos(ChunkCoordinates pos);

    /**
     * @return The stored master position, or null if not linked.
     */
    ChunkCoordinates getMasterPos();

    /**
     * Resolves the actual master TileEntity from the world.
     * Looks up the stored pos in the TE's own world.
     *
     * @return Master TileEntity, or null if not present / wrong type.
     */
    default TileEntity resolveMaster() {
        ChunkCoordinates pos = getMasterPos();
        if (pos == null) return null;
        TileEntity self = (TileEntity) this;
        if (self.getWorldObj() == null) return null;
        TileEntity te = self.getWorldObj()
            .getTileEntity(pos.posX, pos.posY, pos.posZ);
        if (te == null || !acceptedMasterClass().isInstance(te)) return null;
        return te;
    }

    /**
     * @return true if this slave currently has a live master in the world.
     */
    default boolean isLinked() {
        return resolveMaster() != null;
    }
}
