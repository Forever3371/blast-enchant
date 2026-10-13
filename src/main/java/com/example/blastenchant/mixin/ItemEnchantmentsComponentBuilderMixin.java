package com.example.blastenchant.mixin;

import com.example.blastenchant.BlastEnchant;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.1 原版 {@code ItemEnchantmentsComponent.Builder#add} 和 {@code #set} 里写死了
 * {@code Math.min(level, 255)}，任何超过 255 的等级都会被静默截断。
 * 这里把截断上限换成模组自己的上限，否则"锋利 1000"会变成"锋利 255"。
 * <p>
 * 注意：注解上的 {@code remap} 必须保持默认值（true）。
 * 一旦写成 {@code remap = false}，Mixin 就不会把 {@code "add"} / {@code "set"} 通过 refmap
 * 翻译成生产环境的 intermediary 名（{@code method_xxxxx}），
 * 结果就是开发环境一切正常、正式客户端一进游戏就崩
 * （{@code could not find any targets matching 'add' or 'set'}）。
 * 只有 {@code @At} 里的 JDK 方法 {@code Math.min} 才需要 {@code remap = false}。
 */
@Mixin(ItemEnchantmentsComponent.Builder.class)
public class ItemEnchantmentsComponentBuilderMixin {

    @Redirect(
            method = {"add", "set"},
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false)
    )
    private static int blast_enchant$raiseLevelCap(int level, int vanillaCap) {
        return Math.min(level, BlastEnchant.MAX_ENCHANT_LEVEL);
    }
}
