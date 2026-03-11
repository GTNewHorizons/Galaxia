package com.gtnewhorizons.galaxia.rocketmodules.tileentities.gantry;

import static com.gtnewhorizons.galaxia.utility.GalaxiaAPI.LocationGalaxia;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.common.util.Constants.NBT;

import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketModule;
import com.gtnewhorizons.galaxia.rocketmodules.utility.TransitModule;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntityGantry extends TileEntity {

    private final float SPEED = 0.1f;
    private final Deque<TransitModule> queue = new ArrayDeque<>();
    private final static int DISPATCH_INTERVAL = 20;
    private int dispatchCooldown = 0;
    private List<int[]> pendingNeighbourCoords = new ArrayList<>();

    List<TileEntityGantry> neighbours = new ArrayList<>();
    private Vec3 currentDirection;
    private float progress = 0f;
    private TransitModule containedTransitModule;
    public int clientModuleId = -1;
    public float clientPrevProgress = 0f;
    public float clientProgress = 0f;
    public Vec3 clientPrevDirection = null;
    public List<Vec3> neighbourDirs = new ArrayList<>();

    @SideOnly(Side.CLIENT)
    private IModelCustom model;
    @SideOnly(Side.CLIENT)
    private ResourceLocation texture;

    @Override
    public void updateEntity() {
        if (!pendingNeighbourCoords.isEmpty()) {
            for (int[] coords : pendingNeighbourCoords) {
                TileEntity te = worldObj.getTileEntity(coords[0], coords[1], coords[2]);
                if (te instanceof TileEntityGantry teg) {
                    neighbours.add(teg);
                }
            }
            pendingNeighbourCoords.clear();
        }
        if (worldObj.isRemote) {
            if (clientModuleId != -1) {
                clientPrevProgress = clientProgress;
                clientProgress += SPEED;
                if (clientProgress > 1.0f) {
                    clientPrevDirection = currentDirection;
                    clientProgress = 0f;
                    clientPrevProgress = 0f;
                    clientModuleId = -1;
                    currentDirection = null;
                }
            }
            return;
        }

        if (!queue.isEmpty()) {
            if (dispatchCooldown > 0) {
                dispatchCooldown--;
            } else if (containedTransitModule == null) {
                TransitModule entry = queue.poll();
                containedTransitModule = entry;
                currentDirection = GantryAPI.getDirectionTo(this, entry.destination());
                progress = 0f;
                if (this instanceof TileEntityGantryTerminal) {
                    dispatchCooldown = DISPATCH_INTERVAL;
                } else {
                    dispatchCooldown = 0;
                }
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

            }
        }

        if (containedTransitModule == null) return;

        progress += SPEED;

        if (progress >= 1.0f) {
            progress = 1.0f;
            tryHandOff();
        }
        markDirty();
    }

    public Vec3 getDirection() {
        return currentDirection;
    }

    // TODO: ADD MODEL AND TEXTURE FOR GANTRY

    @SideOnly(Side.CLIENT)
    public ResourceLocation getTexture() {
        if (texture == null) {
            texture = LocationGalaxia("textures/model/gantry/texture.png");
        }
        return texture;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getModel() {
        if (model == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/gantry/model.obj");
            model = AdvancedModelLoader.loadModel(loc);
        }
        return model;
    }

    public float getInterpolatedProgress(float partialTicks) {
        return clientPrevProgress + (clientProgress - clientPrevProgress) * partialTicks;
    }

    public float getProgress() {
        return progress;
    }

    public List<TileEntityGantry> getNeighbours() {
        return neighbours;
    }

    public void setDirection(Vec3 dir) {
        currentDirection = dir;
    }

    public void clearModule() {
        progress = 0f;
        clientProgress = 0f;
        clientPrevProgress = 0f;
        clientModuleId = -1;
        containedTransitModule = null;
        currentDirection = null;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public boolean checkValidGraph() {
        return GantryAPI.terminatesWithTerminals(worldObj, xCoord, yCoord, zCoord);
    }

    public void connect(TileEntityGantry other) {
        this.neighbours.add(other);
        other.neighbours.add(this);
        this.updateNeighbourDirs();
        other.updateNeighbourDirs();
        this.markDirty();
        other.markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(other.xCoord, other.yCoord, other.zCoord);
        }
    }

    public void updateNeighbourDirs() {
        if (worldObj.isRemote) return;

        neighbourDirs.clear();
        for (TileEntityGantry neighbour : neighbours) {
            neighbourDirs.add(
                Vec3.createVectorHelper(
                    neighbour.xCoord - xCoord,
                    neighbour.yCoord - yCoord,
                    neighbour.zCoord - zCoord));
        }
    }

    public void disconnect(TileEntityGantry other) {
        this.neighbours.remove(other);
        other.neighbours.remove(this);

    }

    public RocketModule getModule() {
        if (containedTransitModule == null) return null;
        return containedTransitModule.module();
    }

    // TODO:
    public void tryHandOff() {

        TileEntityGantry next = getNeighbourGantry(currentDirection);
        if (next == null || next == this) {
            if (this instanceof TileEntityGantryTerminal teg) {
                teg.passModuleToConsumer();
            }
            clearModule();
            return;
        }

        if (next.acceptModule(containedTransitModule)) {
            clearModule();
            if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    public TileEntityGantry getNeighbourGantry(Vec3 dir) {
        if (dir == null) return null;
        int nx = xCoord + (int) dir.xCoord;
        int ny = yCoord + (int) dir.yCoord;
        int nz = zCoord + (int) dir.zCoord;
        TileEntity te = worldObj.getTileEntity(nx, ny, nz);
        return (te instanceof TileEntityGantry ? (TileEntityGantry) te : null);
    }

    public void enqueueModule(TransitModule transit) {
        queue.addLast(transit);
        if (dispatchCooldown <= 0) if (this instanceof TileEntityGantryTerminal) {
            dispatchCooldown = DISPATCH_INTERVAL;
        }
        dispatchCooldown = 0;
        markDirty();
    }

    public boolean acceptModule(TransitModule transit) {
        if (worldObj.isRemote) return false;
        if (transit == null) {
            return false;
        }

        enqueueModule(transit);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;

    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList neighbourList = new NBTTagList();
        for (TileEntityGantry neighbour : neighbours) {
            NBTTagCompound neighbourTag = new NBTTagCompound();
            neighbourTag.setInteger("x", neighbour.xCoord);
            neighbourTag.setInteger("y", neighbour.yCoord);
            neighbourTag.setInteger("z", neighbour.zCoord);
            neighbourList.appendTag(neighbourTag);
        }
        tag.setTag("neighbours", neighbourList);

        NBTTagList dirList = new NBTTagList();
        for (Vec3 dir : neighbourDirs) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setDouble("x", dir.xCoord);
            entry.setDouble("y", dir.yCoord);
            entry.setDouble("z", dir.zCoord);
            dirList.appendTag(entry);
        }
        tag.setTag("neighbourDirs", dirList);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        NBTTagList neighbourList = tag.getTagList("neighbours", NBT.TAG_COMPOUND);
        pendingNeighbourCoords = new ArrayList<>();

        for (int i = 0; i < neighbourList.tagCount(); i++) {
            NBTTagCompound entry = neighbourList.getCompoundTagAt(i);
            pendingNeighbourCoords
                .add(new int[] { entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"), });
        }

        neighbourDirs.clear();
        NBTTagList list = tag.getTagList("neighbourDirs", NBT.TAG_LIST);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            neighbourDirs
                .add(Vec3.createVectorHelper(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);

        tag.setBoolean("hasModule", containedTransitModule != null);
        tag.setFloat("progress", progress);

        if (currentDirection != null) {
            tag.setFloat("dirX", (float) currentDirection.xCoord);
            tag.setFloat("dirY", (float) currentDirection.yCoord);
            tag.setFloat("dirZ", (float) currentDirection.zCoord);
        }
        if (containedTransitModule != null) {
            tag.setInteger(
                "moduleId",
                containedTransitModule.module()
                    .getId());
        }

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        readFromNBT(tag);

        int incomingId = tag.hasKey("moduleId") ? tag.getInteger("moduleId") : null;

        if (incomingId != -1 && clientModuleId == -1) {
            clientProgress = 0f;
            clientPrevProgress = 0f;
            clientPrevDirection = null;

        }

        clientModuleId = incomingId;

        if (tag.hasKey("dirX")) {
            currentDirection = Vec3
                .createVectorHelper(tag.getFloat("dirX"), tag.getFloat("dirY"), tag.getFloat("dirZ"));
        } else {
            currentDirection = null;
        }

    }
}
