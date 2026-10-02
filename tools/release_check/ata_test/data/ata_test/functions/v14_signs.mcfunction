fill 0 -60 -104 20 -56 -104 minecraft:stone_bricks
fill 0 -55 -104 20 -55 -100 minecraft:stone_bricks
setblock 2 -58 -103 aurelia_transit_architecture:psd_text_panel[facing=south,left=true,right=false]
setblock 3 -58 -103 aurelia_transit_architecture:psd_text_panel[facing=south,left=true,right=true]
setblock 4 -58 -103 aurelia_transit_architecture:psd_text_panel[facing=south,left=true,right=true]
setblock 5 -58 -103 aurelia_transit_architecture:psd_text_panel[facing=south,left=false,right=true]
data merge block 5 -58 -103 {Sign:{Primary:"Beta 3 min",Secondary:"Doors 1-4",Platform:"1"}}
setblock 8 -59 -103 aurelia_transit_architecture:stand_back_sign[facing=south,left=false,right=false]
setblock 11 -56 -102 aurelia_transit_architecture:composition_board[facing=south,left=true,right=false]
setblock 12 -56 -102 aurelia_transit_architecture:composition_board[facing=south,left=true,right=true]
setblock 13 -56 -102 aurelia_transit_architecture:composition_board[facing=south,left=true,right=true]
setblock 14 -56 -102 aurelia_transit_architecture:composition_board[facing=south,left=false,right=true]
data merge block 14 -56 -102 {Sign:{Primary:"1+ 2 3 | 4 5 6!",Secondary:"A B C D"}}
tellraw @a {"text":"1.4 package 5 scene placed (z -104).","color":"green"}
