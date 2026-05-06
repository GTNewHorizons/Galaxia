package com.gtnewhorizons.galaxia.client.gui.station;

import net.minecraft.client.gui.Gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

final class HammerConfigModalWidget extends ParentWidget<HammerConfigModalWidget> {

    static final int WIDTH = 270;
    static final int HEIGHT = 154;

    private static final int BODY_TOP = ModuleConfigModalSupport.HEADER_HEIGHT + 10;
    private static final int BAR_WIDTH = WIDTH - ModuleConfigModalSupport.PANEL_PADDING * 2;

    private final CelestialAsset.ID assetId;
    private final ModuleConfigModalController controller;

    HammerConfigModalWidget(CelestialAsset.ID assetId, ModuleConfigModalController controller) {
        this.assetId = assetId;
        this.controller = controller;
        child(
            ModuleConfigModalSupport.button(this::canUseControls, "Variant", this::cycleVariant)
                .pos(ModuleConfigModalSupport.PANEL_PADDING, HEIGHT - 28)
                .size(66, 20));
        child(
            ModuleConfigModalSupport.button(this::canUseControls, "Tier", this::cycleTier)
                .pos(80, HEIGHT - 28)
                .size(54, 20));
        child(
            ModuleConfigModalSupport.button(this::canUseControls, "Close", controller::close)
                .pos(WIDTH - 62, HEIGHT - 28)
                .size(54, 20));
    }

    @Override
    public boolean canHoverThrough() {
        return false;
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!controller.isHammerOpen()) return;
        ModuleConfigModalSupport.drawFrame("Hammer configuration", WIDTH, HEIGHT);
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) {
            ModuleConfigModalSupport.drawLine(
                "No hammer selected",
                ModuleConfigModalSupport.PANEL_PADDING,
                BODY_TOP,
                EnumColors.MAP_COLOR_TEXT_MUTED.getColor());
            return;
        }
        drawSummary(module, hammer);
    }

    private void drawSummary(ModuleInstance module, ModuleHammer hammer) {
        int x = ModuleConfigModalSupport.PANEL_PADDING;
        int y = BODY_TOP;
        HammerVariant variant = hammer.variant();
        ModuleTier tier = module.tier();
        int chargeTicks = ModuleHammer.chargeTicks(variant, tier);
        long shotEnergy = ModuleHammer.shotEnergyEu(variant);
        long chargeRate = ModuleHammer.chargeRateEuPerTick(variant, tier);

        int lineY = y;
        lineY = ModuleConfigModalSupport.drawLine(
            "Variant: " + variant.name() + "  Tier: " + tier.name(),
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport.drawLine(
            "Shot: " + ModuleConfigModalSupport.formatEu(shotEnergy)
                + " EU  Rate: "
                + ModuleConfigModalSupport.formatEu(chargeRate)
                + " EU/t",
            x,
            lineY,
            EnumColors.MAP_COLOR_TEXT_BODY.getColor());
        lineY = ModuleConfigModalSupport
            .drawLine("Charge: " + (chargeTicks / 20) + "s", x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());

        int barY = lineY + 2;
        int chargeProgress = Math.min(Math.max(module.ticks(), 0), chargeTicks);
        int fillW = (int) ((long) BAR_WIDTH * chargeProgress / chargeTicks);
        Gui.drawRect(x, barY, x + BAR_WIDTH, barY + 8, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
        Gui.drawRect(x, barY, x + fillW, barY + 8, EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
    }

    private void cycleVariant() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;
        HammerVariant next = hammer.variant() == HammerVariant.BASE ? HammerVariant.BIG : HammerVariant.BASE;
        ModuleTier nextTier = HammerConfigRules.tierForVariantSwitch(next, module.tier());
        if (nextTier != module.tier()) {
            CelestialClient.updateModuleConfig(
                assetId,
                controller.moduleIndex(),
                AssetModuleUpdatePacket.ConfigAction.SET_TIER,
                nextTier);
        }
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_HAMMER_VARIANT,
            next);
    }

    private void cycleTier() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;
        CelestialClient.updateModuleConfig(
            assetId,
            controller.moduleIndex(),
            AssetModuleUpdatePacket.ConfigAction.SET_TIER,
            HammerConfigRules.nextTier(hammer.variant(), module.tier()));
    }

    private boolean canUseControls() {
        return controller.isHammerOpen() && selectedModule() != null;
    }

    private ModuleInstance selectedModule() {
        return ModuleConfigModalSupport.module(assetId, controller.moduleIndex());
    }
}
