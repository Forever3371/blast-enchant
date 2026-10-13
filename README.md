# Blast Enchant（高炉附魔）

Minecraft **1.21.1** / **Fabric** 模组：把工具、武器、盔甲放进**高炉**烧炼，
烧出来的还是同一件装备，但会带上**随机的满级附魔**（默认等级 **1000**）。

例如：钻石剑 → 高炉 → 随机变成「锋利 1000」或「亡灵杀手 1000」或「耐久 1000」……

## 特点

- **只抽可用附魔**：钻石剑只能抽到剑能吃的附魔（锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、横扫之刃、耐久、经验修补……），
  不会出现「冰霜行者」这种只能给靴子的附魔。三叉戟只会抽到忠诚/穿刺/激流，靴子只会抽到冰霜行者/深海探索者，以此类推。
- **等级可调**：改配置文件即可把 1000 改成任意等级。
- **只影响高炉**：普通熔炉、烟熏炉保持原版行为（它们本来也没有这些配方）。
- **纯数据包式配方**：67 条 `minecraft:blasting` 配方放在模组内，KubeJS/数据包也能覆盖或追加。

## 安装

1. 安装 Fabric Loader（≥ 0.16）+ **Fabric API**。
2. 把 `blast-enchant-1.0.0.jar` 放进 `mods/` 文件夹。
3. 模组需要**服务端和客户端都装**：物品数据是服务端生成的，客户端装模组是为了让附魔等级正确显示（否则会显示成 `enchantment.level.1000` 这样的原始翻译键）。

## 用法

1. 造一个高炉，加燃料。
2. 把钻石剑（或任何受支持的装备）放进**输入槽**。
3. 烧完 5 秒（100 刻）后取出，它已经变成附了随机满级附魔的同款装备。

> 提示：同一件装备只能加工一次——已经带满级附魔的产物放进高炉不会再被重新随机，
> 所以你可以靠「烧很多把剑、挑一把想要的」来刷附魔。

## 配置

首次启动后生成 `config/blast_enchant.properties`：

```properties
# 附魔等级（默认 1000）
enchant_level=1000

# 每件装备烧出几个附魔，1 = 只随机一个（默认 1）
enchant_count=1

# 是否允许烧出诅咒附魔：绑定诅咒、消失诅咒（默认 false）
allow_curses=false

# 附魔黑名单，多个用英文逗号分隔，例如 minecraft:riptide,minecraft:channeling
blacklist=
```

改完重启游戏（或服务器）生效。

> 关于附魔等级的显示：1.21.1 的附魔等级文本是 `enchantment.level.<等级>` 这个翻译键拼出来的
> （原版只提供了 1~10，即 I~X）。模组的语言文件里已经补了 `enchantment.level.1000 = "1000"`，
> 所以默认配置下会正常显示「锋利 1000」。如果你把 `enchant_level` 改成别的数值，
> 想把客户端显示也做漂亮，就往 `src/main/resources/assets/blast_enchant/lang/*.json` 里补一条
> `"enchantment.level.<你的等级>": "<你想显示的文本>"`（否则会显示成原始翻译键）。

## 支持烧炼的物品

- 木/石/铁/金/钻石/下界合金 的 剑、镐、斧、锹、锄（6 × 5 = 30 种）
- 皮革/锁链/铁/金/钻石/下界合金 的 头盔、胸甲、护腿、靴子（6 × 4 = 24 种）
- 海龟壳、弓、弩、三叉戟、盾牌、鞘翅、钓鱼竿、胡萝卜钓竿、诡异菌钓竿、打火石、剪刀、刷子、重锤

想增删物品：往 `src/main/resources/data/blast_enchant/recipe/` 里加/删对应的 JSON 即可，
格式（1.21.1 的 `minecraft:blasting` 配方）：

```json
{
  "type": "minecraft:blasting",
  "category": "misc",
  "ingredient": { "item": "minecraft:diamond_sword" },
  "result": { "id": "minecraft:diamond_sword", "count": 1 },
  "experience": 0.1,
  "cookingtime": 100
}
```

`cookingtime` 是烧炼刻数（100 刻 = 5 秒），`experience` 是产出的经验值。
也可以做成独立数据包放进世界的 `datapacks/` 覆盖。

## 实现原理

| 文件 | 作用 |
|---|---|
| `data/blast_enchant/recipe/*.json` | 67 条 `blasting` 配方，让高炉接受这些装备（输入输出都是同一件物品） |
| `FurnaceBlaster` | 在高炉 tick 结束时检查输出槽，若是未加工的装备就替换成带随机附魔的副本 |
| `EnchantRoller` | 从「确实能作用在该物品上」的附魔池里随机抽取，写入 `minecraft:enchantments` 组件 |
| `AbstractFurnaceBlockEntityMixin` | 挂到 `AbstractFurnaceBlockEntity#tick` 的 TAIL，只在 `BlastFurnaceBlockEntity` 上生效 |
| `ItemEnchantmentsComponentBuilderMixin` | 绕过原版 `Builder#add/set` 里写死的 `Math.min(level, 255)` |
| `ItemEnchantmentsComponentInitMixin` | 绕过原版构造函数里 `level > 255` 就抛异常的校验 |
| `ItemEnchantmentsComponentMixin` | 绕过原版 `ENCHANTMENT_LEVEL_CODEC = Codec.intRange(0, 255)` |
| `BlastEnchantConfig` | 读取 `config/blast_enchant.properties` |

### 为什么需要三个额外的 Mixin

1.21.1 原版对附魔等级有**三处**硬编码的 255 上限（全部核对过字节码，缺一处都不行）：

- `ItemEnchantmentsComponent.Builder#add` / `#set` 里是 `Math.min(level, 255)` —— 不改它，"锋利 1000"会被静默截断成"锋利 255"。
- `ItemEnchantmentsComponent` 的私有构造函数里有 `if (level > 255) throw new IllegalArgumentException(...)` —— 不改它，`Builder#build()` 当场抛异常。
- `ItemEnchantmentsComponent` 的 `<clinit>` 里 `ENCHANTMENT_LEVEL_CODEC = Codec.intRange(0, 255)` —— 不改它，1000 级的物品**写不进 NBT**（存盘 / `/data get` 直接报错，物品会丢）。

网络同步不受影响（用的是 `PacketCodecs.VAR_INT`，没有范围校验）。三个 Mixin 只放宽上限、不收紧，正常情况下不会影响其他模组。

因为原版附魔最高只有 5 级，判断"产物是否已加工过"的方式是看它是否已存在 ≥ 目标等级的附魔，避免每 tick 反复随机导致附魔跳变。

### 自动化测试

```bash
./gradlew runGameTest
```

`src/gametest/java/` 下的 5 个测试会在无头服务端里真的放一个高炉并推进 250 刻，覆盖：

- 钻石剑烧完确实变成带 1000 级附魔的钻石剑
- 靴子只会抽到靴子能用的附魔，且不会抽到诅咒
- 60 次烧炼出现 ≥3 种不同结果（随机性）
- 1000 级附魔能通过 NBT 往返而不被截断（等级上限 Mixin 的回归测试）
- 普通熔炉完全不受影响

> **注意**：gametest 跑在开发环境里，Minecraft 用的是 yarn 名字。
> 它**测不出 refmap / 命名空间类的问题**（下面「踩坑记录」里的那个崩溃就是这么漏掉的）。

### 生产环境（真实服务器）验证

`tools/production-test/` 是一个数据包，用来在**真正的 Fabric 服务端**（intermediary 命名，和玩家实际运行的环境一致）里做端到端验证。

用法：把它放到服务器世界的 `world/datapacks/production-test/`，启动服务器即可，日志里会出现：

```
[BlastEnchant] DIAG_CONTROL_VANILLA_RECIPE_OK       <- 对照组：原版配方高炉正常
[BlastEnchant] DIAG_EXP_OUTPUT_PRESENT              <- 模组配方高炉产出成功
[BlastEnchant] PASS_FURNACE_OUTPUT_SHARPNESS_1000   <- 产物是锋利 1000，没被截断成 255
[BlastEnchant] PASS_COMPONENT_CODEC_KEEPS_1000      <- 1000 级能通过组件序列化往返
```

它会在世界里放两个高炉：一个烧模组配方（钻石剑），一个烧原版配方（粗铁→铁锭）当对照组。

> 踩过的坑：用 `data merge block` 往高炉里塞物品会走方块实体的 `readNbt`，
> 不显式写 `CookTimeTotal` 就会被重置成 0，而 1.21.1 只在「烧完那一刻」才更新这个字段，
> 结果是高炉看起来一直在烧、却永远烧不出东西。脚手架里写了 `CookTimeTotal:100s` 绕开它。

### 踩坑记录：dev 能跑，正式客户端一进游戏就崩

第一版发布后用户实测崩溃：

```
InvalidInjectionException: Critical injection failure: @Redirect annotation on
blast_enchant$raiseLevelCap could not find any targets matching 'add' or 'set'
in net/minecraft/class_9304$class_9305
```

原因是 `ItemEnchantmentsComponentBuilderMixin` 的 `@Redirect` 注解上多写了一个 `remap = false`。
它会让 Mixin **不去 refmap 里把 `"add"` / `"set"` 翻译成生产环境的 intermediary 名**
（`method_57550` / `method_57547`），于是正式环境找不到目标方法、Mixin 应用失败、游戏崩溃；
而开发环境用的是 yarn 名字面量，恰好能跑通，所以本地测试全绿。

排查方法与结论：

- 看 jar 里的 `blast-enchant-refmap.json`：修复前**完全没有** `ItemEnchantmentsComponentBuilderMixin` 的条目，
  修复后出现了 `"add": "...method_57550..."` / `"set": "...method_57547..."`，与 yarn 映射文件一致。
- `remap = false` 只应该写在 `@At` 的 JDK 方法目标上（如 `Math.min`），**不能写在注解级别**。



## 自己构建

需要 **JDK 21**。项目自带 Gradle wrapper：

```bash
./gradlew build          # 产物在 build/libs/（顺带跑一遍 gametest）
./gradlew runClient      # 开发环境启动客户端
./gradlew runGameTest    # 只跑自动化测试
./gradlew genSources     # 生成 Minecraft 源码（方便查 API）
```

> wrapper 里的 `distributionUrl` 已经指向国内镜像
> `https://mirrors.cloud.tencent.com/gradle/gradle-8.12-bin.zip`（Gradle 8.12 + Loom 1.10.5 的组合）。
> 想换回官方源就把它改回 `https://services.gradle.org/distributions/gradle-8.12-bin.zip`；
> 注意 Loom 1.11 及以上需要 Gradle 8.14+，换 Loom 版本时记得一起换 Gradle。

## 许可

MIT
