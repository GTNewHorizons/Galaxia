package com.gtnewhorizons.galaxia.registry.block.tile.machine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSapling;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenCollectorGUI;

public class TileEntityOxygenCollector extends TileEntityGalaxiaMachine {

    private static final int LEAF_RESCAN_INTERVAL = 200;

    protected FluidTank oxygenTank;

    public int cachedLeafCount;
    private int leafRescanTimer;

    public TileEntityOxygenCollector() {
        this.oxygenTank = new FluidTank(getMaxOxygenBuffer());
    }

    @Override
    public FluidTank getOxygenTank() {
        return oxygenTank;
    }

    @Override
    public double getMaxEnergyBuffer() {
        return ConfigMachines.collector.maxEnergyBuffer;
    }

    @Override
    public int getMaxOxygenBuffer() {
        return ConfigMachines.collector.maxOxygenBuffer;
    }

    @Override
    protected double getEuPerOperation() {
        return ConfigMachines.collector.euPerOperation;
    }

    @Override
    protected int getWorkIntervalTicks() {
        return ConfigMachines.collector.ticksPerOperation;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;

        leafRescanTimer++;
        if (leafRescanTimer >= LEAF_RESCAN_INTERVAL) {
            leafRescanTimer = 0;
            cachedLeafCount = scanLeaves();
        }

        super.updateEntity();
    }

    @Override
    protected void doWork() {
        if (cachedLeafCount == 0) return;

        int generated = cachedLeafCount * ConfigMachines.collector.oxygenPerLeaf;
        int added = fillOxygen(generated, true);

        if (added > 0) {
            active = true;
        }
    }

    private int scanLeaves() {
        int radius = ConfigMachines.collector.scanRadius;
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int bx = xCoord + dx;
                    int by = yCoord + dy;
                    int bz = zCoord + dz;
                    Block block = worldObj.getBlock(bx, by, bz);
                    if (block == null || block.isAir(worldObj, bx, by, bz)) continue;
                    if (block.isLeaves(worldObj, bx, by, bz) || block instanceof BlockSapling) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    protected void writeMachineNBT(NBTTagCompound tag) {
        tag.setInteger("cachedLeafCount", cachedLeafCount);
        tag.setInteger("leafRescanTimer", leafRescanTimer);
    }

    @Override
    protected void readMachineNBT(NBTTagCompound tag) {
        cachedLeafCount = tag.getInteger("cachedLeafCount");
        leafRescanTimer = tag.getInteger("leafRescanTimer");
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return OxygenCollectorGUI.build(this, guiData, syncManager);
    }
}
