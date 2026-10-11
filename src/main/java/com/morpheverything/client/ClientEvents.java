package com.morpheverything.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.morpheverything.MorphForms;
import com.morpheverything.Net;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Client events: draws morphed players as their mob / block, and handles key presses.
 * The real player entity is untouched (controls, inventory, hitbox) - only the drawing is replaced.
 */
public final class ClientEvents {
    /** One hidden "dummy" mob per morphed player. It is never added to the world, only drawn. */
    private static final Map<UUID, LivingEntity> DUMMIES = new HashMap<>();
    private static final Map<UUID, String> DUMMY_FORMS = new HashMap<>();

    private ClientEvents() {}

    // ------------------------------------------------------------------ rendering

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String form = ClientState.MORPHS.get(player.getUUID());
        if (form == null || form.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float partialTick = event.getPartialTick();
        Minecraft mc = Minecraft.getInstance();

        if (MorphForms.isMob(form)) {
            LivingEntity dummy = dummyFor(player, form);
            if (dummy == null) {
                return; // unknown form: just draw the normal player
            }
            copyPose(player, dummy);
            EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(dummy);
            EntityRenderState state = renderer.createRenderState();
            renderer.extractRenderState(dummy, state, partialTick);
            renderer.render(state, pose, buffers, light);
            event.setCanceled(true);
        } else if (MorphForms.isBlock(form)) {
            Block block = MorphForms.blockOf(form);
            if (block == null) {
                return;
            }
            BlockState blockState = block.defaultBlockState();
            pose.pushPose();
            pose.translate(-0.5D, 0.0D, -0.5D);
            mc.getBlockRenderer().renderSingleBlock(blockState, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            event.setCanceled(true);
        }
    }

    private static LivingEntity dummyFor(Player player, String form) {
        UUID id = player.getUUID();
        LivingEntity dummy = DUMMIES.get(id);
        if (dummy != null && form.equals(DUMMY_FORMS.get(id)) && dummy.level() == player.level()) {
            return dummy;
        }
        EntityType<?> type = MorphForms.mobType(form);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(player.level(), EntitySpawnReason.COMMAND);
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        DUMMIES.put(id, living);
        DUMMY_FORMS.put(id, form);
        return living;
    }

    /** Copies everything the renderer reads (rotation, animation, hurt flash) from the real player. */
    private static void copyPose(Player player, LivingEntity dummy) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.setYRot(player.getYRot());
        dummy.yRotO = player.yRotO;
        dummy.setXRot(player.getXRot());
        dummy.xRotO = player.xRotO;
        dummy.yBodyRot = player.yBodyRot;
        dummy.yBodyRotO = player.yBodyRotO;
        dummy.yHeadRot = player.yHeadRot;
        dummy.yHeadRotO = player.yHeadRotO;
        dummy.tickCount = player.tickCount;
        dummy.hurtTime = player.hurtTime;
        dummy.deathTime = player.deathTime;
        dummy.setOnGround(player.onGround());
        dummy.setInvisible(player.isInvisible());
    }

    /** Once per tick: advance the dummy's walk animation using the player's real movement. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.level().isClientSide()) {
            return;
        }
        LivingEntity dummy = DUMMIES.get(player.getUUID());
        if (dummy == null) {
            return;
        }
        if (!ClientState.MORPHS.containsKey(player.getUUID())) {
            DUMMIES.remove(player.getUUID());
            DUMMY_FORMS.remove(player.getUUID());
            return;
        }
        dummy.xo = player.xo;
        dummy.yo = player.yo;
        dummy.zo = player.zo;
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.calculateEntityAnimation(false);
    }

    // ------------------------------------------------------------------ keys

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        while (ClientBootstrap.MENU_KEY.consumeClick()) {
            mc.setScreen(new MorphScreen());
        }
        while (ClientBootstrap.ABILITY_KEY.consumeClick()) {
            Net.CHANNEL.send(new Net.UseAbility(), PacketDistributor.SERVER.noArg());
        }
        while (ClientBootstrap.RESET_KEY.consumeClick()) {
            Net.CHANNEL.send(new Net.RequestMorph(""), PacketDistributor.SERVER.noArg());
        }
        while (ClientBootstrap.CYCLE_KEY.consumeClick()) {
            List<String> recent = ClientState.RECENT;
            if (!recent.isEmpty()) {
                int index = recent.indexOf(ClientState.ownForm());
                String next = recent.get((index + 1) % recent.size());
                Net.CHANNEL.send(new Net.RequestMorph(next), PacketDistributor.SERVER.noArg());
            }
        }
    }
}
