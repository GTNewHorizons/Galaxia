package com.gtnewhorizons.galaxia.rocketmodules.rocket.entities;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.network.TeleportRequestPacket;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.EngineModule;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityRocket extends Entity {

    public enum Phase {
        IDLE, // Sitting in silo yet to launch
        LAUNCHING, // Ascending to space
        FALLING, // Descending in destination dimension
        RETRO, // Retro-rockets firing, rapid deceleration
        TOUCHDOWN // Landed - waiting for player
    }

    // DataWatcher constants
    private static final int DW_PHASE = 10; // byte - Phase ordinal
    private static final int DW_MODULES = 11; // String - Module IDs
    private static final int DW_CAPSULE = 12; // int - Capsule model index

    // Landing tuning constants
    public static final double SPAWN_ALTITUDE = 1200.0;
    public static final double TERMINAL_FALL_SPEED = -3.5; // blocks/tick
    private static final double RETRO_DECEL = 0.031; // blocks/tick²
    private static final double RETRO_START_HEIGHT = 200;
    private static final double SAFE_LAND_SPEED = -0.1;
    private static final int EJECT_DELAY_TICKS = 100;

    private TileEntitySilo targetSilo = null;

    private TileEntitySilo silo;
    private RocketAssembly assembly;

    private final List<Integer> modules = new ArrayList<>();
    private int capsuleIndex = -1;
    private int launchTicks = 0;
    private int touchdownTicks = 0;
    private int destination;

    // Landing fields
    private double targetX;
    private double targetZ;
    private int groundY = -1;
    private EntityPlayerMP lastRider = null;

    private String lastKnownModules = "";

    public EntityRocket(World world) {
        super(world);
        this.noClip = true;
        this.preventEntitySpawning = true;
        this.setSize(3.0F, 1.0F);
    }

    // ---------------------------------------------------------------------------------
    // Public facing API
    // ---------------------------------------------------------------------------------

    public void setDesination(int dim) {
        this.destination = dim;
    }

    public void bindSilo(TileEntitySilo silo) {
        this.silo = silo;
    }

    public void setTargetSilo(TileEntitySilo silo) {
        this.targetSilo = silo;
    }

    public RocketAssembly getAssembly() {
        if (worldObj.isRemote) {
            String current = dataWatcher.getWatchableObjectString(DW_MODULES);
            if (assembly == null || !current.equals(lastKnownModules)) {
                lastKnownModules = current;
                assembly = new RocketAssembly(getModuleTypes());
            }
            return assembly;
        }
        if (assembly == null) {
            assembly = new RocketAssembly(getModuleTypes());
        }
        return assembly;
    }

    public void setCapsuleIndex(int index) {
        this.capsuleIndex = index;
        dataWatcher.updateObject(12, index);
    }

    public int getCapsuleIndex() {
        return worldObj.isRemote ? dataWatcher.getWatchableObjectInt(12) : capsuleIndex;
    }

    public Phase getPhase() {
        return Phase.values()[dataWatcher.getWatchableObjectByte(DW_PHASE)];
    }

    public List<Integer> getModuleTypes() {
        if (worldObj.isRemote) {
            String ser = dataWatcher.getWatchableObjectString(11);
            if (ser == null || ser.isEmpty()) return new ArrayList<>();
            String[] parts = ser.split(",");
            List<Integer> list = new ArrayList<>(parts.length);
            for (String p : parts) {
                try {
                    list.add(Integer.parseInt(p.trim()));
                } catch (Exception ignored) {}
            }
            return list;
        }
        return new ArrayList<>(modules);
    }

    public boolean shouldRender() {
        return getPhase() != Phase.IDLE;
    }

    public void setModules(List<Integer> moduleList) {
        modules.clear();
        modules.addAll(moduleList);
        assembly = null;
        syncModules();
    }

    // ---------------------------------------------------------------------------------
    // Launch (ascent)
    // ---------------------------------------------------------------------------------

    public void launch() {
        modules.clear();
        modules.addAll(silo.getModules());
        assembly = null;
        assembly = new RocketAssembly(modules);
        syncModules();
        setPhase(Phase.LAUNCHING);
        silo.launch();
    }

    // ---------------------------------------------------------------------------------
    // Launch (descent)
    // ---------------------------------------------------------------------------------

    public void beginLanding(double x, double z) {
        this.targetX = x;
        this.targetZ = z;
        this.motionY = TERMINAL_FALL_SPEED;
        setPhase(Phase.FALLING);
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(DW_PHASE, (byte) Phase.IDLE.ordinal()); // launched
        dataWatcher.addObject(DW_MODULES, ""); // modules
        dataWatcher.addObject(DW_CAPSULE, -1); // capsuleIndex
    }

    @Override
    public double getMountedYOffset() {
        return getAssembly().getMountedYOffset();
    }

    // ---------------------------------------------------------------------------------
    // Update loop
    // ---------------------------------------------------------------------------------

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (riddenByEntity instanceof EntityPlayerMP player) {
            lastRider = player;
        }

        Phase phase = getPhase();

        switch (phase) {
            case LAUNCHING -> updateLaunching();
            case FALLING -> updateFalling();
            case RETRO -> updateRetro();
            case TOUCHDOWN -> updateTouchdown();
            default -> {}
        }

        float newH = (float) (getAssembly().getTotalHeight() + 0.5);
        if (Math.abs(this.height - newH) > 0.05f) {
            this.setSize(3.0f, newH);
        }
    }

    // ---------------------------------------------------------------------------------
    // Phase updates
    // ---------------------------------------------------------------------------------

    private void updateLaunching() {
        launchTicks++;

        if (launchTicks > 60) {
            float base = (launchTicks - 60) / 200f;
            float accel = 0.004f * (1 - (float) Math.exp(-base * 3.5));
            motionY += accel;
            moveEntity(0, motionY, 0);
        }

        if (worldObj.isRemote) spawnLaunchParticles();

        // Hand off to teleporter system at correct height
        if (!worldObj.isRemote && this.posY >= 500 && riddenByEntity instanceof EntityPlayer player) {
            player.mountEntity(null);
            GALAXIA_NETWORK.sendToServer(
                new TeleportRequestPacket(destination, player.posX, player.posY, player.posZ, capsuleIndex, modules));
        }
    }

    private void updateFalling() {
        if (!worldObj.isRemote) lockHorizontal();

        if (motionY > TERMINAL_FALL_SPEED) {
            motionY = Math.max(motionY - 0.05, TERMINAL_FALL_SPEED);
        }
        moveEntity(0, motionY, 0);

        if (worldObj.isRemote) spawnDescentParticles(false);

        if (posY - getGroundY() <= RETRO_START_HEIGHT) {
            setPhase(Phase.RETRO);
        }
    }

    private void updateRetro() {
        if (!worldObj.isRemote) lockHorizontal();

        motionY = Math.min(motionY + RETRO_DECEL, SAFE_LAND_SPEED);
        moveEntity(0, motionY, 0);

        if (worldObj.isRemote) spawnDescentParticles(true);

        if (!worldObj.isRemote && (posY - getGroundY() <= 1.0 || motionY >= SAFE_LAND_SPEED)) {
            if (targetSilo != null) {
                landOnSilo(targetSilo);
            } else {
                posY = getGroundY() + 1;
                motionY = 0;
                motionX = 0;
                motionZ = 0;
                groundY = -1;
                setPhase(Phase.TOUCHDOWN);
            }
        }
    }

    private void updateTouchdown() {
        if (worldObj.isRemote) return;

        if (riddenByEntity == null) {
            if (lastRider != null && !lastRider.isDead) {
                lastRider.setPositionAndUpdate(targetX + assembly.getTotalWidth(), getGroundY() + 1, targetZ);
                lastRider = null;
            }
            return;
        }

        touchdownTicks++;
        if (touchdownTicks >= EJECT_DELAY_TICKS && riddenByEntity instanceof EntityPlayerMP player) {
            player.mountEntity(null);
            player.setPositionAndUpdate(targetX + assembly.getTotalWidth(), getGroundY() + 1, targetZ);
            lastRider = null;
            touchdownTicks = 0;
        }
    }

    // ---------------------------------------------------------------------------------
    // Particle spawning
    // ---------------------------------------------------------------------------------

    // TODO improve particles to look cooler
    @SideOnly(Side.CLIENT)
    private void spawnLaunchParticles() {
        Random rand = worldObj.rand;
        double x = posX;
        double y = posY;
        double z = posZ;
        float intensity = Math.min(1.0f, (launchTicks - 40) / 120f);

        // Get all engine placements
        List<RocketAssembly.ModulePlacement> engines = getAssembly().getPlacements()
            .stream()
            .filter(p -> p.type() instanceof EngineModule)
            .collect(Collectors.toList());

        if (engines.isEmpty()) {
            // Fallback to center if no engines
            spawnPlumeParticles(rand, x, y, z, intensity);
            spawnGroundParticles(rand, x, y, z, intensity);
            return;
        }

        // Spawn plumes from each engine
        for (RocketAssembly.ModulePlacement p : engines) {
            double ex = x + p.x();
            double ey = y + p.y(); // Bottom of the engine
            double ez = z + p.z();
            spawnPlumeParticles(rand, ex, ey, ez, intensity);
        }

        // Keep ground particles central for simplicity
        spawnGroundParticles(rand, x, y, z, intensity);
    }

    @SideOnly(Side.CLIENT)
    private void spawnPlumeParticles(Random rand, double ex, double ey, double ez, float intensity) {
        float baseRadius = 0.15f;
        float maxRadius = 0.7f;
        float expansion = intensity * intensity;
        float plumeRadius = baseRadius + expansion * (maxRadius - baseRadius);
        int plumeCount = 6 + (int) (intensity * 14);
        for (int i = 0; i < plumeCount; i++) {
            double px = ex + rand.nextGaussian() * plumeRadius;
            double pz = ez + rand.nextGaussian() * plumeRadius;
            double py = ey - rand.nextFloat() * 0.8;

            double mx = rand.nextGaussian() * (0.08 + expansion * 0.18);
            double mz = rand.nextGaussian() * (0.08 + expansion * 0.18);
            double my = -2.2 * (0.8 + rand.nextFloat() * 0.6);

            worldObj.spawnParticle("flame", px, py, pz, mx, my, mz);
            worldObj.spawnParticle("largesmoke", px, py, pz, mx * 0.7, my * 0.6, mz * 0.7);
        }
    }

    @SideOnly(Side.CLIENT)
    private void spawnGroundParticles(Random rand, double x, double bottomY, double z, float intensity) {
        if (launchTicks < 160) {
            int groundCount = 10 + (int) (intensity * 18);
            for (int i = 0; i < groundCount; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double radius = 0.6 + rand.nextDouble() * 2.2;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                double py = bottomY - 0.3 - rand.nextFloat() * 0.4;
                double mx = Math.cos(angle) * (0.15 + rand.nextFloat() * 0.25);
                double mz = Math.sin(angle) * (0.15 + rand.nextFloat() * 0.25);
                double my = 0.05 + rand.nextFloat() * 0.18;
                worldObj.spawnParticle("largesmoke", px, py, pz, mx, my, mz);
                if (rand.nextFloat() < 0.25f) worldObj.spawnParticle("flame", px, py, pz, mx * 0.3, my * 0.1, mz * 0.3);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void spawnDescentParticles(boolean retro) {
        Random rand = worldObj.rand;
        if (!retro) {
            for (int i = 0; i < 4; i++) {
                worldObj.spawnParticle(
                    "cloud",
                    posX + rand.nextGaussian() * 0.4,
                    posY + height + rand.nextFloat() * 0.5,
                    posZ + rand.nextGaussian() * 0.4,
                    rand.nextGaussian() * 0.04,
                    0.06 + rand.nextFloat() * 0.04,
                    rand.nextGaussian() * 0.04);
            }
        } else {
            float intensity = (float) Math.min(1.0, Math.abs(motionY) / Math.abs(TERMINAL_FALL_SPEED));
            int count = 8 + (int) (intensity * 16);
            for (int i = 0; i < count; i++) {
                double px = posX + rand.nextGaussian() * 0.3;
                double pz = posZ + rand.nextGaussian() * 0.3;
                double mx = rand.nextGaussian() * (0.06 + intensity * 0.15);
                double mz = rand.nextGaussian() * (0.06 + intensity * 0.15);
                double my = -(1.5 + rand.nextFloat() * 0.8 + intensity * 1.2);
                worldObj.spawnParticle("flame", px, posY + 0.2, pz, mx, my, mz);
                worldObj.spawnParticle("largesmoke", px, posY + 0.2, pz, mx * 0.5, my * 0.4, mz * 0.5);
            }

            if (posY - getGroundY() < 20) {
                for (int i = 0; i < 6; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2;
                    double radius = 0.5 + rand.nextDouble() * 1.5;
                    worldObj.spawnParticle(
                        "largesmoke",
                        posX + Math.cos(angle) * radius,
                        posY - 0.5,
                        posZ + Math.sin(angle) * radius,
                        Math.cos(angle) * 0.1,
                        0.04 + rand.nextFloat() * 0.1,
                        Math.sin(angle) * 0.1);;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Phase updates
    // ---------------------------------------------------------------------------------

    private void setPhase(Phase p) {
        dataWatcher.updateObject(DW_PHASE, (byte) p.ordinal());
    }

    private void lockHorizontal() {
        posX = targetX;
        posZ = targetZ;
        motionX = 0;
        motionZ = 0;
    }

    private void landOnSilo(TileEntitySilo silo) {
        if (lastRider != null && !lastRider.isDead) {
            lastRider.setPositionAndUpdate(silo.xCoord + 0.5, silo.yCoord + 2.0, silo.zCoord + 0.5);
            lastRider = null;
        }

        silo.receiveLandingRocket(new ArrayList<>(modules));

        motionX = motionY = motionZ = 0;
        groundY = -1;
        if (riddenByEntity instanceof EntityPlayerMP player) {
            player.mountEntity(null);
            player.setPositionAndUpdate(targetX + assembly.getTotalWidth(), getGroundY() + 1, targetZ);
        }
        setPhase(Phase.IDLE);
    }

    private int getGroundY() {
        if (groundY == -1 && posY < SPAWN_ALTITUDE - 100) {
            groundY = worldObj.getTopSolidOrLiquidBlock((int) targetX, (int) targetZ);
        }
        return groundY == -1 ? 64 : groundY;
    }

    private void syncModules() {
        StringBuilder sb = new StringBuilder();
        for (int t : modules) {
            if (sb.length() > 0) sb.append(",");
            sb.append(t);
        }
        dataWatcher.updateObject(DW_MODULES, sb.toString());
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (int type : modules) {
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("type", type);
            list.appendTag(e);
        }
        tag.setTag("modules", list);
        tag.setInteger("capsuleIndex", capsuleIndex);
        tag.setByte("phase", (byte) getPhase().ordinal());
        tag.setDouble("targetX", targetX);
        tag.setDouble("targetZ", targetZ);
        tag.setInteger("groundY", groundY);
        tag.setDouble("motionYSaved", motionY);
        tag.setInteger("touchdownTicks", touchdownTicks);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        modules.clear();
        NBTTagList list = tag.getTagList("modules", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            modules.add(
                list.getCompoundTagAt(i)
                    .getInteger("type"));
        }
        capsuleIndex = tag.getInteger("capsuleIndex");
        dataWatcher.updateObject(DW_CAPSULE, capsuleIndex);

        byte phaseByte = tag.getByte("phase");
        dataWatcher.updateObject(DW_PHASE, phaseByte);

        targetX = tag.getDouble("targetX");
        targetZ = tag.getDouble("targetZ");
        groundY = tag.getInteger("groundY");
        motionY = tag.getDouble("motionYSaved");
        touchdownTicks = tag.getInteger("touchdownTicks");

        assembly = null;
        syncModules();
    }
}
