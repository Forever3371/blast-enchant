# 生产环境验证：400 刻后断言
# 自检命令的结果
execute if score SelfTest blasttest matches 1.. run say [BlastEnchant] RESULT_SELFTEST_OK
execute if score SelfTest blasttest matches 0 run say [BlastEnchant] RESULT_SELFTEST_FAILED
# 实验组：高炉产物必须恰好是锋利 1000（若 Builder 的 255 截断没被绕过，这里会是 255）
execute store result score FurnaceLevel blasttest run data get block ~ ~1 ~ Items[{Slot:2b}].components."minecraft:enchantments".levels."minecraft:sharpness"
execute if score FurnaceLevel blasttest matches 1000 run say [BlastEnchant] PASS_FURNACE_OUTPUT_SHARPNESS_1000
execute if score FurnaceLevel blasttest matches 1..999 run say [BlastEnchant] FAIL_FURNACE_LEVEL_TRUNCATED
execute if score FurnaceLevel blasttest matches 0 run say [BlastEnchant] FAIL_FURNACE_NO_OUTPUT
# 对照组：原版配方必须能正常烧
execute store result score ControlOut blasttest run data get block ~ ~4 ~ Items[{Slot:2b}].count
execute if score ControlOut blasttest matches 1 run say [BlastEnchant] PASS_CONTROL_VANILLA_RECIPE
execute if score ControlOut blasttest matches 0 run say [BlastEnchant] FAIL_CONTROL_VANILLA_RECIPE
# 组件 codec 必须保留 1000
execute store result score ComponentLevel blasttest run data get entity @e[type=minecraft:item,tag=blasttest_item,limit=1] Item.components."minecraft:enchantments".levels."minecraft:sharpness"
execute if score ComponentLevel blasttest matches 1000 run say [BlastEnchant] PASS_COMPONENT_CODEC_KEEPS_1000
execute if score ComponentLevel blasttest matches 1..999 run say [BlastEnchant] FAIL_COMPONENT_LEVEL_CLAMPED
execute if score ComponentLevel blasttest matches 0 run say [BlastEnchant] FAIL_COMPONENT_MISSING
say [BlastEnchant] production test DONE
