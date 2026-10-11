package com.morpheverything.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/** Client-only setup: key bindings and event registration. */
public final class ClientBootstrap {
    private static final String CATEGORY = "key.categories.morpheverything";

    public static final KeyMapping MENU_KEY = new KeyMapping(
            "key.morpheverything.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping ABILITY_KEY = new KeyMapping(
            "key.morpheverything.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping RESET_KEY = new KeyMapping(
            "key.morpheverything.reset", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping CYCLE_KEY = new KeyMapping(
            "key.morpheverything.cycle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    private ClientBootstrap() {}

    public static void init(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(ClientBootstrap::registerKeys);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(MENU_KEY);
        event.register(ABILITY_KEY);
        event.register(RESET_KEY);
        event.register(CYCLE_KEY);
    }
}
