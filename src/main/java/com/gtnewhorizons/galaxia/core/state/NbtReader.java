package com.gtnewhorizons.galaxia.core.state;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

/** Strict typed reads from one NBT compound with stable error context. */
public final class NbtReader {

    private final NBTTagCompound tag;
    private final String path;
    private final String context;

    public NbtReader(NBTTagCompound tag, String path) {
        this(tag, path, "[STATE]");
    }

    static NbtReader persistence(NBTTagCompound tag, String path) {
        return new NbtReader(tag, path, "[PERSIST]");
    }

    private NbtReader(NBTTagCompound tag, String path, String context) {
        this.tag = tag;
        this.path = path;
        this.context = context;
    }

    public NBTTagCompound tag() {
        return tag;
    }

    public String path() {
        return path;
    }

    public NbtReader compound(String key) {
        require(key, NBT.TAG_COMPOUND);
        return new NbtReader(tag.getCompoundTag(key), childPath(key), context);
    }

    public NBTTagList compounds(String key) {
        return list(key, NBT.TAG_COMPOUND);
    }

    public NBTTagList strings(String key) {
        return list(key, NBT.TAG_STRING);
    }

    public NbtReader element(String listKey, int index, NBTTagCompound element) {
        return new NbtReader(element, childPath(listKey) + "[" + index + "]", context);
    }

    public String string(String key) {
        require(key, NBT.TAG_STRING);
        return tag.getString(key);
    }

    public String nonBlankString(String key) {
        String value = string(key);
        if (value.isBlank()) throw failure(childPath(key), "must not be blank");
        return value;
    }

    public int integer(String key) {
        require(key, NBT.TAG_INT);
        return tag.getInteger(key);
    }

    public int integer(String key, int minimum, int maximum) {
        int value = integer(key);
        if (value < minimum || value > maximum) {
            throw failure(childPath(key), "must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    public long longValue(String key) {
        require(key, NBT.TAG_LONG);
        return tag.getLong(key);
    }

    public double doubleValue(String key) {
        require(key, NBT.TAG_DOUBLE);
        return tag.getDouble(key);
    }

    public boolean bool(String key) {
        require(key, NBT.TAG_BYTE);
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) {
            throw failure(childPath(key), "must be 0 or 1");
        }
        return value == 1;
    }

    public int[] intArray(String key) {
        require(key, NBT.TAG_INT_ARRAY);
        return tag.getIntArray(key);
    }

    public <T extends Enum<T>> T enumValue(Class<T> type, String key) {
        String name = string(key);
        try {
            return Enum.valueOf(type, name);
        } catch (RuntimeException ex) {
            throw failure(childPath(key), "invalid " + type.getSimpleName() + " value " + name, ex);
        }
    }

    private NBTTagList list(String key, int elementType) {
        require(key, NBT.TAG_LIST);
        NBTTagList list = (NBTTagList) tag.getTag(key);
        int actualType = list.func_150303_d();
        if (actualType != NBT.TAG_END && actualType != elementType) {
            throw failure(childPath(key), "has the wrong list element type");
        }
        return list;
    }

    private void require(String key, int type) {
        if (tag == null || !tag.hasKey(key, type)) {
            throw failure(childPath(key), "missing or has wrong type");
        }
    }

    private String childPath(String key) {
        return path + "." + key;
    }

    private IllegalStateException failure(String failurePath, String message) {
        return new IllegalStateException(context + " " + failurePath + ": " + message);
    }

    private IllegalStateException failure(String failurePath, String message, Throwable cause) {
        return new IllegalStateException(context + " " + failurePath + ": " + message, cause);
    }
}
