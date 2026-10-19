package com.gtnewhorizons.galaxia.registry.celestial.station.devices;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.Widget;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationLookup;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;

import cofh.api.energy.IEnergyReceiver;
import cpw.mods.fml.common.Optional;
import gregtech.api.interfaces.tileentity.IColoredTileEntity;
import gregtech.api.interfaces.tileentity.IEnergyConnected;

/**
 * A machine that keeps a station room liveable. Placed in a room's wall or inside it, it takes energy from cables or
 * draws it from the station's stores, and works while it also has what it consumes; the room only counts the machines
 * that work
 */
// these APIs exist in GT6. don't change to gregtech_nh !
@Optional.InterfaceList({
    @Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IEnergyConnected", modid = "gregtech"),
    @Optional.Interface(iface = "cofh.api.energy.IEnergyReceiver", modid = "CoFHCore"),
    @Optional.Interface(iface = "gregtech.api.interfaces.tileentity.IColoredTileEntity", modid = "gregtech") })
public abstract class TileStationDevice extends TileEntity
    implements IGuiHolder<PosGuiData>, IEnergyConnected, IEnergyReceiver, IColoredTileEntity {

    /** Ticks of work the energy buffer holds */
    private static final int BUFFER_TICKS = 40;
    /** Ticks the share of working time mostly reflects */
    private static final int UPTIME_WINDOW = 100;
    /** Ticks between two refills from the station's storage */
    private static final int SUPPLY_INTERVAL = 20;

    static final int WIDTH = 176;
    static final int CONTENT_Y = 76;

    private final StationLookup station = new StationLookup();
    private long energy;
    private boolean working;
    private String status = "galaxia.gui.station_device.not_in_station";
    private double uptime;
    private boolean roomNotified;

    /** Registry name of the machine's block, which also names its screen */
    protected abstract String blockName();

    /** Energy the machine uses every tick it works */
    public abstract long getEnergyUsage();

    /** Lang key of what the machine lacks to work, null while it has everything but energy */
    @Nullable
    protected abstract String missingInput();

    /** Uses up one tick's worth of what the machine consumes */
    protected abstract void consumeInput();

    /** Takes what the machine consumes from the station's storage */
    protected void supply(Station storage) {}

    /** Adds the machine's own widgets below the shared ones, at {@link #CONTENT_Y} */
    protected void addDeviceWidgets(ModularPanel panel, PanelSyncManager syncManager) {}

    /** Height of the machine's own widgets, the inventory goes below them */
    protected int contentHeight() {
        return 18;
    }

    /** True while the machine does its job for the room */
    public boolean isWorking() {
        return working;
    }

    public long getEnergyCapacity() {
        return getEnergyUsage() * BUFFER_TICKS;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        TileStation room = station.room(worldObj, xCoord, yCoord, zCoord);
        if (room == null) {
            roomNotified = false;
            setState(false, "galaxia.gui.station_device.not_in_station");
            return;
        }
        if (!roomNotified) {
            // A machine placed into a formed room makes it look at its blocks again
            room.markStructureDirty();
            roomNotified = true;
        }
        // Energy from cables comes in on its own, the station only makes up for what is missing
        StationGraph graph = room.getGraph();
        if (graph != null && energy < getEnergyCapacity()) {
            energy += graph.drawEnergy(Math.min(getEnergyCapacity() - energy, getEnergyUsage() * 2));
        }
        if (worldObj.getTotalWorldTime() % SUPPLY_INTERVAL == 0) {
            Station storage = station.storage(worldObj, xCoord, yCoord, zCoord);
            if (storage != null) supply(storage);
        }
        String missing = missingInput();
        if (missing != null) {
            setState(false, missing);
        } else if (energy < getEnergyUsage()) {
            setState(false, "galaxia.gui.station_device.no_energy");
        } else {
            energy -= getEnergyUsage();
            consumeInput();
            setState(true, "galaxia.gui.station_device.working");
        }
    }

    private void setState(boolean nowWorking, String nowStatus) {
        working = nowWorking;
        status = nowStatus;
        // A running average, so the share moves from the first tick on
        uptime += ((nowWorking ? 1.0 : 0.0) - uptime) / UPTIME_WINDOW;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        BooleanSyncValue workingSync = new BooleanSyncValue(() -> working);
        StringSyncValue statusSync = new StringSyncValue(() -> status);
        LongSyncValue energySync = new LongSyncValue(() -> energy);
        DoubleSyncValue uptimeSync = new DoubleSyncValue(() -> uptime);
        syncManager.syncValue("working", workingSync);
        syncManager.syncValue("status", statusSync);
        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("uptime", uptimeSync);

        int inventoryY = CONTENT_Y + contentHeight() + (contentHeight() > 0 ? 6 : 0);
        ModularPanel panel = GalaxiaGuiStyle.panel("galaxia:station_device", WIDTH, inventoryY + 83);
        panel.child(
            GalaxiaGuiStyle.title("tile." + blockName() + ".name")
                .pos(8, 7));
        panel.child(
            new Widget<>().size(6)
                .pos(WIDTH - 14, 9)
                .background(
                    new DynamicDrawable(
                        () -> workingSync.getBoolValue() ? EnumTextures.GUI_LED_ON.getImage()
                            : EnumTextures.GUI_LED_OFF.getImage())));
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal(statusSync.getValue()),
                    () -> workingSync.getBoolValue() ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.WARN,
                    1f)
                .pos(8, 20)
                .name("station_device.status"));

        // Energy from the station, with the use on hover
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.gui.station_device.energy"),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    1f)
                .pos(8, 34));
        panel.child(
            GalaxiaGuiStyle
                .bar(() -> (double) energySync.getLongValue() / getEnergyCapacity(), () -> GalaxiaGuiStyle.WARN)
                .size(WIDTH - 16, 6)
                .pos(8, 44)
                .tooltipBuilder(
                    t -> t.addLine(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocalFormatted(
                                "galaxia.gui.station_device.energy.tooltip",
                                String.format("%,d", energySync.getLongValue()),
                                String.format("%,d", getEnergyCapacity()),
                                String.format("%,d", getEnergyUsage())))))
                .tooltipAutoUpdate(true));

        // Progress: how much of the time the machine got everything it needs
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocal("galaxia.gui.station_device.progress"),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    1f)
                .pos(8, 54));
        panel.child(
            GalaxiaGuiStyle.bar(uptimeSync::getDoubleValue, () -> GalaxiaGuiStyle.GOOD)
                .size(WIDTH - 16, 6)
                .pos(8, 64));

        addDeviceWidgets(panel, syncManager);
        panel.child(
            GalaxiaGuiStyle.playerInventory()
                .pos(7, inventoryY));
        return panel;
    }

    // region Energy from cables, the same way the oxygen machines take it

    private boolean useEU() {
        return !ConfigMachines.energy.useRF;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public long injectEnergyUnits(ForgeDirection direction, long voltage, long amperage) {
        if (!useEU()) return 0;
        long added = Math.min(voltage * amperage, getEnergyCapacity() - energy);
        if (added <= 0) return 0;
        energy += added;
        return amperage;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean inputEnergyFrom(ForgeDirection side) {
        return useEU();
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public boolean outputsEnergyTo(ForgeDirection side) {
        return false;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public byte getColorization() {
        return -1;
    }

    @Override
    @Optional.Method(modid = "gregtech")
    public byte setColorization(byte colorization) {
        return -1;
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (useEU()) return 0;
        int ratio = Math.max(1, ConfigMachines.energy.rfPerEU);
        long added = Math.min((long) maxReceive / ratio, getEnergyCapacity() - energy);
        if (added <= 0) return 0;
        if (!simulate) energy += added;
        return (int) (added * ratio);
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public int getEnergyStored(ForgeDirection from) {
        if (useEU()) return 0;
        return (int) Math.min(energy * Math.max(1, ConfigMachines.energy.rfPerEU), Integer.MAX_VALUE);
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public int getMaxEnergyStored(ForgeDirection from) {
        if (useEU()) return 0;
        return (int) Math.min(getEnergyCapacity() * Math.max(1, ConfigMachines.energy.rfPerEU), Integer.MAX_VALUE);
    }

    @Override
    @Optional.Method(modid = "CoFHCore")
    public boolean canConnectEnergy(ForgeDirection from) {
        return !useEU();
    }

    // endregion

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setLong("energy", energy);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        energy = tag.getLong("energy");
    }
}
