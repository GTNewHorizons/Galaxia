package com.gtnewhorizons.galaxia.registry.celestial.station;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;

public abstract class TileStationBase<T extends GalaxiaBootableMultiblock<T>> extends GalaxiaBootableMultiblock<T>
    implements IGuiHolder<PosGuiData>, IGraphListener {

    protected @Nullable StationGraph graph;
    protected List<BlockPos> airlocks = new ArrayList<>();
    protected BlockPos here;

    @Getter
    private boolean sealed = false;
    private boolean sealedDirty = true;

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

        markSealedDirty();
    }

    @Override
    public void onStructureDisformed() {
        super.onStructureDisformed();
        for (BlockPos airlock : airlocks) {
            if (!(airlock.getTE(worldObj) instanceof TileEntityAirlock teLock)) continue;

            teLock.untrackStationController(this.here);
        }
        airlocks.clear();
        sealed = false;
        markSealedDirty();
    }

    public void markSealedDirty() {
        sealedDirty = true;
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
        nbt.setBoolean("sealed", sealed);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.here = new BlockPos(xCoord, yCoord, zCoord);
        if (nbt.hasKey("airlocks")) {
            this.airlocks = BlockPos.listFromNBT(nbt.getTagList("airlocks", Constants.NBT.TAG_COMPOUND));
        }
        if (nbt.hasKey("sealed")) {
            this.sealed = nbt.getBoolean("sealed");
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
        if (!structureValid) return;
        if (sealedDirty) {
            recomputeNetworkSeal();
        }
    }

    private void recomputeNetworkSeal() {
        Set<TileStationBase<?>> component = new ObjectOpenHashSet<>();
        Deque<TileStationBase<?>> queue = new ArrayDeque<>();
        component.add(this);
        queue.add(this);

        boolean breached = false;

        while (!queue.isEmpty()) {
            TileStationBase<?> room = queue.poll();

            for (BlockPos airlockPos : room.airlocks) {
                TileEntityAirlock airlock = airlockPos.getTE(worldObj);
                if (airlock == null || !airlock.isOpen()) continue;

                if (airlock.isExternalConnection()) {
                    breached = true;
                    // Don't short-circuit — keep traversing so we find and
                    // update every room in the component.
                    continue;
                }

                for (BlockPos neighborPos : airlock.getStationControllers()) {
                    if (neighborPos.equals(room.here)) continue;
                    TileStationBase<?> neighbor = neighborPos.getTE(worldObj);
                    if (neighbor == null || !neighbor.structureValid) continue;
                    if (component.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }

        boolean newSealed = !breached;
        for (TileStationBase<?> room : component) {
            room.sealed = newSealed;
            room.sealedDirty = false;
        }
    }
}
