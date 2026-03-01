package com.gtnewhorizons.galaxia.rocketmodules.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.client.gui.GuiPlanetTeleporter;
import com.gtnewhorizons.galaxia.rocketmodules.RocketAssembly;
import com.gtnewhorizons.galaxia.rocketmodules.tileentities.TileEntitySilo;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntityRocket extends Entity {

    private TileEntitySilo silo;
    private RocketAssembly assembly;
    private final List<Integer> modules = new ArrayList<>();
    private int capsuleIndex = -1;
    private int launchTicks = 0;

    public boolean guiOpened = false;

    public EntityRocket(World world) {
        super(world);
        this.noClip = true;
        this.preventEntitySpawning = true;
        this.setSize(3.0F, 1.0F);
    }

    public void bindSilo(TileEntitySilo silo) {
        this.silo = silo;
    }

    public RocketAssembly getAssembly() {
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

    public void launch() {
        dataWatcher.updateObject(10, (byte) 1);
        modules.clear();
        modules.addAll(silo.getModules());
        assembly = new RocketAssembly(modules);
        StringBuilder sb = new StringBuilder();
        for (int t : modules) {
            if (sb.length() > 0) sb.append(",");
            sb.append(t);
        }
        dataWatcher.updateObject(11, sb.toString());
        silo.launch();
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(10, (byte) 0); // launched
        dataWatcher.addObject(11, ""); // modules
        dataWatcher.addObject(12, -1); // capsuleIndex
    }

    public boolean shouldRender() {
        return dataWatcher.getWatchableObjectByte(10) == 1;
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

    @Override
    public double getMountedYOffset() {
        return getAssembly().getMountedYOffset();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!worldObj.isRemote && riddenByEntity == null) this.setDead();

        if (this.posY >= 500 && riddenByEntity instanceof EntityPlayer player) {
            if (player == Minecraft.getMinecraft().thePlayer && !guiOpened) {
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiPlanetTeleporter());
                guiOpened = true;
            }
        }

        byte launched = dataWatcher.getWatchableObjectByte(10);
        if (launched == 1) {
            launchTicks++;
            // rocket stays on place first 3 seconds but still emits particles
            if (launchTicks > 60) {
                float base = (launchTicks - 60) / 200f;
                float accel = 0.004f * (1 - (float) Math.exp(-base * 3.5));
                this.motionY += accel;
                this.moveEntity(0, motionY, 0);
            }
            if (worldObj.isRemote) {
                spawnLaunchParticles();
            }
        }

        float newH = (float) (getAssembly().getTotalHeight() + 0.5);
        if (Math.abs(this.height - newH) > 0.05F) {
            this.setSize(3.0F, newH);
        }
    }

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
            .filter(
                p -> p.type()
                    .getThrust() > 0)
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
        assembly = null;
    }
}
