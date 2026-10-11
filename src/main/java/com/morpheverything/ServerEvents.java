package com.morpheverything;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge event hooks that run on the server (also in single player). */
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MorphManager.onJoin(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MorphManager.onJoin(sp);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MorphManager.onJoin(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MorphManager.onLogout(sp);
        }
    }

    /** Keep unlocked forms after death. The active form is reset by death (but kept when leaving the End). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer neu && event.getOriginal() instanceof ServerPlayer old) {
            MorphData.copy(old, neu, !event.isWasDeath());
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MorphManager.tick(sp);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead instanceof Player) return;
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer killer && MorphConfig.UNLOCK_ON_KILL.get()) {
            MorphManager.unlock(killer, MorphForms.mobForm(dead.getType()));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp && MorphConfig.UNLOCK_ON_BREAK.get()) {
            MorphManager.unlock(sp, MorphForms.blockForm(event.getState().getBlock()));
        }
    }
}
