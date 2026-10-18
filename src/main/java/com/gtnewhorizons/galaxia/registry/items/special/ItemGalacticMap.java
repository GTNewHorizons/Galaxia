package com.gtnewhorizons.galaxia.registry.items.special;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.compat.teams.GTTeamsCompat;
import com.gtnewhorizons.galaxia.compat.teams.GalaxiaTeamData.Achievement;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemGalacticMap extends Item implements IGuiHolder<GuiData> {

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return GalacticMapWidget.createScreen(mainPanel);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            if (!GTTeamsCompat.hasAchievement(player, Achievement.STAR_MAP_ACTIVATION)) {
                player.addChatComponentMessage(new ChatComponentTranslation("galaxia.starmap.activation_required"));
                return stack;
            }
            GalacticMapWidget.prepareOpen((EntityPlayerMP) player);
            GuiFactories.item()
                .open(player);
        }
        return stack;
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        if (!GTTeamsCompat.hasAchievement(guiData.getPlayer(), Achievement.STAR_MAP_ACTIVATION)) {
            return ModularPanel.defaultPanel("galaxia:starmap_locked")
                .child(
                    IKey.lang("galaxia.starmap.activation_required")
                        .asWidget());
        }
        return GalacticMapWidget.build(syncManager, guiData.getPlayer());
    }
}
