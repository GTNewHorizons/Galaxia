package com.gtnewhorizons.galaxia.client.gui.station;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.AssetModuleUpdatePacket;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class HammerConfigScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_hammer_config",
        HammerConfigScreen::new);

    private static final int PANEL_WIDTH = 270;
    private static final int PANEL_HEIGHT = 154;
    private static final int BODY_TOP = ModuleConfigScreenSupport.HEADER_HEIGHT + 10;
    private static final int BAR_WIDTH = PANEL_WIDTH - ModuleConfigScreenSupport.PANEL_PADDING * 2;

    private static volatile @Nullable CelestialAsset.ID pendingAssetId;
    private static volatile int pendingModuleIndex = -1;
    private static volatile @Nullable GuiScreen pendingReturnScreen;

    public static void open(CelestialAsset.ID assetId, int moduleIndex) {
        pendingAssetId = assetId;
        pendingModuleIndex = moduleIndex;
        pendingReturnScreen = Minecraft.getMinecraft().currentScreen;
        ModuleConfigScreenSupport.resetFactoryHolder(FACTORY);
        FACTORY.openClient();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(GuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(GuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        syncManager.syncValue(StarmapActionSyncHandler.KEY, new StarmapActionSyncHandler());
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_hammer_config", PANEL_WIDTH, PANEL_HEIGHT);
        ModuleConfigScreenSupport.addFrame(panel, "Hammer configuration", PANEL_WIDTH, PANEL_HEIGHT);

        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer)) {
            panel.child(
                IKey.str("No hammer selected")
                    .asWidget()
                    .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(ModuleConfigScreenSupport.PANEL_PADDING, BODY_TOP));
            panel.child(
                ModuleConfigScreenSupport.button("Close", this::close)
                    .pos(PANEL_WIDTH - 62, PANEL_HEIGHT - 28)
                    .size(54, 20));
            return panel;
        }

        panel.child(
            new HammerSummaryWidget().pos(ModuleConfigScreenSupport.PANEL_PADDING, BODY_TOP)
                .size(BAR_WIDTH, 64));
        panel.child(
            ModuleConfigScreenSupport.button("Variant", this::cycleVariant)
                .pos(ModuleConfigScreenSupport.PANEL_PADDING, PANEL_HEIGHT - 28)
                .size(66, 20));
        panel.child(
            ModuleConfigScreenSupport.button("Tier", this::cycleTier)
                .pos(80, PANEL_HEIGHT - 28)
                .size(54, 20));
        panel.child(
            ModuleConfigScreenSupport.button("Close", this::close)
                .pos(PANEL_WIDTH - 62, PANEL_HEIGHT - 28)
                .size(54, 20));
        return panel;
    }

    private void cycleVariant() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;
        HammerVariant next = hammer.variant() == HammerVariant.BASE ? HammerVariant.BIG : HammerVariant.BASE;
        ModuleTier nextTier = HammerConfigRules.tierForVariantSwitch(next, module.tier());
        if (nextTier != module.tier()) {
            CelestialClient.updateModuleConfig(
                pendingAssetId,
                pendingModuleIndex,
                AssetModuleUpdatePacket.ConfigAction.SET_TIER,
                nextTier);
        }
        CelestialClient.updateModuleConfig(
            pendingAssetId,
            pendingModuleIndex,
            AssetModuleUpdatePacket.ConfigAction.SET_HAMMER_VARIANT,
            next);
    }

    private void cycleTier() {
        ModuleInstance module = selectedModule();
        if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;
        CelestialClient.updateModuleConfig(
            pendingAssetId,
            pendingModuleIndex,
            AssetModuleUpdatePacket.ConfigAction.SET_TIER,
            HammerConfigRules.nextTier(hammer.variant(), module.tier()));
    }

    private static @Nullable ModuleInstance selectedModule() {
        return ModuleConfigScreenSupport.module(pendingAssetId, pendingModuleIndex);
    }

    private void close() {
        ModuleConfigScreenSupport.closeTo(pendingReturnScreen);
    }

    private static final class HammerSummaryWidget extends ParentWidget<HammerSummaryWidget> {

        @Override
        public boolean canHoverThrough() {
            return true;
        }

        @Override
        public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            ModuleInstance module = selectedModule();
            if (module == null || !(module.component() instanceof ModuleHammer hammer)) return;

            int x = getArea().x;
            int y = getArea().y;
            HammerVariant variant = hammer.variant();
            ModuleTier tier = module.tier();
            int chargeTicks = ModuleHammer.chargeTicks(variant, tier);
            long shotEnergy = ModuleHammer.shotEnergyEu(variant);
            long chargeRate = ModuleHammer.chargeRateEuPerTick(variant, tier);

            int lineY = y;
            lineY = ModuleConfigScreenSupport.drawLine(
                "Variant: " + variant.name() + "  Tier: " + tier.name(),
                x,
                lineY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            lineY = ModuleConfigScreenSupport.drawLine(
                "Shot: " + ModuleConfigScreenSupport.formatEu(shotEnergy)
                    + " EU  Rate: "
                    + ModuleConfigScreenSupport.formatEu(chargeRate)
                    + " EU/t",
                x,
                lineY,
                EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            lineY = ModuleConfigScreenSupport
                .drawLine("Charge: " + (chargeTicks / 20) + "s", x, lineY, EnumColors.MAP_COLOR_TEXT_BODY.getColor());

            int barY = lineY + 2;
            int chargeProgress = Math.min(Math.max(module.ticks(), 0), chargeTicks);
            int fillW = (int) ((long) getArea().width * chargeProgress / chargeTicks);
            Gui.drawRect(x, barY, x + getArea().width, barY + 8, EnumColors.MAP_COLOR_BTN_DISABLED.getColor());
            Gui.drawRect(x, barY, x + fillW, barY + 8, EnumColors.MAP_COLOR_SIDEBAR_CONFIRM_TEXT_ENABLED.getColor());
        }
    }
}
