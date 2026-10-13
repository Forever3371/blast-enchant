package com.example.blastenchant;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.command.CommandManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blast Enchant —— 把工具/武器/盔甲放进高炉烧炼，
 * 产出带有随机（可用）附魔、等级由配置决定的同一件装备。
 */
public class BlastEnchant implements ModInitializer {

    public static final String MOD_ID = "blast_enchant";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 附魔等级上限。
     * <p>
     * 1.21.1 原版的硬上限是 255（{@code ItemEnchantmentsComponent} 的 NBT codec 与
     * {@code Builder#add/set} 都写死了 255），本模组用两个 Mixin 把它放宽到这里，
     * 否则"锋利 1000"会被截断成 255，甚至无法写入存档。
     */
    public static final int MAX_ENCHANT_LEVEL = 100000;

    @Override
    public void onInitialize() {
        BlastEnchantConfig.load();

        // 数据包 /reload 之后附魔注册表会重建，清掉"该物品能不能附魔"的缓存
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> EnchantRoller.clearCache());

        // 游戏内自检：/blastenchant selftest（需要 OP/权限等级 2）
        if (BlastEnchantConfig.enableSelfTestCommand) {
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                    dispatcher.register(CommandManager.literal("blastenchant")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.literal("selftest")
                                    .executes(context -> BlastEnchantSelfTest.run(context.getSource())))));
        }

        LOGGER.info("[Blast Enchant] 已加载：高炉烧炼装备 -> 随机 {} 级附魔（数量 {}，诅咒附魔 {}）",
                BlastEnchantConfig.enchantLevel,
                BlastEnchantConfig.enchantCount,
                BlastEnchantConfig.allowCurses ? "允许" : "禁止");
    }
}
