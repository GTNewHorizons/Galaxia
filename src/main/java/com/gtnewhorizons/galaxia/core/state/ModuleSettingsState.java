package com.gtnewhorizons.galaxia.core.state;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.MinerSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.ModuleSettings;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.RecipeModuleSettings;

/** Canonical NBT state for module settings. */
public final class ModuleSettingsState {

    private static final String PATH = "moduleSettings";

    private ModuleSettingsState() {}

    public static NBTTagCompound encode(ModuleSettings settings) {
        NBTTagCompound out = new NBTTagCompound();
        if (settings instanceof MinerSettings miner) {
            NBTTagList blacklist = new NBTTagList();
            for (String oreKey : miner.blacklistedOreKeys()) blacklist.appendTag(new NBTTagString(oreKey));
            out.setTag("blacklist", blacklist);
        } else if (settings instanceof RecipeModuleSettings recipes) {
            out.setTag("book", RecipeBookState.encode(recipes.book()));
        } else {
            throw fail(PATH, "unsupported settings " + settings);
        }
        return out;
    }

    public static ModuleSettings decode(FacilityModuleKind kind, NBTTagCompound encoded) {
        return decode(kind, new NbtReader(encoded, PATH));
    }

    static ModuleSettings decode(FacilityModuleKind kind, NbtReader in) {
        if (kind == FacilityModuleKind.MINER) {
            if (!in.tag()
                .func_150296_c()
                .equals(Set.of("blacklist"))) throw fail(in.path(), "invalid miner settings fields");
            return new MinerSettings(readUniqueStrings(in, "blacklist"));
        }
        if (FacilityModuleRegistry.get(kind)
            .settingsGroups()) {
            if (!in.tag()
                .func_150296_c()
                .equals(Set.of("book"))) throw fail(in.path(), "invalid recipe settings fields");
            return new RecipeModuleSettings(RecipeBookState.decode(in.compound("book")));
        }
        throw fail(in.path(), "unsupported settings kind " + kind);
    }

    private static Set<String> readUniqueStrings(NbtReader in, String key) {
        NBTTagList tags = in.strings(key);
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            String value = tags.getStringTagAt(i);
            if (value == null || value.isBlank()) throw fail(in.path() + "." + key + "[" + i + "]", "blank string");
            if (!values.add(value)) {
                throw fail(in.path() + "." + key + "[" + i + "]", "duplicate string " + value);
            }
        }
        return values;
    }

    private static IllegalStateException fail(String path, String message) {
        return new IllegalStateException("[STATE] " + path + ": " + message);
    }
}
