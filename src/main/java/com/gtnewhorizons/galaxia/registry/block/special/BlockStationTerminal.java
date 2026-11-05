package com.gtnewhorizons.galaxia.registry.block.special;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.api.GalaxiaAPI;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.gui.StationGuiFactory;

/**
 * Access point to the whole station it stands in. Any number can be placed, they all show and change the same station
 */
public class BlockStationTerminal extends Block {

    public BlockStationTerminal() {
        super(Material.iron);
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 1);
        setBlockName("station_terminal");
        setBlockTextureName("galaxia:space_station/station_terminal");
        setCreativeTab(Galaxia.creativeTab);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        Station station = stationAround(world, x, y, z);
        if (station == null) {
            player.addChatComponentMessage(new ChatComponentTranslation("galaxia.chat.station_terminal.no_station"));
            return true;
        }
        StationGuiFactory.INSTANCE.open((EntityPlayerMP) player, station.assetId);
        return true;
    }

    /**
     * The station whose rooms contain a position
     */
    public static Station stationAround(World world, int x, int y, int z) {
        TileStation room = GalaxiaAPI.getStationAround(world, world.provider.dimensionId, x, y, z);
        if (room == null) return null;
        StationGraph graph = room.getGraph();
        TileStation controller = graph != null ? graph.getController() : room;
        return controller.getBackingStation();
    }
}
