package com.example.blastenchant;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * 极简配置：不依赖任何配置库，直接读写 config/blast_enchant.properties。
 */
public final class BlastEnchantConfig {

    /** 烧出来的附魔等级。 */
    public static int enchantLevel = 1000;

    /** 每件装备烧出几个附魔（1 = 只随机一个）。 */
    public static int enchantCount = 1;

    /** 是否允许烧出诅咒附魔（绑定诅咒 / 消失诅咒）。 */
    public static boolean allowCurses = false;

    /** 是否注册 /blastenchant selftest 自检命令（正式服务器不需要时可以关掉）。 */
    public static boolean enableSelfTestCommand = true;

    /** 附魔黑名单，写附魔 id（例如 minecraft:riptide），逗号分隔。 */
    public static final List<String> blacklist = new ArrayList<>();

    private static final String FILE_NAME = "blast_enchant.properties";

    private BlastEnchantConfig() {
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = new Properties();

        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, defaultContent(), StandardCharsets.UTF_8);
                BlastEnchant.LOGGER.info("[Blast Enchant] 已生成默认配置：{}", path);
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } catch (IOException e) {
            BlastEnchant.LOGGER.warn("[Blast Enchant] 读取配置失败，使用内置默认值", e);
        }

        enchantLevel = readInt(props, "enchant_level", 1000, 1, BlastEnchant.MAX_ENCHANT_LEVEL);
        enchantCount = readInt(props, "enchant_count", 1, 1, 64);
        allowCurses = Boolean.parseBoolean(props.getProperty("allow_curses", "false").trim());
        enableSelfTestCommand = Boolean.parseBoolean(props.getProperty("enable_selftest_command", "true").trim());

        blacklist.clear();
        for (String raw : props.getProperty("blacklist", "").split(",")) {
            String id = raw.trim();
            if (!id.isEmpty()) {
                blacklist.add(id);
            }
        }
    }

    private static int readInt(Properties props, String key, int fallback, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                BlastEnchant.LOGGER.warn("[Blast Enchant] 配置项 {}={} 超出范围 [{}, {}]，使用默认值 {}",
                        key, value, min, max, fallback);
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            BlastEnchant.LOGGER.warn("[Blast Enchant] 配置项 {}={} 不是整数，使用默认值 {}", key, raw, fallback);
            return fallback;
        }
    }

    private static String defaultContent() {
        return """
                # Blast Enchant 配置
                # 把工具/武器/盔甲放进高炉，烧完会变成带有随机附魔的同一件装备。

                # 附魔等级（默认 1000）
                enchant_level=1000

                # 每件装备烧出几个附魔，1 = 只随机一个（默认 1）
                enchant_count=1

                # 是否允许烧出诅咒附魔：绑定诅咒、消失诅咒（默认 false）
                allow_curses=false

                # 是否注册 /blastenchant selftest 自检命令（默认 true；正式服务器想关掉就写 false）
                enable_selftest_command=true

                # 附魔黑名单，多个用英文逗号分隔，例如 minecraft:riptide,minecraft:channeling
                blacklist=
                """;
    }

    /** 供调试/测试使用：不读文件，直接套用一组内存值。 */
    public static void applyDefaultsForTesting() {
        enchantLevel = 1000;
        enchantCount = 1;
        allowCurses = false;
        blacklist.clear();
        blacklist.addAll(Arrays.asList());
    }
}
