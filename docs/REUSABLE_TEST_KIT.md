# 可复用测试套件

给「以后还要写别的模组」准备的。三层测试，成本从低到高，**关键在于它们的覆盖范围不一样**——尤其是第一层有个致命盲区。

---

## 三层体系总览

| 问题类型 | ① gametest | ② 游戏内自检命令 | ③ 生产服务端脚本 |
|---|:---:|:---:|:---:|
| 业务逻辑写错 | ✅ | ✅ | ✅ |
| 注册表 / 数据包 / 配方加载 | ✅ | ✅ | ✅ |
| 存档（NBT）往返 | 部分 | ✅ | ✅ |
| **Mixin refmap / 命名空间** | ❌ | ✅ | ✅ |
| 多模组冲突 | ❌ | ✅ | 部分 |
| 客户端渲染 / GUI | ❌ | ❌ | ❌（只能人工看） |

> **最重要的一行**：gametest 跑在开发环境（yarn 名字），**永远测不出 refmap 缺失这类问题**。
> 这次的线上崩溃就是这么漏掉的。所以第 ② 层（自检命令）是性价比最高的一层，写新模组时优先做它。

---

## ① gametest（开发环境，验证逻辑）

`build.gradle`：

```groovy
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "你的模组id-test"
        enableGameTests = true
        enableClientGameTests = false
        eula = true
    }
}
```

三个必须注意的点（都是踩过的）：

1. 源集目录是 **`src/gametest/java`**（不是 `src/test/java`）。
2. **必须**自己写 `src/gametest/resources/fabric.mod.json`，否则测试类不会被当成模组扫描。
3. Fabric 2.0.x 的 gametest 改用 **entrypoint** 发现测试类：

```json
{
  "schemaVersion": 1,
  "id": "你的模组id-test",
  "version": "1.0.0",
  "environment": "*",
  "entrypoints": {
    "fabric-gametest": ["com.example.yourmod.test.YourGameTest"]
  },
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.1",
    "fabric-api": "*"
  }
}
```

测试类骨架：

```java
public class YourGameTest implements FabricGameTest {
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void somethingWorks(TestContext context) {
        // 断言……
        context.assertTrue(cond, "失败信息");
        context.complete();          // ← 不调这句会报 "Didn't succeed or fail within N ticks"
    }
}
```

跑：`./gradlew runGameTest`（注意任务名是 `runGameTest`，不是 `runGametest`）。

两个高频坑：

- `context.setBlockState(pos, ...)` 用的是**相对结构的坐标**；要手动 tick 方块实体时必须用 `blockEntity.getPos()` 拿绝对坐标，否则 tick 到空气上（报 `Cannot set property ... as it does not exist in Block{minecraft:air}`）。
- 手动 tick 比等真实刻快得多：`for (int i = 0; i < 400; i++) AbstractFurnaceBlockEntity.tick(world, pos, state, be);`

---

## ② 游戏内自检命令（正式环境，推荐每个模组都做）

`BlastEnchantSelfTest` 就是模板，照抄结构即可。设计要点：

1. 每项检查返回一个小记录，便于汇总：
   ```java
   private record Result(String name, boolean pass, String detail) {}
   ```
2. 输出用 `source.sendFeedback(() -> Text.literal(...).formatted(Formatting.GREEN), false)`，通过绿、失败红。
3. **返回值有意义**：全部通过返回检查项数量，否则返回 0。这样可以直接在数据包/函数里断言：
   ```
   execute store result score SelfTest obj run blastenchant selftest
   execute if score SelfTest obj matches 1.. run say 自检通过
   ```
4. **世界内端到端检查要挑空气位置**，并且 `try/finally` 恢复：
   ```java
   BlockState previous = world.getBlockState(pos);
   try { ... } finally { world.setBlockState(pos, previous); }
   ```
5. 注册时给个配置开关，正式服务器可以关掉：
   ```java
   CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
       dispatcher.register(CommandManager.literal("yourmod")
           .requires(source -> source.hasPermissionLevel(2))
           .then(CommandManager.literal("selftest")
               .executes(ctx -> YourSelfTest.run(ctx.getSource())))));
   ```

---

## ③ 生产服务端脚本（正式命名空间，可无人值守）

`tools/production-test/` 是一个可直接丢进服务器世界的数据包。用法：

1. 拷到 `world/datapacks/production-test/`，启动服务器即可（`load` 函数自动执行）。
2. 最简单的方式是直接调用自检命令：
   ```
   blastenchant selftest
   ```
   输出会进服务器日志。
3. 需要断言具体数值时，用记分板中转：
   ```
   execute store result score X blasttest run data get block ~ ~1 ~ Items[{Slot:2b}].components."minecraft:enchantments".levels."minecraft:sharpness"
   execute if score X blasttest matches 1000 run say PASS_SHARPNESS_1000
   ```

数据包结构（1.21 起目录是**单数**）：

```
world/datapacks/production-test/pack.mcmeta                                  {"pack":{"pack_format":48,...}}
world/datapacks/production-test/data/minecraft/tags/function/load.json       {"values":["你的ns:load"]}
world/datapacks/production-test/data/你的ns/function/load.mcfunction
```

下载服务端（生产用的 loader，不是 Loom）：

```powershell
Invoke-WebRequest "https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.10/1.1.2/server/jar" -OutFile fabric-server-launch.jar
java -jar fabric-server-launch.jar nogui
```

### 这里踩过的坑（都是真金白银换的）

1. **函数里不能写 `stop`**。数据包解析发生在命令树建好之前，`stop` 会报 `Unknown or incomplete command`，导致**整个函数加载失败**（连带 `#minecraft:load` 标签引用缺失）。
2. **`data merge block` 会走方块实体的 `readNbt`**。给高炉塞物品时不写 `CookTimeTotal`，它会被重置成 0；而 1.21.1 只在「烧完那一刻」才更新这个字段，于是高炉看起来一直在烧（CookTime 在涨）却**永远不产出**。要显式写 `CookTimeTotal:100s`。
3. **选择器参数用 `=` 不是 `:`**（`limit=1`，写 `limit:1` 会整条函数加载失败）。
4. **函数里的 `data get` 输出不会进日志**（反馈被抑制），要用 `execute if score ... run say` 把结论打出来。
5. 想验证"原版行为没被带坏"，就在同一个脚本里放一个**对照组**（例如原版配方的高炉）。这次就是靠对照组才确认问题出在脚手架而不是模组上。

---

## 搬去下一个模组的清单

- [ ] 复制 `BlastEnchantSelfTest` → `YourModSelfTest`，改检查项（保留「等级上限」那种"我以为对但其实很脆"的断言思路）
- [ ] 复制 `build.gradle` 里的 `fabricApi { configureTests { ... } }`
- [ ] 复制 `src/gametest/resources/fabric.mod.json`，改 id 与 entrypoint 类名
- [ ] 复制 `tools/production-test/` 的数据包骨架，改命名空间
- [ ] **每次改动 Mixin 后必做**：`./gradlew build` → 解压 jar 确认 `*-refmap.json` 里有该 Mixin 的方法条目 → 跑一次生产脚本
- [ ] 发布前把这三层都跑一遍（顺序：gametest → 游戏内自检 → 生产脚本）
