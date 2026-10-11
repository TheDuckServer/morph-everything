package com.morpheverything.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** What the client currently knows (filled by server packets). */
public final class ClientState {
    /** Which player is morphed into what. Key = player UUID. */
    public static final Map<UUID, String> MORPHS = new HashMap<>();
    public static boolean unlockAll = false;
    public static final Set<String> UNLOCKED = new LinkedHashSet<>();
    public static final List<String> FAVORITES = new ArrayList<>();
    public static final List<String> RECENT = new ArrayList<>();

    private ClientState() {}

    public static String ownForm() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        return MORPHS.getOrDefault(mc.player.getUUID(), "");
    }

    public static boolean isUnlocked(String form) {
        if (unlockAll || UNLOCKED.contains(form)) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isCreative();
    }
}
