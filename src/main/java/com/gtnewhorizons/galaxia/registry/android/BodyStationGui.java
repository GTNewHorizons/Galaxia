package com.gtnewhorizons.galaxia.registry.android;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentTranslation;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.MathUtils;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizons.galaxia.api.GalaxiaCelestialAPI;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticMapWidget;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.core.network.PacketUtil;
import com.gtnewhorizons.galaxia.core.network.ServerTickTaskQueue;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectKey;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Owns station presentation and each open capsule's navigation, selection and rename draft. */
final class BodyStationGui {

    private static final int PANEL_WIDTH = 244;
    private static final int PANEL_HEIGHT = 256;
    private static final int PANEL_PADDING = 8;
    private static final int INVENTORY_BOTTOM = 7;
    private static final int CONTROL_WIDTH = PANEL_WIDTH - 2 * PANEL_PADDING;
    private static final int CONTROL_HEIGHT = 112;
    private static final int SELECTION_LIST_HEIGHT = 96;
    private static final int BODY_ROW_HEIGHT = 24;
    private static final int BODY_BUTTON_WIDTH = 166;
    private static final int RENAME_BUTTON_WIDTH = 58;
    private static final int NAME_PANEL_WIDTH = 228;
    private static final int NAME_PANEL_HEIGHT = 100;
    private static final int NAME_BUTTON_WIDTH = 100;
    private static final int NAME_BUTTON_HEIGHT = 22;
    private static final int NAME_ACTION_Y = 62;
    private static final int BODY_ID_PREFIX_LENGTH = 8;
    private static final int DOCK_CONTENT_Y = 58;
    private static final int DOCK_CONTENT_HEIGHT = 84;
    private static final int DOCK_GRID_COLUMNS = 3;
    private static final int DOCK_SLOT_PITCH = 20;
    private static final int DOCK_GRID_Y = 26;
    private static final int DOCK_BODY_X = 156;
    private static final int DOCK_BODY_Y = 10;
    private static final int DOCK_BODY_WIDTH = 48;
    private static final int DOCK_BODY_HEIGHT = 64;
    private static final int DOCK_HINT_Y = 146;
    private static final int DOCK_ENERGY_BAR_Y = 32;
    private static final int DOCK_ENERGY_BAR_HEIGHT = 4;
    private static final int DOCK_STATUS_Y = 40;
    private static final int CAPSULE_STATUS_Y = 36;

    /** Atlas regions in assembly order. The torso region also includes the neck. */
    private enum AssemblyPart {

        LEFT_ARM(4, 18, 8, 26),
        TORSO(14, 16, 20, 26),
        RIGHT_ARM(36, 18, 8, 26),
        LEFT_LEG(14, 44, 8, 20),
        RIGHT_LEG(26, 44, 8, 20),
        HEAD(16, 0, 16, 16);

        private final int x;
        private final int y;
        private final int width;
        private final int height;

        AssemblyPart(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    private final TileEntityBodyStation station;

    BodyStationGui(TileEntityBodyStation station) {
        this.station = station;
    }

    ModularPanel build(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        settings.canInteractWith(player -> player.worldObj.isRemote || station.isUseableByPlayer(player));
        BooleanSyncValue valid = new BooleanSyncValue(station::isStructureValid, value -> {});
        BooleanSyncValue power = new BooleanSyncValue(station::isPowered, value -> {});
        IntSyncValue energyValue = new IntSyncValue(() -> (int) station.storedEnergy(), value -> {});
        IntSyncValue progress = new IntSyncValue(station::processingProgress, value -> {});
        IntSyncValue duration = new IntSyncValue(station::processingDuration, value -> {});
        BooleanSyncValue failed = new BooleanSyncValue(station::operationFailed, value -> {});
        syncManager.syncValue("formed", valid);
        syncManager.syncValue("powered", power);
        syncManager.syncValue("energy", energyValue);
        syncManager.syncValue("progress", progress);
        syncManager.syncValue("duration", duration);
        syncManager.syncValue("failed", failed);
        ModularPanel panel = GalaxiaGuiStyle.panel(station.kind().blockName, PANEL_WIDTH, PANEL_HEIGHT)
            .child(
                GalaxiaGuiStyle.playerInventory()
                    .leftRel(.5f)
                    .bottom(INVENTORY_BOTTOM));
        panel.child(
            GalaxiaGuiStyle.title(station.getInventoryName())
                .pos(PANEL_PADDING, 6));
        if (!station.isCapsule()) panel.child(
            GalaxiaGuiStyle
                .bar(() -> (double) energyValue.getIntValue() / station.energyCapacity(), () -> GalaxiaGuiStyle.ACCENT)
                .pos(PANEL_PADDING, DOCK_ENERGY_BAR_Y)
                .size(CONTROL_WIDTH, DOCK_ENERGY_BAR_HEIGHT));
        panel.child(
            IKey.dynamic(
                () -> IKey.lang("galaxia.android.station.energy", energyValue.getIntValue(), station.energyCapacity())
                    .get())
                .asWidget()
                .color(GalaxiaGuiStyle.TEXT)
                .pos(PANEL_PADDING, 22));
        panel.child(
            IKey.dynamic(
                () -> IKey
                    .lang(
                        !valid.getBoolValue() ? "galaxia.android.station.status.unformed"
                            : !power.getBoolValue() ? "galaxia.android.station.status.unpowered"
                                : failed.getBoolValue() ? "galaxia.android.station.status.failed"
                                    : !station.isCapsule() && duration.getIntValue() > 0
                                        ? "galaxia.android.station.status.processing"
                                        : "galaxia.android.station.status.ready")
                    .get())
                .asWidget()
                .color(
                    () -> !valid.getBoolValue() || failed.getBoolValue() ? GalaxiaGuiStyle.BAD
                        : !power.getBoolValue() ? GalaxiaGuiStyle.WARN
                            : !station.isCapsule() && duration.getIntValue() > 0 ? GalaxiaGuiStyle.ACCENT
                                : GalaxiaGuiStyle.GOOD)
                .pos(PANEL_PADDING, station.isCapsule() ? CAPSULE_STATUS_Y : DOCK_STATUS_Y));
        if (station.isCapsule()) panel.child(
            new CapsuleControls(data).build(syncManager)
                .pos(PANEL_PADDING, 54));
        else {
            DoubleSupplier fraction = () -> duration.getIntValue() > 0
                ? (double) progress.getIntValue() / duration.getIntValue()
                : 0;
            ParentWidget<?> process = new ParentWidget<>().pos(PANEL_PADDING, DOCK_CONTENT_Y)
                .size(CONTROL_WIDTH, DOCK_CONTENT_HEIGHT)
                .background(EnumTextures.GUI_WELL.getImage());
            process.child(
                IKey.lang("galaxia.android.station.materials")
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT_DIM)
                    .pos(PANEL_PADDING, PANEL_PADDING));
            for (int i = 0; i < station.inputInventory()
                .getSlots(); i++)
                process.child(
                    new ItemSlot().slot(new ModularSlot(station.inputInventory(), i))
                        .background(EnumTextures.GUI_SLOT.getImage())
                        .pos(
                            PANEL_PADDING + i % DOCK_GRID_COLUMNS * DOCK_SLOT_PITCH,
                            DOCK_GRID_Y + i / DOCK_GRID_COLUMNS * DOCK_SLOT_PITCH));
            ParentWidget<?> assembly = new ParentWidget<>().name("android.dock.progress")
                .pos(DOCK_BODY_X, DOCK_BODY_Y)
                .size(DOCK_BODY_WIDTH, DOCK_BODY_HEIGHT)
                .background(
                    EnumTextures.ANDROID_ASSEMBLY.getImage()
                        .getSubArea(0, 0, 1, .5f)
                        .withColorOverride(GalaxiaGuiStyle.BAR_EDGE));
            AssemblyPart[] parts = AssemblyPart.values();
            for (AssemblyPart part : parts) {
                DoubleSupplier assembledRows = () -> Math
                    .floor(MathUtils.clamp(fraction.getAsDouble() * parts.length - part.ordinal(), 0, 1) * part.height)
                    / part.height;
                assembly.child(
                    new ProgressWidget().value(new DoubleValue.Dynamic(assembledRows, null))
                        .texture(
                            null,
                            EnumTextures.ANDROID_ASSEMBLY.getImage()
                                .getSubArea(
                                    (float) part.x / DOCK_BODY_WIDTH,
                                    (float) (DOCK_BODY_HEIGHT + part.y) / (2 * DOCK_BODY_HEIGHT),
                                    (float) (part.x + part.width) / DOCK_BODY_WIDTH,
                                    (float) (DOCK_BODY_HEIGHT + part.y + part.height) / (2 * DOCK_BODY_HEIGHT))
                                .withColorOverride(GalaxiaGuiStyle.ACCENT),
                            part.height)
                        .direction(ProgressWidget.Direction.UP)
                        .pos(part.x, part.y)
                        .size(part.width, part.height));
            }
            process.child(assembly);
            panel.child(process);
            panel.child(
                IKey.lang("galaxia.android.station.dock.hint")
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT_DIM)
                    .pos(PANEL_PADDING, DOCK_HINT_Y)
                    .width(PANEL_WIDTH - 2 * PANEL_PADDING));
        }
        return panel;
    }

    private record BodyEntry(UUID id, String name, CelestialObjectKey destination, AndroidBodies.Chassis chassis) {}

    static void registerFactory() {
        GuiManager.registerFactory(CapsuleGuiFactory.INSTANCE);
    }

    private static final class CapsuleGuiData extends PosGuiData {

        private final boolean starmap;
        private final UUID bodyId;

        private CapsuleGuiData(EntityPlayer player, int x, int y, int z, boolean starmap, @Nullable UUID bodyId) {
            super(player, x, y, z);
            this.starmap = starmap;
            this.bodyId = bodyId;
        }
    }

    /** Replaces a capsule container when navigating to the map or back to its destination list. */
    private static final class CapsuleGuiFactory implements UIFactory<CapsuleGuiData> {

        private static final CapsuleGuiFactory INSTANCE = new CapsuleGuiFactory();

        private void open(EntityPlayerMP player, TileEntityBodyStation station, boolean starmap,
            @Nullable UUID bodyId) {
            if (!station.isCapsule() || !station.isUseableByPlayer(player)) return;
            GalacticMapWidget.prepareOpen(player);
            GuiManager.open(
                this,
                new CapsuleGuiData(player, station.xCoord, station.yCoord, station.zCoord, starmap, bodyId),
                player);
        }

        @Override
        @Nonnull
        public String getFactoryName() {
            return "galaxia:capsule_navigation";
        }

        @Override
        public ModularPanel createPanel(CapsuleGuiData data, PanelSyncManager syncManager, UISettings settings) {
            TileEntityBodyStation station = (TileEntityBodyStation) data.getTileEntity();
            return data.starmap ? new BodyStationGui(station).new CapsuleControls(data).buildMap(syncManager)
                : station.buildUI(data, syncManager, settings);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public ModularScreen createScreen(CapsuleGuiData data, ModularPanel mainPanel) {
            return GalacticMapWidget.createScreen(mainPanel);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player, CapsuleGuiData data) {
            return player == data.getPlayer() && data.getTileEntity() instanceof TileEntityBodyStation station
                && station.isCapsule()
                && station.isUseableByPlayer(player);
        }

        @Override
        public void writeGuiData(CapsuleGuiData data, PacketBuffer buffer) {
            buffer.writeInt(data.getX());
            buffer.writeInt(data.getY());
            buffer.writeInt(data.getZ());
            buffer.writeBoolean(data.starmap);
            buffer.writeBoolean(data.bodyId != null);
            if (data.bodyId != null) PacketUtil.writeId(buffer, data.bodyId);
        }

        @Override
        @Nonnull
        public CapsuleGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
            return new CapsuleGuiData(
                player,
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean() ? PacketUtil.readId(buffer) : null);
        }
    }

    /** One player's capsule navigation and rename draft, scoped to the open container. */
    private final class CapsuleControls {

        private final PosGuiData data;
        private final UUID selectedBody;
        private CelestialObjectKey selectedDestination;
        private UUID renameTarget;
        private String nameDraft = "";
        private IPanelHandler nameEditor;

        private CapsuleControls(PosGuiData data) {
            this.data = data;
            selectedBody = data instanceof CapsuleGuiData capsule ? capsule.bodyId : null;
        }

        private List<AndroidBodies.AndroidBody> ownedBodies() {
            if (station.getWorldObj().isRemote || !station.canOperate(data.getPlayer())) return List.of();
            UUID owner = station.ownerTeam();
            return owner == null ? List.of()
                : AndroidControl.bodies()
                    .bodies(owner);
        }

        private boolean atSelectedDestination(AndroidBodies.AndroidBody body) {
            return selectedDestination != null && GalaxiaCelestialAPI.findByDimension(
                body.dock()
                    .dimension())
                .map(
                    destination -> destination.key()
                        .equals(selectedDestination))
                .orElse(false);
        }

        private List<BodyEntry> entries() {
            return ownedBodies().stream()
                .filter(this::atSelectedDestination)
                .map(body -> new BodyEntry(body.id(), body.name(), selectedDestination, body.chassis()))
                .sorted(
                    Comparator.comparing(BodyEntry::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(BodyEntry::id))
                .toList();
        }

        private Optional<AndroidBodies.AndroidBody> selectedBody() {
            return ownedBodies().stream()
                .filter(
                    body -> body.id()
                        .equals(selectedBody))
                .findFirst();
        }

        private ParentWidget<?> build(PanelSyncManager syncManager) {
            InteractionSyncHandler openMap = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (mouse.mouseButton != 0 || station.getWorldObj().isRemote
                    || !(data.getPlayer() instanceof EntityPlayerMP player)) return;
                ServerTickTaskQueue.schedule(() -> CapsuleGuiFactory.INSTANCE.open(player, station, true, null));
            });
            syncManager.syncValue("android.openMap", openMap);
            StringSyncValue selectionName = new StringSyncValue(
                () -> selectedBody().map(
                    body -> body.name()
                        .isEmpty()
                            ? body.id()
                                .toString()
                                .substring(0, BODY_ID_PREFIX_LENGTH)
                            : body.name())
                    .orElse(""),
                value -> {});
            GenericSyncValue<CelestialObjectKey, ?> planet = GenericSyncValue.builder(CelestialObjectKey.class)
                .getter(
                    () -> selectedBody().flatMap(
                        body -> GalaxiaCelestialAPI.findByDimension(
                            body.dock()
                                .dimension()))
                        .map(CelestialObject::key)
                        .orElse(null))
                .setter(value -> {})
                .serializer(PacketUtil::writeCelestialObjectKey)
                .deserializer(PacketUtil::readCelestialObjectKey)
                .equalsDefault()
                .copyImmutable()
                .nullable()
                .build();
            syncManager.syncValue("android.selectionName", selectionName);
            syncManager.syncValue("android.selectionPlanet", planet);
            InteractionSyncHandler connect = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (mouse.mouseButton != 0 || station.getWorldObj().isRemote
                    || !(data.getPlayer() instanceof EntityPlayerMP player)
                    || !station.isUseableByPlayer(player)) return;
                if (selectedBody == null) {
                    player.addChatMessage(new ChatComponentTranslation("galaxia.android.station.select_android"));
                    return;
                }
                if (!station.contains(player)) {
                    player.addChatMessage(new ChatComponentTranslation("galaxia.android.station.enter_capsule"));
                    return;
                }
                if (!station.isPowered() || !AndroidControl.activate(player, station.anchor(), selectedBody)) player
                    .addChatMessage(new ChatComponentTranslation("galaxia.android.station.connection_unavailable"));
            });
            syncManager.syncValue("android.connect", connect);
            ParentWidget<?> controls = new ParentWidget<>().size(CONTROL_WIDTH, CONTROL_HEIGHT);
            controls.child(
                IKey.dynamic(
                    () -> selectionName.getStringValue()
                        .isEmpty()
                            ? IKey.lang("galaxia.android.station.select_android")
                                .get()
                            : selectionName.getStringValue())
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT)
                    .pos(0, 0)
                    .width(CONTROL_WIDTH));
            controls.child(
                IKey.dynamic(
                    () -> planet.getValue() == null ? ""
                        : GalaxiaCelestialAPI.findBodyByKey(planet.getValue())
                            .map(CelestialObject::displayName)
                            .orElse(""))
                    .asWidget()
                    .color(GalaxiaGuiStyle.TEXT_DIM)
                    .pos(0, 16)
                    .width(CONTROL_WIDTH));
            controls.child(
                GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.change_selection"))
                    .name("android.open_starmap")
                    .pos(0, 40)
                    .size(CONTROL_WIDTH, 20)
                    .syncHandler(openMap));
            controls.child(
                GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.connect"))
                    .name("android.connect")
                    .pos(0, 64)
                    .size(CONTROL_WIDTH, 20)
                    .syncHandler(connect));
            return controls;
        }

        private ModularPanel buildMap(PanelSyncManager syncManager) {
            nameEditor = syncManager.syncedPanel("android.rename", true, this::buildNameEditor);
            GenericSyncValue<CelestialObjectKey, ?> selected = GenericSyncValue.builder(CelestialObjectKey.class)
                .getter(() -> selectedDestination)
                .setter(key -> {
                    if (station.getWorldObj().isRemote || key == null
                        || GalaxiaCelestialAPI.findBodyByKey(key)
                            .isPresent()) {
                        selectedDestination = key;
                    }
                })
                .deserializer(PacketUtil::readCelestialObjectKey)
                .serializer(PacketUtil::writeCelestialObjectKey)
                .equalsDefault()
                .copyImmutable()
                .nullable()
                .build()
                .allowC2S();
            syncManager.syncValue("android.destination", selected);
            ParentWidget<?> choices = new ParentWidget<>().name("android.selection")
                .size(CONTROL_WIDTH, SELECTION_LIST_HEIGHT)
                .child(bodyList(syncManager).size(CONTROL_WIDTH, SELECTION_LIST_HEIGHT));
            return GalacticMapWidget
                .buildBodyPicker(syncManager, data.getPlayer(), key -> selected.setValue(key, true, true), choices);
        }

        private DynamicSyncedWidget<?> bodyList(PanelSyncManager syncManager) {
            GenericListSyncHandler<BodyEntry> entries = new GenericListSyncHandler<>(
                this::entries,
                null,
                buffer -> new BodyEntry(
                    PacketUtil.readId(buffer),
                    ByteBufUtils.readUTF8String(buffer),
                    PacketUtil.readCelestialObjectKey(buffer),
                    AndroidBodies.Chassis.valueOf(ByteBufUtils.readUTF8String(buffer))),
                (buffer, entry) -> {
                    PacketUtil.writeId(buffer, entry.id());
                    ByteBufUtils.writeUTF8String(buffer, entry.name());
                    PacketUtil.writeCelestialObjectKey(buffer, entry.destination());
                    ByteBufUtils.writeUTF8String(
                        buffer,
                        entry.chassis()
                            .name());
                },
                BodyEntry::equals,
                entry -> entry);
            syncManager.syncValue("android.bodies", entries);
            DynamicLinkedSyncHandler<GenericListSyncHandler<BodyEntry>> list = new DynamicLinkedSyncHandler<>(entries)
                .widgetProvider((manager, value) -> {
                    ListWidget<IWidget, ?> rows = new ListWidget<>().size(CONTROL_WIDTH, SELECTION_LIST_HEIGHT);
                    if (value.getValue()
                        .isEmpty())
                        rows.child(
                            IKey.lang("galaxia.android.station.empty")
                                .asWidget()
                                .color(GalaxiaGuiStyle.TEXT)
                                .height(BODY_ROW_HEIGHT));
                    for (BodyEntry entry : value.getValue()) rows.child(bodyRow(manager, entry));
                    return rows;
                });
            syncManager.syncValue("android.bodyList", list);
            return new DynamicSyncedWidget<>().syncHandler(list);
        }

        private ParentWidget<?> bodyRow(PanelSyncManager manager, BodyEntry entry) {
            UUID id = entry.id();
            InteractionSyncHandler operate = manager.getOrCreateSyncHandler(
                "operate_" + id,
                0,
                InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (mouse.mouseButton != 0 || station.getWorldObj().isRemote
                        || !(data.getPlayer() instanceof EntityPlayerMP operator)
                        || !station.isUseableByPlayer(operator)
                        || !station.isPowered()) return;
                    AndroidBodies.AndroidBody body = AndroidControl.bodies()
                        .body(id)
                        .orElse(null);
                    if (body == null || !body.teamId()
                        .equals(station.ownerTeam()) || !atSelectedDestination(body)) return;
                    ServerTickTaskQueue.schedule(() -> CapsuleGuiFactory.INSTANCE.open(operator, station, false, id));
                }));
            InteractionSyncHandler rename = manager.getOrCreateSyncHandler(
                "rename_" + id,
                0,
                InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (mouse.mouseButton != 0 || station.getWorldObj().isRemote
                        || !station.isUseableByPlayer(data.getPlayer())) return;
                    AndroidBodies.AndroidBody body = AndroidControl.bodies()
                        .body(id)
                        .orElse(null);
                    if (body == null || !body.teamId()
                        .equals(station.ownerTeam()) || !atSelectedDestination(body)) return;
                    renameTarget = id;
                    nameDraft = body.name();
                }));
            return new ParentWidget<>().size(CONTROL_WIDTH, BODY_ROW_HEIGHT)
                .setEnabledIf(
                    widget -> entry.destination()
                        .equals(selectedDestination))
                .child(
                    GalaxiaGuiStyle
                        .textButton(
                            IKey.dynamic(
                                () -> IKey.lang(
                                    "galaxia.android.station.body_model",
                                    entry.name()
                                        .isEmpty()
                                            ? IKey.lang(
                                                "galaxia.android.station.body",
                                                id.toString()
                                                    .substring(0, BODY_ID_PREFIX_LENGTH))
                                                .get()
                                            : entry.name(),
                                    IKey.lang(
                                        entry.chassis()
                                            .translationKey())
                                        .get())
                                    .get()))
                        .name("android.body." + id)
                        .size(BODY_BUTTON_WIDTH, 22)
                        .syncHandler(operate))
                .child(
                    GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.rename"))
                        .name("android.rename." + id)
                        .pos(BODY_BUTTON_WIDTH + 4, 0)
                        .size(RENAME_BUTTON_WIDTH, 22)
                        .onMousePressed(button -> {
                            if (button != 0) return false;
                            rename.onMousePressed(button);
                            nameEditor.openPanel();
                            return true;
                        }));
        }

        private ModularPanel buildNameEditor(PanelSyncManager syncManager, IPanelHandler handler) {
            StringSyncValue draft = new StringSyncValue(() -> nameDraft, value -> nameDraft = value).allowC2S();
            syncManager.syncValue("android.name", draft);
            InteractionSyncHandler save = new InteractionSyncHandler().setOnMousePressed(mouse -> {
                if (mouse.mouseButton != 0 || station.getWorldObj().isRemote
                    || renameTarget == null
                    || !station.isUseableByPlayer(data.getPlayer())) return;
                try {
                    AndroidControl.bodies()
                        .rename(station.ownerTeam(), renameTarget, nameDraft);
                    handler.closePanel();
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    data.getPlayer()
                        .addChatMessage(new ChatComponentTranslation("galaxia.android.station.invalid_name"));
                } catch (IOException ex) {
                    Galaxia.LOG.error("Cannot commit android name", ex);
                    data.getPlayer()
                        .addChatMessage(new ChatComponentTranslation("galaxia.android.station.rename_failed"));
                }
            });
            syncManager.syncValue("android.saveName", save);
            return GalaxiaGuiStyle.panel("android_rename", NAME_PANEL_WIDTH, NAME_PANEL_HEIGHT)
                .child(
                    GalaxiaGuiStyle.title("galaxia.android.station.rename")
                        .pos(PANEL_PADDING, PANEL_PADDING))
                .child(
                    new TextFieldWidget().name("android.name.input")
                        .setTextColor(GalaxiaGuiStyle.TEXT)
                        .background(EnumTextures.GUI_WELL.getImage())
                        .pos(PANEL_PADDING, 28)
                        .size(NAME_PANEL_WIDTH - 2 * PANEL_PADDING, 20)
                        .setMaxLength(AndroidBodies.MAX_NAME_LENGTH)
                        .autoUpdateOnChange(true)
                        .value(draft))
                .child(
                    GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.save"))
                        .name("android.name.save")
                        .pos(PANEL_PADDING, NAME_ACTION_Y)
                        .size(NAME_BUTTON_WIDTH, NAME_BUTTON_HEIGHT)
                        .syncHandler(save))
                .child(
                    GalaxiaGuiStyle.textButton(IKey.lang("galaxia.android.station.cancel"))
                        .pos(NAME_PANEL_WIDTH - PANEL_PADDING - NAME_BUTTON_WIDTH, NAME_ACTION_Y)
                        .size(NAME_BUTTON_WIDTH, NAME_BUTTON_HEIGHT)
                        .onMousePressed(button -> {
                            if (button != 0) return false;
                            handler.closePanel();
                            return true;
                        }));
        }
    }
}
