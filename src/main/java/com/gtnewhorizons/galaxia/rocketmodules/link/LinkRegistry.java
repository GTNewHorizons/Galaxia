package com.gtnewhorizons.galaxia.rocketmodules.link;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.galaxia.core.CommonProxy;

/**
 * Central registry that tracks which master-class → slave-class pairs are compatible.
 * <p>
 * Register your pairs during mod init in {@link CommonProxy} before any linking tool is used.
 * <p>
 * Example:
 *
 * <pre>
 * LinkRegistry.register(TileEntityModuleAssembler.class, TileEntitySilo.class);
 * </pre>
 *
 * When the LinkingTool tries to connect two TileEntities it calls
 * {@link #areCompatible(TileEntity, TileEntity)} which validates the pair using both
 * this registry AND the slave's own {@code acceptedMasterClass()}.
 */
public class LinkRegistry {

    private static final Map<Class<? extends TileEntity>, Set<Class<? extends TileEntity>>> SLAVE_TO_MASTERS = new HashMap<>();

    private LinkRegistry() {}

    /**
     * Register a valid master-slave pair.
     *
     * @param masterClass The TileEntity class that acts as master.
     * @param slaveClass  The TileEntity class that acts as slave.
     */
    public static void register(Class<? extends TileEntity> masterClass, Class<? extends TileEntity> slaveClass) {
        SLAVE_TO_MASTERS.computeIfAbsent(slaveClass, k -> new HashSet<>())
            .add(masterClass);
    }

    /**
     * Checks whether {@code master} and {@code slave} form a valid registered pair,
     * and that the slave's own {@code acceptedMasterClass()} agrees.
     *
     * @param master Candidate master TileEntity (must implement ILinkable).
     * @param slave  Candidate slave TileEntity (must implement ILinkable).
     * @return true if the link is allowed.
     */
    public static boolean areCompatible(TileEntity master, TileEntity slave) {
        if (!(master instanceof ILinkable lMaster) || !(slave instanceof ILinkable lSlave)) return false;

        if (!lMaster.canBeMaster() || !lSlave.canBeSlave()) return false;

        Set<Class<? extends TileEntity>> acceptedMasters = SLAVE_TO_MASTERS.get(slave.getClass());
        if (acceptedMasters == null) return false;

        boolean registryMatch = acceptedMasters.stream()
            .anyMatch(mc -> mc.isInstance(master));
        if (!registryMatch) return false;

        Class<? extends TileEntity> slaveAccepted = lSlave.acceptedMasterClass();
        return slaveAccepted.equals(TileEntity.class) || slaveAccepted.isInstance(master);
    }
}
