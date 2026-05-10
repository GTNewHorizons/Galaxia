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

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.galaxia.api.IOxygenHandler;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.BeamEffectPacket;
import com.gtnewhorizons.galaxia.registry.items.baubles.ItemOxygenTank;

import baubles.api.BaublesApi;
import cpw.mods.fml.common.network.NetworkRegistry;

public class TileEntityOxygenPylon extends TileEntityGalaxiaMachine implements IOxygenHandler {

    protected FluidTank oxygenTank = new FluidTank(10000);

    /** Default pylon service radius in blocks. Exposed here as a named constant per spec. */
    public static final int PYLON_RADIUS = 9;

    /** Packet broadcast radius: observers at the edge of the pylon area should see beams too. */
    private static final double BEAM_BROADCAST_RADIUS = PYLON_RADIUS + 4;

    /** UUIDs of players who were in range during the previous cycle. */
    private final Set<UUID> previousCyclePlayers = new HashSet<>();

    /** Number of players charged during the last cycle, synced to GUI. */
    private int lastChargedCount;

    @Override
    protected double getMaxEnergyBuffer() {
        return ConfigMachines.pylon.maxEnergyBuffer;
    }

    @Override
    protected int getMaxOxygenBuffer() {
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
        if (storedOxygen <= 0) return;

        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(
            xCoord - PYLON_RADIUS,
            yCoord - PYLON_RADIUS,
            zCoord - PYLON_RADIUS,
            xCoord + PYLON_RADIUS + 1,
            yCoord + PYLON_RADIUS + 1,
            zCoord + PYLON_RADIUS + 1);

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

            // How much oxygen can be put in tanks
            int canPush = Math.min(oxygenPerPlayer, storedOxygen);
            if (canPush <= 0) break;

            int pushed = pushOxygenToPlayer(player, canPush);
            if (pushed > 0) {
                storedOxygen -= pushed;
                charged++;
                active = true;
            }

            if (!previousCyclePlayers.contains(id)) {
                sendBeamPacket((EntityPlayerMP) player);
            }
        }

        previousCyclePlayers.clear();
        previousCyclePlayers.addAll(currentCyclePlayers);
        lastChargedCount = charged;
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
            player.posY + player.getEyeHeight(),
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
    public FluidTank getOxygenTank() {
        return oxygenTank;
    }

    @Override
    protected void writeMachineNBT(NBTTagCompound tag) {
        tag.setInteger("lastChargedCount", lastChargedCount);
        writeOxygenToNBT(tag);
    }

    @Override
    protected void readMachineNBT(NBTTagCompound tag) {
        lastChargedCount = tag.getInteger("lastChargedCount");
        readOxygenFromNBT(tag);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        IntSyncValue energySync = new IntSyncValue(() -> (int) Math.min(storedEnergy, Integer.MAX_VALUE), v -> {});
        IntSyncValue maxEnergySync = new IntSyncValue(
            () -> (int) Math.min(getMaxEnergyBuffer(), Integer.MAX_VALUE),
            v -> {});
        IntSyncValue oxygenSync = new IntSyncValue(() -> storedOxygen, v -> {});
        IntSyncValue maxOxygenSync = new IntSyncValue(this::getMaxOxygenBuffer, v -> {});
        IntSyncValue chargedSync = new IntSyncValue(() -> lastChargedCount, v -> {});

        syncManager.syncValue("energy", energySync);
        syncManager.syncValue("maxEnergy", maxEnergySync);
        syncManager.syncValue("oxygen", oxygenSync);
        syncManager.syncValue("maxOxygen", maxOxygenSync);
        syncManager.syncValue("charged", chargedSync);

        String unit = energyUnitLabel();

        return ModularPanel.defaultPanel("oxygen_pylon", 176, 120)
            .child(
                IKey.lang("galaxia.gui.oxygen_pylon.title")
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
                        IKey.str("Radius: " + PYLON_RADIUS + " blocks")
                            .asWidget()
                            .height(12)
                            .marginBottom(2))
                    .child(
                        IKey.dynamic(() -> "Players charged: " + chargedSync.getIntValue())
                            .asWidget()
                            .height(12)
                            .marginBottom(2))
                    .child(
                        IKey.dynamic(() -> active ? "\u00a7aActive" : "\u00a77Idle")
                            .asWidget()
                            .height(12)));
    }
}
