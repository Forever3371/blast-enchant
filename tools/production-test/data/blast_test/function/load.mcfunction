# 生产环境（intermediary 命名）验证：入口
# 两个必须知道的坑：
#  1) data merge 会走方块实体的 readNbt，CookTimeTotal 不给值就会被重置为 0，
#     而 1.21.1 只在"烧完那一刻"才更新它 -> 高炉一直在烧却永远不产出。必须显式写入。
#  2) 数据包解析阶段命令树还没建好，函数里不能写 stop，否则整个函数加载失败。
say [BlastEnchant] production test START
scoreboard objectives add blasttest dummy
# 1) 模组自带自检命令：覆盖等级上限 / 附魔池 / 配方 / 高炉端到端
#    返回值 = 通过的检查项数量（全部通过才 > 0），可以直接断言
execute store result score SelfTest blasttest run blastenchant selftest
execute if score SelfTest blasttest matches 1.. run say [BlastEnchant] PASS_SELFTEST_ALL_CHECKS
execute if score SelfTest blasttest matches 0 run say [BlastEnchant] FAIL_SELFTEST
# 2) 实验组：模组自定义配方（钻石剑 -> 附魔钻石剑）
setblock ~ ~1 ~ minecraft:blast_furnace
data merge block ~ ~1 ~ {BurnTime:2000s,CookTime:0s,CookTimeTotal:100s,Items:[{Slot:0b,id:"minecraft:diamond_sword",count:1},{Slot:1b,id:"minecraft:coal",count:64}]}
# 3) 对照组：原版配方（粗铁 -> 铁锭），证明环境本身没问题
setblock ~ ~4 ~ minecraft:blast_furnace
data merge block ~ ~4 ~ {BurnTime:2000s,CookTime:0s,CookTimeTotal:100s,Items:[{Slot:0b,id:"minecraft:raw_iron",count:1},{Slot:1b,id:"minecraft:coal",count:64}]}
# 4) 组件链路：直接解析带 1000 级附魔的组件（codec + 构造函数校验）
loot spawn ~ ~7 ~ loot {pools:[{rolls:1,entries:[{type:"minecraft:item",name:"minecraft:diamond_boots",functions:[{function:"minecraft:set_components",components:{"minecraft:enchantments":{levels:{"minecraft:sharpness":1000}}}}]}]}]}
tag @e[type=minecraft:item,sort=nearest,limit=1] add blasttest_item
schedule function blast_test:check 400t
say [BlastEnchant] scheduled check in 400 ticks
