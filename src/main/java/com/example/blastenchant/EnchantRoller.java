package com.example.blastenchant;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 随机附魔的核心逻辑：只从"确实能作用在这件装备上"的附魔里抽。
 * 例如钻石剑只会抽到锋利/亡灵杀手/节肢杀手/击退/火焰附加/抢夺/横扫之刃/耐久/经验修补……，
 * 而不会抽到只能给靴子的冰霜行者。
 * <p>
 * 注意：1.21 起附魔是数据驱动的动态注册表，必须通过世界的 RegistryManager 拿，
 * 不能用 Registries.ENCHANTMENT（那里没有附魔）。
 */
public final class EnchantRoller {

    /** 原版 minecraft:curse 标签（绑定诅咒 + 消失诅咒）。 */
    private static final TagKey<Enchantment> CURSE_TAG =
            TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("curse"));

    /** 缓存：物品 -> 是否至少有一个可用附魔。物品种类有限，缓存后每 tick 的开销可忽略。 */
    private static final Map<Item, Boolean> ENCHANTABLE_CACHE = new HashMap<>();

    private EnchantRoller() {
    }

    /** 是否为高炉需要处理的装备：有耐久（工具/武器/盔甲/盾/弓……）且能被附魔。 */
    public static boolean isBlastable(ServerWorld world, ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxDamage() <= 0) {
            return false;
        }
        return ENCHANTABLE_CACHE.computeIfAbsent(stack.getItem(), item -> hasApplicableEnchantment(world, item));
    }

    private static boolean hasApplicableEnchantment(ServerWorld world, Item item) {
        ItemStack probe = new ItemStack(item);
        for (Enchantment enchantment : enchantmentRegistry(world)) {
            if (enchantment.isAcceptableItem(probe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否已经被"附魔"过了。
     * 原版附魔最高只有 5 级，所以只要已经存在 >= 目标等级的附魔，就说明是我们加工出来的产物，
     * 避免每 tick 反复随机（否则附魔会一直跳变）。
     */
    public static boolean alreadyEnchanted(ItemStack stack) {
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null || component.isEmpty()) {
            return false;
        }
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            if (component.getLevel(entry) >= BlastEnchantConfig.enchantLevel) {
                return true;
            }
        }
        return false;
    }

    /** 复制一件装备并附上随机的满级附魔；若没有可用附魔则原样返回。 */
    public static ItemStack roll(ServerWorld world, ItemStack original) {
        List<RegistryEntry<Enchantment>> pool = collectPool(world, original);
        if (pool.isEmpty()) {
            return original;
        }

        Random random = world.getRandom();

        // 洗牌取前 N 个，保证同一次烧炼不会抽出重复的附魔
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(pool, i, j);
        }
        int count = Math.min(BlastEnchantConfig.enchantCount, pool.size());

        ItemEnchantmentsComponent.Builder builder =
                new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        for (int i = 0; i < count; i++) {
            builder.add(pool.get(i), BlastEnchantConfig.enchantLevel);
        }

        ItemStack result = original.copy();
        result.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return result;
    }

    private static List<RegistryEntry<Enchantment>> collectPool(ServerWorld world, ItemStack stack) {
        Registry<Enchantment> registry = enchantmentRegistry(world);
        List<RegistryEntry<Enchantment>> pool = new ArrayList<>();

        for (Enchantment enchantment : registry) {
            if (!enchantment.isAcceptableItem(stack)) {
                continue;
            }
            RegistryEntry<Enchantment> entry = registry.getEntry(enchantment);
            if (!BlastEnchantConfig.allowCurses && entry.isIn(CURSE_TAG)) {
                continue;
            }
            Identifier id = registry.getId(enchantment);
            if (id == null || BlastEnchantConfig.blacklist.contains(id.toString())) {
                continue;
            }
            pool.add(entry);
        }
        return pool;
    }

    private static Registry<Enchantment> enchantmentRegistry(ServerWorld world) {
        return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
    }

    /** 清空"可用附魔"缓存（数据包 /reload 之后附魔集合可能变化）。 */
    public static void clearCache() {
        ENCHANTABLE_CACHE.clear();
    }
}
