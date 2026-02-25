package com.gtnewhorizons.galaxia.registry.entity.rocket;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.handlers.GuiHandler;
import com.gtnewhorizons.galaxia.registry.block.tileentities.TileSiloController;

public class RocketEntity extends Entity {

    private int cx, cy, cz;
    private boolean launched = false;

    public RocketEntity(World world) {
        super(world);
        setSize(1.0F, 1.0F);
    }

    public RocketEntity(World world, int x, int y, int z) {
        this(world);
        this.cx = x;
        this.cy = y;
        this.cz = z;
    }

    public void launch() {
        if (launched || worldObj.isRemote) return;
        launched = true;
        motionY = 3.5D;
    }

    // === БЕРЁМ ВСЁ ИЗ ТАЙЛА (никакого дублирования данных) ===
    private TileSiloController getController() {
        if (worldObj == null) return null;
        TileEntity te = worldObj.getTileEntity(cx, cy, cz);
        return te instanceof TileSiloController c ? c : null;
    }

    public List<ModuleData> getModules() {
        TileSiloController c = getController();
        return c != null ? c.modules : Collections.emptyList();
    }

    public int getRocketHeight() {
        TileSiloController c = getController();
        if (c == null) return 1;
        int h = 1;
        for (ModuleData m : c.modules) h += m.type().height;
        return h;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (launched) {
            if (posY > 500) setDead();
            return;
        }

        TileSiloController ctrl = getController();
        if (ctrl != null) {
            setPosition(cx + 0.5, cy + 1.0, cz + 0.5);
            setSize(1.0F, getRocketHeight());
        } else if (!worldObj.isRemote) {
            setDead();
        }
    }

    // === УБИРАЕМ "СТЕРЖЕНЬ" И ВСЁ ЛИШНЕЕ ===
    @Override
    public boolean shouldRenderInPass(int pass) {
        return false; // полностью отключаем vanilla render
    }

    @Override
    public boolean canRenderOnFire() { return false; }

    // === ОСТАЛЬНОЕ БЕЗ ИЗМЕНЕНИЙ ===
    @Override
    protected void entityInit() {}

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
        cx = nbt.getInteger("cx");
        cy = nbt.getInteger("cy");
        cz = nbt.getInteger("cz");
        launched = nbt.getBoolean("launched");
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setInteger("cx", cx);
        nbt.setInteger("cy", cy);
        nbt.setInteger("cz", cz);
        nbt.setBoolean("launched", launched);
    }

    @Override
    public boolean interactFirst(EntityPlayer player) {
        if (!worldObj.isRemote) {
            player.openGui(Galaxia.instance, GuiHandler.GUI_ROCKET, worldObj, getEntityId(), 0, 0);
        }
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox() { return boundingBox; }
    @Override
    public AxisAlignedBB getCollisionBox(Entity entityIn) { return boundingBox; }
    @Override
    public boolean canBeCollidedWith() { return !launched; }

    public int getControllerX() { return cx; }
    public int getControllerY() { return cy; }
    public int getControllerZ() { return cz; }

    // preventing recursion and access issues
    public void resize(float width, float height){
        setSize( width, height);
    }
}
