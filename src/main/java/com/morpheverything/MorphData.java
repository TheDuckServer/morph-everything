package com.morpheverything;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** Per-player data stored in the player's persistent NBT (saved with the world). */
public final class MorphData {
    private static final String ROOT = "morpheverything";
    private static final int MAX_RECENT = 8;

    private MorphData() {}

    private static CompoundTag root(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(ROOT)) {
            pd.put(ROOT, new CompoundTag());
        }
        return pd.getCompound(ROOT);
    }

    private static List<String> readList(CompoundTag t, String key) {
        List<String> out = new ArrayList<>();
        ListTag list = t.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return out;
    }

    private static void writeList(CompoundTag t, String key, Collection<String> values) {
        ListTag list = new ListTag();
        for (String s : values) {
            list.add(StringTag.valueOf(s));
        }
        t.put(key, list);
    }

    public static String current(ServerPlayer p) {
        return root(p).getString("current");
    }

    public static void setCurrent(ServerPlayer p, String form) {
        root(p).putString("current", form == null ? "" : form);
    }

    public static Set<String> unlocked(ServerPlayer p) {
        return new LinkedHashSet<>(readList(root(p), "unlocked"));
    }

    /** @return true if the form was newly unlocked */
    public static boolean addUnlocked(ServerPlayer p, String form) {
        Set<String> set = unlocked(p);
        if (!set.add(form)) return false;
        writeList(root(p), "unlocked", set);
        return true;
    }

    public static List<String> favorites(ServerPlayer p) {
        return readList(root(p), "favorites");
    }

    public static void toggleFavorite(ServerPlayer p, String form) {
        List<String> fav = favorites(p);
        if (!fav.remove(form)) {
            fav.add(form);
        }
        writeList(root(p), "favorites", fav);
    }

    public static List<String> recent(ServerPlayer p) {
        return readList(root(p), "recent");
    }

    public static void pushRecent(ServerPlayer p, String form) {
        List<String> list = recent(p);
        list.remove(form);
        list.add(0, form);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        writeList(root(p), "recent", list);
    }

    public static boolean grantedFlight(ServerPlayer p) {
        return root(p).getBoolean("grantedFlight");
    }

    public static void setGrantedFlight(ServerPlayer p, boolean value) {
        root(p).putBoolean("grantedFlight", value);
    }

    /** Copies persistent morph data to a new player object (after death / returning from the End). */
    public static void copy(ServerPlayer from, ServerPlayer to, boolean keepCurrent) {
        CompoundTag src = root(from);
        CompoundTag dst = root(to);
        writeList(dst, "unlocked", readList(src, "unlocked"));
        writeList(dst, "favorites", readList(src, "favorites"));
        writeList(dst, "recent", readList(src, "recent"));
        if (keepCurrent) {
            dst.putString("current", src.getString("current"));
            dst.putBoolean("grantedFlight", src.getBoolean("grantedFlight"));
        }
    }
}
