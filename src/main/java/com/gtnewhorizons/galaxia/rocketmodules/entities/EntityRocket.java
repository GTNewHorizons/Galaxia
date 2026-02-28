package com.gtnewhorizons.galaxia.rocketmodules.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
            float t = launchTicks / 200f;
            float accel = 0.004f * (1 - (float) Math.exp(-t * 3.5));
            this.motionY += accel;
            this.moveEntity(0, motionY, 0);
            if (worldObj.isRemote) {
                spawnRocketParticles();
            }
        }

        float newH = (float) (getAssembly().getTotalHeight() + 0.5);
        if (Math.abs(this.height - newH) > 0.05F) {
            this.setSize(3.0F, newH);
        }
    }

    // TODO move to thruster code so particles are emitted by engines
    @SideOnly(Side.CLIENT)
    private void spawnRocketParticles() {
        if (launchTicks < 3) return;

        double x = this.posX;
        double z = this.posZ;
        double bottomY = this.posY - 0.65;

        float heightFactor = (float) Math.min(4.0, this.posY / 160.0);
        float nozzleSpread = 0.25f;
        float exhaustExpansion = 0.02f + heightFactor * 0.08f;
        float downSpeedBase = -0.28f * (1.0f - heightFactor * 0.75f);
        float intensity = Math.min(1.2f, launchTicks / 35f) * Math.max(0.2f, 1.0f - (float) this.posY / 650f);

        Random rand = this.worldObj.rand;

        // flame
        for (int i = 0; i < 7 + (int) (intensity * 11); i++) {
            double px = x + rand.nextGaussian() * nozzleSpread;
            double pz = z + rand.nextGaussian() * nozzleSpread;
            double py = bottomY - rand.nextFloat() * 0.4;

            double mx = rand.nextGaussian() * exhaustExpansion;
            double mz = rand.nextGaussian() * exhaustExpansion;
            double my = downSpeedBase * (1.4f + rand.nextFloat() * 0.4f);

            this.worldObj.spawnParticle("flame", px, py, pz, mx, my, mz);
            this.worldObj.spawnParticle("largesmoke", px, py - 0.2, pz, mx * 0.7f, my * 0.85f, mz * 0.7f);
        }

        // main smoke
        for (int i = 0; i < 9 + (int) (intensity * 18); i++) {
            double px = x + rand.nextGaussian() * nozzleSpread;
            double pz = z + rand.nextGaussian() * nozzleSpread;
            double py = bottomY - rand.nextFloat() * 0.8;

            double mx = rand.nextGaussian() * (exhaustExpansion * 1.2f);
            double mz = rand.nextGaussian() * (exhaustExpansion * 1.2f);
            double my = downSpeedBase * (0.9f + rand.nextFloat() * 0.6f);

            this.worldObj.spawnParticle("largesmoke", px, py, pz, mx, my, mz);
        }

        // plasma trail
        if (this.posY > 220 && rand.nextFloat() < 0.65f) {
            for (int i = 0; i < 4; i++) {
                double px = x + rand.nextGaussian() * nozzleSpread;
                double pz = z + rand.nextGaussian() * nozzleSpread;
                double py = bottomY - 1.5 - rand.nextFloat() * 2.5;

                double mx = rand.nextGaussian() * (exhaustExpansion * 1.8f);
                double mz = rand.nextGaussian() * (exhaustExpansion * 1.8f);
                double my = downSpeedBase * 0.5f - rand.nextFloat() * 0.1f;

                this.worldObj.spawnParticle(
                    "reddust",
                    px,
                    py,
                    pz,
                    0.8 + rand.nextFloat() * 0.4,
                    0.9 + rand.nextFloat() * 0.6,
                    1.0 + rand.nextFloat() * 0.3);

                if (rand.nextFloat() < 0.4f)
                    this.worldObj.spawnParticle("magicCrit", px, py, pz, mx * 0.5f, my * 0.3f, mz * 0.5f);
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
