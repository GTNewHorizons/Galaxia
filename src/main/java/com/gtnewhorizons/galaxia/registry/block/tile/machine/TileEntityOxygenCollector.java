package com.gtnewhorizons.galaxia.registry.block.tile.machine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSapling;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;

public class TileEntityOxygenCollector extends TileEntityGalaxiaMachine {

    private static final int LEAF_RESCAN_INTERVAL = 200;

    protected FluidTank oxygenTank;

    private int cachedLeafCount;
    private int leafRescanTimer;

    public TileEntityOxygenCollector() {
        this.oxygenTank = new FluidTank(getMaxOxygenBuffer());
    }

    @Override
    public FluidTank getOxygenTank() {
        return oxygenTank;
    }

    @Override
    protected double getMaxEnergyBuffer() {
        return ConfigMachines.collector.maxEnergyBuffer;
    }

    @Override
    protected int getMaxOxygenBuffer() {
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
        IntSyncValue energySync = new IntSyncValue(() -> (int) Math.min(storedEnergy, Integer.MAX_VALUE), _ -> {});
        IntSyncValue maxEnergySync = new IntSyncValue(
            () -> (int) Math.min(getMaxEnergyBuffer(), Integer.MAX_VALUE),
            _ -> {});
        IntSyncValue oxygenSync = new IntSyncValue(this::getStoredOxygen, _ -> {});
        IntSyncValue maxOxygenSync = new IntSyncValue(this::getMaxOxygenBuffer, _ -> {});
        IntSyncValue leafSync = new IntSyncValue(() -> cachedLeafCount, _ -> {});

        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("maxEnergy", maxEnergySync);
        syncManager.syncValue("oxygen", oxygenSync);
        syncManager.syncValue("maxOxygen", maxOxygenSync);
        syncManager.syncValue("leaves", leafSync);

        String unit = energyUnitLabel();

        return ModularPanel.defaultPanel("oxygen_collector", 176, 120)
            .child(
                IKey.lang("galaxia.gui.oxygen_collector.title")
                    .asWidget()
                    .top(6)
                    .left(8))
            .child(
                Flow.column()
                    .top(20)
                    .left(8)
                    .right(8)
                    .height(90)
                    .child(
                        IKey.dynamic(() -> unit + ": " + energySync.getIntValue() + " / " + maxEnergySync.getIntValue())
                            .asWidget()
                            .height(12)
                            .marginBottom(2))
                    .child(
                        IKey.dynamic(() -> "O2: " + oxygenSync.getIntValue() + " / " + maxOxygenSync.getIntValue())
                            .asWidget()
                            .height(12)
                            .marginBottom(2))
                    .child(
                        IKey.dynamic(() -> "Leaves in range: " + leafSync.getIntValue())
                            .asWidget()
                            .height(12)
                            .marginBottom(2))
                    .child(
                        IKey.dynamic(() -> active ? "§aGenerating" : "§7Idle")
                            .asWidget()
                            .height(12)));
    }
}
