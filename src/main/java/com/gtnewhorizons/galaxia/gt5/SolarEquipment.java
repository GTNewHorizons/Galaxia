package com.gtnewhorizons.galaxia.gt5;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.SolarIllumination;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IEnergyConnected;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;

/** Passive panel hardware and its sole active native GT electrical endpoint. */
public final class SolarEquipment {

    public static final int COLLECTOR_ID = 23065;
    public static final long VOLTAGE = 32;
    public static Block panel;
    public static Dynamo collector;
    private static ITexture[] missingTextures;

    private SolarEquipment() {}

    public static void registerPanel() {
        panel = new Panel().setBlockName("galaxia.solar_panel")
            .setBlockTextureName("minecraft:missingno")
            .setCreativeTab(Galaxia.creativeTab);
        GameRegistry.registerBlock(panel, "solar_panel");
    }

    public static void registerCollector() {
        if (GregTechAPI.METATILEENTITIES[COLLECTOR_ID] != null) {
            throw new IllegalStateException("Solar Energy Dynamo meta ID is occupied: " + COLLECTOR_ID);
        }
        collector = new Dynamo(COLLECTOR_ID);
    }

    public static final class Panel extends Block {

        private Panel() {
            super(Material.iron);
            setHardness(3);
            setResistance(10);
            setLightOpacity(255);
            setBlockBounds(0, 0, 0, 1, 0.125f, 1);
        }

        @Override
        public void onBlockAdded(World world, int x, int y, int z) {
            if (!world.isRemote) world.setBlockMetadataWithNotify(
                x,
                y,
                z,
                SolarIllumination.collectionDirection(world) == ForgeDirection.EAST ? 1 : 0,
                2);
        }

        @Override
        public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
            if (world.getBlockMetadata(x, y, z) == 1) setBlockBounds(0, 0, 0, 0.125f, 1, 1);
            else setBlockBounds(0, 0, 0, 1, 0.125f, 1);
        }

        @Override
        public void setBlockBoundsForItemRender() {
            setBlockBounds(0, 0, 0, 1, 0.125f, 1);
        }

        @Override
        public boolean isOpaqueCube() {
            return false;
        }

        @Override
        public boolean renderAsNormalBlock() {
            return false;
        }
    }

    public static final class Dynamo extends MTETieredMachineBlock {

        private SolarArrays arrays;
        private SolarArrays.Collector array;
        private long potentialAmps, acceptedAmps;

        private Dynamo(int id) {
            super(id, "galaxia.solar_energy_dynamo", "Solar Energy Dynamo", 1, 0, new String[0]);
        }

        private Dynamo(String name, int tier, String[] description, ITexture[][][] textures) {
            super(name, tier, 0, description, textures);
        }

        @Override
        public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
            return new Dynamo(mName, mTier, mDescriptionArray, mTextures);
        }

        @Override
        public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
            boolean active, boolean redstone) {
            if (missingTextures == null) missingTextures = new ITexture[] { TextureFactory.of(panel, 0) };
            return missingTextures;
        }

        @Override
        public ITexture[][][] getTextureSet(ITexture[] textures) {
            return new ITexture[][][] { { textures } };
        }

        @Override
        public boolean isValidSlot(int slot) {
            return false;
        }

        @Override
        public boolean allowPutStack(IGregTechTileEntity tile, int slot, ForgeDirection side, ItemStack stack) {
            return false;
        }

        @Override
        public boolean allowPullStack(IGregTechTileEntity tile, int slot, ForgeDirection side, ItemStack stack) {
            return false;
        }

        @Override
        public boolean isEnetOutput() {
            return true;
        }

        @Override
        public boolean isOutputFacing(ForgeDirection side) {
            return side == getBaseMetaTileEntity().getFrontFacing()
                .getOpposite();
        }

        @Override
        public boolean isFacingValid(ForgeDirection side) {
            return side == SolarIllumination.collectionDirection(getBaseMetaTileEntity().getWorld());
        }

        @Override
        public long maxEUStore() {
            return 0;
        }

        @Override
        public long maxEUOutput() {
            return VOLTAGE;
        }

        @Override
        public long maxAmperesOut() {
            return potentialAmps;
        }

        @Override
        public void onPostTick(IGregTechTileEntity tile, long tick) {
            if (!tile.isServerSide()) return;
            ForgeDirection direction = SolarIllumination.collectionDirection(tile.getWorld());
            if (tile.getFrontFacing() != direction) tile.setFrontFacing(direction);
            if (array == null) {
                arrays = SolarArrayTracking.arrays(tile.getWorld());
                array = arrays.register(
                    new SolarArrays.Pos(tile.getXCoord(), tile.getYCoord(), tile.getZCoord()),
                    direction == ForgeDirection.EAST);
            }
            int panels = SolarArrayTracking.litPanels(arrays, array);
            double availableEU = panels * 32.0 * SolarArrayTracking.intensity(tile.getWorld());
            potentialAmps = (long) Math.floor(availableEU / VOLTAGE);
            acceptedAmps = potentialAmps == 0 ? 0
                : IEnergyConnected.Util.emitEnergyToNetwork(VOLTAGE, potentialAmps, tile);
            setEUVar(0);
            tile.setActive(potentialAmps > 0);
        }

        @Override
        public void onRemoval() {
            detach();
            super.onRemoval();
        }

        @Override
        public void onUnload() {
            detach();
            super.onUnload();
        }

        private void detach() {
            if (arrays != null && array != null) arrays.unregister(array);
            array = null;
            arrays = null;
        }

        @Override
        public void saveNBTData(NBTTagCompound nbt) {
            // Geometry and current-tick output are reconstructed after loading.
        }

        @Override
        public void loadNBTData(NBTTagCompound nbt) {
            detach();
            potentialAmps = 0;
            acceptedAmps = 0;
        }

        public long availableEUPerTick() {
            return potentialAmps * VOLTAGE;
        }

        public long acceptedEUPerTick() {
            return acceptedAmps * VOLTAGE;
        }

        @Override
        public String[] getDescription() {
            return new String[] { net.minecraft.util.StatCollector.translateToLocal("galaxia.solar.dynamo.desc") };
        }

        @Override
        public String[] getInfoData() {
            return new String[] { net.minecraft.util.StatCollector
                .translateToLocalFormatted("galaxia.solar.dynamo.info", availableEUPerTick(), acceptedEUPerTick()) };
        }
    }
}
