package com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.entities;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketBlueprint;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartInstance;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlight;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.flight.RocketFlights;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.CapsulePartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.IRocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.LanderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RiderPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.TileEntitySilo;
import com.gtnewhorizons.galaxia.registry.rocketmodules.utility.RocketTeleportHelper;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import lombok.Getter;
import lombok.Setter;

/**
 * The rocket as an entity: standing in a silo for the crew to board, climbing after launch, or descending onto a pad
 */
public class EntityRocket extends Entity implements IEntityAdditionalSpawnData {

    public static final double SPAWN_ALTITUDE = 300.0;
    public static final double TERMINAL_FALL_SPEED = -0.5;
    /** Height above the pad at which a climbing rocket leaves the world */
    public static final double DEPARTURE_HEIGHT = 160.0;
    /** Height above the pad a landing rocket appears at */
    public static final double LANDING_START_HEIGHT = 140.0;
    private static final double MAX_THRUST = 2.0;
    private static final double MIN_LANDING_SPEED = 0.12;
    private static final double MAX_LANDING_SPEED = 1.5;

    private static final int WATCHER_LAUNCHED = 16;
    private static final int WATCHER_LANDING = 17;

    private TileEntitySilo targetSilo;

    private int siloX, siloY, siloZ;
    private boolean hasSilo = false;

    @Getter
    private RocketBlueprint blueprint = new RocketBlueprint();

    @Setter
    private int capsuleIndex = 0;

    @Getter
    private final List<EntityRocketSeat> passengerSeats = new ArrayList<>();

    /** Where the crewed flight goes, set when the silo approves the launch */
    @Getter
    private RocketFlight pendingFlight;
    /** Uncrewed rockets only fly the launch animation, the flight itself is tracked by RocketFlights */
    private boolean uncrewed = false;
    private double launchY;
    /** Flight this rocket is landing for, null when it is not heading to a silo */
    private UUID landingFlight;
    private double landingTargetY;

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

    public boolean hasPassengers() {
        if (riddenByEntity != null) return true;
        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity != null) return true;
        }
        return false;
    }

    /** Players riding the capsule or any seat */
    public List<EntityPlayer> getCrew() {
        List<EntityPlayer> crew = new ArrayList<>();
        if (riddenByEntity instanceof EntityPlayer player) crew.add(player);
        for (EntityRocketSeat seat : passengerSeats) {
            if (seat.riddenByEntity instanceof EntityPlayer player) crew.add(player);
        }
        return crew;
    }

    public void setBlueprint(RocketBlueprint bp) {
        this.blueprint = bp != null ? bp.copy() : new RocketBlueprint();
    }

    public boolean shouldRender() {
        return isLaunched() || isLanding();
    }

    public boolean isLaunched() {
        return this.dataWatcher.getWatchableObjectByte(WATCHER_LAUNCHED) == 1;
    }

    public boolean isLanding() {
        return this.dataWatcher.getWatchableObjectByte(WATCHER_LANDING) == 1;
    }

    /**
     * Crewed launch, triggered by the pilot. The silo has the final say: without a destination, fuel or a free pad
     * the rocket stays on the ground and the crew is told why
     */
    public void launch() {
        if (worldObj.isRemote || isLaunched() || isLanding()) return;

        if (targetSilo == null || !targetSilo.beginCrewedLaunch(this)) {
            String problem = targetSilo != null ? targetSilo.getLastProblem() : "galaxia.silo.problem.no_destination";
            for (EntityPlayer player : getCrew()) {
                player.addChatComponentMessage(new ChatComponentTranslation(problem));
            }
            return;
        }
        startClimb();
    }

    /** Plays the launch animation for a rocket whose flight is already tracked by the silo */
    public void launchUncrewed() {
        uncrewed = true;
        startClimb();
    }

    private void startClimb() {
        launchY = posY;
        this.dataWatcher.updateObject(WATCHER_LAUNCHED, (byte) 1);
    }

    /**
     * Hands the surviving stages and fuel to this rocket for a crewed flight
     *
     * @param flight What arrives at the destination
     */
    public void prepareFlight(RocketFlight flight) {
        this.pendingFlight = flight;
    }

    /**
     * Starts descending onto a silo pad
     *
     * @param flightId The flight to complete on touchdown, null to just land
     * @param targetY  Height of the pad
     */
    public void beginLanding(UUID flightId, double targetY) {
        this.landingFlight = flightId;
        this.landingTargetY = targetY;
        this.motionY = TERMINAL_FALL_SPEED;
        this.dataWatcher.updateObject(WATCHER_LANDING, (byte) 1);
    }

    public void turnToLanderAndCache() {
        List<RocketPartInstance> toKeep = new ArrayList<>();
        for (RocketPartInstance part : blueprint.getParts()) {
            IRocketPartDef def = part.def();
            if (def instanceof LanderPartDef || def instanceof RiderPartDef || def instanceof CapsulePartDef) {
                toKeep.add(part);
            }
        }
        blueprint.clear();
        for (RocketPartInstance p : toKeep) {
            blueprint.addPartUnchecked(p);
        }
    }

    public void initializeSeats() {
        for (EntityRocketSeat seat : passengerSeats) {
            if (!seat.isDead && seat.riddenByEntity == null) seat.setDead();
        }
        passengerSeats.clear();

        int riderCount = 0;
        for (RocketPartInstance part : blueprint.getParts()) {
            if (part.def() instanceof RiderPartDef rider) {
                for (int i = 0; i < rider.riderCapacity(); i++) {
                    EntityRocketSeat seat = new EntityRocketSeat(worldObj, this, riderCount++, 0.0, 1.5 + i * 0.8, 0.0);
                    worldObj.spawnEntityInWorld(seat);
                    passengerSeats.add(seat);
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!worldObj.isRemote && hasSilo && targetSilo == null) {
            TileEntity te = worldObj.getTileEntity(siloX, siloY, siloZ);
            if (te instanceof TileEntitySilo silo) {
                targetSilo = silo;
            }
        }

        if (isLanding()) {
            tickLanding();
            return;
        }
        if (!isLaunched()) return;

        motionY += 0.08;
        if (motionY > MAX_THRUST) {
            motionY = MAX_THRUST;
        }

        moveEntity(motionX, motionY, motionZ);

        if (worldObj.isRemote) return;

        if (posY - launchY > DEPARTURE_HEIGHT || posY > 1000) {
            if (!uncrewed && pendingFlight != null) RocketTeleportHelper.departCrewed(this, pendingFlight);
            setDead();
        }
    }

    /** Slows down as the pad gets closer, then docks with the silo underneath */
    private void tickLanding() {
        double distance = posY - landingTargetY;
        motionY = -Math.max(MIN_LANDING_SPEED, Math.min(MAX_LANDING_SPEED, distance / 40.0));
        if (distance <= -motionY) {
            setPosition(posX, landingTargetY, posZ);
            motionY = 0;
            if (!worldObj.isRemote) touchDown();
            return;
        }
        moveEntity(0, motionY, 0);
    }

    private void touchDown() {
        this.dataWatcher.updateObject(WATCHER_LANDING, (byte) 0);
        if (landingFlight != null) {
            TileEntitySilo silo = RocketFlights.landNow(landingFlight);
            landingFlight = null;
            if (silo != null) {
                setTargetSilo(silo);
                silo.adoptRocketEntity(this);
            }
        }
    }

    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (worldObj.isRemote || passengerSeats.isEmpty() && !hasCapsule()) return false;

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
        return blueprint.getParts()
            .stream()
            .anyMatch(p -> p.def() instanceof CapsulePartDef);
    }

    public void setTargetSilo(TileEntitySilo silo) {
        this.targetSilo = silo;
        if (silo != null) {
            this.siloX = silo.xCoord;
            this.siloY = silo.yCoord;
            this.siloZ = silo.zCoord;
            this.hasSilo = true;
        } else {
            this.hasSilo = false;
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
        capsuleIndex = tag.getInteger("capsuleIndex");

        siloX = tag.getInteger("siloX");
        siloY = tag.getInteger("siloY");
        siloZ = tag.getInteger("siloZ");
        hasSilo = tag.getBoolean("hasSilo");

        if (tag.hasKey("landingFlight")) {
            landingFlight = UUID.fromString(tag.getString("landingFlight"));
            landingTargetY = tag.getDouble("landingTargetY");
            this.dataWatcher.updateObject(WATCHER_LANDING, (byte) 1);
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setTag("blueprint", blueprint.serializeNBT());
        tag.setInteger("capsuleIndex", capsuleIndex);

        tag.setInteger("siloX", siloX);
        tag.setInteger("siloY", siloY);
        tag.setInteger("siloZ", siloZ);
        tag.setBoolean("hasSilo", hasSilo);

        if (landingFlight != null) {
            tag.setString("landingFlight", landingFlight.toString());
            tag.setDouble("landingTargetY", landingTargetY);
        }
    }

    @Override
    protected void entityInit() {
        this.dataWatcher.addObject(WATCHER_LAUNCHED, (byte) 0);
        this.dataWatcher.addObject(WATCHER_LANDING, (byte) 0);
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("blueprint", blueprint.serializeNBT());

        try {
            CompressedStreamTools.write(tag, new ByteBufOutputStream(buffer));
        } catch (IOException _) {}
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        try {
            DataInputStream stream = new DataInputStream(new ByteBufInputStream(buffer));
            NBTTagCompound tag = CompressedStreamTools.read(stream);
            blueprint = RocketBlueprint.deserializeNBT(tag.getCompoundTag("blueprint"), RocketPartRegistry.instance());
        } catch (IOException _) {}
    }
}
