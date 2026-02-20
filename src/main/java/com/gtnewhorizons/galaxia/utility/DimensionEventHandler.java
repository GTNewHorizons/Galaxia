package com.gtnewhorizons.galaxia.utility;

import com.gtnewhorizons.galaxia.dimension.DimensionDef;
import com.gtnewhorizons.galaxia.dimension.DimensionEnum;
import com.gtnewhorizons.galaxia.dimension.EffectDef;
import com.gtnewhorizons.galaxia.dimension.SolarSystemRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import scala.Int;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/**
 * A Handler class to deal with effects of entering a new Galaxia dimension
 */
public class DimensionEventHandler {

    public static final String MODID = "galaxia";
    public static final Logger LOG = LogManager.getLogger(MODID);

    // A HashMap of all UUID on server with the last dimension they were in
    private final Map<UUID, Integer> lastDimension = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayer player = event.player;
        UUID id = player.getUniqueID();
        int currentDim = player.dimension;
        // Only applies to first dimensional transfer, i.e. no "previous" dimension
        if (lastDimension.isEmpty()) {
            lastDimension.put(id, currentDim);
        }
        int prevDim = lastDimension.getOrDefault(id, currentDim);

        // Get all custom dimension IDs from Galaxia
        ArrayList<Integer> galaxiaDims = new ArrayList<>();
        for (DimensionEnum dim : DimensionEnum.values()) {
            galaxiaDims.add(dim.getId());
        }

        // Only apply to galaxia dims, and only immediately after transfer
        if (!(galaxiaDims.contains(currentDim))) return;
        if (prevDim == currentDim) return;
        applyEffects(SolarSystemRegistry.getById(currentDim).effects, player);

        // Update previous dimension map
        lastDimension.put(id, currentDim);
    }

    private void applyEffects(EffectDef def, EntityPlayer player) {
        if (def.withering) {
            LOG.info("WITHER");
            applyWithering(player);
        }
    }

    private void applyWithering(EntityPlayer player) {
        if (!player.isPotionActive(Potion.wither)) {
            player.addPotionEffect(new PotionEffect(Potion.wither.id, 200, 1));
        }
    }
}
