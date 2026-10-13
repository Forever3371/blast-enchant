package com.example.blastenchant;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.recipe.BlastingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏内自检：{@code /blastenchant selftest}
 * <p>
 * 这个类是一份可复用的模板：把"我以为对的东西"全部变成"跑一次就知道对不对"的检查，
 * 而且**跑在真实运行环境里**（正式 jar 里也是 intermediary 命名），
 * 所以能抓到开发环境测不出来的问题（例如 Mixin 的 refmap 未生成）。
 * <p>
 * 检查项：
 * <ol>
 *     <li>等级上限：组件写入 / 读取 / NBT 往返三关，必须都保持目标等级</li>
 *     <li>附魔池：对剑/靴子/三叉戟/弓/剪刀各抽样 40 次，只允许可用附魔、正确等级、无诅咒、有随机性</li>
 *     <li>配方：本模组命名空间下的 blasting 配方是否都加载了</li>
 *     <li>端到端：临时放一个真高炉烧一次，验证产物确实是满级附魔装备（跑完恢复原方块）</li>
 * </ol>
 */
public final class BlastEnchantSelfTest {

    private BlastEnchantSelfTest() {
    }

    private record Result(String name, boolean pass, String detail) {
    }

    /** @return 全部通过时返回检查项数量，否则返回 0（便于在函数里用 execute store 断言） */
    public static int run(ServerCommandSource source) {
        List<Result> results = new ArrayList<>();
        ServerWorld world = source.getWorld();

        results.add(levelCapCheck(world));
        results.addAll(poolChecks(world));
        results.add(recipeCheck(source.getServer()));
        results.add(blastFurnaceCheck(world, source.getPosition()));

        int passed = 0;
        for (Result result : results) {
            if (result.pass()) {
                passed++;
            }
        }
        final int passedCount = passed;
        final boolean allPass = passed == results.size();

        source.sendFeedback(() -> Text.literal("===== Blast Enchant 自检 =====").formatted(Formatting.AQUA), false);
        for (Result result : results) {
            source.sendFeedback(() -> Text.literal((result.pass() ? "  [通过] " : "  [失败] ")
                            + result.name() + "：" + result.detail())
                    .formatted(result.pass() ? Formatting.GREEN : Formatting.RED), false);
        }
        source.sendFeedback(() -> Text.literal("结果：" + passedCount + "/" + results.size()
                        + (allPass ? "  全部通过" : "  存在失败项"))
                .formatted(allPass ? Formatting.GREEN : Formatting.RED), false);

        return allPass ? results.size() : 0;
    }

    /** 1. 原版有 3 处 255 硬上限，任何一处没绕过都会在这里暴露。 */
    private static Result levelCapCheck(ServerWorld world) {
        String name = "附魔等级上限";
        try {
            Registry<Enchantment> registry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            Enchantment sharpness = registry.get(Identifier.ofVanilla("sharpness"));
            if (sharpness == null) {
                return new Result(name, false, "注册表里找不到 minecraft:sharpness");
            }
            RegistryEntry<Enchantment> entry = registry.getEntry(sharpness);
            int expected = BlastEnchantConfig.enchantLevel;

            ItemEnchantmentsComponent.Builder builder =
                    new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            builder.add(entry, expected);
            ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
            stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

            ItemEnchantmentsComponent built = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (built == null) {
                return new Result(name, false, "组件写入后读不回来（构造阶段可能抛异常）");
            }
            int afterBuild = built.getLevel(entry);
            if (afterBuild != expected) {
                return new Result(name, false, "Builder 阶段被截断成 " + afterBuild + "（期望 " + expected + "）");
            }

            RegistryOps<NbtElement> ops = world.getRegistryManager().getOps(NbtOps.INSTANCE);
            NbtElement encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            ItemEnchantmentsComponent afterRoundTrip = decoded.get(DataComponentTypes.ENCHANTMENTS);
            if (afterRoundTrip == null || afterRoundTrip.isEmpty()) {
                return new Result(name, false, "NBT 往返后附魔整个丢了（codec 上限没放开）");
            }
            int level = afterRoundTrip.getLevel(entry);
            if (level != expected) {
                return new Result(name, false, "NBT 往返后变成 " + level + "（期望 " + expected + "）");
            }
            return new Result(name, true, "组件 + NBT 往返都保持 " + expected + " 级");
        } catch (Exception e) {
            return new Result(name, false, "抛出异常：" + e);
        }
    }

    /** 2. 附魔池：抽到的必须"真的能作用在这件装备上"，而且等级正确。 */
    private static List<Result> poolChecks(ServerWorld world) {
        List<Result> results = new ArrayList<>();
        results.add(poolCheck(world, new ItemStack(Items.DIAMOND_SWORD)));
        results.add(poolCheck(world, new ItemStack(Items.DIAMOND_BOOTS)));
        results.add(poolCheck(world, new ItemStack(Items.TRIDENT)));
        results.add(poolCheck(world, new ItemStack(Items.BOW)));
        results.add(poolCheck(world, new ItemStack(Items.SHEARS)));
        return results;
    }

    private static Result poolCheck(ServerWorld world, ItemStack sample) {
        String name = "附魔池 " + sample.getItem().getName().getString();
        String vanillaName = net.minecraft.registry.Registries.ITEM.getId(sample.getItem()).getPath();
        TagKey<Enchantment> curseTag = TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("curse"));
        Set<String> seen = new LinkedHashSet<>();

        try {
            for (int i = 0; i < 40; i++) {
                ItemStack rolled = EnchantRoller.roll(world, sample.copy());
                ItemEnchantmentsComponent component = rolled.get(DataComponentTypes.ENCHANTMENTS);
                if (component == null || component.isEmpty()) {
                    return new Result(name, false, vanillaName + " 一次都没抽到附魔");
                }
                for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
                    if (!entry.value().isAcceptableItem(rolled)) {
                        return new Result(name, false, "抽到用不了的附魔 " + entry.getIdAsString());
                    }
                    if (!BlastEnchantConfig.allowCurses && entry.isIn(curseTag)) {
                        return new Result(name, false, "抽到诅咒附魔 " + entry.getIdAsString());
                    }
                    int level = component.getLevel(entry);
                    if (level != BlastEnchantConfig.enchantLevel) {
                        return new Result(name, false, "等级是 " + level + "，期望 " + BlastEnchantConfig.enchantLevel);
                    }
                    seen.add(entry.getIdAsString());
                }
            }
        } catch (Exception e) {
            return new Result(name, false, "抛出异常：" + e);
        }

        if (seen.size() < 2) {
            return new Result(name, false, "40 次抽样只出现 " + seen.size() + " 种附魔：" + seen);
        }
        return new Result(name, true, "40 次抽样拿到 " + seen.size() + " 种可用附魔：" + seen);
    }

    /** 3. 配方是否都加载了（数据包被覆盖、写错路径都会在这里暴露）。 */
    private static Result recipeCheck(MinecraftServer server) {
        String name = "烧炼配方";
        int count = 0;
        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            if (entry.id().getNamespace().equals(BlastEnchant.MOD_ID)
                    && entry.value() instanceof BlastingRecipe) {
                count++;
            }
        }
        if (count < 60) {
            return new Result(name, false, "只加载了 " + count + " 条 blasting 配方（期望 ≥ 60）");
        }
        Identifier probe = Identifier.of(BlastEnchant.MOD_ID, "diamond_sword");
        if (server.getRecipeManager().get(probe).isEmpty()) {
            return new Result(name, false, count + " 条配方已加载，但查不到 " + probe);
        }
        return new Result(name, true, count + " 条 blasting 配方已加载，diamond_sword 可查");
    }

    /**
     * 4. 端到端：在附近的空气位置临时放一个真高炉，手动推进 400 刻，看产物。
     * 用空气位置是为了不破坏任何建筑，跑完会恢复原方块。
     */
    private static Result blastFurnaceCheck(ServerWorld world, Vec3d sourcePos) {
        String name = "高炉端到端";
        BlockPos base = BlockPos.ofFloored(sourcePos);
        BlockPos pos = null;
        for (int dy = 2; dy <= 10 && pos == null; dy++) {
            BlockPos candidate = base.up(dy);
            if (world.getBlockState(candidate).isAir()) {
                pos = candidate;
            }
        }
        if (pos == null) {
            return new Result(name, false, "附近 10 格内找不到空的方块位置");
        }

        BlockState previous = world.getBlockState(pos);
        try {
            world.setBlockState(pos, Blocks.BLAST_FURNACE.getDefaultState());
            if (!(world.getBlockEntity(pos) instanceof BlastFurnaceBlockEntity furnace)) {
                return new Result(name, false, "高炉方块实体没有生成");
            }
            furnace.setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            furnace.setStack(1, new ItemStack(Items.COAL, 8));

            for (int i = 0; i < 400; i++) {
                AbstractFurnaceBlockEntity.tick(world, pos, world.getBlockState(pos), furnace);
            }

            ItemStack output = furnace.getStack(2);
            if (output.isEmpty()) {
                return new Result(name, false, "烧了 400 刻没有任何产出（配方没匹配上？）");
            }
            if (!output.isOf(Items.DIAMOND_SWORD)) {
                return new Result(name, false, "产物不是钻石剑，而是 " + output);
            }
            ItemEnchantmentsComponent component = output.get(DataComponentTypes.ENCHANTMENTS);
            if (component == null || component.isEmpty()) {
                return new Result(name, false, "产物没有附魔：" + output);
            }
            RegistryEntry<Enchantment> first = component.getEnchantments().iterator().next();
            int level = component.getLevel(first);
            if (level != BlastEnchantConfig.enchantLevel) {
                return new Result(name, false, "附魔等级是 " + level + "，期望 " + BlastEnchantConfig.enchantLevel);
            }
            return new Result(name, true, "钻石剑 -> " + first.getIdAsString() + " " + level);
        } catch (Exception e) {
            return new Result(name, false, "抛出异常：" + e);
        } finally {
            world.setBlockState(pos, previous);
        }
    }
}
