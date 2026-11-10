package com.gtnewhorizons.galaxia.registry.celestial.station.gui;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizons.galaxia.api.BlockPos;
import com.gtnewhorizons.galaxia.client.EnumTextures;
import com.gtnewhorizons.galaxia.client.gui.GalaxiaGuiStyle;
import com.gtnewhorizons.galaxia.client.gui.ui.GalaxiaUi;
import com.gtnewhorizons.galaxia.core.Galaxia;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.station.Station;
import com.gtnewhorizons.galaxia.registry.celestial.station.StationGraph;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStation;
import com.gtnewhorizons.galaxia.registry.celestial.station.TileStationBase;
import com.gtnewhorizons.galaxia.registry.dimension.OrbitDimensions;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Station terminal: the rooms of one station as a graph seen from above, joined by their airlocks, with the state of
 * the selected room and totals for the whole station. The server sends one tag describing the station, the client
 * only draws it
 */
public final class StationTerminalUI implements IGuiHolder<StationGuiFactory.Data> {

    public static final StationTerminalUI INSTANCE = new StationTerminalUI();

    /** Functional blocks listed for a room, in this order */
    static final String[] FUNCTIONAL_BLOCKS = { "oxygenator", "air_purifier", "wither_blocker", "cooling_coil",
        "heating_coil" };

    /** Fixed lines of a formed room plus one per functional block */
    private static final int DETAIL_LINES = 9 + FUNCTIONAL_BLOCKS.length;
    private static final int WIDTH = 330;
    private static final int HEIGHT = 214;

    private StationTerminalUI() {}

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(StationGuiFactory.Data data, ModularPanel mainPanel) {
        return new ModularScreen(Galaxia.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(StationGuiFactory.Data data, PanelSyncManager syncManager, UISettings settings) {
        View view = new View();
        NBTTagCompound[] received = { new NBTTagCompound() };
        syncManager.syncValue(
            "station_state",
            GenericSyncValue.builder(NBTTagCompound.class)
                .getter(() -> data.isClient() ? received[0] : write(data))
                .setter(tag -> {
                    received[0] = tag;
                    view.read(tag);
                })
                .adapter(ByteBufAdapters.NBT)
                .build());

        ModularPanel panel = GalaxiaGuiStyle.panel("galaxia:station_terminal", WIDTH, HEIGHT);
        panel
            .child(
                GalaxiaGuiStyle
                    .label(
                        () -> StatCollector
                            .translateToLocalFormatted("galaxia.gui.station_terminal.title", view.location),
                        () -> GalaxiaGuiStyle.TEXT_TITLE,
                        1f)
                    .pos(8, 7));
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station_terminal.summary",
                        view.rooms.size(),
                        view.sealedRooms(),
                        view.upkeep),
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    0.6f)
                .pos(8, 19));

        // Graph of the rooms
        panel.child(
            new Widget<>().size(186, 148)
                .pos(8, 30)
                .background(EnumTextures.GUI_WELL.getImage()));
        panel.child(
            new StationGraphWidget(view).size(182, 144)
                .pos(10, 32)
                .name("station.graph"));
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> view.rooms.isEmpty() ? StatCollector.translateToLocal("galaxia.gui.station_terminal.no_rooms")
                        : "",
                    () -> GalaxiaGuiStyle.TEXT_DIM,
                    0.7f)
                .pos(16, 100));

        // Selected room
        panel.child(
            new Widget<>().size(124, 148)
                .pos(198, 30)
                .background(EnumTextures.GUI_WELL.getImage()));
        // Lines past the room's last one are off, so the list scrolls only over what the room has
        ListWidget<IWidget, ?> details = new ListWidget<>();
        details.scrollDirection(GalaxiaUi.scrollbar())
            .pos(203, 34)
            .size(117, 142);
        for (int i = 0; i < DETAIL_LINES; i++) {
            final int line = i;
            details.child(
                GalaxiaGuiStyle.label(() -> view.detailLine(line), () -> view.detailColor(line), 0.6f)
                    .height(10)
                    .setEnabledIf(
                        w -> !view.detailLine(line)
                            .isEmpty())
                    .name(i == 0 ? "station.room_title" : null));
        }
        panel.child(details);

        // Station totals
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station_terminal.energy",
                        view.energyStored,
                        view.energyCapacity),
                    () -> GalaxiaGuiStyle.TEXT,
                    0.6f)
                .pos(8, 184));
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station_terminal.fluids",
                        view.fluidStored,
                        view.fluidCapacity),
                    () -> GalaxiaGuiStyle.TEXT,
                    0.6f)
                .pos(8, 195));
        panel.child(
            GalaxiaGuiStyle
                .label(
                    () -> StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station_terminal.items",
                        view.itemStored,
                        view.itemCapacity),
                    () -> GalaxiaGuiStyle.TEXT,
                    0.6f)
                .pos(8, 206)
                .name("station.items"));
        return panel;
    }

    /**
     * Describes the station for the client
     */
    static NBTTagCompound write(StationGuiFactory.Data data) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!(CelestialAssetStore.findAsset(data.getStation()) instanceof Station station)) return tag;
        OrbitDimensions.ofBody(station.celestialObjectKey)
            .ifPresent(orbit -> tag.setInteger("orbit", orbit.dimensionId()));
        TileStation controller = station.getTileController();
        StationGraph graph = controller == null ? null : controller.getGraph();
        if (graph == null) return tag;

        List<BlockPos> order = new ArrayList<>();
        NBTTagList rooms = new NBTTagList();
        long upkeep = 0;
        for (Map.Entry<BlockPos, TileStationBase<?>> entry : graph.getPieces()
            .entrySet()) {
            if (!(entry.getValue() instanceof TileStation room)) continue;
            order.add(entry.getKey());
            NBTTagCompound r = new NBTTagCompound();
            BlockPos pos = entry.getKey();
            r.setInteger("x", pos.x());
            r.setInteger("y", pos.y());
            r.setInteger("z", pos.z());
            r.setBoolean("main", room == controller);
            r.setString(
                "behavior",
                room.getBehavior()
                    .getUnlocalizedName());
            r.setBoolean("formed", room.isStructureValid());
            r.setBoolean("sealed", room.isSealed());
            r.setBoolean("open", !room.isEnclosedStructure());
            r.setDouble("oxygen", room.getOxygenLevel());
            if (room.isStructureValid()) {
                r.setInteger("volume", room.getVolume());
                r.setInteger("walls", room.getStructureBlocksAmount());
                r.setLong("upkeep", room.getUpkeepEUt());
                upkeep += room.getUpkeepEUt();
            }
            r.setInteger(
                "attachments",
                room.getAttachments()
                    .size());
            r.setInteger(
                "plugs",
                room.getStationPlugs()
                    .size());
            for (String kind : FUNCTIONAL_BLOCKS) r.setInteger(kind, room.countFunctionalBlocks(kind));
            rooms.appendTag(r);
        }
        tag.setTag("rooms", rooms);

        List<Integer> links = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            for (BlockPos other : graph.linksOf(order.get(i))) {
                int j = order.indexOf(other);
                if (j > i) {
                    links.add(i);
                    links.add(j);
                }
            }
        }
        tag.setIntArray(
            "links",
            links.stream()
                .mapToInt(Integer::intValue)
                .toArray());
        tag.setLong("upkeep", upkeep);
        tag.setString(
            "energy",
            graph.getEnergyStored()
                .toString());
        tag.setString(
            "energyCapacity",
            graph.getEnergyCapacity()
                .toString());
        tag.setString(
            "fluid",
            graph.getFluidStored()
                .toString());
        tag.setString(
            "fluidCapacity",
            graph.getFluidCapacity()
                .toString());
        tag.setString(
            "items",
            graph.getItemStored()
                .toString());
        tag.setString(
            "itemCapacity",
            graph.getItemCapacity()
                .toString());
        return tag;
    }

    /** One room as the client sees it */
    record Room(int x, int y, int z, boolean main, String behavior, boolean formed, boolean sealed, boolean open,
        double oxygen, int volume, int walls, long upkeep, int attachments, int plugs, int[] functional) {

        /** color of the room on the graph: green when breathable, yellow when sealed, red when open to space */
        int color() {
            if (!formed) return GalaxiaGuiStyle.TEXT_DIM;
            if (!sealed) return GalaxiaGuiStyle.BAD;
            return oxygen >= 100 ? GalaxiaGuiStyle.GOOD : GalaxiaGuiStyle.WARN;
        }
    }

    /**
     * Client copy of the station, filled from the synced tag
     */
    static final class View {

        String location = "";
        final List<Room> rooms = new ArrayList<>();
        int[] links = new int[0];
        long upkeep;
        String energyStored = "0";
        String energyCapacity = "0";
        String fluidStored = "0";
        String fluidCapacity = "0";
        String itemStored = "0";
        String itemCapacity = "0";
        int selected = -1;

        void read(NBTTagCompound tag) {
            location = tag.hasKey("orbit") ? OrbitDimensions.byDimension(tag.getInteger("orbit"))
                .map(OrbitDimensions.Orbit::displayName)
                .orElse("") : "";
            rooms.clear();
            NBTTagList list = tag.getTagList("rooms", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound r = list.getCompoundTagAt(i);
                int[] functional = new int[FUNCTIONAL_BLOCKS.length];
                for (int k = 0; k < functional.length; k++) functional[k] = r.getInteger(FUNCTIONAL_BLOCKS[k]);
                rooms.add(
                    new Room(
                        r.getInteger("x"),
                        r.getInteger("y"),
                        r.getInteger("z"),
                        r.getBoolean("main"),
                        r.getString("behavior"),
                        r.getBoolean("formed"),
                        r.getBoolean("sealed"),
                        r.getBoolean("open"),
                        r.getDouble("oxygen"),
                        r.getInteger("volume"),
                        r.getInteger("walls"),
                        r.getLong("upkeep"),
                        r.getInteger("attachments"),
                        r.getInteger("plugs"),
                        functional));
            }
            links = tag.getIntArray("links");
            upkeep = tag.getLong("upkeep");
            energyStored = format(tag.getString("energy"));
            energyCapacity = format(tag.getString("energyCapacity"));
            fluidStored = format(tag.getString("fluid"));
            fluidCapacity = format(tag.getString("fluidCapacity"));
            itemStored = format(tag.getString("items"));
            itemCapacity = format(tag.getString("itemCapacity"));
            if (selected >= rooms.size()) selected = -1;
            if (selected < 0) {
                for (int i = 0; i < rooms.size(); i++) {
                    if (rooms.get(i)
                        .main()) selected = i;
                }
            }
        }

        int sealedRooms() {
            int count = 0;
            for (Room room : rooms) if (room.sealed()) count++;
            return count;
        }

        Room selectedRoom() {
            return selected >= 0 && selected < rooms.size() ? rooms.get(selected) : null;
        }

        /** Lines of the room panel, empty past the end */
        String detailLine(int line) {
            Room room = selectedRoom();
            if (room == null)
                return line == 0 ? StatCollector.translateToLocal("galaxia.gui.station_terminal.select") : "";
            List<String> lines = new ArrayList<>();
            String behavior = StatCollector.translateToLocal(room.behavior());
            lines.add(
                room.main()
                    ? StatCollector.translateToLocalFormatted("galaxia.gui.station_terminal.main_room", behavior)
                    : behavior);
            lines.add(
                StatCollector
                    .translateToLocalFormatted("galaxia.gui.station_terminal.at", room.x(), room.y(), room.z()));
            if (!room.formed()) {
                lines.add(StatCollector.translateToLocal("galaxia.gui.station_terminal.not_formed"));
            } else {
                lines.add(
                    StatCollector.translateToLocal(
                        room.open() ? "galaxia.gui.station_terminal.open"
                            : room.sealed() ? "galaxia.gui.station_terminal.sealed"
                                : "galaxia.gui.station_terminal.breached"));
                lines.add(
                    StatCollector
                        .translateToLocalFormatted("galaxia.gui.station_terminal.oxygen", (int) room.oxygen()));
                lines
                    .add(StatCollector.translateToLocalFormatted("galaxia.gui.station_terminal.volume", room.volume()));
                lines.add(StatCollector.translateToLocalFormatted("galaxia.gui.station_terminal.walls", room.walls()));
                lines
                    .add(StatCollector.translateToLocalFormatted("galaxia.gui.station_terminal.upkeep", room.upkeep()));
                lines.add(
                    StatCollector.translateToLocalFormatted(
                        "galaxia.gui.station_terminal.attachments",
                        room.attachments(),
                        room.plugs()));
                for (int k = 0; k < FUNCTIONAL_BLOCKS.length; k++) {
                    if (room.functional()[k] <= 0) continue;
                    lines.add(
                        StatCollector.translateToLocalFormatted(
                            "galaxia.gui.station_terminal.functional",
                            StatCollector.translateToLocal("tile." + FUNCTIONAL_BLOCKS[k] + ".name"),
                            room.functional()[k]));
                }
            }
            return line < lines.size() ? lines.get(line) : "";
        }

        int detailColor(int line) {
            Room room = selectedRoom();
            if (line == 0) return GalaxiaGuiStyle.TEXT_TITLE;
            if (room != null && line == 2) return room.color();
            return GalaxiaGuiStyle.TEXT;
        }

        private static String format(String number) {
            if (number.isEmpty()) return "0";
            try {
                return String.format("%,d", new BigInteger(number));
            } catch (NumberFormatException e) {
                return number;
            }
        }
    }
}
