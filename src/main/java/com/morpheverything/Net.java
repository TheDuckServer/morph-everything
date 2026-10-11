package com.morpheverything;

import com.morpheverything.client.ClientPackets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;

/** Network channel + all packets. The server never trusts the client: every request is re-checked. */
public final class Net {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(MorphEverything.MODID, "main"))
            .networkProtocolVersion(1)
            .simpleChannel();

    private Net() {}

    public static void register() {
        CHANNEL.messageBuilder(RequestMorph.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestMorph::encode).decoder(RequestMorph::decode)
                .consumerMainThread(RequestMorph::handle).add();
        CHANNEL.messageBuilder(ToggleFavorite.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleFavorite::encode).decoder(ToggleFavorite::decode)
                .consumerMainThread(ToggleFavorite::handle).add();
        CHANNEL.messageBuilder(UseAbility.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseAbility::encode).decoder(UseAbility::decode)
                .consumerMainThread(UseAbility::handle).add();

        CHANNEL.messageBuilder(SyncOwnData.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncOwnData::encode).decoder(SyncOwnData::decode)
                .consumerMainThread(SyncOwnData::handle).add();
        CHANNEL.messageBuilder(MorphState.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MorphState::encode).decoder(MorphState::decode)
                .consumerMainThread(MorphState::handle).add();
        CHANNEL.messageBuilder(MorphStateAll.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MorphStateAll::encode).decoder(MorphStateAll::decode)
                .consumerMainThread(MorphStateAll::handle).add();
    }

    // ---------------- client -> server ----------------

    /** Empty string = reset to normal player. */
    public record RequestMorph(String form) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(form, 256);
        }

        static RequestMorph decode(FriendlyByteBuf buf) {
            return new RequestMorph(buf.readUtf(256));
        }

        void handle(CustomPayloadEvent.Context ctx) {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                MorphManager.requestMorph(sender, form);
            }
        }
    }

    public record ToggleFavorite(String form) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(form, 256);
        }

        static ToggleFavorite decode(FriendlyByteBuf buf) {
            return new ToggleFavorite(buf.readUtf(256));
        }

        void handle(CustomPayloadEvent.Context ctx) {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                MorphManager.toggleFavorite(sender, form);
            }
        }
    }

    public record UseAbility() {
        void encode(FriendlyByteBuf buf) {
        }

        static UseAbility decode(FriendlyByteBuf buf) {
            return new UseAbility();
        }

        void handle(CustomPayloadEvent.Context ctx) {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                MorphManager.useAbility(sender);
            }
        }
    }

    // ---------------- server -> client ----------------

    public record SyncOwnData(boolean unlockAll, List<String> unlocked, List<String> favorites, List<String> recent) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(unlockAll);
            writeStrings(buf, unlocked);
            writeStrings(buf, favorites);
            writeStrings(buf, recent);
        }

        static SyncOwnData decode(FriendlyByteBuf buf) {
            boolean all = buf.readBoolean();
            List<String> u = readStrings(buf);
            List<String> f = readStrings(buf);
            List<String> r = readStrings(buf);
            return new SyncOwnData(all, u, f, r);
        }

        void handle(CustomPayloadEvent.Context ctx) {
            ClientPackets.handleOwnData(this);
        }
    }

    /** One player's morph changed. Empty form = not morphed. */
    public record MorphState(UUID player, String form) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(player);
            buf.writeUtf(form, 256);
        }

        static MorphState decode(FriendlyByteBuf buf) {
            return new MorphState(buf.readUUID(), buf.readUtf(256));
        }

        void handle(CustomPayloadEvent.Context ctx) {
            ClientPackets.handleMorphState(this);
        }
    }

    /** Full list of morphed players (sent to a player who just joined). */
    public record MorphStateAll(Map<UUID, String> states) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(states.size());
            for (Map.Entry<UUID, String> e : states.entrySet()) {
                buf.writeUUID(e.getKey());
                buf.writeUtf(e.getValue(), 256);
            }
        }

        static MorphStateAll decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            Map<UUID, String> map = new HashMap<>();
            for (int i = 0; i < n; i++) {
                UUID id = buf.readUUID();
                String form = buf.readUtf(256);
                map.put(id, form);
            }
            return new MorphStateAll(map);
        }

        void handle(CustomPayloadEvent.Context ctx) {
            ClientPackets.handleMorphStateAll(this);
        }
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) {
            buf.writeUtf(s, 256);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(buf.readUtf(256));
        }
        return out;
    }
}
