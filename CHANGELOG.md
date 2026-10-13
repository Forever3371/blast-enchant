# 更新日志

## [1.1.0] - 2026-10-13

### 新增

- 内置自检命令 `/blastenchant selftest`：在真实运行环境里检查附魔等级上限、附魔池、配方加载和高炉端到端，
  并返回通过项数量，便于在数据包/CI 里断言。可用配置项 `enable_selftest_command` 关闭。
- 文档 [`docs/IN_GAME_TESTS.md`](docs/IN_GAME_TESTS.md)：游戏内验收清单（含存档往返、多模组共存检查）。
- 文档 [`docs/REUSABLE_TEST_KIT.md`](docs/REUSABLE_TEST_KIT.md)：三层测试套件的复用指南。
- [`tools/production-test/`](tools/production-test)：可在真实 Fabric 服务端无人值守运行的验证数据包。

### 修复

- **修复正式环境一进游戏就崩溃的问题**：
  `ItemEnchantmentsComponentBuilderMixin` 的 `@Redirect` 注解上误加了 `remap = false`，
  导致 `blast-enchant-refmap.json` 缺少 `add` / `set` 的 intermediary 映射，
  正式客户端加载 `ItemEnchantmentsComponent$Builder` 时 Mixin 注入失败：

  ```
  InvalidInjectionException: Critical injection failure: @Redirect annotation on
  blast_enchant$raiseLevelCap could not find any targets matching 'add' or 'set'
  ```

  开发环境的 gametest 用的是 yarn 名字面量，因此测不出该问题；现已用真实 Fabric 服务端验证。

## [1.0.0] - 2026-10-13

### 新增

- 首次发布：把工具、武器、盔甲放进**高炉**烧炼，产出带有随机满级附魔（默认 1000 级）的同一件装备。
- 67 条 `minecraft:blasting` 配方，覆盖六种材质的剑镐斧锹锄、全套盔甲，以及弓、弩、三叉戟、盾牌、
  鞘翅、钓鱼竿、打火石、剪刀、刷子、重锤等可附魔装备。
- 只从「确实能作用在该物品上」的附魔池里抽取，默认排除诅咒附魔。
- 用三个 Mixin 绕过 1.21.1 原版对附魔等级的 **255** 硬上限：
  `Builder#add/set` 的截断、私有构造函数的校验异常、NBT codec 的取值范围。
- 配置文件 `config/blast_enchant.properties`：等级、每件装备附魔数量、是否允许诅咒、附魔黑名单。
