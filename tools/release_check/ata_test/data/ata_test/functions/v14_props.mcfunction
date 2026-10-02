fill -2 -60 -105 50 -52 -83 minecraft:air
fill -2 -61 -105 50 -61 -83 minecraft:smooth_stone
setblock 2 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 3 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 4 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 6 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid_half]
setblock 7 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid_half]
setblock 8 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid_half]
setblock 10 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 11 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 12 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 14 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass_half]
setblock 15 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass_half]
setblock 16 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass_half]
setblock 18 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid_glass]
setblock 19 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid_glass]
setblock 20 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid_glass]
setblock 24 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 28 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 25 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 29 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 26 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 30 -60 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 24 -59 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 28 -59 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 25 -59 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 29 -59 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 26 -59 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 30 -59 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 24 -58 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 28 -58 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 25 -58 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 29 -58 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 26 -58 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=solid]
setblock 30 -58 -95 aurelia_transit_architecture:noise_barrier[facing=north,kind=glass]
setblock 2 -60 -89 aurelia_transit_architecture:fare_gate[facing=south,kind=end]
setblock 3 -60 -89 aurelia_transit_architecture:fare_gate[facing=south,kind=gate]
setblock 4 -60 -89 aurelia_transit_architecture:fare_gate[facing=south,kind=gate]
setblock 5 -60 -89 aurelia_transit_architecture:fare_gate[facing=south,kind=wide]
setblock 6 -60 -89 aurelia_transit_architecture:fare_gate[facing=south,kind=end]
setblock 9 -60 -89 aurelia_transit_architecture:card_reader[facing=south,kind=post]
setblock 11 -60 -90 minecraft:stone_bricks
setblock 11 -59 -90 minecraft:stone_bricks
setblock 11 -59 -89 aurelia_transit_architecture:card_reader[facing=south,kind=wall]
fill 14 -60 -90 30 -57 -90 minecraft:stone_bricks
fill 14 -56 -90 30 -56 -87 minecraft:stone_bricks
setblock 15 -59 -89 aurelia_transit_architecture:booth_window[facing=south]
setblock 16 -59 -89 aurelia_transit_architecture:booth_window[facing=south]
setblock 19 -58 -89 aurelia_transit_architecture:cctv_camera[facing=south,kind=wall]
setblock 21 -57 -88 aurelia_transit_architecture:cctv_camera[facing=south,kind=pendant]
setblock 23 -57 -88 aurelia_transit_architecture:cctv_camera[facing=south,kind=dome]
setblock 25 -59 -89 aurelia_transit_architecture:lift_status_panel[facing=south,status=in_service,left=false,right=false]
data merge block 25 -59 -89 {Sign:{Primary:"Lift A",Secondary:"Street - Platforms"}}
setblock 27 -59 -89 aurelia_transit_architecture:lift_status_panel[facing=south,status=out_of_service,left=false,right=false]
data merge block 27 -59 -89 {Sign:{Primary:"Lift B",Secondary:"Street - Platforms"}}
setblock 29 -59 -89 aurelia_transit_architecture:lift_status_panel[facing=south,status=maintenance,left=false,right=false]
data merge block 29 -59 -89 {Sign:{Primary:"Lift C",Secondary:"Street - Platforms"}}
tellraw @a {"text":"1.4 props scene placed (z -105..-83).","color":"green"}
tp @a 16 -59 -84 180 10
