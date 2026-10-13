package com.example.blastenchant.mixin;

import com.example.blastenchant.BlastEnchant;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 1.21.1 里附魔等级的第 3 处、也是最要命的一处硬上限：
 * {@code ItemEnchantmentsComponent} 的私有构造函数会遍历所有附魔，
 * 只要有一个等级 &gt; 255 就直接
 * {@code throw new IllegalArgumentException("Enchantment ... has invalid level ...")}。
 * 也就是说用 Builder 拼出 1000 级之后，{@code build()} 会当场抛异常。
 * 这里把构造函数里的常量 255 换成模组自己的上限。
 */
@Mixin(ItemEnchantmentsComponent.class)
public class ItemEnchantmentsComponentInitMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 255))
    private int blast_enchant$raiseConstructorLevelCap(int vanillaMax) {
        return BlastEnchant.MAX_ENCHANT_LEVEL;
    }
}
