# 生产环境验证：400 刻后断言
# 说明：
#  - 附魔是随机的，所以这里只断言"产物存在且带附魔组件"；等级的精确值由自检命令负责。
#  - 不用 loot 表的 set_components 构造附魔组件做断言：实测原版会把这种构造出来的附魔
#    规范化掉（minecraft:enchantments.levels 键存在但内容为空），它不能作为断言入口。
#    模组自身路径（EnchantRoller + ItemStack#set）的组件往返由自检命令覆盖。
# 自检命令结果
execute if score SelfTest blasttest matches 1.. run say [BlastEnchant] RESULT_SELFTEST_OK
execute if score SelfTest blasttest matches 0 run say [BlastEnchant] RESULT_SELFTEST_FAILED
# 实验组：世界 tick 路径产出的高炉产物
execute store result score ExpOut blasttest run data get block ~ ~1 ~ Items[{Slot:2b}].count
execute if score ExpOut blasttest matches 1 run say [BlastEnchant] PASS_FURNACE_OUTPUT_PRESENT
execute if score ExpOut blasttest matches 0 run say [BlastEnchant] FAIL_FURNACE_NO_OUTPUT
execute if data block ~ ~1 ~ Items[{Slot:2b}].components."minecraft:enchantments" run say [BlastEnchant] PASS_FURNACE_OUTPUT_IS_ENCHANTED
execute unless data block ~ ~1 ~ Items[{Slot:2b}].components."minecraft:enchantments" run say [BlastEnchant] FAIL_FURNACE_OUTPUT_NOT_ENCHANTED
# 对照组：原版配方必须能正常烧（证明环境本身没问题）
execute store result score ControlOut blasttest run data get block ~ ~4 ~ Items[{Slot:2b}].count
execute if score ControlOut blasttest matches 1 run say [BlastEnchant] PASS_CONTROL_VANILLA_RECIPE
execute if score ControlOut blasttest matches 0 run say [BlastEnchant] FAIL_CONTROL_VANILLA_RECIPE
say [BlastEnchant] production test DONE
