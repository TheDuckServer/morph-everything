package com.morpheverything;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A "form" is a String: "mob:minecraft:zombie" or "block:minecraft:stone".
 * Works for vanilla and modded entries because it reads the game registries.
 */
public final class MorphForms {
    public static final String MOB = "mob:";
    public static final String BLOCK = "block:";

    public enum Category { ALL, FAVORITES, RECENT, ANIMALS, MONSTERS, VILLAGERS, BOSSES, BLOCKS, OTHER }

    private static final Set<String> BOSS_IDS = Set.of("ender_dragon", "wither", "warden", "elder_guardian");
    private static final Set<String> VILLAGER_IDS = Set.of("villager", "wandering_trader", "witch",
            "iron_golem", "snow_golem", "pillager", "vindicator", "evoker", "illusioner");
    private static final Set<String> FLIER_IDS = Set.of("bat", "bee", "parrot", "allay", "vex", "ghast",
            "phantom", "blaze", "ender_dragon", "wither");
    private static final Set<String> AQUATIC_IDS = Set.of("guardian", "elder_guardian", "drowned", "axolotl", "turtle");

    private static final Map<ResourceLocation, Boolean> LIVING_CACHE = new ConcurrentHashMap<>();

    private MorphForms() {}

    public static boolean isMob(String form) {
        return form != null && form.startsWith(MOB);
    }

    public static boolean isBlock(String form) {
        return form != null && form.startsWith(BLOCK);
    }

    /** Returns the entity type for a "mob:..." form, or null. */
    public static EntityType<?> mobType(String form) {
        if (!isMob(form)) return null;
        ResourceLocation id = ResourceLocation.tryParse(form.substring(MOB.length()));
        if (id == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /** Returns the block for a "block:..." form, or null. */
    public static Block blockOf(String form) {
        if (!isBlock(form)) return null;
        ResourceLocation id = ResourceLocation.tryParse(form.substring(BLOCK.length()));
        if (id == null) return null;
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    public static String mobForm(EntityType<?> type) {
        return MOB + BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    public static String blockForm(Block block) {
        return BLOCK + BuiltInRegistries.BLOCK.getKey(block);
    }

    /** True if this entity type creates a LivingEntity (so it can be rendered on a player). */
    public static boolean isMorphableMob(EntityType<?> type, Level level) {
        if (type == EntityType.PLAYER) return false;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return LIVING_CACHE.computeIfAbsent(key, k -> {
            try {
                Entity e = type.create(level, EntitySpawnReason.COMMAND);
                boolean ok = e instanceof LivingEntity;
                if (e != null) e.discard();
                return ok;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /** Only obtainable blocks that render as a normal model can be used. */
    public static boolean isMorphableBlock(Block block) {
        if (block.asItem() == Items.AIR) return false;
        BlockState state = block.defaultBlockState();
        return !state.isAir() && state.getRenderShape() == RenderShape.MODEL;
    }

    public static Category categoryOfMob(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if ("minecraft".equals(id.getNamespace())) {
            if (BOSS_IDS.contains(id.getPath())) return Category.BOSSES;
            if (VILLAGER_IDS.contains(id.getPath())) return Category.VILLAGERS;
        }
        MobCategory c = type.getCategory();
        if (c == MobCategory.MONSTER) return Category.MONSTERS;
        if (c == MobCategory.MISC) return Category.OTHER;
        return Category.ANIMALS;
    }

    public static boolean isFlier(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return "minecraft".equals(id.getNamespace()) && FLIER_IDS.contains(id.getPath());
    }

    public static boolean isAquatic(EntityType<?> type) {
        MobCategory c = type.getCategory();
        if (c == MobCategory.WATER_CREATURE || c == MobCategory.WATER_AMBIENT
                || c == MobCategory.UNDERGROUND_WATER_CREATURE) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return "minecraft".equals(id.getNamespace()) && AQUATIC_IDS.contains(id.getPath());
    }

    public static boolean isEntityId(EntityType<?> type, String path) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return "minecraft".equals(id.getNamespace()) && id.getPath().equals(path);
    }

    public static Component displayName(String form) {
        if (form == null || form.isEmpty()) return Component.translatable("gui.morpheverything.none");
        EntityType<?> t = mobType(form);
        if (t != null) return t.getDescription();
        Block b = blockOf(form);
        if (b != null) return b.getName();
        return Component.literal(form);
    }
}
