package com.gtnewhorizons.galaxia.block.module;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.gtnewhorizons.galaxia.block.tileentities.TileEntityModuleController;
import com.gtnewhorizons.galaxia.items.special.ItemModuleMover;
import com.gtnewhorizons.galaxia.modules.ModuleConfig;
import com.gtnewhorizons.galaxia.modules.ModuleType;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;

public class BlockModuleController extends BlockContainer {

    public BlockModuleController() {
        super(Material.iron);
        setBlockName("module.controller");
        setHardness(-1F);
        setResistance(6000000F);
        setBlockUnbreakable();
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityModuleController();
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (world.isRemote) return;

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityModuleController ctrl) {
            if (ctrl.getType() == null) {
                String id = ModuleTypes.CAPSULE_3X3.getId();
                ctrl.setModule(id);
                ctrl.buildStructure();
            }
        }
    }

    @Override
    public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemModuleMover mover) {
            if (!world.isRemote) {
                mover.selectModule(world, x, y, z, player, held);
            }
            return;
        }
        super.onBlockClicked(world, x, y, z, player);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public int getRenderType() {
        return ModuleConfig.DEBUG_RENDER ? 0 : -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public boolean isNormalCube() {
        return ModuleConfig.DEBUG_RENDER;
    }

    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityModuleController ctrl)) {
            return super.collisionRayTrace(world, x, y, z, start, end);
        }

        ModuleType type = ctrl.getType();
        if (type == null) return null;

        double cx = x + 0.5 + type.getOffsetX();
        double cy = y + 0.5 + type.getOffsetY();
        double cz = z + 0.5 + type.getOffsetZ();

        AxisAlignedBB rel = type.getModelBounds();
        float s = type.getScale();

        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            cx + rel.minX * s,
            cy + rel.minY * s,
            cz + rel.minZ * s,
            cx + rel.maxX * s,
            cy + rel.maxY * s,
            cz + rel.maxZ * s);

        MovingObjectPosition mop = box.calculateIntercept(start, end);
        if (mop != null) {
            mop.blockX = x;
            mop.blockY = y;
            mop.blockZ = z;
            return mop;
        }
        return null;
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityModuleController ctrl)) {
            return super.getSelectedBoundingBoxFromPool(world, x, y, z);
        }

        ModuleType type = ctrl.getType();
        if (type == null) {
            return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
        }

        double cx = x + 0.5 + type.getOffsetX();
        double cy = y + 0.5 + type.getOffsetY();
        double cz = z + 0.5 + type.getOffsetZ();

        AxisAlignedBB rel = type.getModelBounds();
        float s = type.getScale();

        return AxisAlignedBB.getBoundingBox(
            cx + rel.minX * s,
            cy + rel.minY * s,
            cz + rel.minZ * s,
            cx + rel.maxX * s,
            cy + rel.maxY * s,
            cz + rel.maxZ * s);
    }
}
