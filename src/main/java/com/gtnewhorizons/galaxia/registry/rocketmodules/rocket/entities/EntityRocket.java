package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.EntityGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.RocketScheduleSyncHandler;
import com.gtnewhorizons.galaxia.core.state.NbtReader;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.Rocket;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef.Kind;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import lombok.Getter;

public class EntityRocket extends Entity implements IEntityAdditionalSpawnData, IGuiHolder<EntityGuiData> {

    private RocketBlueprint blueprint = new RocketBlueprint();
    @Nullable
    private UUID vehicleId;
    private long projectionGeneration;

    @Getter
    private final List<EntityRocketSeat> passengerSeats = new ArrayList<>();

    public EntityRocket(World world) {
        super(world);
        setSize(3f, 10f);
        noClip = true;
        this.ignoreFrustumCheck = true;
    }

    public boolean isPlayerAboard(EntityPlayer player) {
        if (riddenByEntity == player) return true;
        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity == player) return true;
        }
        return false;
    }

    @Nullable
    public UUID vehicleId() {
        return vehicleId;
    }

    public long projectionGeneration() {
        return projectionGeneration;
    }

    @Override
    public double getMountedYOffset() {
        return 1.5;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isDead;
    }

    @Nullable
    public Rocket rocket() {
        return vehicleId == null || worldObj.isRemote ? null
            : Galaxia.proxy.serverRuntime()
                .rockets()
                .find(vehicleId);
    }

    public boolean bind(Rocket vehicle) {
        if (worldObj.isRemote) return false;
        var projection = vehicle.claimProjection(
            getUniqueID(),
            new Rocket.PhysicalPosition(worldObj.provider.dimensionId, posX, posY, posZ, rotationYaw));
        if (projection == null) return false;
        var footprint = vehicle.footprint();
        setSize(Math.max(footprint.width(), footprint.depth()), footprint.height());
        var position = vehicle.projectionPosition();
        if (position != null && position.dimension() == worldObj.provider.dimensionId)
            setPositionAndRotation(position.x(), position.y(), position.z(), position.yaw(), rotationPitch);
        vehicleId = vehicle.id();
        projectionGeneration = projection.generation();
        return true;
    }

    public boolean isCurrentProjection(Rocket vehicle) {
        return vehicle.id()
            .equals(vehicleId) && vehicle.ownsProjection(getUniqueID(), projectionGeneration);
    }

    public RocketBlueprint getBlueprint() {
        if (worldObj.isRemote) return blueprint;
        Rocket vehicle = rocket();
        return vehicle == null ? new RocketBlueprint() : vehicle.builtBlueprint();
    }

    @Override
    public ModularPanel buildUI(EntityGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return RocketScheduleSyncHandler.build(syncManager, this::vehicleId);
    }

    public boolean shouldRender() {
        return this.dataWatcher.getWatchableObjectByte(16) == 1;
    }

    public void setProjectionVisible(boolean visible) {
        if (!worldObj.isRemote) this.dataWatcher.updateObject(16, (byte) (visible ? 1 : 0));
    }

    public void initializeSeats() {
        passengerSeats.removeIf(seat -> seat.isDead);
        for (Entity entity : worldObj.loadedEntityList) {
            if (entity instanceof EntityRocketSeat seat && seat.getRocket() == this && !passengerSeats.contains(seat)) {
                passengerSeats.add(seat);
            }
        }

        int riderCount = 0;
        for (RocketPartInstance part : getBlueprint().getParts()) {
            if (part.def()
                .seats() > 0) {
                for (int i = 0; i < part.def()
                    .seats(); i++) {
                    int index = riderCount++;
                    if (passengerSeats.stream()
                        .anyMatch(existing -> existing.seatIndex() == index)) continue;
                    EntityRocketSeat seat = new EntityRocketSeat(
                        worldObj,
                        this,
                        index,
                        0.0,
                        getMountedYOffset() + i * 0.8,
                        0.0);
                    worldObj.spawnEntityInWorld(seat);
                    passengerSeats.add(seat);
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote) {
            Rocket vehicle = rocket();
            if (vehicle == null || !isCurrentProjection(vehicle)) {
                setDead();
                return;
            }
        }

    }

    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (worldObj.isRemote || passengerSeats.isEmpty() && !hasCapsule()) return false;
        var vehicle = rocket();
        if (vehicle == null || vehicle.isRunning()
            || !isCurrentProjection(vehicle)
            || !vehicle.teamId()
                .equals(com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat.getTeam(player)))
            return false;

        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity == null) {
                if (!Galaxia.proxy.serverRuntime()
                    .rockets()
                    .board(vehicle.id(), player.getUniqueID())) return false;
                player.mountEntity(seat);
                return true;
            }
        }

        if (hasCapsule() && riddenByEntity == null) {
            if (!Galaxia.proxy.serverRuntime()
                .rockets()
                .board(vehicle.id(), player.getUniqueID())) return false;
            player.mountEntity(this);
            return true;
        }
        return false;
    }

    /** Reconnect and physical arrival restore an already accepted passenger, including a running schedule. */
    public boolean restorePassenger(EntityPlayer player) {
        Rocket vehicle = rocket();
        if (vehicle == null || !isCurrentProjection(vehicle)
            || !vehicle.passengers()
                .contains(player.getUniqueID())
            || vehicle.restrictsPassengers()) return false;
        if (isPlayerAboard(player)) return true;
        initializeSeats();
        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity == null) {
                player.mountEntity(seat);
                return true;
            }
        }
        if (hasCapsule() && riddenByEntity == null) {
            player.mountEntity(this);
            return true;
        }
        return false;
    }

    private boolean hasCapsule() {
        return getBlueprint().getParts()
            .stream()
            .anyMatch(
                p -> p.def()
                    .kind() == Kind.CAPSULE);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        var in = new NbtReader(tag, "rocketEntity");
        vehicleId = UUID.fromString(in.string("vehicle"));
        projectionGeneration = in.longValue("generation");
        if (projectionGeneration <= 0) throw new IllegalStateException("Invalid Rocket entity generation");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        if (vehicleId == null) throw new IllegalStateException("Unbound Rocket entity cannot be saved");
        tag.setString("vehicle", vehicleId.toString());
        tag.setLong("generation", projectionGeneration);
    }

    @Override
    protected void entityInit() {
        this.dataWatcher.addObject(16, (byte) 0);
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        NBTTagCompound tag = new NBTTagCompound();
        writeEntityToNBT(tag);
        tag.setTag("blueprint", getBlueprint().serializeNBT());

        try {
            CompressedStreamTools.write(tag, new ByteBufOutputStream(buffer));
        } catch (IOException ex) {
            throw new UncheckedIOException("Rocket spawn state could not be encoded", ex);
        }
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        try {
            DataInputStream stream = new DataInputStream(new ByteBufInputStream(buffer));
            NBTTagCompound tag = CompressedStreamTools.read(stream);
            readEntityFromNBT(tag);
            blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
        } catch (IOException ex) {
            throw new UncheckedIOException("Rocket spawn state could not be decoded", ex);
        }
    }
}
