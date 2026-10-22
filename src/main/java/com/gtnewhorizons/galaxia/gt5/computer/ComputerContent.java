package com.gtnewhorizons.galaxia.gt5.computer;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.galaxia.compat.gt.computer.MTEComputer;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.FluidState;
import gregtech.api.fluid.GTFluidFactory;

/** Galaxia-hosted computing hardware and recipes, staged for later TecTech ownership. */
public final class ComputerContent {

    public enum RackTier {

        EV(5L, 1920L, 10),
        IV(20L, 7680L, 20),
        LUV(80L, 30720L, 40);

        private final long capacity;
        private final long eu;
        private final int coolant;

        RackTier(long capacity, long eu, int coolant) {
            this.capacity = capacity;
            this.eu = eu;
            this.coolant = coolant;
        }

        public long capacityDeciKb() {
            return capacity;
        }

        public long euPerTick() {
            return eu;
        }

        public int coolantPerTick() {
            return coolant;
        }
    }

    public static Block hardware;
    private static Fluid chilledCoolant;
    private static ItemStack controller;
    private static ItemStack cluster;
    private static ItemStack radiator;
    private static ItemStack port;
    private static ItemStack cable;

    private ComputerContent() {}

    public static void preInit() {
        hardware = new HardwareBlock();
        GameRegistry.registerBlock(hardware, HardwareItem.class, "computing_hardware");
        chilledCoolant = GTFluidFactory.of("galaxia.chilled_coolant", "Chilled Coolant", FluidState.LIQUID, 200);
    }

    public static void registerMachines() {
        controller = new MTEComputer(
            23060,
            "galaxia.computer_controller",
            "Computer Controller",
            MTEComputer.Kind.CONTROLLER).getStackForm(1L);
        cluster = new MTEComputer(23061, "galaxia.computing_cluster", "Computing Cluster", MTEComputer.Kind.CLUSTER)
            .getStackForm(1L);
        radiator = new MTEComputer(23062, "galaxia.coolant_radiator", "Coolant Radiator", MTEComputer.Kind.RADIATOR)
            .getStackForm(1L);
        port = new MTEInformationPort(23063, "galaxia.information_connector", "Information Connector").getStackForm(1L);
        cable = new MTEInformationCable(23064, "galaxia.information_cable", "Fluoride Information Cable")
            .getStackForm(1L);
    }

    public static FluidStack chilled(int amount) {
        return new FluidStack(chilledCoolant, amount);
    }

    public static FluidStack neutral(int amount) {
        return FluidRegistry.getFluidStack("ic2coolant", amount);
    }

    public static ItemStack controller() {
        return controller.copy();
    }

    public static ItemStack cluster() {
        return cluster.copy();
    }

    public static ItemStack radiator() {
        return radiator.copy();
    }

    public static ItemStack port() {
        return port.copy();
    }

    public static ItemStack cable() {
        return cable.copy();
    }

    private static final class HardwareBlock extends Block {

        private HardwareBlock() {
            super(Material.iron);
            setBlockName("galaxia.computing_hardware");
            setBlockTextureName("galaxia:machine/research_outpost_casing");
            setHardness(5.0F);
            setResistance(10.0F);
            setCreativeTab(CreativeTabs.tabRedstone);
        }

        @Override
        public int damageDropped(int meta) {
            return meta;
        }

        @Override
        public void getSubBlocks(Item item, CreativeTabs tab, List stacks) {
            for (int meta = 0; meta < 4; meta++) stacks.add(new ItemStack(item, 1, meta));
        }
    }

    public static final class HardwareItem extends ItemBlock {

        public HardwareItem(Block block) {
            super(block);
            setHasSubtypes(true);
        }

        @Override
        public int getMetadata(int damage) {
            return damage;
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            return "tile.galaxia.computing_hardware." + stack.getItemDamage();
        }

        @Override
        public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
            int meta = stack.getItemDamage();
            if (meta < 0 || meta >= RackTier.values().length) return;
            RackTier tier = RackTier.values()[meta];
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "galaxia.computer.rack.stats",
                    tier.capacityDeciKb() * 2L,
                    tier.euPerTick(),
                    tier.coolantPerTick()));
        }
    }
}
