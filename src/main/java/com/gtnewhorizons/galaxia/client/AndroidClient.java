package com.gtnewhorizons.galaxia.client;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.core.network.AndroidControlPacket;
import com.gtnewhorizons.galaxia.registry.android.EntityBody;
import com.gtnewhorizons.galaxia.registry.android.TileEntityBodyStation.ConnectionStatus;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import roadhog360.simpleskinbackport.ducks.INewBipedModel;

/** Client presentation for server-owned body control. */
@SideOnly(Side.CLIENT)
public final class AndroidClient {

    public static final ResourceLocation ROBOT_SKIN = new ResourceLocation(
        Galaxia.MODID,
        "textures/entity/android.png");

    private static final Set<Integer> ANDROID_ACTORS = new HashSet<>();
    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 7;
    private static final int BAR_RIGHT_OFFSET = 10;
    private static final int BAR_BOTTOM_OFFSET = 39;
    private static final int BATTERY_LABEL_BOTTOM_OFFSET = 72;
    private static final int CONTROL_HINT_BOTTOM_OFFSET = 85;
    private static final int STATION_STATUS_BOTTOM_OFFSET = 111;
    private static final int STATION_HINT_BOTTOM_OFFSET = 98;
    private static final long STATION_DISCONNECT_DURATION_NANOS = 2_500_000_000L;
    private static final int CIRCUIT_TOP_CLEARANCE = 80;
    private static final int CIRCUIT_BOTTOM_CLEARANCE = 108;
    private static final int CIRCUIT_VERTICAL_MARGIN = 5;
    private static final float CIRCUIT_LINE_WIDTH = 2F;
    private static final int CIRCUIT_COLOR = 0x805ACDFF;
    private static final int CIRCUIT_COORDINATE_SCALE = 1000;
    private static final int[][] CIRCUIT_TRACE = { { 0, 7 }, { 120, 7 }, { 150, 2 }, { 380, 2 }, { 410, 8 }, { 690, 8 },
        { 720, 3 }, { 1000, 3 } };
    private static final int[][] CIRCUIT_BRANCH = { { 0, 15 }, { 270, 15 }, { 300, 10 }, { 490, 10 }, { 520, 15 },
        { 780, 15 } };
    private static final int PANEL_COLOR = 0xCC15222A;
    private static final int BATTERY_COLOR = 0xFF5BDCD1;
    private static final int WARNING_COLOR = 0xFFFF6655;
    private static final int LOW_BATTERY_COLOR = 0xFFFFC55A;
    private static final int BATTERY_ALERT_WIDTH = 300;
    private static final int BATTERY_ALERT_HEIGHT = 52;
    private static final int BATTERY_ALERT_TITLE_OFFSET = 18;
    private static final int BATTERY_ALERT_HINT_OFFSET = 8;
    private static final float BATTERY_ALERT_TITLE_SCALE = 2F;
    private static final long BOOT_DURATION_NANOS = 850_000_000L;
    private static final long EYE_OPENING_DURATION_NANOS = 650_000_000L;
    private static final int BOOT_COLOR = 0x5ACDFF;
    private static final int BOOT_BACKGROUND_COLOR = 0x07151D;
    private static final long BIOLOGICAL_DAMAGE_DURATION_NANOS = 650_000_000L;
    private static final int BIOLOGICAL_DAMAGE_COLOR = 0xE52C45;
    private static final int BIOLOGICAL_DAMAGE_GLITCH_BANDS = 7;
    private static final int CONSOLE_BAR_WIDTH = 150;
    private static final int CONSOLE_BAR_OFFSET = 42;
    private static final int CONSOLE_WIDTH = 288;
    private static final int CONSOLE_ROWS = 5;
    private static final int CONSOLE_ROW_HEIGHT = 11;
    private static final int CONSOLE_TITLE_OFFSET = 50;
    private static final int CONSOLE_TOP_OFFSET = 28;
    private static final int CONSOLE_FRAME_TOP_OFFSET = 58;
    private static final int CONSOLE_FRAME_BOTTOM_OFFSET = 30;
    private static final int CONSOLE_FRAME_PADDING = 12;
    private static final String[] BOOT_CONSOLE_KEYS = { "galaxia.android.hud.boot.kernel",
        "galaxia.android.hud.boot.link", "galaxia.android.hud.boot.identity", "galaxia.android.hud.boot.memory",
        "galaxia.android.hud.boot.motors", "galaxia.android.hud.boot.sensors", "galaxia.android.hud.boot.power",
        "galaxia.android.hud.boot.cargo", "galaxia.android.hud.boot.telemetry", "galaxia.android.hud.boot.visuals",
        "galaxia.android.hud.boot.handoff", "galaxia.android.hud.boot.ready" };
    private static final int CONSOLE_SCANLINE_SPACING = 5;
    private static final String[] DESTRUCTION_CONSOLE_KEYS = { "galaxia.android.hud.destruction.rogue",
        "galaxia.android.hud.destruction.lock", "galaxia.android.hud.destruction.arming",
        "galaxia.android.hud.destruction.priming", "galaxia.android.hud.destruction.safety",
        "galaxia.android.hud.destruction.charge", "galaxia.android.hud.destruction.ready",
        "galaxia.android.hud.destruction.imminent" };
    private static final int DESTRUCTION_BACKGROUND_COLOR = 0x21070B;
    private static final String[] PORTAL_WARNING_KEYS = { "galaxia.android.hud.portal.blocked",
        "galaxia.android.hud.portal.local", "galaxia.android.hud.portal.retreat", "galaxia.android.hud.portal.cancel",
        "galaxia.android.hud.portal.deadline" };
    private static final int EYELID_SEGMENTS = 64;
    private static final int EYELID_COLOR = 0x05080A;
    private static final float CAPE_MODEL_SCALE = 0.0625F;
    private static final float CAPE_BACK_OFFSET = 0.125F;
    private static final float CAPE_REST_ANGLE = 6;

    private static boolean active;
    private static long battery;
    private static long capacity;
    private static int countdown = -1;
    private static boolean portalWarning;
    private static int countdownSpan;
    private static long biologicalDamageStarted;
    // World unload also runs for dimension changes. This role belongs to the connection instead.
    private static boolean acknowledgedAndroid;
    private static boolean bootTransition;
    private static long transitionStarted;
    private static ConnectionStatus stationConnection = ConnectionStatus.NONE;
    private static ConnectionStatus disconnectedStation = ConnectionStatus.NONE;
    private static long stationDisconnectedAt;

    public static boolean isAndroid(EntityPlayer player) {
        return player != null && player.worldObj == Minecraft.getMinecraft().theWorld
            && ANDROID_ACTORS.contains(player.getEntityId());
    }

    public static void status(int actorId, boolean controlled, long charge, long maximumCharge, int remainingTicks,
        boolean retreatAllowed, boolean explosion) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (controlled) ANDROID_ACTORS.add(actorId);
        else ANDROID_ACTORS.remove(actorId);
        if (actorId == mc.thePlayer.getEntityId()) {
            if (controlled != acknowledgedAndroid) {
                acknowledgedAndroid = controlled;
                bootTransition = controlled;
                transitionStarted = System.nanoTime();
            }
            active = controlled;
            battery = charge;
            capacity = maximumCharge;
            if (countdown < 0 && remainingTicks >= 0) countdownSpan = Math.max(1, remainingTicks);
            countdown = remainingTicks;
            portalWarning = retreatAllowed;
            if (!controlled) biologicalDamageStarted = 0;
        }
        if (explosion) {
            Entity actor = mc.theWorld.getEntityByID(actorId);
            if (actor != null) {
                Vec3 look = actor.getLookVec();
                double x = actor.posX + look.xCoord * 1.5;
                double y = actor.posY + actor.getEyeHeight() + look.yCoord * 1.5;
                double z = actor.posZ + look.zCoord * 1.5;
                // The server owns positional audio. This local effect has no damage, sound or knockback.
                mc.theWorld.spawnParticle("hugeexplosion", x, y, z, 0, 0, 0);
            }
        }
    }

    public static void biologicalDamage(int actorEntityId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (active && mc.thePlayer != null && actorEntityId == mc.thePlayer.getEntityId()) {
            biologicalDamageStarted = System.nanoTime();
        }
    }

    public static void stationConnection(ConnectionStatus status) {
        if (status == ConnectionStatus.NONE && stationConnection != ConnectionStatus.NONE
            && stationConnection != ConnectionStatus.WRONG_DOCK) {
            disconnectedStation = stationConnection;
            stationDisconnectedAt = System.nanoTime();
        } else {
            disconnectedStation = ConnectionStatus.NONE;
        }
        stationConnection = status;
    }

    private static void resetWorldPresentation() {
        ANDROID_ACTORS.clear();
        active = false;
        battery = 0;
        capacity = 0;
        countdown = -1;
        countdownSpan = 0;
        biologicalDamageStarted = 0;
        stationConnection = ConnectionStatus.NONE;
        disconnectedStation = ConnectionStatus.NONE;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        resetWorldPresentation();
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                resetWorldPresentation();
                acknowledgedAndroid = false;
                transitionStarted = 0;
            });
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getMinecraft()
            .isGamePaused() && countdown > 0) countdown--;
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (GalaxiaKeyBinds.END_ANDROID_CONTROL.isPressed() && active) {
            Galaxia.GALAXIA_NETWORK.sendToServer(new AndroidControlPacket.EndRequest());
        }
    }

    @SubscribeEvent
    public void hideFood(RenderGameOverlayEvent.Pre event) {
        if (active && event.type == RenderGameOverlayEvent.ElementType.FOOD) event.setCanceled(true);
    }

    @SubscribeEvent
    public void drawStatus(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null
            || mc.gameSettings.hideGUI
            || mc.thePlayer.isDead
            || mc.thePlayer.getHealth() <= 0
            || mc.currentScreen instanceof GuiGameOver) return;
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        if (active) drawAndroidStatus(width, height);
        drawStationStatus(width, height);
        drawEffects(width, height);
        if (active && countdown < 0 && transitionStarted == 0) drawBatteryAlert(width, height);
    }

    private static void drawStationStatus(int width, int height) {
        if (stationConnection == ConnectionStatus.NONE) {
            if (disconnectedStation == ConnectionStatus.NONE
                || System.nanoTime() - stationDisconnectedAt >= STATION_DISCONNECT_DURATION_NANOS) return;
            boolean capsule = disconnectedStation == ConnectionStatus.CAPSULE_READY
                || disconnectedStation == ConnectionStatus.CAPSULE_OFFLINE;
            centered(
                StatCollector.translateToLocal(
                    "galaxia.android.hud.station." + (capsule ? "capsule" : "dock") + ".disconnected"),
                width / 2,
                height - STATION_STATUS_BOTTOM_OFFSET,
                LOW_BATTERY_COLOR);
            return;
        }
        boolean ready = stationConnection == ConnectionStatus.CAPSULE_READY
            || stationConnection == ConnectionStatus.DOCK_CONNECTED;
        centered(
            StatCollector.translateToLocal(
                "galaxia.android.hud.station." + stationConnection.name()
                    .toLowerCase(Locale.ROOT)),
            width / 2,
            height - (stationConnection == ConnectionStatus.DOCK_CONNECTED ? STATION_HINT_BOTTOM_OFFSET
                : STATION_STATUS_BOTTOM_OFFSET),
            ready ? BATTERY_COLOR : LOW_BATTERY_COLOR);
        if (stationConnection == ConnectionStatus.DOCK_CONNECTED) return;
        String hint = switch (stationConnection) {
            case CAPSULE_READY -> "capsule";
            case WRONG_DOCK -> "wrong_dock";
            default -> "offline";
        };
        centered(
            StatCollector.translateToLocalFormatted(
                "galaxia.android.hud.station.hint." + hint,
                GameSettings.getKeyDisplayString(GalaxiaKeyBinds.END_ANDROID_CONTROL.getKeyCode())),
            width / 2,
            height - STATION_HINT_BOTTOM_OFFSET,
            0xFFFFFF);
    }

    private static void drawBatteryAlert(int width, int height) {
        if (capacity <= 0) return;
        double percent = (double) battery / capacity * 100;
        if (percent > ConfigMachines.android.lowBatteryPercent) return;
        boolean critical = percent <= ConfigMachines.android.criticalBatteryPercent;
        int color = critical ? WARNING_COLOR : LOW_BATTERY_COLOR;
        int center = width / 2;
        int middle = height / 2;
        int panelWidth = Math.min(BATTERY_ALERT_WIDTH, width);
        Gui.drawRect(
            center - panelWidth / 2,
            middle - BATTERY_ALERT_HEIGHT / 2,
            center + panelWidth / 2,
            middle + BATTERY_ALERT_HEIGHT / 2,
            PANEL_COLOR);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(center, middle - BATTERY_ALERT_TITLE_OFFSET, 0);
            GL11.glScalef(BATTERY_ALERT_TITLE_SCALE, BATTERY_ALERT_TITLE_SCALE, 1);
            centered(
                StatCollector.translateToLocal(
                    critical ? "galaxia.android.hud.critical_battery" : "galaxia.android.hud.low_battery"),
                0,
                0,
                color);
        } finally {
            GL11.glPopMatrix();
        }
        centered(
            StatCollector.translateToLocal("galaxia.android.hud.recharge"),
            center,
            middle + BATTERY_ALERT_HINT_OFFSET,
            color);
    }

    private static void drawAndroidStatus(int width, int height) {
        int center = width / 2;
        drawCircuits(width, height);
        double ratio = capacity > 0 ? Math.max(0, Math.min(1, (double) battery / capacity)) : 0;
        double lowBatteryRatio = ConfigMachines.android.lowBatteryPercent / 100.0;
        int color = ratio <= lowBatteryRatio ? WARNING_COLOR : BATTERY_COLOR;
        int x = center + BAR_RIGHT_OFFSET;
        int y = height - BAR_BOTTOM_OFFSET;
        Gui.drawRect(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, PANEL_COLOR);
        Gui.drawRect(x + 1, y + 1, x + 1 + (int) ((BAR_WIDTH - 2) * ratio), y + BAR_HEIGHT - 1, color);
        centered(
            StatCollector.translateToLocalFormatted("galaxia.android.hud.battery", battery, capacity),
            center,
            height - BATTERY_LABEL_BOTTOM_OFFSET,
            color);
        centered(
            StatCollector.translateToLocalFormatted(
                "galaxia.android.hud.end_control",
                GameSettings.getKeyDisplayString(GalaxiaKeyBinds.END_ANDROID_CONTROL.getKeyCode())),
            center,
            height - CONTROL_HINT_BOTTOM_OFFSET,
            0xFFFFFF);
    }

    private static void drawEffects(int width, int height) {
        long now = System.nanoTime();
        long duration = bootTransition ? BOOT_DURATION_NANOS : EYE_OPENING_DURATION_NANOS;
        float transitionProgress = (float) ((double) (now - transitionStarted) / duration);
        if (transitionProgress >= 1) transitionStarted = 0;
        float damageProgress = (float) ((double) (now - biologicalDamageStarted) / BIOLOGICAL_DAMAGE_DURATION_NANOS);
        if (damageProgress >= 1) biologicalDamageStarted = 0;
        boolean destruction = active && countdown >= 0;
        if (transitionStarted == 0 && biologicalDamageStarted == 0 && !destruction) return;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (destruction) drawDestruction(width, height);
            else if (transitionStarted != 0) {
                if (bootTransition) drawBoot(width, height, transitionProgress);
                else drawOpeningEyes(width, height, transitionProgress);
            }
            if (biologicalDamageStarted != 0) drawBiologicalDamage(width, height, damageProgress);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawBoot(int width, int height, float progress) {
        float opacity = 1 - smoothStep(Math.max(0, (progress - .2F) / .8F));
        drawConsoleFrame(width, height, progress, BOOT_COLOR, BOOT_BACKGROUND_COLOR, opacity);
        float textOpacity = 1 - smoothStep(Math.max(0, (progress - .65F) / .35F));
        if (textOpacity > .05F) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            int color = (int) (textOpacity * 255) << 24 | BOOT_COLOR;
            centered(
                StatCollector.translateToLocal("galaxia.android.hud.boot"),
                width / 2,
                height / 2 - CONSOLE_TITLE_OFFSET,
                color);
            drawConsole(width, height, progress, color, BOOT_CONSOLE_KEYS);
        }
    }

    private static void drawDestruction(int width, int height) {
        float progress = Math.max(0, Math.min(1, 1 - (float) countdown / Math.max(1, countdownSpan)));
        int color = WARNING_COLOR & 0xFFFFFF;
        drawConsoleFrame(width, height, progress, color, DESTRUCTION_BACKGROUND_COLOR, .2F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        centered(
            StatCollector.translateToLocal(
                portalWarning ? "galaxia.android.hud.portal.title" : "galaxia.android.hud.destruction.title"),
            width / 2,
            height / 2 - CONSOLE_TITLE_OFFSET,
            WARNING_COLOR);
        drawConsole(
            width,
            height,
            progress,
            WARNING_COLOR,
            portalWarning ? PORTAL_WARNING_KEYS : DESTRUCTION_CONSOLE_KEYS);
        centered(
            StatCollector.translateToLocalFormatted("galaxia.android.hud.self_destruct", (countdown + 19) / 20),
            width / 2,
            height / 2 + CONSOLE_BAR_OFFSET + CONSOLE_ROW_HEIGHT,
            WARNING_COLOR);
    }

    private static void drawConsoleFrame(int width, int height, float progress, int color, int background,
        float opacity) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int center = width / 2;
        int middle = height / 2;
        int barWidth = Math.min(CONSOLE_BAR_WIDTH, width / 2);
        int left = center - barWidth / 2;
        int barY = middle + CONSOLE_BAR_OFFSET;
        int consoleWidth = Math.min(CONSOLE_WIDTH, width - CONSOLE_FRAME_PADDING * 2);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        transitionQuad(tessellator, 0, 0, width, height, background, opacity);
        for (int y = 0; y < height; y += CONSOLE_SCANLINE_SPACING) {
            transitionQuad(tessellator, 0, y, width, y + 1, color, opacity * .08F);
        }
        transitionQuad(tessellator, left - 1, barY - 1, left + barWidth + 1, barY + 4, color, opacity * .3F);
        transitionQuad(
            tessellator,
            left,
            barY,
            left + barWidth * smoothStep(progress),
            barY + 3,
            color,
            Math.max(.6F, opacity));
        int bracket = Math.max(8, consoleWidth / 12);
        for (int side = -1; side <= 1; side += 2) {
            int edge = center + side * (consoleWidth / 2 + CONSOLE_FRAME_PADDING);
            transitionQuad(
                tessellator,
                edge,
                middle - CONSOLE_FRAME_TOP_OFFSET,
                edge + 1,
                middle + CONSOLE_FRAME_BOTTOM_OFFSET,
                color,
                Math.max(.4F, opacity * .8F));
            transitionQuad(
                tessellator,
                side < 0 ? edge : edge - bracket,
                middle - CONSOLE_FRAME_TOP_OFFSET,
                side < 0 ? edge + bracket : edge + 1,
                middle - CONSOLE_FRAME_TOP_OFFSET + 1,
                color,
                Math.max(.4F, opacity * .8F));
        }
        tessellator.draw();
    }

    private static void drawConsole(int width, int height, float progress, int color, String[] keys) {
        int lineCount = Math.min(keys.length, 1 + (int) (progress / .75F * (keys.length - 1)));
        int firstLine = Math.max(0, lineCount - CONSOLE_ROWS);
        int consoleWidth = Math.min(CONSOLE_WIDTH, width - CONSOLE_FRAME_PADDING * 2);
        int left = (width - consoleWidth) / 2;
        int top = height / 2 - CONSOLE_TOP_OFFSET;
        Minecraft mc = Minecraft.getMinecraft();
        for (int line = firstLine; line < lineCount; line++) {
            String text = StatCollector.translateToLocal(keys[line]);
            if (line == lineCount - 1 && ((int) (progress * 12) & 1) == 0) text += "_";
            mc.fontRenderer.drawStringWithShadow(
                mc.fontRenderer.trimStringToWidth(text, consoleWidth),
                left,
                top + (line - firstLine) * CONSOLE_ROW_HEIGHT,
                color);
        }
    }

    private static void drawBiologicalDamage(int width, int height, float progress) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        float opacity = (1 - progress) * .36F;
        int phase = (int) (progress * 18);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        transitionQuad(tessellator, 0, 0, width, height, BIOLOGICAL_DAMAGE_COLOR, opacity);
        for (int band = 0; band < BIOLOGICAL_DAMAGE_GLITCH_BANDS; band++) {
            int y = (height * band / BIOLOGICAL_DAMAGE_GLITCH_BANDS + phase * 17) % height;
            transitionQuad(
                tessellator,
                0,
                y,
                width,
                Math.min(height, y + 2 + (band + phase) % 5),
                BIOLOGICAL_DAMAGE_COLOR,
                opacity * .8F);
        }
        tessellator.draw();
        if (opacity > .02F) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            centered(
                StatCollector.translateToLocal("galaxia.android.hud.biological_damage"),
                width / 2 + (phase % 3 - 1) * 2,
                height / 4,
                (int) ((1 - progress) * 255) << 24 | BIOLOGICAL_DAMAGE_COLOR);
        }
    }

    private static void drawOpeningEyes(int width, int height, float progress) {
        float opening = smoothStep(progress);
        float opacity = 1 - smoothStep(Math.max(0, (progress - .8F) / .2F));
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(
            (EYELID_COLOR >> 16 & 255) / 255F,
            (EYELID_COLOR >> 8 & 255) / 255F,
            (EYELID_COLOR & 255) / 255F,
            opacity);
        for (int segment = 0; segment < EYELID_SEGMENTS; segment++) {
            double x1 = (double) width * segment / EYELID_SEGMENTS;
            double x2 = (double) width * (segment + 1) / EYELID_SEGMENTS;
            double top1 = eyelidEdge(x1, width, height, opening);
            double top2 = eyelidEdge(x2, width, height, opening);
            tessellator.addVertex(x1, 0, 0);
            tessellator.addVertex(x1, top1, 0);
            tessellator.addVertex(x2, top2, 0);
            tessellator.addVertex(x2, 0, 0);
            tessellator.addVertex(x1, height - top1, 0);
            tessellator.addVertex(x1, height, 0);
            tessellator.addVertex(x2, height, 0);
            tessellator.addVertex(x2, height - top2, 0);
        }
        tessellator.draw();
    }

    private static double eyelidEdge(double x, int width, int height, float opening) {
        double curveX = (2 * x / width - 1) / (.12 + 1.9 * opening);
        double aperture = height * .75 * opening * Math.sqrt(Math.max(0, 1 - curveX * curveX));
        return Math.max(0, height / 2.0 - aperture);
    }

    private static float smoothStep(float value) {
        return value * value * (3 - 2 * value);
    }

    private static void transitionQuad(Tessellator tessellator, double left, double top, double right, double bottom,
        int color, float opacity) {
        tessellator
            .setColorRGBA_F((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, opacity);
        tessellator.addVertex(left, bottom, 0);
        tessellator.addVertex(right, bottom, 0);
        tessellator.addVertex(right, top, 0);
        tessellator.addVertex(left, top, 0);
    }

    private static void centered(String text, int center, int y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRenderer.drawStringWithShadow(text, center - mc.fontRenderer.getStringWidth(text) / 2, y, color);
    }

    private static void drawCircuits(int width, int height) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(CIRCUIT_LINE_WIDTH);
        GL11.glColor4f(
            (CIRCUIT_COLOR >> 16 & 255) / 255F,
            (CIRCUIT_COLOR >> 8 & 255) / 255F,
            (CIRCUIT_COLOR & 255) / 255F,
            (CIRCUIT_COLOR >>> 24) / 255F);
        for (int corner = 0; corner < 4; corner++) {
            boolean bottom = corner >= 2;
            boolean right = (corner & 1) != 0;
            int clearance = bottom ? CIRCUIT_BOTTOM_CLEARANCE : CIRCUIT_TOP_CLEARANCE;
            int span = Math.max(0, width / 2 - clearance);
            for (int[][] trace : new int[][][] { CIRCUIT_TRACE, CIRCUIT_BRANCH }) {
                Tessellator tessellator = Tessellator.instance;
                tessellator.startDrawing(GL11.GL_LINE_STRIP);
                for (int[] point : trace) {
                    int x = span * point[0] / CIRCUIT_COORDINATE_SCALE;
                    int y = CIRCUIT_VERTICAL_MARGIN + point[1];
                    tessellator.addVertex(right ? width - x : x, bottom ? height - y : y, 0);
                }
                tessellator.draw();
            }
        }
        GL11.glPopAttrib();
    }

    /** Both projections use the ordinary biped equipment renderer, with distinct biological and android appearance. */
    public static final class BodyRenderer extends RenderBiped {

        public BodyRenderer() {
            super(createBodyModel(), 0.5F);
        }

        private static ModelBiped createBodyModel() {
            ModelBiped model = new ModelBiped();
            if (Loader.isModLoaded("simpleskinbackport")) configureSkinLayers(model);
            return model;
        }

        @Optional.Method(modid = "simpleskinbackport")
        private static void configureSkinLayers(ModelBiped model) {
            ((INewBipedModel) model).ssb$set64x();
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityLiving entity) {
            EntityBody body = (EntityBody) entity;
            return body.isBiological() ? body.biologicalSkin() : ROBOT_SKIN;
        }

        @Override
        protected void renderEquippedItems(EntityLiving entity, float partialTicks) {
            super.renderEquippedItems(entity, partialTicks);
            EntityBody body = (EntityBody) entity;
            if (!body.isBiological() || body.isInvisible()) return;
            ResourceLocation cape = body.biologicalCape();
            if (cape == null) return;
            GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
            GL11.glPushMatrix();
            try {
                bindTexture(cape);
                GL11.glColor4f(1, 1, 1, 1);
                GL11.glTranslatef(0, 0, CAPE_BACK_OFFSET);
                GL11.glRotatef(CAPE_REST_ANGLE, 1, 0, 0);
                GL11.glRotatef(180, 0, 1, 0);
                modelBipedMain.renderCloak(CAPE_MODEL_SCALE);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
    }
}
