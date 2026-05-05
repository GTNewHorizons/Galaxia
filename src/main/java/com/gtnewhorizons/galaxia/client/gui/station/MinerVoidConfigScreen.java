package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SimpleGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.client.CelestialClient;
import com.gtnewhorizons.galaxia.client.EnumColors;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.BorderedRect;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.StarmapActionSyncHandler;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleMiner;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public final class MinerVoidConfigScreen implements IGuiHolder<GuiData> {

    public static final SimpleGuiFactory FACTORY = new SimpleGuiFactory(
        "galaxia_miner_void_config",
        MinerVoidConfigScreen::new);

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 252;
    private static final int BODY_TOP = ModuleConfigScreenSupport.HEADER_HEIGHT + 10;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int SCROLL_HEIGHT = 172;

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
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_miner_void_config", PANEL_WIDTH, PANEL_HEIGHT);
        ModuleConfigScreenSupport.addFrame(panel, "Miner void configuration", PANEL_WIDTH, PANEL_HEIGHT);

        AutomatedFacility facility = ModuleConfigScreenSupport.facility(pendingAssetId);
        ModuleInstance module = ModuleConfigScreenSupport.module(pendingAssetId, pendingModuleIndex);
        if (facility == null || module == null || !(module.component() instanceof ModuleMiner)) {
            panel.child(
                IKey.str("No miner selected")
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
            IKey.str("Set percent of this station's mined ore to void.")
                .asWidget()
                .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                .shadow(true)
                .pos(ModuleConfigScreenSupport.PANEL_PADDING, BODY_TOP));

        List<MinerVoidOptions.Entry> options = MinerVoidOptions.forFacility(facility);
        if (options.isEmpty()) {
            panel.child(
                IKey.str("No ores available on this body")
                    .asWidget()
                    .color(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
                    .shadow(true)
                    .pos(ModuleConfigScreenSupport.PANEL_PADDING, BODY_TOP + 18));
        } else {
            ScrollWidget<?> scroll = new ScrollWidget<>().pos(ModuleConfigScreenSupport.PANEL_PADDING, BODY_TOP + 18)
                .size(PANEL_WIDTH - ModuleConfigScreenSupport.PANEL_PADDING * 2, SCROLL_HEIGHT);
            int rowY = 0;
            for (MinerVoidOptions.Entry option : options) {
                scroll.child(
                    createRow(option).pos(0, rowY)
                        .size(PANEL_WIDTH - ModuleConfigScreenSupport.PANEL_PADDING * 2, ROW_HEIGHT));
                rowY += ROW_HEIGHT + ROW_GAP;
            }
            panel.child(scroll);
        }

        panel.child(
            ModuleConfigScreenSupport.button("Close", this::close)
                .pos(PANEL_WIDTH - 62, PANEL_HEIGHT - 28)
                .size(54, 20));
        return panel;
    }

    private ParentWidget<?> createRow(MinerVoidOptions.Entry option) {
        ParentWidget<?> row = new ParentWidget<>()
            .background(
                ModuleConfigScreenSupport.drawable(
                    (ctx, x, y, w, h) -> BorderedRect.draw(
                        x,
                        y,
                        w,
                        h,
                        EnumColors.MAP_COLOR_BTN_ENABLED_DEFAULT.getColor(),
                        EnumColors.MAP_COLOR_BTN_BORDER_ENABLED.getColor())))
            .overlay(ModuleConfigScreenSupport.drawable((ctx, x, y, w, h) -> {
                String name = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(option.displayName(), 126);
                Minecraft.getMinecraft().fontRenderer
                    .drawStringWithShadow(name, x + 5, y + 5, EnumColors.MAP_COLOR_TEXT_BODY.getColor());
            }));
        row.child(
            ModuleConfigScreenSupport.button("0", () -> setPercent(option.key(), 0))
                .pos(138, 4)
                .size(18, 10));
        row.child(
            ModuleConfigScreenSupport.button("-1", () -> addPercent(option.key(), -1))
                .pos(160, 4)
                .size(22, 10));
        row.child(
            createPercentField(option.key()).pos(186, 4)
                .size(30, 10));
        row.child(
            ModuleConfigScreenSupport.button("+1", () -> addPercent(option.key(), 1))
                .pos(220, 4)
                .size(22, 10));
        row.child(
            ModuleConfigScreenSupport.button("All", () -> setPercent(option.key(), 100))
                .pos(246, 4)
                .size(28, 10));
        return row;
    }

    private TextFieldWidget createPercentField(String oreKey) {
        return new TextFieldWidget().setMaxLength(3)
            .setPattern(Pattern.compile("[0-9]*"))
            .setDefaultNumber(0)
            .setNumbers(0, 100)
            .setFormatAsInteger(true)
            .acceptsExpressions(false)
            .autoUpdateOnChange(false)
            .setTextColor(EnumColors.MAP_COLOR_TEXT_TITLE.getColor())
            .hintColor(EnumColors.MAP_COLOR_TEXT_MUTED.getColor())
            .value(new StringValue.Dynamic(() -> {
                AutomatedFacility facility = ModuleConfigScreenSupport.facility(pendingAssetId);
                return facility == null ? "" : String.valueOf(facility.minerVoidChancePercent(oreKey));
            }, text -> {
                int parsed = 0;
                if (text != null && !text.isEmpty()) {
                    try {
                        parsed = Integer.parseInt(text);
                    } catch (NumberFormatException ignored) {
                        AutomatedFacility facility = ModuleConfigScreenSupport.facility(pendingAssetId);
                        parsed = facility == null ? 0 : facility.minerVoidChancePercent(oreKey);
                    }
                }
                setPercent(oreKey, parsed);
            }))
            .setFocusOnGuiOpen(false);
    }

    private void addPercent(String oreKey, int delta) {
        AutomatedFacility facility = ModuleConfigScreenSupport.facility(pendingAssetId);
        if (facility == null) return;
        setPercent(oreKey, facility.minerVoidChancePercent(oreKey) + delta);
    }

    private void setPercent(String oreKey, int percent) {
        ModuleInstance module = ModuleConfigScreenSupport.module(pendingAssetId, pendingModuleIndex);
        if (module == null || !(module.component() instanceof ModuleMiner)) return;
        CelestialClient.updateMinerVoidPercent(
            pendingAssetId,
            pendingModuleIndex,
            oreKey,
            AutomatedFacility.clampMinerVoidChancePercent(percent));
    }

    private void close() {
        ModuleConfigScreenSupport.closeTo(pendingReturnScreen);
    }
}
