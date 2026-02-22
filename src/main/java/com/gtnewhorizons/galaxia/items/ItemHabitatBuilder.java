package com.gtnewhorizons.galaxia.items;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.block.GalaxiaBlocks;
import com.gtnewhorizons.galaxia.client.gui.HabitatBuilderGui;
import com.gtnewhorizons.galaxia.modules.ModuleType;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;
import com.gtnewhorizons.galaxia.modules.TileEntityModuleController;

public class ItemHabitatBuilder extends Item implements IGuiHolder<GuiData> {

    // TODO fix item synchronisation issue (in creative only places chosen module on inventory open, in survival just
    // doesnt update)
    private static final String LANG_PREFIX = "galaxia.habitat_builder.";

    public ItemHabitatBuilder() {
        super();
        setMaxStackSize(1);
        setUnlocalizedName("habitatBuilder");
    }

    public static String getSelectedModule(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("selectedModule")) {
            return stack.getTagCompound()
                .getString("selectedModule");
        }
        return ModuleTypes.HUB_3X3.getId();
    }

    public static void setSelectedModule(ItemStack stack, String id) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("selectedModule", id);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            GuiFactories.item()
                .open(player);
        }
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        String moduleId = getSelectedModule(stack);
        ForgeDirection dir = ForgeDirection.getOrientation(side);

        int px = x + dir.offsetX;
        int py = y + dir.offsetY;
        int pz = z + dir.offsetZ;

        if (!world.isAirBlock(px, py, pz) && !world.getBlock(px, py, pz)
            .isReplaceable(world, px, py, pz)) {
            return false;
        }

        world.setBlock(px, py, pz, GalaxiaBlocks.moduleController);

        TileEntityModuleController te = (TileEntityModuleController) world.getTileEntity(px, py, pz);
        if (te != null) {
            te.setModule(moduleId);
            te.buildStructure();
        }

        return true;
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager guiSyncManager, UISettings uiSettings) {
        return new HabitatBuilderGui(guiData, guiSyncManager).build();
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String id = getSelectedModule(stack);
        ModuleType t = ModuleTypes.byId(id);
        if (t != null) {
            String name = StatCollector.translateToLocal("galaxia.module." + t.getId() + ".name");
            list.add(StatCollector.translateToLocalFormatted(LANG_PREFIX + "tooltip.selected", name));
        }
        list.add(StatCollector.translateToLocal(LANG_PREFIX + "tooltip.open"));
        list.add(StatCollector.translateToLocal(LANG_PREFIX + "tooltip.build"));
    }
}
