package com.example.blastenchant.mixin;

import com.example.blastenchant.FurnaceBlaster;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 挂在三种熔炉共用的 tick 上（AbstractFurnaceBlockEntity#tick 是 public static），
 * 在 tick 结束后检查输出槽，把烧出来的装备加工成随机满级附魔版。
 * <p>
 * 选择 tick 而不是 craftRecipe 的原因：craftRecipe 是私有方法、签名跨版本易变，
 * 而 tick 的签名稳定，且本逻辑是幂等的（已加工过的产物会被 alreadyEnchanted 跳过）。
 * <p>
 * 已核对 1.21.1 字节码：签名为
 * {@code public static void tick(World, BlockPos, BlockState, AbstractFurnaceBlockEntity)}，
 * 第一个参数是 World（不是 ServerWorld），所以处理方法必须按 World 声明。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private static void blast_enchant$enchantBlastFurnaceOutput(World world, BlockPos pos, BlockState state,
                                                                AbstractFurnaceBlockEntity blockEntity,
                                                                CallbackInfo ci) {
        FurnaceBlaster.onServerTick(world, blockEntity);
    }
}
