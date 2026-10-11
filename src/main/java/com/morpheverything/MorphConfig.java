package com.morpheverything;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side settings (file: config/morpheverything-common.toml). */
public final class MorphConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue UNLOCK_ALL;
    public static final ForgeConfigSpec.BooleanValue UNLOCK_ON_KILL;
    public static final ForgeConfigSpec.BooleanValue UNLOCK_ON_BREAK;
    public static final ForgeConfigSpec.BooleanValue ALLOW_MOBS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FLIGHT;
    public static final ForgeConfigSpec.BooleanValue WATER_ABILITIES;
    public static final ForgeConfigSpec.BooleanValue FIRE_IMMUNITY;
    public static final ForgeConfigSpec.BooleanValue ENDERMAN_TELEPORT;
    public static final ForgeConfigSpec.BooleanValue CREEPER_EXPLOSION;
    public static final ForgeConfigSpec.BooleanValue CREEPER_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue MAX_SCALE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("unlocking");
        UNLOCK_ALL = b.comment("If true, every form is available immediately.")
                .define("unlockAll", false);
        UNLOCK_ON_KILL = b.comment("Unlock a mob form when you kill that mob.")
                .define("unlockOnKill", true);
        UNLOCK_ON_BREAK = b.comment("Unlock a block form when you break that block.")
                .define("unlockOnBreak", true);
        b.pop();

        b.push("forms");
        ALLOW_MOBS = b.comment("Allow morphing into mobs.").define("allowMobs", true);
        ALLOW_BLOCKS = b.comment("Allow morphing into blocks.").define("allowBlocks", true);
        MAX_SCALE = b.comment("Largest hitbox scale (1.0 = normal player size 1.8 blocks tall). Huge mobs are capped to this.")
                .defineInRange("maxScale", 3.0D, 0.5D, 8.0D);
        BLACKLIST = b.comment("Forms that nobody may use. Examples: \"mob:minecraft:ender_dragon\", \"block:minecraft:bedrock\"")
                .defineListAllowEmpty("blacklist", List.of(), o -> o instanceof String);
        b.pop();

        b.push("abilities");
        ALLOW_FLIGHT = b.comment("Flying mobs (bat, bee, parrot, allay, vex, ghast, phantom, blaze, dragon, wither) let you fly.")
                .define("allowFlight", true);
        WATER_ABILITIES = b.comment("Aquatic mobs give water breathing and swim speed.")
                .define("waterAbilities", true);
        FIRE_IMMUNITY = b.comment("Fire-immune mobs give fire resistance.")
                .define("fireImmunity", true);
        ENDERMAN_TELEPORT = b.comment("Enderman form: ability key teleports you where you look (up to 32 blocks).")
                .define("endermanTeleport", true);
        CREEPER_EXPLOSION = b.comment("Creeper form: ability key makes an explosion. DANGEROUS, off by default.")
                .define("creeperExplosion", false);
        CREEPER_BREAKS_BLOCKS = b.comment("If the creeper explosion is enabled, should it destroy blocks?")
                .define("creeperExplosionBreaksBlocks", false);
        b.pop();

        SPEC = b.build();
    }

    private MorphConfig() {}
}
