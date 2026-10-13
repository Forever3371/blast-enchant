package com.example.blastenchant;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * 高炉产出加工：烧炼完成的瞬间，把输出槽里的装备换成带随机附魔的同一件装备。
 * 只在服务端执行，且只作用于高炉（普通熔炉、烟熏炉不受影响）。
 */
public final class FurnaceBlaster {

    /** 高炉输出槽的槽位编号（0=输入，1=燃料，2=产物）。 */
    private static final int OUTPUT_SLOT = 2;

    private FurnaceBlaster() {
    }

    /**
     * @param world    来自 AbstractFurnaceBlockEntity#tick 的第一个参数（1.21.1 里声明类型是 World）
     * @param furnace  正在 tick 的熔炉方块实体
     */
    public static void onServerTick(World world, AbstractFurnaceBlockEntity furnace) {
        if (!(furnace instanceof BlastFurnaceBlockEntity)) {
            return;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        ItemStack output = furnace.getStack(OUTPUT_SLOT);
        if (output.isEmpty()
                || !EnchantRoller.isBlastable(serverWorld, output)
                || EnchantRoller.alreadyEnchanted(output)) {
            return;
        }

        furnace.setStack(OUTPUT_SLOT, EnchantRoller.roll(serverWorld, output));
        furnace.markDirty();
    }
}
