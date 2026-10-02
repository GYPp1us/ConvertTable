package com.example.converttable;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded-chunk-only discovery; inspecting the network never reads inventory contents. */
public final class TableRangeScanner {
    public static final int NETWORK_RADIUS=16, NODE_LIMIT=1024, GRID=33;
    public static final int NETWORK_UNKNOWN=4, NETWORK_LIMIT=8;
    public record Snapshot(int containers,int nodes,int ground,int flags,int[] pixels,Map<BlockPos,Integer> surfaces) {
        public static Snapshot empty() {return new Snapshot(0,0,0,0,new int[GRID*GRID],Map.of());}
    }
    public static Snapshot scan(Level level,BlockPos origin,boolean sculk) {
        int flags=0; int[] pixels=new int[GRID*GRID];
        if(!sculk)return Snapshot.empty();
        Map<BlockPos,Integer> distances=new HashMap<>(),surfaces=new HashMap<>();
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(origin);distances.put(origin,0);
        int nodes=0;
        while(!queue.isEmpty()) {
            BlockPos pos=queue.removeFirst();BlockState from=level.getBlockState(pos);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
                int span=Math.abs(dx)+Math.abs(dy)+Math.abs(dz);
                if(span==0||span==3)continue; // Surfaces sharing only a corner do not connect.
                BlockPos next=pos.offset(dx,dy,dz);
                if(distances.containsKey(next))continue;
                if(!level.hasChunkAt(next)){flags|=NETWORK_UNKNOWN;continue;}
                BlockState state=level.getBlockState(next);
                if(!isNode(state)||!touches(from,pos,state,next))continue;
                if(next.distManhattan(origin)>NETWORK_RADIUS||nodes>=NODE_LIMIT){flags|=NETWORK_LIMIT;continue;}
                int distance=distances.get(pos)+1;distances.put(next,distance);queue.addLast(next);nodes++;
                boolean top=state.is(Blocks.SCULK);
                if(top) {
                    BlockPos above=next.above();
                    if(!level.hasChunkAt(above)){top=false;flags|=NETWORK_UNKNOWN;}
                    else top=level.getBlockState(above).getCollisionShape(level,above).isEmpty();
                }
                if(top)surfaces.put(next,distance);
                // Top-down coverage: east is right, north is up; collapse height, not depth.
                int x=next.getX()-origin.getX()+NETWORK_RADIUS,z=next.getZ()-origin.getZ()+NETWORK_RADIUS;
                pixels[z*GRID+x]=Math.max(pixels[z*GRID+x],top?2:1);
            }
        }
        return new Snapshot(0,nodes,surfaces.size(),flags,pixels,Map.copyOf(surfaces));
    }
    public static boolean isNode(BlockState state) {
        return state.is(Blocks.SCULK)||state.is(Blocks.SCULK_VEIN)||state.is(Blocks.SCULK_CATALYST)
            ||state.is(Blocks.SCULK_SENSOR)||state.is(Blocks.CALIBRATED_SCULK_SENSOR)||state.is(Blocks.SCULK_SHRIEKER);
    }
    public static boolean connects(BlockState a,BlockState b,Direction direction) {
        return touches(a,BlockPos.ZERO,b,BlockPos.ZERO.relative(direction));
    }
    /** Veins are actual face squares. Share an edge/area, never jump through a missing face or a corner. */
    private static boolean touches(BlockState a,BlockPos pa,BlockState b,BlockPos pb) {
        boolean av=a.is(Blocks.SCULK_VEIN),bv=b.is(Blocks.SCULK_VEIN);
        if(!av&&!bv)return pa.distManhattan(pb)==1;
        for(Direction fa:Direction.values()) {
            if(av&&!MultifaceBlock.hasFace(a,fa))continue;
            for(Direction fb:Direction.values()) {
                if(bv&&!MultifaceBlock.hasFace(b,fb))continue;
                int positive=0;boolean intersects=true;
                for(Direction.Axis axis:Direction.Axis.values()) {
                    int ac=axis.choose(pa.getX(),pa.getY(),pa.getZ()),bc=axis.choose(pb.getX(),pb.getY(),pb.getZ());
                    int alo=ac,ahi=ac+1,blo=bc,bhi=bc+1;
                    if(fa.getAxis()==axis)alo=ahi=ac+(fa.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0);
                    if(fb.getAxis()==axis)blo=bhi=bc+(fb.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0);
                    int overlap=Math.min(ahi,bhi)-Math.max(alo,blo);
                    if(overlap<0){intersects=false;break;}
                    if(overlap>0)positive++;
                }
                if(intersects&&positive>=1)return true;
            }
        }
        return false;
    }
}
