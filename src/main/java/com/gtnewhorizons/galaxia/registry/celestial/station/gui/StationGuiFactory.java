package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.AbstractUIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.utils.Platform;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Opens the station terminal for a station by its asset id, so the same screen works from a terminal block and from
 * the starmap without any distance limit. Only the team owning the station may open it
 */
public final class StationGuiFactory extends AbstractUIFactory<StationGuiFactory.Data> {

    public static final StationGuiFactory INSTANCE = new StationGuiFactory();

    private StationGuiFactory() {
        super("galaxia:station_terminal");
        GuiManager.registerFactory(this);
    }

    /** Loads the class so the factory registers on both sides */
    public static void init() {}

    /** Gui data naming the station the screen shows */
    public static final class Data extends GuiData {

        private final CelestialAsset.ID station;

        public Data(@NotNull EntityPlayer player, CelestialAsset.ID station) {
            super(player);
            this.station = station;
        }

        public CelestialAsset.ID getStation() {
            return station;
        }
    }

    public void open(EntityPlayerMP player, CelestialAsset.ID station) {
        GuiManager.open(this, new Data(player, station), player);
    }

    @SideOnly(Side.CLIENT)
    public void openClient(CelestialAsset.ID station) {
        GuiManager.openFromClient(this, new Data(Platform.getClientPlayer(), station));
    }

    @Override
    public @NotNull IGuiHolder<Data> getGuiHolder(Data data) {
        return StationTerminalUI.INSTANCE;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, Data data) {
        if (player.worldObj.isRemote) return true;
        if (!(CelestialAssetStore.findAsset(data.getStation()) instanceof Station)) return false;
        UUID team = GTTeamsCompat.getTeam(player);
        return team != null && CelestialAssetStore.isOwnedBy(team, data.getStation());
    }

    @Override
    public void writeGuiData(Data data, PacketBuffer buffer) {
        UUID id = data.getStation()
            .id();
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
    }

    @Override
    public @NotNull Data readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new Data(player, CelestialAsset.ID.from(new UUID(buffer.readLong(), buffer.readLong())));
    }
}
