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
import net.minecraft.util.AxisAlignedBB;
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
    private final static int DISPATCH_INTERVAL = 40;
    private int dispatchCooldown = 0;
    private List<int[]> pendingNeighbourCoords = new ArrayList<>();

    List<TileEntityGantry> neighbours = new ArrayList<>();
    private Vec3 currentDirection;
    private float progress = 0f;
    private TransitModule containedTransitModule;
    public int clientModuleId = -1;
    public float clientPrevProgress = 0f;
    public float clientProgress = 0f;
    public List<Vec3> neighbourDirs = new ArrayList<>();
    public boolean isJunction = false;
    public Vec3 facing = null;

    private Vec3 incomingDirection;
    public Vec3 clientIncomingDirection;

    // I beam
    @SideOnly(Side.CLIENT)
    private IModelCustom straightModel;
    @SideOnly(Side.CLIENT)
    private ResourceLocation straightTexture;

    // 45° angle up beam
    @SideOnly(Side.CLIENT)
    private IModelCustom diagonalModel;
    @SideOnly(Side.CLIENT)
    private ResourceLocation diagonalTexture;

    // L beam
    @SideOnly(Side.CLIENT)
    private IModelCustom cornerModel;
    @SideOnly(Side.CLIENT)
    private ResourceLocation cornerTexture;

    // T beam
    @SideOnly(Side.CLIENT)
    private IModelCustom tModel;
    @SideOnly(Side.CLIENT)
    private ResourceLocation tTexture;

    // crossway beam
    @SideOnly(Side.CLIENT)
    private IModelCustom plusModel;
    @SideOnly(Side.CLIENT)
    private ResourceLocation plusTexture;

    @SideOnly(Side.CLIENT)
    private ResourceLocation errorTexture;

    /**
     * Updates the tile entity. In this case, handling current module progression
     * and queue management
     */
    @Override
    public void updateEntity() {
        // Lazy loading for neighbours
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
            // -1 used as "null" as 0 is a valid ID
            if (clientModuleId != -1) {
                // If module, increase progress
                clientPrevProgress = clientProgress;
                clientProgress += SPEED;
                if (clientProgress > 1.0f) {
                    // If above 1, reset
                    clientProgress = 0f;
                    clientPrevProgress = 0f;
                    clientModuleId = -1;
                    currentDirection = null;
                }
            }
            return;
        }

        // Handle dispatch queue
        if (!queue.isEmpty()) {
            // If cooling down, lower cooldown counter
            if (dispatchCooldown > 0) {
                dispatchCooldown--;
            } else if (containedTransitModule == null) {
                // If no contained module, take the next and process
                TransitModule entry = queue.poll();
                containedTransitModule = entry;
                currentDirection = GantryAPI.getDirectionTo(this, entry.destination());
                progress = 0f;
                // Only add cooldown back if a terminal and remaining queue
                if (!queue.isEmpty() && this instanceof TileEntityGantryTerminal) {
                    dispatchCooldown = DISPATCH_INTERVAL;
                } else {
                    dispatchCooldown = 0;
                }
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }

        if (containedTransitModule == null) {
            return;
        }

        // Progress module
        progress += SPEED;

        // If progress finished, hand off to next gantry
        if (progress >= 1.0f) {
            progress = 1.0f;
            handOff();
        }
        markDirty();
    }

    /**
     * Gets the current direction of the gantry
     *
     * @return The current direction
     */
    public Vec3 getDirection() {
        return currentDirection;
    }

    // TODO: ADD MODEL AND TEXTURE FOR GANTRY

    // MODEL AND TEXTURE GETTERS
    @SideOnly(Side.CLIENT)
    public ResourceLocation getStraightTexture() {
        if (straightTexture == null) {
            straightTexture = LocationGalaxia("textures/model/gantry/straight.png");
        }
        return straightTexture;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getStraightModel() {
        if (straightModel == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/gantry/straight.obj");
            straightModel = AdvancedModelLoader.loadModel(loc);
        }
        return straightModel;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getDiagonalTexture() {
        if (diagonalTexture == null) {
            diagonalTexture = LocationGalaxia("textures/model/gantry/diagonal.png");
        }
        return diagonalTexture;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getDiagonalModel() {
        if (diagonalModel == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/gantry/diagonal.obj");
            diagonalModel = AdvancedModelLoader.loadModel(loc);
        }
        return diagonalModel;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getCornerTexture() {
        if (cornerTexture == null) {
            cornerTexture = LocationGalaxia("textures/model/gantry/corner.png");
        }
        return cornerTexture;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getCornerModel() {
        if (cornerModel == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/gantry/corner.obj");
            cornerModel = AdvancedModelLoader.loadModel(loc);
        }
        return cornerModel;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getSemiCrossTexture() {
        if (tTexture == null) {
            tTexture = LocationGalaxia("textures/model/gantry/semicross.png");
        }
        return tTexture;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getSemiCrossModel() {
        if (tModel == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/gantry/semicross.obj");
            tModel = AdvancedModelLoader.loadModel(loc);
        }
        return tModel;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getCrossTexture() {
        if (plusTexture == null) {
            plusTexture = LocationGalaxia("textures/model/gantry/cross.png");
        }
        return plusTexture;
    }

    @SideOnly(Side.CLIENT)
    public IModelCustom getCrossModel() {
        if (plusModel == null) {
            ResourceLocation loc = LocationGalaxia("textures/model/gantry/cross.obj");
            plusModel = AdvancedModelLoader.loadModel(loc);
        }
        return plusModel;
    }

    @SideOnly(Side.CLIENT)
    public ResourceLocation getErrorTexture() {
        if (errorTexture == null) {
            errorTexture = LocationGalaxia("textures/model/gantry/error.png");
        }
        return errorTexture;
    }

    /**
     * Gets an interpolated progress based on the partial tick
     *
     * @param partialTicks The amount through the current tick the world is
     * @return The interpolated progress
     */
    public float getInterpolatedProgress(float partialTicks) {
        return clientPrevProgress + (clientProgress - clientPrevProgress) * partialTicks;
    }

    /**
     * Gets the current progress of the module stored (0 if no module)
     *
     * @return Current progress / 0 if no module
     */
    public float getProgress() {
        return progress;
    }

    /**
     * Gets the list of gantries that count as valid neighbours to this one
     *
     * @return The list of valid gantry neighbours
     */
    public List<TileEntityGantry> getNeighbours() {
        return neighbours;
    }

    /**
     * Sets the current direction of the gantry
     *
     * @param dir The target direction
     */
    public void setDirection(Vec3 dir) {
        currentDirection = dir;
    }

    /**
     * Clears the module from gantry and resets progress
     */
    public void clearModule() {
        progress = 0f;
        clientProgress = 0f;
        clientPrevProgress = 0f;
        clientModuleId = -1;
        containedTransitModule = null;
        currentDirection = null;
        incomingDirection = null;
        clientIncomingDirection = null;
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Ensures the graph is valid by all endpoints being terminals
     *
     * @return Boolean : True => is valid graph
     */
    public boolean checkValidGraph() {
        return GantryAPI.terminatesWithTerminals(worldObj, xCoord, yCoord, zCoord);
    }

    /**
     * Connects another gantry to this one and updates both
     *
     * @param other The other gantry to connect to
     */
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

    /**
     * Update neighbour direction list
     */
    public void updateNeighbourDirs() {
        if (worldObj.isRemote) return;

        // Clear list and go through neighbours getting vectors
        neighbourDirs.clear();
        for (TileEntityGantry neighbour : neighbours) {
            neighbourDirs.add(
                Vec3.createVectorHelper(
                    neighbour.xCoord - xCoord,
                    neighbour.yCoord - yCoord,
                    neighbour.zCoord - zCoord));
        }
        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        return;
    }

    /**
     * Disconnects the gantry from another one and updates both
     *
     * @param other The other gantry to disconnect from
     */
    public void disconnect(TileEntityGantry other) {
        this.neighbours.remove(other);
        other.neighbours.remove(this);
        this.updateNeighbourDirs();
        other.updateNeighbourDirs();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(other.xCoord, other.yCoord, other.zCoord);
        }

    }

    public RocketModule getModule() {
        if (containedTransitModule == null) return null;
        return containedTransitModule.module();
    }

    /**
     * Hands off a module to the next gantry in the path of the module
     */
    public void handOff() {

        // Get next gantry to hand to
        TileEntityGantry next = getNeighbourGantry(currentDirection);
        if (next == null || next == this) {
            // If terminal endpoint, pass to consumer (assembler / silo etc.)
            if (this instanceof TileEntityGantryTerminal teg) {
                teg.passModuleToConsumer();
            }
            clearModule();
            return;
        }

        // If next gantry can take it, pass it on
        if (next.acceptModule(containedTransitModule, currentDirection)) {
            clearModule();
            if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /**
     * Gets the neighbouring gantry based on a given direction
     *
     * @param dir The vector direction to check
     *
     * @return The gantry found or null
     */
    public TileEntityGantry getNeighbourGantry(Vec3 dir) {
        if (dir == null) return null;
        int nx = xCoord + (int) dir.xCoord;
        int ny = yCoord + (int) dir.yCoord;
        int nz = zCoord + (int) dir.zCoord;
        TileEntity te = worldObj.getTileEntity(nx, ny, nz);
        return (te instanceof TileEntityGantry ? (TileEntityGantry) te : null);
    }

    /**
     * Adds a new transit module to the dispatch queue
     *
     * @param transit The transit module to add
     */
    public void enqueueModule(TransitModule transit) {
        queue.addLast(transit);
        markDirty();
    }

    /**
     * Takes an incoming transit module and direction and adds it to the dispatch
     * queue
     *
     * @param transit       The transit module
     * @param fromDirection The direction it came from
     *
     * @return Boolean : True => accepted module
     */
    public boolean acceptModule(TransitModule transit, Vec3 fromDirection) {
        if (worldObj.isRemote) return false;
        if (transit == null) {
            return false;
        }
        this.incomingDirection = fromDirection;

        enqueueModule(transit);
        markDirty();
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;

    }

    // Overloaded method for initial injection with no previous direction
    public boolean acceptModule(TransitModule transit) {
        return acceptModule(transit, null);
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
        NBTTagList list = tag.getTagList("neighbourDirs", NBT.TAG_COMPOUND);
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

        if (incomingDirection != null) {
            tag.setFloat("inDirX", (float) incomingDirection.xCoord);
            tag.setFloat("inDirY", (float) incomingDirection.yCoord);
            tag.setFloat("inDirZ", (float) incomingDirection.zCoord);
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

        // DO NOT call readFromNBT(tag) here — it will clobber xCoord/yCoord/zCoord

        // Re-read neighbourDirs manually
        neighbourDirs.clear();
        NBTTagList list = tag.getTagList("neighbourDirs", NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            neighbourDirs
                .add(Vec3.createVectorHelper(entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")));
        }

        // Module/direction sync
        int incomingId = tag.hasKey("moduleId") ? tag.getInteger("moduleId") : -1;
        if (incomingId != -1 && clientModuleId == -1) {
            clientProgress = 0f;
            clientPrevProgress = 0f;
        }
        clientModuleId = incomingId;

        if (tag.hasKey("dirX")) {
            currentDirection = Vec3
                .createVectorHelper(tag.getFloat("dirX"), tag.getFloat("dirY"), tag.getFloat("dirZ"));
        } else {
            currentDirection = null;
        }

        if (tag.hasKey("inDirX")) {
            clientIncomingDirection = Vec3
                .createVectorHelper(tag.getFloat("inDirX"), tag.getFloat("inDirY"), tag.getFloat("inDirZ"));
        } else {
            clientIncomingDirection = null;
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512 * 512;
    }
}
