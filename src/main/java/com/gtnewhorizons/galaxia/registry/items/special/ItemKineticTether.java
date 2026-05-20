package com.gtnewhorizons.galaxia.registry.items.special;

import static com.gtnewhorizons.galaxia.core.Galaxia.GALAXIA_NETWORK;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;

import com.gtnewhorizons.galaxia.core.network.TetherPacket;
import com.gtnewhorizons.galaxia.registry.items.tether.KineticTether;
import com.gtnewhorizons.galaxia.registry.items.tether.KineticTetherState;
import com.gtnewhorizons.galaxia.registry.items.tether.TetherData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemKineticTether extends Item {

    @SideOnly(Side.CLIENT)
    public static class ClientEventHandler {

        private static boolean wasLmbDown = false;
        private static boolean wasRmbDown = false;

        @SubscribeEvent
        public void onMouse(MouseEvent event) {

            EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().thePlayer;

            if (player == null) return;

            ItemStack held = player.getHeldItem();

            if (held == null || !(held.getItem() instanceof ItemKineticTether)) {

                wasLmbDown = false;
                wasRmbDown = false;
                return;
            }

            if (event.button == 0) {

                boolean down = event.buttonstate;

                if (down && !wasLmbDown) {

                    GALAXIA_NETWORK.sendToServer(new TetherPacket(TetherPacket.ACTION_START_ANCHOR));

                    event.setCanceled(true);

                } else if (!down && wasLmbDown) {

                    GALAXIA_NETWORK.sendToServer(new TetherPacket(TetherPacket.ACTION_END_ANCHOR));
                }

                wasLmbDown = down;
            }

            if (event.button == 1) {

                boolean down = event.buttonstate;

                if (down && !wasRmbDown) {

                    GALAXIA_NETWORK.sendToServer(new TetherPacket(TetherPacket.ACTION_START_PROPULSION));

                    event.setCanceled(true);

                } else if (!down && wasRmbDown) {

                    GALAXIA_NETWORK.sendToServer(new TetherPacket(TetherPacket.ACTION_END_PROPULSION));
                }

                wasRmbDown = down;
            }
        }

        @SubscribeEvent
        public void onLeftClickBlock(PlayerInteractEvent event) {
            if (event.action == Action.LEFT_CLICK_BLOCK && isHoldingTether(event.entityPlayer)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onRightClickBlock(PlayerInteractEvent event) {
            if (event.action == Action.RIGHT_CLICK_BLOCK && isHoldingTether(event.entityPlayer)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onRightClickItem(PlayerInteractEvent event) {
            if (event.action == Action.RIGHT_CLICK_AIR && isHoldingTether(event.entityPlayer)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void onAttackEntity(AttackEntityEvent event) {
            if (isHoldingTether(event.entityPlayer)) {
                event.setCanceled(true);
            }
        }

        private boolean isHoldingTether(EntityPlayer player) {
            ItemStack stack = player.getHeldItem();
            return stack != null && stack.getItem() instanceof ItemKineticTether;
        }
    }

    public static void onPlayerTick(EntityPlayer player) {

        TetherData data = KineticTetherState.get(player);

        if (data.tetherActive) {
            KineticTether.applyPhysics(player, data);
        }
    }
}
