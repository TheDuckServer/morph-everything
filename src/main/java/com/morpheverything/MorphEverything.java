package com.morpheverything;

import com.mojang.logging.LogUtils;
import com.morpheverything.client.ClientBootstrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(MorphEverything.MODID)
public class MorphEverything {
    public static final String MODID = "morpheverything";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MorphEverything(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, MorphConfig.SPEC);
        Net.register();
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(context);
        }
    }
}
