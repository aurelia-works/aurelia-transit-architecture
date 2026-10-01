import os
A="aurelia_transit_architecture:"; Y=-60; Z=-30; L=[]
def sb(x,y,z,b): L.append(f"setblock {x} {y} {z} {A}{b}")
def row(x1,x2,y,z,kindblock):  # E-W row with explicit joins
    for x in range(x1,x2+1):
        e="true" if x<x2 else "false"; w="true" if x>x1 else "false"
        L.append(f"setblock {x} {y} {z} {A}{kindblock},east={e},west={w}]")
L.append(f"fill -2 {Y} -44 46 {Y+12} -16 minecraft:air")
L.append(f"fill -2 {Y-1} -44 46 {Y-1} -16 minecraft:smooth_stone")
# stairs in every shape, each in a glazed enclosure bay (facing north = climbs north)
for i,shape in enumerate(["straight","inner_left","inner_right","outer_left","outer_right"]):
    x=2+i*3; sb(x,Y,Z-10,f"station_stair[facing=north,half=bottom,shape={shape}]")
    sb(x,Y+1,Z-10,f"station_stair[facing=north,half=top,shape={shape}]")
    for k,kind in enumerate(["glazed","windowed","clad"]):
        sb(x+1,Y+k,Z-10,f"stair_enclosure[facing=west,kind={kind}]")
# fences / handrails / balustrade rows
row(2,14,Y,Z,"station_fence[kind=platform"); row(2,14,Y,Z+2,"station_fence[kind=trackside")
row(2,14,Y,Z+4,"handrail[kind=handrail"); row(2,14,Y,Z+6,"handrail[kind=balustrade"); row(2,14,Y,Z+8,"handrail[kind=ramp_rail")
# viaduct: two columns, crossbeam, braces, wind screens on top (look up at it from below)
for x in (20,32):
    for y in range(Y,Y+7): sb(x,y,Z,"viaduct_column[style=concrete]")
for x in range(20,33): sb(x,Y+7,Z,"viaduct_beam[axis=x,kind=crossbeam,concrete=true]")
sb(21,Y+6,Z,"viaduct_brace[facing=east,kind=knee]"); sb(31,Y+6,Z,"viaduct_brace[facing=west,kind=knee]")
for x in range(20,33):
    sb(x,Y+8,Z,"platform_windscreen[facing=north,kind=lower]"); sb(x,Y+9,Z,"platform_windscreen[facing=north,kind=upper]")
# entrance pylon (walk round both faces)
sb(40,Y,Z,"entrance_pylon[facing=north,half=lower]"); sb(40,Y+1,Z,"entrance_pylon[facing=north,half=upper]")
# boarding markers on Alpha P1 (floor stickers above the paving)
for i,m in enumerate(["door","accessible","wait","ramp","assist"]): sb(2+i*2,Y+1,4,f"boarding_marker[facing=north,marker={m}]")
L.append('tellraw @a {"text":"Shader showcase placed north of Alpha (z -40..-22) and boarding markers on P1.","color":"green"}')
L.append(f"tp @a 17 {Y+1} -14 180 0")
open(os.path.join(os.path.dirname(__file__),"ata_test/data/ata_test/functions/showcase.mcfunction"),"w").write("\n".join(L)+"\n")
print(len(L))
