package com.example.converttable;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Loaded-chunk-only discovery; inspecting the network never reads inventory contents. */
public final class TableRangeScanner {
    public static final int CONTAINER_RADIUS=4, CONTAINER_LIMIT=8, NETWORK_RADIUS=16, NODE_LIMIT=1024, GRID=33;
    public static final int CONTAINER_UNKNOWN=1, CONTAINER_OVERFLOW=2, NETWORK_UNKNOWN=4, NETWORK_LIMIT=8;
    public record Snapshot(int containers,int nodes,int ground,int flags,int[] pixels,Map<BlockPos,Integer> surfaces) {
        public static Snapshot empty() {return new Snapshot(0,0,0,0,new int[GRID*GRID],Map.of());}
    }
    private record Storage(BlockPos pos,Container container,boolean accessible) {}
    private record Discovery(List<Storage> entries,int flags) {}
    private static Discovery discover(Level level,BlockPos origin) {
        Map<BlockPos,Storage> found=new TreeMap<>(Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        int flags=0;
        for(BlockPos cursor:BlockPos.betweenClosed(origin.offset(-4,-4,-4),origin.offset(4,4,4))) {
            BlockPos pos=cursor.immutable();
            if(pos.equals(origin)||pos.distManhattan(origin)>CONTAINER_RADIUS)continue;
            if(!level.hasChunkAt(pos)){flags|=CONTAINER_UNKNOWN;continue;}
            var be=level.getBlockEntity(pos);
            if(!(be instanceof Container container)||be instanceof ConversionTableBlockEntity)continue;
            boolean accessible=accessible(container);
            BlockPos key=pos; BlockState state=level.getBlockState(pos);
            if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
                BlockPos partner=ChestBlock.getConnectedBlockPos(pos,state);
                if(!level.hasChunkAt(partner)){flags|=CONTAINER_UNKNOWN;continue;}
                BlockState other=level.getBlockState(partner);
                if(other.is(state.getBlock()) && other.getValue(ChestBlock.TYPE)!=ChestType.SINGLE
                    && other.getValue(ChestBlock.TYPE)!=state.getValue(ChestBlock.TYPE)
                    && other.getValue(ChestBlock.FACING)==state.getValue(ChestBlock.FACING)
                    && level.getBlockEntity(partner) instanceof Container second) {
                    accessible &= accessible(second);
                    if(partner.asLong()<pos.asLong()){key=partner;container=new CompoundContainer(second,container);}
                    else container=new CompoundContainer(container,second);
                }
            }
            found.putIfAbsent(key,new Storage(key,container,accessible));
        }
        if(found.size()>CONTAINER_LIMIT)flags|=CONTAINER_OVERFLOW;
        return new Discovery(found.values().stream().limit(CONTAINER_LIMIT).toList(),flags);
    }
    private static boolean accessible(Container container) {
        if(container instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity be && be.isLocked())return false;
        return !(container instanceof net.minecraft.world.RandomizableContainer loot) || loot.getLootTable()==null;
    }
    public static List<Container> containers(Level level,BlockPos origin) {
        return discover(level,origin).entries().stream().filter(Storage::accessible).map(Storage::container).toList();
    }
    public static Snapshot scan(Level level,BlockPos origin,boolean sculk) {
        var storage=discover(level,origin); int flags=storage.flags(); int[] pixels=new int[GRID*GRID];
        if(!sculk)return new Snapshot(storage.entries().size(),0,0,flags,pixels,Map.of());
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
                int x=next.getX()-origin.getX()+NETWORK_RADIUS,y=NETWORK_RADIUS-next.getY()+origin.getY();
                pixels[y*GRID+x]=Math.max(pixels[y*GRID+x],top?2:1);
            }
        }
        return new Snapshot(storage.entries().size(),nodes,surfaces.size(),flags,pixels,Map.copyOf(surfaces));
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
