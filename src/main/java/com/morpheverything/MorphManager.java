package com.morpheverything;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** All server-side morph rules. Clients can only ask; this class decides. */
public final class MorphManager {
    private static final ResourceLocation SCALE_ID =
            ResourceLocation.fromNamespaceAndPath(MorphEverything.MODID, "morph_scale");
    private static final double PLAYER_HEIGHT = 1.8D;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final Map<UUID, Long> ABILITY_COOLDOWN = new HashMap<>();

    private MorphManager() {}

    // ------------------------------------------------------------------ rules

    /** Does this form exist, is it morphable, and does the server config allow it? */
    public static boolean isFormEnabled(Level level, String form) {
        if (form == null || form.isEmpty() || form.length() > 200) return false;
        if (MorphConfig.BLACKLIST.get().contains(form)) return false;
        if (MorphForms.isMob(form)) {
            if (!MorphConfig.ALLOW_MOBS.get()) return false;
            EntityType<?> type = MorphForms.mobType(form);
            return type != null && MorphForms.isMorphableMob(type, level);
        }
        if (MorphForms.isBlock(form)) {
            if (!MorphConfig.ALLOW_BLOCKS.get()) return false;
            Block block = MorphForms.blockOf(form);
            return block != null && MorphForms.isMorphableBlock(block);
        }
        return false;
    }

    public static boolean isUnlocked(ServerPlayer p, String form) {
        return MorphConfig.UNLOCK_ALL.get() || p.isCreative() || MorphData.unlocked(p).contains(form);
    }

    // ------------------------------------------------------------------ requests from clients

    public static void requestMorph(ServerPlayer p, String form) {
        long now = p.level().getGameTime();
        Long last = LAST_REQUEST.get(p.getUUID());
        if (last != null && now - last < 5L) {
            return;
        }
        LAST_REQUEST.put(p.getUUID(), now);

        if (form == null || form.isEmpty()) {
            setMorph(p, "");
            return;
        }
        if (!isFormEnabled(p.level(), form) || !isUnlocked(p, form)) {
            p.displayClientMessage(Component.translatable("msg.morpheverything.denied"), true);
            syncData(p);
            return;
        }
        setMorph(p, form);
    }

    public static void toggleFavorite(ServerPlayer p, String form) {
        if (!isFormEnabled(p.level(), form)) return;
        MorphData.toggleFavorite(p, form);
        syncData(p);
    }

    // ------------------------------------------------------------------ state changes

    public static void setMorph(ServerPlayer p, String form) {
        MorphData.setCurrent(p, form);
        if (!form.isEmpty()) {
            MorphData.pushRecent(p, form);
        }
        applyScale(p, form);
        updateFlight(p, form);
        broadcast(p.getUUID(), form);
        syncData(p);
    }

    public static void unlock(ServerPlayer p, String form) {
        if (!isFormEnabled(p.level(), form)) return;
        if (MorphData.addUnlocked(p, form)) {
            p.displayClientMessage(
                    Component.translatable("msg.morpheverything.unlocked", MorphForms.displayName(form)), true);
            syncData(p);
        }
    }

    /** Called on login / respawn / dimension change: re-apply the saved form and sync everyone. */
    public static void onJoin(ServerPlayer p) {
        String form = MorphData.current(p);
        if (!form.isEmpty() && !isFormEnabled(p.level(), form)) {
            form = "";
            MorphData.setCurrent(p, "");
        }
        applyScale(p, form);
        updateFlight(p, form);

        Map<UUID, String> all = new HashMap<>();
        if (p.getServer() != null) {
            for (ServerPlayer other : p.getServer().getPlayerList().getPlayers()) {
                String f = MorphData.current(other);
                if (!f.isEmpty()) {
                    all.put(other.getUUID(), f);
                }
            }
        }
        Net.CHANNEL.send(new Net.MorphStateAll(all), PacketDistributor.PLAYER.with(p));
        broadcast(p.getUUID(), form);
        syncData(p);
    }

    public static void onLogout(ServerPlayer p) {
        broadcast(p.getUUID(), "");
        LAST_REQUEST.remove(p.getUUID());
        ABILITY_COOLDOWN.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ networking helpers

    public static void broadcast(UUID player, String form) {
        Net.CHANNEL.send(new Net.MorphState(player, form), PacketDistributor.ALL.noArg());
    }

    public static void syncData(ServerPlayer p) {
        Net.CHANNEL.send(new Net.SyncOwnData(
                MorphConfig.UNLOCK_ALL.get(),
                List.copyOf(MorphData.unlocked(p)),
                MorphData.favorites(p),
                MorphData.recent(p)), PacketDistributor.PLAYER.with(p));
    }

    // ------------------------------------------------------------------ physical effects

    /** Hitbox: the player's size attribute is scaled so the hitbox height matches the form. */
    private static void applyScale(ServerPlayer p, String form) {
        AttributeInstance inst = p.getAttribute(Attributes.SCALE);
        if (inst == null) return;
        inst.removeModifier(SCALE_ID);
        double scale = scaleFor(form);
        if (Math.abs(scale - 1.0D) > 0.001D) {
            inst.addTransientModifier(new AttributeModifier(
                    SCALE_ID, scale - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        p.refreshDimensions();
    }

    private static double scaleFor(String form) {
        if (form == null || form.isEmpty()) return 1.0D;
        double height = 1.0D;
        EntityType<?> type = MorphForms.mobType(form);
        if (type != null) {
            height = type.getHeight();
        }
        double scale = height / PLAYER_HEIGHT;
        return Math.max(0.2D, Math.min(MorphConfig.MAX_SCALE.get(), scale));
    }

    private static void updateFlight(ServerPlayer p, String form) {
        boolean want = false;
        if (MorphConfig.ALLOW_FLIGHT.get() && MorphForms.isMob(form)) {
            EntityType<?> type = MorphForms.mobType(form);
            want = type != null && MorphForms.isFlier(type);
        }
        if (want && !p.getAbilities().mayfly) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            MorphData.setGrantedFlight(p, true);
        } else if (!want && MorphData.grantedFlight(p)) {
            MorphData.setGrantedFlight(p, false);
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
        }
    }

    /** Called every tick for every server player. */
    public static void tick(ServerPlayer p) {
        String form = MorphData.current(p);
        if (p.tickCount % 10 == 0) {
            updateFlight(p, form);
        }
        if (form.isEmpty() || p.tickCount % 20 != 0 || !MorphForms.isMob(form)) return;
        EntityType<?> type = MorphForms.mobType(form);
        if (type == null) return;

        if (MorphConfig.WATER_ABILITIES.get() && MorphForms.isAquatic(type)) {
            addEffect(p, MobEffects.WATER_BREATHING);
            addEffect(p, MobEffects.DOLPHINS_GRACE);
        }
        if (MorphConfig.FIRE_IMMUNITY.get() && type.fireImmune()) {
            addEffect(p, MobEffects.FIRE_RESISTANCE);
        }
    }

    private static void addEffect(ServerPlayer p, Holder<MobEffect> effect) {
        p.addEffect(new MobEffectInstance(effect, 60, 0, false, false, false));
    }

    // ------------------------------------------------------------------ active abilities

    /** The "ability" key. Creeper: explosion (config). Enderman: teleport (config). */
    public static void useAbility(ServerPlayer p) {
        String form = MorphData.current(p);
        EntityType<?> type = MorphForms.mobType(form);
        if (type == null) return;

        long now = p.level().getGameTime();
        Long until = ABILITY_COOLDOWN.get(p.getUUID());
        if (until != null && now < until) return;

        if (MorphForms.isEntityId(type, "creeper")) {
            if (!MorphConfig.CREEPER_EXPLOSION.get()) {
                p.displayClientMessage(Component.translatable("msg.morpheverything.ability_disabled"), true);
                return;
            }
            ABILITY_COOLDOWN.put(p.getUUID(), now + 100L);
            Level.ExplosionInteraction mode = MorphConfig.CREEPER_BREAKS_BLOCKS.get()
                    ? Level.ExplosionInteraction.MOB
                    : Level.ExplosionInteraction.NONE;
            p.level().explode(p, p.getX(), p.getY(), p.getZ(), 3.0F, mode);
        } else if (MorphForms.isEntityId(type, "enderman")) {
            if (!MorphConfig.ENDERMAN_TELEPORT.get()) {
                p.displayClientMessage(Component.translatable("msg.morpheverything.ability_disabled"), true);
                return;
            }
            ABILITY_COOLDOWN.put(p.getUUID(), now + 40L);
            HitResult hit = p.pick(32.0D, 1.0F, false);
            Vec3 target = hit.getLocation();
            p.randomTeleport(target.x, target.y, target.z, true);
        }
    }
}
