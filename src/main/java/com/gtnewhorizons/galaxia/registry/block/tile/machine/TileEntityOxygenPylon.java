package com.gtnewhorizons.galaxia.registry.block.tile.machine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.api.IOxygenHandler;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.BeamEffectPacket;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenPylonGUI;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.network.NetworkRegistry;

public class TileEntityOxygenPylon extends TileEntityGalaxiaMachine implements IOxygenHandler {

    protected FluidTank oxygenTank;

    public static final int PYLON_RADIUS = 9;
    private static final double BEAM_BROADCAST_RADIUS = PYLON_RADIUS + 4;

    private final Set<UUID> previousCyclePlayers = new HashSet<>();
    public int lastChargedCount;

    public TileEntityOxygenPylon() {
        this.oxygenTank = new FluidTank(getMaxOxygenBuffer());
    }

    @Override
    public FluidTank getOxygenTank() {
        return oxygenTank;
    }

    @Override
    public double getMaxEnergyBuffer() {
        return ConfigMachines.pylon.maxEnergyBuffer;
    }

    @Override
    public int getMaxOxygenBuffer() {
        return ConfigMachines.pylon.maxOxygenBuffer;
    }

    @Override
    protected double getEuPerOperation() {
        return ConfigMachines.pylon.euPerOperation;
    }

    @Override
    protected int getWorkIntervalTicks() {
        return ConfigMachines.pylon.ticksPerOperation;
    }

    @Override
    protected void doWork() {
        if (getStoredOxygen() <= 0) {
            previousCyclePlayers.clear();
            lastChargedCount = 0;
            return;
        }

        AxisAlignedBB area = getRangeAABB();

        List<EntityPlayer> playersInRange = worldObj.getEntitiesWithinAABB(EntityPlayer.class, area);

        if (playersInRange.isEmpty()) {
            previousCyclePlayers.clear();
            lastChargedCount = 0;
            return;
        }

        int oxygenPerPlayer = ConfigMachines.pylon.oxygenPerPlayerPerCycle;
        int charged = 0;
        Set<UUID> currentCyclePlayers = new HashSet<>();

        for (EntityPlayer player : playersInRange) {
            UUID id = player.getUniqueID();
            currentCyclePlayers.add(id);

            int canPush = Math.min(oxygenPerPlayer, getStoredOxygen());
            if (canPush <= 0) break;

            int pushed = pushOxygenToPlayer(player, canPush);

            if (pushed > 0) {
                drainOxygen(pushed, true);
                charged++;
                active = true;

                if (player instanceof EntityPlayerMP mp) {
                    sendBeamPacket(mp);
                }
            }
        }

        previousCyclePlayers.clear();
        previousCyclePlayers.addAll(currentCyclePlayers);
        lastChargedCount = charged;
    }

    public AxisAlignedBB getRangeAABB() {
        return AxisAlignedBB.getBoundingBox(
            xCoord - PYLON_RADIUS,
            yCoord - PYLON_RADIUS,
            zCoord - PYLON_RADIUS,
            xCoord + PYLON_RADIUS + 1,
            yCoord + PYLON_RADIUS + 1,
            zCoord + PYLON_RADIUS + 1);
    }

    private int pushOxygenToPlayer(EntityPlayer player, int amount) {
        var baubles = BaublesApi.getBaubles(player);
        if (baubles == null) return 0;

        int remaining = amount;
        for (int slot : Galaxia.oxygenSlots) {
            if (remaining <= 0) break;
            var stack = baubles.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemOxygenTank tankItem)) continue;

            int current = tankItem.getCurrentOxygen(stack);
            int max = tankItem.getMaxOxygen();
            int space = max - current;
            if (space <= 0) continue;

            int fill = Math.min(remaining, space);
            tankItem.fillTank(stack, fill);
            remaining -= fill;
        }
        return amount - remaining;
    }

    private void sendBeamPacket(EntityPlayerMP player) {
        BeamEffectPacket packet = new BeamEffectPacket(
            xCoord,
            yCoord,
            zCoord,
            player.posX,
            player.posY + player.getEyeHeight() / 2,
            player.posZ);

        Galaxia.GALAXIA_NETWORK.sendToAllAround(
            packet,
            new NetworkRegistry.TargetPoint(
                worldObj.provider.dimensionId,
                xCoord + 0.5,
                yCoord + 0.5,
                zCoord + 0.5,
                BEAM_BROADCAST_RADIUS));
    }

    @Override
    protected void writeMachineNBT(NBTTagCompound tag) {
        tag.setInteger("lastChargedCount", lastChargedCount);
    }

    @Override
    protected void readMachineNBT(NBTTagCompound tag) {
        lastChargedCount = tag.getInteger("lastChargedCount");
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return OxygenPylonGUI.build(this, guiData, syncManager);
    }
}
