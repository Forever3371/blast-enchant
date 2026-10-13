package com.example.blastenchant.test;

import com.example.blastenchant.EnchantRoller;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * 自动化测试：在无头服务端里真的放一个高炉，验证从"放进去"到"取出来"的全流程。
 * 运行：./gradlew runGameTest
 */
public class BlastEnchantGameTest implements FabricGameTest {

    private static final int EXPECTED_LEVEL = 1000;
    private static final BlockPos FURNACE_POS = new BlockPos(1, 1, 1);
    private static final TagKey<Enchantment> CURSE_TAG =
            TagKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("curse"));

    /** 核心流程：钻石剑进高炉，出来是带 1000 级附魔的钻石剑。 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void blastFurnaceEnchantsSword(TestContext context) {
        ServerWorld world = context.getWorld();
        BlastFurnaceBlockEntity furnace = placeBlastFurnace(context);

        furnace.setStack(0, new ItemStack(Items.DIAMOND_SWORD));
        furnace.setStack(1, new ItemStack(Items.COAL, 64));

        // 手动推进 250 刻（高炉默认 100 刻烧一件），等价于在真实世界里等它烧完
        tickFurnace(world, furnace, 250);

        ItemStack output = furnace.getStack(2);
        context.assertTrue(output.isOf(Items.DIAMOND_SWORD), "产物不是钻石剑，而是 " + output);
        context.assertEquals(EXPECTED_LEVEL, highestLevel(output), "附魔等级不对：" + output);
        context.complete();
    }

    /** 附魔必须"真的可用"：钻石靴子不允许烧出锋利，只允许靴子能吃的附魔。 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void bootsOnlyGetApplicableEnchantments(TestContext context) {
        ServerWorld world = context.getWorld();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 40; i++) {
            ItemStack boots = EnchantRoller.roll(world, new ItemStack(Items.DIAMOND_BOOTS));
            ItemEnchantmentsComponent component = boots.get(DataComponentTypes.ENCHANTMENTS);
            context.assertTrue(component != null && !component.isEmpty(), "靴子没有抽到任何附魔");

            for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
                context.assertTrue(entry.value().isAcceptableItem(boots),
                        "抽到了靴子用不了的附魔：" + entry.getIdAsString());
                context.assertFalse(entry.isIn(CURSE_TAG),
                        "默认不应该抽到诅咒附魔：" + entry.getIdAsString());
                context.assertEquals(EXPECTED_LEVEL, component.getLevel(entry), "等级不对");
                seen.add(entry.getIdAsString());
            }
        }

        context.assertTrue(seen.size() > 1, "40 次烧炼只出现了一种附魔，随机性有问题：" + seen);
        context.complete();
    }

    /** 附魔是随机的：反复烧同一件武器应该出现多种结果。 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void enchantChoiceIsRandom(TestContext context) {
        ServerWorld world = context.getWorld();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 60; i++) {
            ItemStack sword = EnchantRoller.roll(world, new ItemStack(Items.DIAMOND_SWORD));
            ItemEnchantmentsComponent component = sword.get(DataComponentTypes.ENCHANTMENTS);
            context.assertTrue(component != null && !component.isEmpty(), "钻石剑没有抽到任何附魔");
            for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
                seen.add(entry.getIdAsString() + " " + component.getLevel(entry));
            }
        }

        context.assertTrue(seen.size() >= 3, "60 次烧炼结果太单一：" + seen);
        context.complete();
    }

    /**
     * 关键回归测试：1000 级附魔必须能写进 NBT 再读回来（就是存盘走的那条路径）。
     * 原版 1.21.1 的等级上限是 255，这个测试用来确认三个等级上限 Mixin 真的生效。
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void highEnchantLevelSurvivesNbtRoundTrip(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack rolled = EnchantRoller.roll(world, new ItemStack(Items.DIAMOND_SWORD));
        context.assertEquals(EXPECTED_LEVEL, highestLevel(rolled), "抽取结果本身就不是 1000 级");

        // 附魔是动态注册表，序列化必须带上注册表上下文
        RegistryOps<NbtElement> ops = world.getRegistryManager().getOps(NbtOps.INSTANCE);
        NbtElement encoded = ItemStack.CODEC.encodeStart(ops, rolled).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();

        context.assertEquals(EXPECTED_LEVEL, highestLevel(decoded),
                "NBT 往返之后等级被改掉了，NBT=" + encoded);
        context.complete();
    }

    /** 普通熔炉不受影响（它没有这些配方，也不该被加工）。 */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void normalFurnaceIsUntouched(TestContext context) {
        ServerWorld world = context.getWorld();
        context.setBlockState(FURNACE_POS, Blocks.FURNACE);
        AbstractFurnaceBlockEntity furnace = context.getBlockEntity(FURNACE_POS);

        furnace.setStack(0, new ItemStack(Items.DIAMOND_SWORD));
        furnace.setStack(1, new ItemStack(Items.COAL, 64));
        tickFurnace(world, furnace, 250);

        ItemStack output = furnace.getStack(2);
        context.assertTrue(output.isEmpty(),
                "普通熔炉竟然烧出了东西：" + output + "；高炉配方不应作用于熔炉");
        context.complete();
    }

    private static BlastFurnaceBlockEntity placeBlastFurnace(TestContext context) {
        context.setBlockState(FURNACE_POS, Blocks.BLAST_FURNACE);
        BlastFurnaceBlockEntity furnace = context.getBlockEntity(FURNACE_POS);
        context.assertTrue(furnace != null, "高炉方块实体没有生成");
        return furnace;
    }

    /**
     * 直接手动 tick 高炉（走的就是被 Mixin 修改过的 AbstractFurnaceBlockEntity#tick）。
     * 注意必须用方块实体的绝对坐标：TestContext 的坐标是相对结构的，直接拿去 tick 会 tick 到空气。
     */
    private static void tickFurnace(ServerWorld world, AbstractFurnaceBlockEntity furnace, int ticks) {
        BlockPos pos = furnace.getPos();
        for (int i = 0; i < ticks; i++) {
            AbstractFurnaceBlockEntity.tick(world, pos, world.getBlockState(pos), furnace);
        }
    }

    private static int highestLevel(ItemStack stack) {
        ItemEnchantmentsComponent component = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (component == null || component.isEmpty()) {
            return -1;
        }
        int highest = -1;
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            highest = Math.max(highest, component.getLevel(entry));
        }
        return highest;
    }
}
