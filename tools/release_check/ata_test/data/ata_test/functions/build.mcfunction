gamerule doDaylightCycle false
gamerule doWeatherCycle false
time set noon
weather clear
gamerule doMobSpawning false
forceload add -16 -16 400 64
tellraw @a {"text":"Loading area, placing in 3 s...","color":"yellow"}
schedule function ata_test:place 60t
