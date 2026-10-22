package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.galaxia.api.BlockPos;

/**
 * Server-wide rocket state saved with the world: every known silo (so other silos can pick it as a destination) and
 * every rocket currently in flight
 */
public class RocketWorldData extends WorldSavedData {

    public static final String NAME = "galaxia_rockets";

    /**
     * A silo other silos can fly to
     *
     * @param dimension Dimension it is in
     * @param pos       Controller position
     * @param name      Name the owner gave it
     */
    public record SiloRecord(int dimension, BlockPos pos, String name) {

        public String key() {
            return keyOf(dimension, pos);
        }

        NBTTagCompound serializeNBT() {
            NBTTagCompound tag = pos.toNBT();
            tag.setInteger("dim", dimension);
            tag.setString("name", name);
            return tag;
        }

        static SiloRecord deserializeNBT(NBTTagCompound tag) {
            return new SiloRecord(tag.getInteger("dim"), BlockPos.fromNBT(tag), tag.getString("name"));
        }
    }

    final Map<String, SiloRecord> silos = new LinkedHashMap<>();
    final List<RocketFlight> flights = new ArrayList<>();

    public RocketWorldData(String name) {
        super(name);
    }

    public static String keyOf(int dimension, BlockPos pos) {
        return dimension + ":" + pos.x() + ":" + pos.y() + ":" + pos.z();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        silos.clear();
        NBTTagList siloList = tag.getTagList("silos", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < siloList.tagCount(); i++) {
            SiloRecord record = SiloRecord.deserializeNBT(siloList.getCompoundTagAt(i));
            silos.put(record.key(), record);
        }

        flights.clear();
        NBTTagList flightList = tag.getTagList("flights", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < flightList.tagCount(); i++) {
            flights.add(RocketFlight.deserializeNBT(flightList.getCompoundTagAt(i)));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList siloList = new NBTTagList();
        for (SiloRecord record : silos.values()) siloList.appendTag(record.serializeNBT());
        tag.setTag("silos", siloList);

        NBTTagList flightList = new NBTTagList();
        for (RocketFlight flight : flights) flightList.appendTag(flight.serializeNBT());
        tag.setTag("flights", flightList);
    }
}
