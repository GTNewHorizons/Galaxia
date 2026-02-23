package com.gtnewhorizons.galaxia.items.special;

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
import com.gtnewhorizons.galaxia.block.GalaxiaBlocksEnum;
import com.gtnewhorizons.galaxia.block.tileentities.TileEntityModuleController;
import com.gtnewhorizons.galaxia.client.gui.HabitatBuilderGui;
import com.gtnewhorizons.galaxia.modules.ModuleType;
import com.gtnewhorizons.galaxia.modules.ModuleTypes;

/**
 * Class to contain the HabitatBuilder and GUI
 */
public class ItemHabitatBuilder extends Item implements IGuiHolder<GuiData> {

    // TODO fix builder not working in survival mode because of not updating held item

    /**
     * Constructor to set max stack of 1 and name
     */
    public ItemHabitatBuilder() {
        super();
        setMaxStackSize(1);
        setUnlocalizedName("habitatBuilder");
    }

    /**
     * Gets the selected module if there is one, if not returns the default module
     *
     * @param stack The item stack for the item
     * @return The selected module's string
     */
    public static String getSelectedModule(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("selectedModule")) {
            return stack.getTagCompound()
                .getString("selectedModule");
        }
        return ModuleTypes.HUB_3X3.getId();
    }

    /**
     * Sets the selected module NBT
     *
     * @param stack The item stack for the item
     * @param id    The ID of the module to set
     */
    public static void setSelectedModule(ItemStack stack, String id) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound()
            .setString("selectedModule", id);
    }

    /**
     * Handler for the right click action of the item (opening GUI etc.)
     *
     * @param stack  The item stack for the item
     * @param world  The world it is used in
     * @param player The player using the item
     * @return The item stack for the item
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            GuiFactories.item()
                .open(player);
        }
        return stack;
    }

    /**
     * The handler for when the item has been used (i.e. GUI screen closed by submitting
     *
     * @param stack  The item stack for the builder item
     * @param player The player using the item
     * @param world  The world it is being used in
     * @param x      X coordinate to place in world
     * @param y      Y coordinate to place in world
     * @param z      Z coordinate to place in world
     * @param side   The direction for the habitat to face
     * @param hitX   Not used in this implementation, required from original signature
     * @param hitY   Not used in this implementation, required from original signature
     * @param hitZ   Not used in this implementation, required from original signature
     * @return Boolean : True => successful item use and placement of module
     */
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

        world.setBlock(px, py, pz, GalaxiaBlocksEnum.MODULE_CONTROLLER.get());

        TileEntityModuleController te = (TileEntityModuleController) world.getTileEntity(px, py, pz);
        if (te != null) {
            te.setModule(moduleId);
            te.buildStructure();
        }

        return true;
    }

    /**
     * Creates the UI as a ModularPanel
     *
     * @param guiData        Data about the context of the GUI creation
     * @param guiSyncManager Sync handler for where widgets register
     * @param uiSettings     General settings for all of UI, not specific to this module
     * @return ModularPanel UI
     */
    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager guiSyncManager, UISettings uiSettings) {
        return new HabitatBuilderGui(guiData, guiSyncManager).build();
    }

    /**
     * Adds information to the item based on selected modules etc.
     *
     * @param stack    The item stack for the item
     * @param player   The player entity using the item
     * @param list     The list of information to add to
     * @param advanced Not used in this implementation, required from original signature
     */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        String id = getSelectedModule(stack);
        ModuleType t = ModuleTypes.byId(id);
        if (t != null) {
            String name = StatCollector.translateToLocal("galaxia.gui.module." + t.getId() + ".name");
            list.add(StatCollector.translateToLocalFormatted("galaxia.tooltip.habitat_builder.selected", name));
        }
        list.add(StatCollector.translateToLocal("galaxia.tooltip.habitat_builder.open"));
        list.add(StatCollector.translateToLocal("galaxia.tooltip.habitat_builder.build"));
    }
}
