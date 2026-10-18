package com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

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

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A physical node. The world Rocket manifest owns module custody, movement and route claims. */
public class TileEntityGantry extends TileEntity {

    public static final double MOVEMENT_PER_TICK = 0.2;
    private UUID nodeId = UUID.randomUUID();
    final List<TileEntityGantry> neighbours = new ArrayList<>();
    private final Map<UUID, Neighbour> savedNeighbours = new LinkedHashMap<>();
    public final List<Vec3> neighbourDirs = new ArrayList<>();
    private Vec3 currentDirection;
    public Vec3 clientIncomingDirection;
    public int clientModuleId = -1;
    public float clientPrevProgress;
    public float clientProgress;

    private record Neighbour(UUID id, int x, int y, int z) {}

    public UUID nodeId() {
        return nodeId;
    }

    public int dimension() {
        return worldObj.provider.dimensionId;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null) return;
        if (worldObj.isRemote) {
            clientPrevProgress = clientProgress;
            return;
        }
        GantryAPI.tickNode(
            Galaxia.proxy.serverRuntime()
                .rockets(),
            this);
    }

    /** Resolves only loaded, exact neighbor identities and retains unresolved links for later ticks. */
    public List<TileEntityGantry> getNeighbours() {
        if (worldObj == null) return List.copyOf(neighbours);
        for (Neighbour link : savedNeighbours.values()) {
            if (!worldObj.blockExists(link.x(), link.y(), link.z())) continue;
            TileEntity tile = worldObj.getTileEntity(link.x(), link.y(), link.z());
            if (tile instanceof TileEntityGantry gantry && gantry.nodeId()
                .equals(link.id()) && !neighbours.contains(gantry)) {
                neighbours.add(gantry);
            }
        }
        return neighbours.stream()
            .filter(
                next -> !next.isInvalid() && next.getWorldObj() == worldObj
                    && worldObj.blockExists(next.xCoord, next.yCoord, next.zCoord)
                    && worldObj.getTileEntity(next.xCoord, next.yCoord, next.zCoord) == next)
            .toList();
    }

    public void connect(TileEntityGantry other) {
        if (other == this) return;
        neighbours.removeIf(
            previous -> previous != other && previous.xCoord == other.xCoord
                && previous.yCoord == other.yCoord
                && previous.zCoord == other.zCoord);
        savedNeighbours.values()
            .removeIf(
                previous -> !previous.id()
                    .equals(other.nodeId()) && previous.x() == other.xCoord
                    && previous.y() == other.yCoord
                    && previous.z() == other.zCoord);
        if (!neighbours.contains(other)) neighbours.add(other);
        if (!other.neighbours.contains(this)) other.neighbours.add(this);
        savedNeighbours.put(other.nodeId(), new Neighbour(other.nodeId(), other.xCoord, other.yCoord, other.zCoord));
        other.savedNeighbours.put(nodeId, new Neighbour(nodeId, xCoord, yCoord, zCoord));
        updateNeighbourDirs();
        other.updateNeighbourDirs();
    }

    public void disconnect(TileEntityGantry other) {
        neighbours.remove(other);
        other.neighbours.remove(this);
        savedNeighbours.remove(other.nodeId());
        other.savedNeighbours.remove(nodeId);
        updateNeighbourDirs();
        other.updateNeighbourDirs();
    }

    public void updateNeighbourDirs() {
        if (worldObj != null && worldObj.isRemote) return;
        neighbourDirs.clear();
        for (TileEntityGantry next : getNeighbours()) {
            neighbourDirs
                .add(Vec3.createVectorHelper(next.xCoord - xCoord, next.yCoord - yCoord, next.zCoord - zCoord));
        }
        syncProjection();
    }

    public boolean checkValidGraph() {
        return worldObj != null && GantryAPI.terminatesWithTerminals(worldObj, xCoord, yCoord, zCoord);
    }

    @Nullable
    public RocketPartInstance getModule() {
        if (worldObj == null || worldObj.isRemote) return null;
        var module = GantryAPI.heldModule(
            Galaxia.proxy.serverRuntime()
                .rockets(),
            nodeId);
        return module == null ? null : module.part();
    }

    public Vec3 getDirection() {
        return currentDirection;
    }

    public float getProgress() {
        return clientProgress;
    }

    public float getInterpolatedProgress(float partialTicks) {
        return clientPrevProgress + (clientProgress - clientPrevProgress) * partialTicks;
    }

    void projectDirection(TileEntityGantry next) {
        currentDirection = Vec3.createVectorHelper(next.xCoord - xCoord, next.yCoord - yCoord, next.zCoord - zCoord);
    }

    void projectDirectionToController() {
        if (this instanceof TileEntityGantryTerminal terminal) {
            TileEntity controller = terminal.controller();
            currentDirection = controller == null ? null
                : Vec3.createVectorHelper(
                    controller.xCoord - xCoord,
                    controller.yCoord - yCoord,
                    controller.zCoord - zCoord);
        }
    }

    public void syncProjection() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("nodeId", nodeId.toString());
        var links = new LinkedHashMap<>(savedNeighbours);
        for (TileEntityGantry next : neighbours) {
            links.put(next.nodeId(), new Neighbour(next.nodeId(), next.xCoord, next.yCoord, next.zCoord));
        }
        var encoded = new NBTTagList();
        for (Neighbour next : links.values()) {
            var link = new NBTTagCompound();
            link.setString(
                "id",
                next.id()
                    .toString());
            link.setInteger("x", next.x());
            link.setInteger("y", next.y());
            link.setInteger("z", next.z());
            encoded.appendTag(link);
        }
        tag.setTag("neighbours", encoded);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        var in = new NbtReader(tag, "gantry");
        nodeId = UUID.fromString(in.string("nodeId"));
        neighbours.clear();
        savedNeighbours.clear();
        var links = in.compounds("neighbours");
        neighbourDirs.clear();
        for (int i = 0; i < links.tagCount(); i++) {
            var link = in.element("neighbours", i, links.getCompoundTagAt(i));
            var next = new Neighbour(
                UUID.fromString(link.string("id")),
                link.integer("x"),
                link.integer("y"),
                link.integer("z"));
            if (savedNeighbours.put(next.id(), next) != null)
                throw new IllegalStateException("Duplicate Gantry neighbor");
            neighbourDirs.add(Vec3.createVectorHelper(next.x() - xCoord, next.y() - yCoord, next.z() - zCoord));
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        var tag = new NBTTagCompound();
        tag.setString("nodeId", nodeId.toString());
        var directions = new NBTTagList();
        for (Vec3 direction : neighbourDirs) {
            var entry = new NBTTagCompound();
            entry.setDouble("x", direction.xCoord);
            entry.setDouble("y", direction.yCoord);
            entry.setDouble("z", direction.zCoord);
            directions.appendTag(entry);
        }
        tag.setTag("directions", directions);
        var module = GantryAPI.heldModule(
            Galaxia.proxy.serverRuntime()
                .rockets(),
            nodeId);
        tag.setInteger(
            "moduleId",
            module == null ? -1
                : module.part()
                    .def()
                    .id());
        tag.setFloat(
            "progress",
            module == null || module.gantryTransport() == null ? 0
                : (float) module.gantryTransport()
                    .progress());
        // A handoff can reach this node after its tick. Project the new trip, never the previous module's heading.
        currentDirection = null;
        if (module != null && module.gantryTransport() != null) {
            var trip = module.gantryTransport();
            int nextIndex = trip.routeIndex() + 1;
            if (nextIndex < trip.reservedRoute()
                .size()) {
                var next = savedNeighbours.get(
                    trip.reservedRoute()
                        .get(nextIndex));
                if (next != null) {
                    currentDirection = Vec3.createVectorHelper(next.x() - xCoord, next.y() - yCoord, next.z() - zCoord);
                }
            } else {
                projectDirectionToController();
            }
        }
        if (currentDirection != null) {
            tag.setDouble("dirX", currentDirection.xCoord);
            tag.setDouble("dirY", currentDirection.yCoord);
            tag.setDouble("dirZ", currentDirection.zCoord);
        }
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        var tag = packet.func_148857_g();
        nodeId = UUID.fromString(tag.getString("nodeId"));
        clientModuleId = tag.getInteger("moduleId");
        clientPrevProgress = clientProgress;
        clientProgress = tag.getFloat("progress");
        currentDirection = tag.hasKey("dirX")
            ? Vec3.createVectorHelper(tag.getDouble("dirX"), tag.getDouble("dirY"), tag.getDouble("dirZ"))
            : null;
        var directions = tag.getTagList("directions", NBT.TAG_COMPOUND);
        neighbourDirs.clear();
        for (int i = 0; i < directions.tagCount(); i++) {
            var direction = directions.getCompoundTagAt(i);
            neighbourDirs.add(
                Vec3.createVectorHelper(direction.getDouble("x"), direction.getDouble("y"), direction.getDouble("z")));
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

}
