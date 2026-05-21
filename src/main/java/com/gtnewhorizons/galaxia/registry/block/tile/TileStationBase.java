package com.gtnewhorizons.galaxia.registry.block.tile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.compat.structure.ArbitraryShapeDefinition;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.registry.block.GalaxiaBootableMultiblock;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.interfaces.IGraphListener;

public abstract class TileStationBase<T extends GalaxiaBootableMultiblock<T>> extends GalaxiaBootableMultiblock<T>
    implements IGuiHolder<PosGuiData>, IGraphListener {

    public static final List<Block> BASE_VALID_BLOCKS = List.of(
        GalaxiaBlocksEnum.SPACE_STATION_BLOCK.get(),
        GalaxiaBlocksEnum.SPACE_STATION_PANEL.get(),
        GalaxiaBlocksEnum.SPACE_STATION_GLASS.get());

    protected @Nullable StationGraph graph;
    protected List<BlockPos> airlocks = new ArrayList<>();
    protected BlockPos here;

    private boolean oxygenated = false;
    protected int oxygenLevel = 100;

    public TileStationBase() {
        super();
    }

    @Override
    public Block getControllerBlock() {
        return GalaxiaBlocksEnum.STATION_CONTROLLER.get();
    }

    @Override
    protected boolean checkStructure() {
        return isValidDimension(worldObj) && super.checkStructure();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.here = new BlockPos(xCoord, yCoord, zCoord);

        for (BlockPos airlock : airlocks) {
            if (!(airlock.getTE(worldObj) instanceof TileEntityAirlock teLock)) continue;

            if (!teLock.trackStationController(this.here)) {
                Galaxia.LOG.warn(
                    "Airlock at %s cannot track more than %d controllers",
                    airlock,
                    TileEntityAirlock.MAX_CONNECTIONS);
            }
        }
    }

    @Override
    public void onStructureDisformed() {
        super.onStructureDisformed();
        for (BlockPos airlock : airlocks) {
            if (!(airlock.getTE(worldObj) instanceof TileEntityAirlock teLock)) continue;

            teLock.untrackStationController(this.here);
        }
        airlocks.clear();
        oxygenated = false;
    }

    public void registerAirlock(int x, int y, int z) {
        BlockPos airlock = new BlockPos(x, y, z);
        if (!this.airlocks.contains(airlock)) {
            this.airlocks.add(airlock);
        }
    }

    public boolean isValidDimension(World world) {
        CelestialObjectId objectId = GalaxiaCelestialAPI.getObjectFromDimension(world.provider.dimensionId);
        return objectId != CelestialObjectId.INVALID;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("airlocks", BlockPos.listToNBT(airlocks));
        nbt.setBoolean("oxygenated", oxygenated);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.here = new BlockPos(xCoord, yCoord, zCoord);
        if (nbt.hasKey("airlocks")) {
            this.airlocks = BlockPos.listFromNBT(nbt.getTagList("airlocks", Constants.NBT.TAG_COMPOUND));
        }
        if (nbt.hasKey("oxygenated")) {
            this.oxygenated = nbt.getBoolean("oxygenated");
        }
    }

    public abstract int getSearchRadius();

    @Override
    public void invalidate() {
        super.invalidate();
        for (BlockPos b : airlocks) {
            TileEntityAirlock airlock = b.getTE(worldObj);
            if (airlock == null) continue;

            airlock.untrackStationController(this.here);
        }
    }

    public boolean isInside(int x, int y, int z) {
        int searchRadius = getSearchRadius() * 2;
        if (Math.max(Math.abs(x - xCoord), Math.max(Math.abs(y - yCoord), Math.abs(z - zCoord))) > searchRadius)
            return false;

        if (getStructureDefinition() instanceof ArbitraryShapeDefinition<?>def) {
            return def.isInsideStructure(x, y, z);
        }

        boolean top = false, bottom = false;
        for (int d = 1; d <= searchRadius; d++) {
            if (getStructureDefinition().isContainedInStructure("main", x, y + d, z)) top = true;
            if (getStructureDefinition().isContainedInStructure("main", x, y - d, z)) bottom = true;
            if (top && bottom) return true;
        }

        return false;
    }

    public void tick() {
        oxygenated = checkOxygenLevels(new HashSet<>());
    }

    public boolean isOxygenated() {
        return oxygenated;
    }

    private boolean checkOxygenLevels(Set<BlockPos> visited) {
        if (!structureValid) return false;

        // Prevent cycles
        if (!visited.add(here)) {
            return oxygenLevel > 0;
        }

        boolean hasOpenAirlock = false;
        boolean foundOxygenPath = false;

        for (BlockPos airlockPos : airlocks) {
            TileEntityAirlock airlock = airlockPos.getTE(worldObj);
            if (airlock == null) continue;
            if (!airlock.isOpen()) continue;

            // Open to outside = immediate failure
            if (airlock.isExternalConnection()) return false;

            hasOpenAirlock = true;

            for (BlockPos otherPos : airlock.getStationControllers()) {
                if (otherPos.equals(here)) continue;

                TileStationBase other = otherPos.getTE(worldObj);
                if (other == null) continue;

                if (other.checkOxygenLevels(visited)) {
                    foundOxygenPath = true;
                }
            }
        }

        // Case 1: no open doors → sealed
        if (!hasOpenAirlock) {
            return oxygenLevel > 0;
        }

        // Case 2: doors open → rely on network
        return foundOxygenPath;
    }

}
