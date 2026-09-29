"""16x End-table art revision. Authoring outputs only; never changes runtime resources."""
from pathlib import Path
import base64
import json
import uuid
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
TEX = HERE / 'textures'
TEX.mkdir(exist_ok=True)


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'convert_table/end_refined/' + name))


def save_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def original(name):
    return np.array(Image.open(HERE/'references'/f'{name}.png').convert('RGBA'))[:16,:16].copy()


endstone = original('end_stone')
purpur = original('purpur_block')
obsidian = original('obsidian')
# Retain the vanilla cluster layout while compressing contrast within purple stone.
purple_mean=purpur[:,:,:3].mean(axis=(0,1))
purpur[:,:,:3]=np.clip(np.rint((purple_mean+(purpur[:,:,:3].astype(float)-purple_mean)*.75)*[1.05,.73,1.2]),0,255).astype(np.uint8)

# Textures stay 16x16 with one texel per voxel; the shortened side uses 13 active rows.
BODY_HEIGHT=13
HEIGHT_REDUCTION=2
P = {
    'dark': (34,17,53,255), 'deep': (77,25,120,255), 'dormant': (102,35,156,255),
    'dim': (123,40,177,255), 'violet': (151,53,206,255), 'bright': (178,82,232,255),
    'light': (203,126,250,255), 'white': (229,175,255,255),
}


def trim_upper_rail_row(pixels):
    """Remove one pixel row inside the upper endstone rail; keep texels below at 1:1."""
    result=np.zeros_like(pixels)
    result[:13]=np.concatenate((pixels[:1],pixels[2:14]),axis=0)
    return result


def make_side(side=False):
    # Obsidian's linked clusters remain visible. No generated white-noise overlay.
    result = obsidian.copy()
    emission = np.zeros((16,16), dtype=np.uint8)
    # A continuous, two-pixel-wide energy window with deliberate asymmetric facets.
    facets = [('violet','bright'),('bright','light'),('light','white'),
              ('white','bright'),('light','violet'),('bright','dim')]
    for row,pair in enumerate(facets,4):
        for dx,key in enumerate(pair):
            result[row,7+dx] = P[key]
            emission[row,7+dx] = {'deep':35,'dim':60,'violet':95,'bright':140,'light':180,'white':210}[key]
    glyph_left = [(3,5),(4,5),(3,6),(3,7),(4,7),(4,8),(3,9),(4,9)]
    glyph_right = [(11,6),(12,6),(12,7),(11,8),(12,8),(11,9),(11,10),(12,10)]
    if side:
        glyph_left = [(3,5),(3,6),(4,6),(4,7),(3,8),(4,8),(3,9)]
        glyph_right = [(11,5),(12,5),(12,6),(11,7),(12,7),(12,8),(11,9),(12,9)]
    for i,(x,y) in enumerate(glyph_left+glyph_right):
        y-=1  # Keep every rune texel, moving the pattern into the shorter black panel.
        result[y,x] = (123,41,175,255) if i%3 else (140,49,194,255)
        emission[y,x] = 28 if i%3 else 34
    # The backboard closes at the third depth, two voxels inside the outer envelope.
    result[:,:2,3]=0;result[:,14:,3]=0
    # Stop below the tabletop so the backboard cannot overlap the cavity's inner walls.
    result[:3,:,3]=0;result[11:,:,3]=0
    return trim_upper_rail_row(result), trim_upper_rail_row(emission)


def make_layers():
    # Three structural tiers: carved backboard, horizontal rails, corner guards.
    pixels = endstone.copy()
    pixels[11:14]=endstone[13:16]  # Preserve the original bottom rail's three texture rows.
    mask = np.zeros((16,16), dtype=bool)
    mask[:3,1:15] = True; mask[11:14,1:15] = True
    pixels[~mask] = [0,0,0,0]
    # The upper guards widen inward to wrap a 2x2 crystal in a one-voxel collar.
    guards = purpur.copy()
    guards[10:14]=purpur[12:16]
    guard_mask = np.zeros((16,16),dtype=bool)
    for x in (0,12):
        guard_mask[:4,x:x+4]=True
    # Foot guards extend upward by two voxels (2 -> 4 high), retaining their width.
    for x in (0,13):
        guard_mask[10:14,x:x+3]=True
    guards[~guard_mask]=[0,0,0,0]
    # Preserve the endstone clasp at the center of the upper rail.
    pixels[0:3,7:9] = endstone[5:8,6:8]
    # The upper rail loses one row (3 -> 2 high). Lower artwork remains at its world height.
    pixels,mask=trim_upper_rail_row(pixels),trim_upper_rail_row(mask)
    guards,guard_mask=trim_upper_rail_row(guards),trim_upper_rail_row(guard_mask)
    # Upper purple guards translate with the roof while retaining their four-voxel height.
    for x in (0,12):
        guard_mask[:4,x:x+4]=True
        guards[:4,x:x+4]=purpur[:4,x:x+4]
    return guards,guard_mask,pixels,mask


def energy_top():
    # Hand-authored stepped runic vortex; no radial distance/sine/circle fitting.
    rows = [
        '0000000000000000',
        '0000000000000000',
        '0000000000000000',
        '0000012222100000',
        '0000123333210000',
        '0001232114321000',
        '0002323441432000',
        '0002314554132000',
        '0002314554132000',
        '0002341443232000',
        '0001234112321000',
        '0000123333210000',
        '0000012222100000',
        '0000000000000000',
        '0000000000000000',
        '0000000000000000',
    ]
    colors = [P[k] for k in ['dark','deep','dim','violet','bright','light']]
    indices = np.array([[int(c) for c in row] for row in rows])
    return np.array(colors,dtype=np.uint8)[indices],np.array([0,15,35,70,110,160],dtype=np.uint8)[indices]


texture_names=[]
texture_arrays={}


def add_texture(name, color, emission=None, roughness=.8):
    if emission is None: emission=np.zeros((16,16),dtype=np.uint8)
    Image.fromarray(color).save(TEX/f'{name}.png')
    rgba=np.zeros((16,16,4),dtype=np.uint8)
    rgba[:]=[128,128,255,255]  # Intentionally neutral: no sub-voxel normal/height relief.
    Image.fromarray(rgba).save(TEX/f'{name}_n.png')
    rgba[:]=[round((1-roughness**.5)*255),10,32,0]
    rgba[:,:,3]=emission
    Image.fromarray(rgba).save(TEX/f'{name}_s.png')
    Image.fromarray(emission).save(TEX/f'{name}_emission.png')
    texture_names.append(name);texture_arrays[name]=color


guards,guard_mask,frame,frame_mask=make_layers()
add_texture('side_purpur_guards',guards,roughness=.72)
add_texture('side_midframe',frame)
for name,side in [('side_inset_front',False),('side_inset_side',True)]:
    a,e=make_side(side);add_texture(name,a,e)
add_texture('end_stone',endstone,roughness=.9)
add_texture('purpur',purpur,roughness=.72)
add_texture('obsidian',obsidian,roughness=.65)
top=endstone.copy()
# The four endstone rails contain endstone only; purple caps are a separate material.
add_texture('top_frame',top)
pool,emission=energy_top();add_texture('pool',pool,emission,.55)
crystal=np.array([[P[['violet','bright','light','bright'][(x+y)%4]] for x in range(16)] for y in range(16)],dtype=np.uint8)
add_texture('crystal',crystal,np.full((16,16),100,dtype=np.uint8),.5)


parts={}


def quad(part, points, texture, uv=None, group='static'):
    is_face=uv is None
    if uv is None: uv=[(0,0),(0,BODY_HEIGHT),(16,BODY_HEIGHT),(16,0)]
    if is_face or not part.startswith('side_'):
        points=list(reversed(points));uv=list(reversed(uv))
    parts.setdefault(part,{'group':group,'faces':[]})['faces'].append({'vertices':points,'uv':uv,'texture':texture})


def turn(points, side):
    # Exact quarter turns preserve integer rest-pose coordinates.
    for _ in range(side):points=[(16-z,y,x) for x,y,z in points]
    return points


for side in range(4):
    part=f'side_{side}_three_layers'
    front=[(0,BODY_HEIGHT,0),(0,0,0),(16,0,0),(16,BODY_HEIGHT,0)]
    # Fronts at depth 0 / 1 / 2, all with exactly matching 16x16 pixel projections.
    quad(part,turn(front,side),'side_purpur_guards')
    quad(part,turn([(x,y,1) for x,y,z in front],side),'side_midframe')
    # Engraved rune pixels are cut into the base panel, with closed one-voxel returns.
    inset_name='side_inset_front' if side%2==0 else 'side_inset_side'
    albedo=texture_arrays[inset_name]
    depths=np.full((16,16),2,dtype=int)
    purple=(albedo[:,:,0]>65)&(albedo[:,:,2]>albedo[:,:,0])
    depths[purple]=3
    for row in range(BODY_HEIGHT):
        for x in range(2,14):
            if albedo[row,x,3]==0:continue
            y=BODY_HEIGHT-1-row;d=int(depths[row,x])
            pts=[(x,y+1,d),(x,y,d),(x+1,y,d),(x+1,y+1,d)]
            quad(part,turn(list(reversed(pts)),side),inset_name,list(reversed([(x,row),(x,row+1),(x+1,row+1),(x+1,row)])))
            if d!=3:continue
            for dx,dr in [(-1,0),(1,0),(0,-1),(0,1)]:
                if depths[row+dr,x+dx]==3:continue
                if dx==-1:pts=[(x,y,2),(x,y,3),(x,y+1,3),(x,y+1,2)]
                elif dx==1:pts=[(x+1,y+1,2),(x+1,y+1,3),(x+1,y,3),(x+1,y,2)]
                elif dr==-1:pts=[(x,y+1,2),(x,y+1,3),(x+1,y+1,3),(x+1,y+1,2)]
                else:pts=[(x+1,y,2),(x+1,y,3),(x,y,3),(x,y,2)]
                quad(part,turn(list(reversed(pts)),side),'obsidian',list(reversed([(x,row),(x+1,row),(x+1,row+1),(x,row+1)])))
    # Close each mask only across its own one-voxel step; decoration stays in the textures.
    for depth,layer_mask,texture in [(0,guard_mask,'side_purpur_guards'),(1,frame_mask,'side_midframe')]:
        for row in range(BODY_HEIGHT):
            for x in range(16):
                if not layer_mask[row,x]:continue
                y=BODY_HEIGHT-1-row;d=depth;D=depth+1
                boundaries=[((-1,0),[(x,y,d),(x,y,D),(x,y+1,D),(x,y+1,d)]),
                    ((1,0),[(x+1,y+1,d),(x+1,y+1,D),(x+1,y,D),(x+1,y,d)]),
                    ((0,-1),[(x,y+1,d),(x,y+1,D),(x+1,y+1,D),(x+1,y+1,d)]),
                    ((0,1),[(x+1,y,d),(x+1,y,D),(x,y,D),(x,y,d)])]
                for (dx,dr),points in boundaries:
                    xx,rr=x+dx,row+dr
                    if 0<=xx<16 and 0<=rr<BODY_HEIGHT and not layer_mask[rr,xx]:
                        # Adjacent sides already close the inner corner post faces.
                        if depth==1 and ((dx==-1 and x==1) or (dx==1 and x==14)):
                            continue
                        # Give each horizontal corner return to only one adjacent side.
                        if dr and x==depth:
                            continue
                        uv=[(x,row),(x+1,row),(x+1,row+1),(x,row+1)]
                        quad(part,turn(points,side),texture,uv)


# Close the foot guards' inward top/side returns after removing the old tall posts.
for side in range(4):
    patch=[([(1,4,1),(1,4,3),(3,4,3),(3,4,1)],[(1,1),(1,3),(3,3),(3,1)]),
           ([(3,0,1),(3,4,1),(3,4,3),(3,0,3)],[(1,13),(1,9),(3,9),(3,13)]),
           ([(3,0,3),(3,4,3),(1,4,3),(1,0,3)],[(1,13),(1,9),(3,9),(3,13)])]
    for points,uv in patch:quad('side_foot_closure',turn(points,side),'purpur',uv)


def box(part,a,b,texture,group='static'):
    x,y,z=a;X,Y,Z=b
    directions=[([(x,Y,z),(x,y,z),(X,y,z),(X,Y,z)],[(x,16-Y),(x,16-y),(X,16-y),(X,16-Y)]),
        ([(X,Y,Z),(X,y,Z),(x,y,Z),(x,Y,Z)],[(16-X,16-Y),(16-X,16-y),(16-x,16-y),(16-x,16-Y)]),
        ([(x,Y,Z),(x,y,Z),(x,y,z),(x,Y,z)],[(16-Z,16-Y),(16-Z,16-y),(16-z,16-y),(16-z,16-Y)]),
        ([(X,Y,z),(X,y,z),(X,y,Z),(X,Y,Z)],[(z,16-Y),(z,16-y),(Z,16-y),(Z,16-Y)]),
        ([(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],[(x,z),(X,z),(X,Z),(x,Z)]),
        ([(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],[(x,Z),(X,Z),(X,z),(x,z)])]
    for points,uv in directions:
        # The side surfaces already close the deck perimeter.
        if part=='deck' and any(all(p[axis]==edge for p in points) for axis in (0,2) for edge in (0,16)):
            continue
        quad(part,points,texture,uv,group)


# The carved energy bed is two voxels below the tabletop (Y=14 versus Y=16).
box('cavity_floor',(2,12,2),(14,13,14),'obsidian')
box('base',(2,0,2),(14,1,14),'end_stone')

# The tabletop follows the side depths: purple corner caps outside an inset endstone rail.
roof={}
for z in range(16):
    for x in range(16):
        corner=(x<4 or x>=12) and (z<4 or z>=12)
        rail=1<=x<15 and 1<=z<15 and (x in (1,2,13,14) or z in (1,2,13,14))
        if corner or rail:roof[x,z]='purpur' if corner else 'top_frame'
for (x,z),texture in roof.items():
    quad('deck',[(x,16,z),(x+1,16,z),(x+1,16,z+1),(x,16,z+1)],texture,
         [(x,z),(x+1,z),(x+1,z+1),(x,z+1)])
    for dx,dz in [(-1,0),(1,0),(0,-1),(0,1)]:
        if (x+dx,z+dz) in roof:continue
        # Outer faces already belong to the three side layers; only cavity-facing edges remain.
        if (dx==-1 and x in (0,1)) or (dx==1 and x+1 in (15,16)) or (dz==-1 and z in (0,1)) or (dz==1 and z+1 in (15,16)):
            continue
        if dx==-1:pts=[(x,16,z+1),(x,15,z+1),(x,15,z),(x,16,z)]
        elif dx==1:pts=[(x+1,16,z),(x+1,15,z),(x+1,15,z+1),(x+1,16,z+1)]
        elif dz==-1:pts=[(x,16,z),(x,15,z),(x+1,15,z),(x+1,16,z)]
        else:pts=[(x+1,16,z+1),(x+1,15,z+1),(x,15,z+1),(x,16,z+1)]
        # The corner guard's one-voxel returns already cover the outer strip.
        if min(p[0] for p in pts)<1 or max(p[0] for p in pts)>15 or min(p[2] for p in pts)<1 or max(p[2] for p in pts)>15:
            continue
        quad('deck',pts,texture,[(x,z),(x,z+1),(x+1,z+1),(x+1,z)])

# Ring diameter 10 -> 8 voxels: contract the radius by exactly one voxel on each side.
outer=[
    '00111100','01111110','11111111','11111111',
    '11111111','11111111','01111110','00111100']
# The perimeter is a single voxel wide, including every square step.
def inset_mask(outline):
    size=len(outline)
    return [''.join('1' if outline[z][x]=='1' and all(
        0<=x+dx<size and 0<=z+dz<size and outline[z+dz][x+dx]=='1'
        for dx,dz in [(-1,0),(1,0),(0,-1),(0,1)]) else '0' for x in range(size)) for z in range(size)]
inner=inset_mask(outer)

# Emit only exterior faces of the integer-cell volume into one rotor mesh.
cells={}
for z in range(8):
    for x in range(8):
        if outer[z][x]=='0':continue
        # Separate floating annulus above the tabletop. No connecting basin walls.
        # Ring underside Y=17 > tabletop Y=16: externally visible one-voxel air gap.
        if inner[z][x]=='0':cells[x+4,17,z+4]='purpur'
# Keep the carved energy bed's existing footprint; only the floating ring is contracted.
energy_outline=[
    '0001111000','0011111100','0111111110','1111111111','1111111111',
    '1111111111','1111111111','0111111110','0011111100','0001111000']
for z,row in enumerate(inset_mask(energy_outline)):
    for x,filled in enumerate(row):
        if filled=='1':cells[x+3,13,z+3]='pool'
for (x,y,z),texture in cells.items():
    X,Y,Z=x+1,y+1,z+1
    sides=[((0,0,-1),[(x,Y,z),(x,y,z),(X,y,z),(X,Y,z)],[(x,16-Y),(x,16-y),(X,16-y),(X,16-Y)]),
      ((0,0,1),[(X,Y,Z),(X,y,Z),(x,y,Z),(x,Y,Z)],[(16-X,16-Y),(16-X,16-y),(16-x,16-y),(16-x,16-Y)]),
      ((-1,0,0),[(x,Y,Z),(x,y,Z),(x,y,z),(x,Y,z)],[(16-Z,16-Y),(16-Z,16-y),(16-z,16-y),(16-z,16-Y)]),
      ((1,0,0),[(X,Y,z),(X,y,z),(X,y,Z),(X,Y,Z)],[(z,16-Y),(z,16-y),(Z,16-y),(Z,16-Y)]),
      ((0,1,0),[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],[(x,z),(X,z),(X,Z),(x,Z)]),
      ((0,-1,0),[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)],[(x,Z),(X,Z),(X,z),(x,z)])]
    for (dx,dy,dz),points,uv in sides:
        if (x+dx,y+dy,z+dz) not in cells:quad('rotor',points,texture,uv,'rotor')

for x in (1,13):
    for z in (1,13):
        box('corner_crystals',(x,16,z),(x+2,19,z+2),'crystal')

# One-voxel purpur collar around all four sides of each crystal's root.
for x in (0,12):
    for z in (0,12):
        box('crystal_collars',(x,16,z),(x+4,17,z+1),'purpur')
        box('crystal_collars',(x,16,z+3),(x+4,17,z+4),'purpur')
        box('crystal_collars',(x,16,z+1),(x+1,17,z+3),'purpur')
        box('crystal_collars',(x+3,16,z+1),(x+4,17,z+3),'purpur')

# First remove two voxels from the black body. Then remove Y=12..13 from the upper
# endstone rail: roof/ring/crystals move down one more voxel; the lower body and bed stay.
for name,part in parts.items():
    if name.startswith('side_') or name=='base':continue
    for face in part['faces']:
        face['vertices']=[(x,y-HEIGHT_REDUCTION-(1 if y-HEIGHT_REDUCTION>=13 else 0),z)
                          for x,y,z in face['vertices']]

save_json(HERE/'scene.json',{'texel_density':16,'minimum_feature_voxels':1,'side_layer_depths':[0,1,2],
    'body_height':13,'black_body_height_reduction':2,'upper_endstone_height':2,'ring_outer_diameter':8,
    'ring_bottom':14,'tabletop':13,'ring_top':15,'ring_width_voxels':1,'energy_bed_top':12,
    'foot_guard_height':4,'crystal_top':16,'collar_top':14,
    'overall_dimensions':[16,16,16],
    'pivot':[8,14,8],'textures':texture_names,'parts':parts})

# Editable mesh project: the side remains three textured surfaces, not a cuboid mosaic.
elements=[];groups={'static':[],'rotor':[]}
for name,part in parts.items():
    vertices={};faces={}
    for i,face in enumerate(part['faces']):
        ids=[];uv={}
        for j,(pos,coord) in enumerate(zip(face['vertices'],face['uv'])):
            key=f'v{i}_{j}';vertices[key]=pos;ids.append(key);uv[key]=coord
        faces[f'f{i}']={'vertices':ids,'uv':uv,'texture':texture_names.index(face['texture'])}
    element_id=ident(name);groups[part['group']].append(element_id)
    elements.append({'name':name,'uuid':element_id,'type':'mesh','origin':[0,0,0],
        'rotation':[0,0,0],'vertices':vertices,'faces':faces})
data={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':'End conversion table - 16x revision',
 'resolution':{'width':16,'height':16},'elements':elements,
 'outliner':[{'name':g,'uuid':ident(g+'_bone'),'origin':[8,14,8],'children':children} for g,children in groups.items()],
 'textures':[{'name':n+'.png','uuid':ident(n+'_texture'),'id':str(i),'uv_width':16,'uv_height':16,'mode':'bitmap',
    'source':'data:image/png;base64,'+base64.b64encode((TEX/f'{n}.png').read_bytes()).decode()} for i,n in enumerate(texture_names)],
 'animations':[{'name':'idle','uuid':ident('idle'),'loop':'loop','length':12,'snapping':20,
    'animators':{ident('rotor_bone'):{'name':'rotor','type':'bone','keyframes':[
        {'channel':'rotation','time':t,'data_points':[{'x':'0','y':str(t*30),'z':'0'}],
         'uuid':ident('key'+str(t)),'interpolation':'linear'} for t in (0,3,6,9,12)]}}}]}
save_json(HERE/'end_16x_refined.bbmodel',data)

# Enlarged nearest-neighbour swatches for review, never used as source textures.
sheet=Image.new('RGB',(1152,420),(23,25,34));draw=ImageDraw.Draw(sheet)
try:font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
except OSError:font=ImageFont.load_default()
for i,(name,label) in enumerate([('side_purpur_guards','PURPUR / DEPTH 0'),('side_midframe','FRAME / DEPTH 1'),('side_inset_front','BACKBOARD / DEPTH 2'),('side_inset_side','BACKBOARD / SIDE')]):
    a=Image.fromarray(texture_arrays[name]).resize((256,256),Image.Resampling.NEAREST)
    sheet.paste(a,(24+i*284,74),a);draw.text((24+i*284,33),label,font=font,fill=(216,208,223))
draw.text((24,366),'NATIVE 16 x 16 TEXTURES  /  NO MICRO-NOISE, NORMAL RELIEF OR BEVELS',font=font,fill=(175,168,187))
sheet.save(HERE/'texture_sheet.png')
print('End art revision authored: upper endstone rail 3 -> 2 voxels; overall dimensions 16 x 16 x 16.')
