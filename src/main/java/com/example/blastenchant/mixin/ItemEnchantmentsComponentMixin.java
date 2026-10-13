package com.example.blastenchant.mixin;

import com.example.blastenchant.BlastEnchant;
import com.mojang.serialization.Codec;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.1 原版把附魔等级的序列化上限硬编码成 255：
 * {@code ENCHANTMENT_LEVEL_CODEC = Codec.intRange(0, 255)}（在 &lt;clinit&gt; 里构造）。
 * 不改掉它的话，1000 级的附魔写不进 NBT（存盘 / /data get 都会失败），物品会直接丢失。
 * <p>
 * 已核对 1.21.1 字节码：&lt;clinit&gt; 中只有这一处 Codec.intRange 调用，
 * 网络同步用的是 PacketCodecs.VAR_INT，不走这个 codec，所以只需改这一处。
 */
@Mixin(ItemEnchantmentsComponent.class)
public class ItemEnchantmentsComponentMixin {

    @Redirect(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;intRange(II)Lcom/mojang/serialization/Codec;",
                    remap = false
            ),
            remap = false
    )
    private static Codec<Integer> blast_enchant$raiseLevelCodecCap(int min, int vanillaMax) {
        return Codec.intRange(min, BlastEnchant.MAX_ENCHANT_LEVEL);
    }
}
