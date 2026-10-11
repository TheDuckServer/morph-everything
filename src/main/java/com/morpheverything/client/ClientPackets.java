package com.morpheverything.client;

import com.morpheverything.Net;

/** Client-side handlers of server packets (kept here so the dedicated server never loads client classes). */
public final class ClientPackets {
    private ClientPackets() {}

    public static void handleOwnData(Net.SyncOwnData msg) {
        ClientState.unlockAll = msg.unlockAll();
        ClientState.UNLOCKED.clear();
        ClientState.UNLOCKED.addAll(msg.unlocked());
        ClientState.FAVORITES.clear();
        ClientState.FAVORITES.addAll(msg.favorites());
        ClientState.RECENT.clear();
        ClientState.RECENT.addAll(msg.recent());
    }

    public static void handleMorphState(Net.MorphState msg) {
        if (msg.form().isEmpty()) {
            ClientState.MORPHS.remove(msg.player());
        } else {
            ClientState.MORPHS.put(msg.player(), msg.form());
        }
    }

    public static void handleMorphStateAll(Net.MorphStateAll msg) {
        ClientState.MORPHS.clear();
        ClientState.MORPHS.putAll(msg.states());
    }
}
