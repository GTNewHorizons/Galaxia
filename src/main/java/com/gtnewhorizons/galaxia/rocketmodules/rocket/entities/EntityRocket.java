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
import com.gtnewhorizons.galaxia.rocketmodules.rocket.ModuleRegistry;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.EngineModule;
import com.gtnewhorizons.galaxia.rocketmodules.rocket.modules.LanderModule;
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
    private static final int DW_IS_LANDER = 13; // byte - boolean

    // Landing tuning constants
    public static final double SPAWN_ALTITUDE = 1200.0;
    public static final double TERMINAL_FALL_SPEED = -3.5; // blocks/tick
    private static final double RETRO_DECEL = 0.031; // blocks/tick²
    private static final double RETRO_START_HEIGHT = 200;
    private static final double SAFE_LAND_SPEED = -0.2;
    private static final int EJECT_DELAY_TICKS = 100;

    private TileEntitySilo targetSilo = null;

    private TileEntitySilo silo;
    private RocketAssembly assembly;

    private final List<Integer> modules = new ArrayList<>();
    // To be used to "remember" the rocket when still in orbit
    private List<Integer> cachedModules = new ArrayList<>();
    private boolean isLander = false;

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

    /**
     * Basic override to give entity collision
     *
     * @return Boolean : True => can be collided with
     */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    /**
     * Sets the destination dimension of a rocket. Should be set via the planet
     * selector and should not be invoked after launch until next landing
     *
     * @param dim The target dimension ID
     */
    public void setDestination(int dim) {
        this.destination = dim;
    }

    /**
     * Binds a silo TileEntity as the silo this rocket has been built and launched
     * from
     *
     * @param silo The silo TileEntity that this rocket was created from
     */
    public void bindSilo(TileEntitySilo silo) {
        this.silo = silo;
    }

    /**
     * Sets a silo as a "target", such that it will land in that silo. This will
     * only be invoked if a suitable silo is either found/selected, and generally
     * should be set on dimensional transfer
     *
     * @param silo The target silo TileEntity
     */
    public void setTargetSilo(TileEntitySilo silo) {
        this.targetSilo = silo;
    }

    /**
     * Gets the RocketAssembly for the current modules in the silo. Also syncs to
     * client via data watcher
     *
     * @return RocketAssembly associated with this silo in current state
     */
    public RocketAssembly getAssembly() {
        if (worldObj.isRemote) {
            // Client-side syncing
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

    /**
     * Sets the index for the "main" capsule module from the list of modules. In the
     * case of multiple capsules, the "main" capsule is determined as the one that
     * the primary rider will mount on launch
     *
     * @param index The index to point to the main capsule module
     */
    public void setCapsuleIndex(int index) {
        this.capsuleIndex = index;
        dataWatcher.updateObject(12, index);
    }

    /**
     * Gets the index for the "main" capsule module from module list. In the
     * case of multiple capsules, the "main" capsule is determined as the one that
     * the primary rider will mount on launch
     *
     * @return The index of the main capsule
     */
    public int getCapsuleIndex() {
        return worldObj.isRemote ? dataWatcher.getWatchableObjectInt(12) : capsuleIndex;
    }

    /**
     * Gets the current "Phase" of the rocket, which determines its behaviour
     *
     * @see Phase
     *
     * @return Phase of the rocket
     */
    public Phase getPhase() {
        return Phase.values()[dataWatcher.getWatchableObjectByte(DW_PHASE)];
    }

    /**
     * Gets a list of module types as integer IDs from the current modules list.
     * Works on both client (via data watcher) and server
     *
     * @return The integer list of module types as IDs
     */
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

    /**
     * Determines whether the entity should be rendered. This should be false if it
     * is still "in the silo", as the rendering is handled there by the TESR of the
     * silo
     *
     * @return Boolean : True => should be rendered as an entity
     */
    public boolean shouldRender() {
        return getPhase() != Phase.IDLE;
    }

    /**
     * Sets the module list to a new list, and syncs them
     *
     * @param moduleList The new list of modules to update to
     */
    public void setModules(List<Integer> moduleList) {
        modules.clear();
        modules.addAll(moduleList);
        assembly = null;
        syncModules();
    }

    /**
     * Updates the rocket to act as a "lander", stripping it down to the
     * LanderModules, and caching the rest of the rocket for relaunch
     */
    public void turnToLanderAndCache() {
        // Cache current modules
        cachedModules.clear();
        for (Integer m : modules) {
            cachedModules.add(m);
        }

        // Reduce modules to only Lander Modules
        modules.clear();
        for (Integer m : cachedModules) {
            if (ModuleRegistry.fromId(m) instanceof LanderModule) modules.add(m);
        }
        isLander = true;
        // Synced with client for Waila compat
        dataWatcher.updateObject(DW_IS_LANDER, (byte) 1);
        destination = 0;
        syncModules();
    }

    /**
     * Resets the active modules to the previously cached ones, and resets the
     * cache. Stops the rocket acting as a "lander"
     */
    public void reattachCachedModules() {
        modules.clear();
        setModules(cachedModules);
        cachedModules.clear();
        isLander = false;
        dataWatcher.updateObject(DW_IS_LANDER, (byte) 0);
    }

    /**
     * Public getter for checking lander status (used in Waila for lang file to
     * correctly display name)
     *
     * @return Boolean : True => Is a lander
     */
    public boolean isLander() {
        return dataWatcher.getWatchableObjectByte(DW_IS_LANDER) == 1;
    }

    // ---------------------------------------------------------------------------------
    // Launch (ascent)
    // ---------------------------------------------------------------------------------

    /**
     * Launches the rocket
     * If the rocket is a lander, just launch. If not, update the silo and assembly
     */
    public void launch() {
        if (!isLander) {
            modules.clear();
            modules.addAll(silo.getModules());
            assembly = null;
            assembly = new RocketAssembly(modules);
            syncModules();
            silo.launch();
        }

        setPhase(Phase.LAUNCHING);
    }

    /**
     * Determines the right click interaction with a player. Rockets are only
     * mountable directly if in touchdown phase as a lander
     *
     * @param player The player interacting with the rocket
     *
     * @return Boolean : True => Successful interaction
     */
    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (worldObj.isRemote) return true;
        if (!(player instanceof EntityPlayerMP)) return false;
        if (getPhase() != Phase.TOUCHDOWN) return false;
        if (!isLander) return false;

        player.mountEntity(this);
        launch();
        return true;
    }

    // ---------------------------------------------------------------------------------
    // Launch (descent)
    // ---------------------------------------------------------------------------------

    /**
     * Begins the landing procedures, setting the coordinates and motion and
     * updating phases
     *
     * @param x The x-coordinate to begin landing at
     * @param z The z-coordinate to begin landing at
     */
    public void beginLanding(double x, double z) {
        this.targetX = x;
        this.targetZ = z;
        this.motionY = TERMINAL_FALL_SPEED;
        setPhase(Phase.FALLING);
    }

    /**
     * Overrides the entity intialisation, used to add datawatcher objects mostly
     */
    @Override
    protected void entityInit() {
        dataWatcher.addObject(DW_PHASE, (byte) Phase.IDLE.ordinal()); // launched
        dataWatcher.addObject(DW_MODULES, ""); // modules
        dataWatcher.addObject(DW_CAPSULE, -1); // capsuleIndex
        dataWatcher.addObject(DW_IS_LANDER, (byte) 0);
    }

    /**
     * Overrides default mounted height with a height determined by the
     * RocketAssmebly
     *
     * @return The mounted Y offset
     */
    @Override
    public double getMountedYOffset() {
        return getAssembly().getMountedYOffset();
    }

    // ---------------------------------------------------------------------------------
    // Update loop
    // ---------------------------------------------------------------------------------

    /**
     * Overall update loop, defers to different update loops depending on state
     */
    @Override
    public void onUpdate() {
        super.onUpdate();

        if (riddenByEntity instanceof EntityPlayerMP player) {
            lastRider = player;
        }

        Phase phase = getPhase();

        // Divert to separate update loops based on phase
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
            // Always send full rocket for overworld
            if (destination == 0) {
                // If the "cached" orbiting rocket is larger than current, regain cached modules
                if (cachedModules.size() > modules.size()) {
                    reattachCachedModules();
                }
            }
            // Teleport player and rocket to target dimension, remount, and set to LANDING
            GALAXIA_NETWORK.sendToServer(
                new TeleportRequestPacket(destination, player.posX, player.posY, player.posZ, capsuleIndex, modules));
        }
    }

    private void updateFalling() {
        // Stops drifting horizontally whilst falling
        if (!worldObj.isRemote) lockHorizontal();

        if (motionY > TERMINAL_FALL_SPEED) {
            motionY = Math.max(motionY - 0.05, TERMINAL_FALL_SPEED);
        }
        moveEntity(0, motionY, 0);

        if (worldObj.isRemote) spawnDescentParticles(false);

        // Once at correct height, set phase to retro burning
        if (posY - getGroundY() <= RETRO_START_HEIGHT) {
            setPhase(Phase.RETRO);
        }
    }

    private void updateRetro() {
        // Stops drifting horizontally whilst falling
        if (!worldObj.isRemote) lockHorizontal();

        // Decelerate until at safe speed
        motionY = Math.min(motionY + RETRO_DECEL, SAFE_LAND_SPEED);
        moveEntity(0, motionY, 0);

        if (worldObj.isRemote) spawnDescentParticles(true);

        if (!worldObj.isRemote && posY - getGroundY() <= 1.0) {
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

        // Delay then eject player
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

    // TODO: Fix descent particles - suspect it is a culling issue
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

    /**
     * Locks horizontal movement to prevent drifting
     */
    private void lockHorizontal() {
        posX = targetX;
        posZ = targetZ;
        motionX = 0;
        motionZ = 0;
    }

    /**
     * Handles the logic of landing on a silo if one is found previously - To be
     * used only when silo in landing trajectory
     *
     * @param silo The silo TileEntity to land on
     */
    private void landOnSilo(TileEntitySilo silo) {
        if (lastRider != null && !lastRider.isDead) {
            lastRider.setPositionAndUpdate(silo.xCoord + 0.5, silo.yCoord + 2.0, silo.zCoord + 0.5);
            lastRider = null;
        }

        // Adds the rocket modules to the silo
        silo.receiveLandingRocket(new ArrayList<>(modules));

        motionX = motionY = motionZ = 0;
        groundY = -1;
        if (riddenByEntity instanceof EntityPlayerMP player) {
            player.mountEntity(null);
            player.setPositionAndUpdate(targetX + assembly.getTotalWidth(), getGroundY() + 1, targetZ);
        }
        setPhase(Phase.IDLE);
        // Kill this entity once landed, as a new one created by silo
        this.setDead();
    }

    /**
     * Helper method to get the ground Y level whilst landing
     *
     * @return The ground Y level below the rocket landing trajectory
     */
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
        tag.setBoolean("isLander", isLander);
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
        isLander = tag.getBoolean("isLander");
        dataWatcher.updateObject(DW_IS_LANDER, (byte) (isLander ? 1 : 0));

        assembly = null;
        syncModules();
    }
}
