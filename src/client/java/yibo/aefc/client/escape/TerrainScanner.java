package yibo.aefc.client.escape;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 地形扫描器：以玩家为中心扫描 (2R+1)×(2R+1) 网格（R = {@value #SCAN_RADIUS}），
 * 检测每格的可行走性、地板类型、坠落高度和是否为 1 格台阶。
 * 使用 {@link ClientLevel#getBlockState} 读取方块数据。
 *
 * <p>扫描结果会被缓存，仅在玩家移动到新 {@link BlockPos} 或外部调用
 * {@link #forceRescan()} 时才重新扫描。
 *
 * <p>缓存可通过 {@link #getCached(BlockPos)} 查询单个格点，供 EscapeInput 做跳跃预判。
 */
public class TerrainScanner {
    /** 地形扫描半径（格） */
    public static final int SCAN_RADIUS = 3;
    /** 最大安全坠落高度（格），超过此高度的坠落方向会被惩罚 */
    public static final int MAX_SAFE_FALL = 2;
    /** 向下搜索地板的最大深度（格），与 MAX_SAFE_FALL 无关，仅用于判断"无底洞" */
    private static final int MAX_DROP_SEARCH = 5;
    /** 无底洞的占位坠落高度（格），用于评分惩罚 */
    private static final float VOID_DROP_HEIGHT = 10.0F;

    private BlockPos lastPlayerPos;
    private Map<BlockPos, CellInfo> cachedGrid;

    /**
     * 扫描玩家周围地形，返回以玩家脚底方块为中心的 (2R+1)×(2R+1) 网格
     * （R = {@value #SCAN_RADIUS}）。若玩家未移动则复用缓存的扫描结果。
     */
    public Map<BlockPos, CellInfo> scan(ClientLevel level, Vec3 playerPos) {
        BlockPos playerBP = BlockPos.containing(playerPos);

        if (cachedGrid != null && playerBP.equals(lastPlayerPos)) {
            return cachedGrid;
        }

        int scanRadius = SCAN_RADIUS;
        Map<BlockPos, CellInfo> grid = new HashMap<>();
        int playerY = playerBP.getY();

        for (int dx = -scanRadius; dx <= scanRadius; dx++) {
            for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                BlockPos cellBP = playerBP.offset(dx, 0, dz);
                grid.put(cellBP, evaluateCell(level, cellBP, playerY));
            }
        }

        lastPlayerPos = playerBP;
        cachedGrid = grid;
        return grid;
    }

    /** 强制下次 {@link #scan} 重新扫描，用于撞墙等触发重评估场景。 */
    public void forceRescan() {
        cachedGrid = null;
    }

    /**
     * 查询缓存中某个格点的扫描结果。若缓存未初始化或格点不在扫描范围内返回 null。
     * 供 EscapeInput 做跳跃预判时复用最新一次扫描数据，避免重复读方块。
     */
    public CellInfo getCached(BlockPos pos) {
        return cachedGrid != null ? cachedGrid.get(pos) : null;
    }

    // -----------------------------------------------------------------
    // 格点评估
    // -----------------------------------------------------------------

    private CellInfo evaluateCell(ClientLevel level, BlockPos basePos, int playerY) {
        BlockPos bodyPos  = basePos.atY(playerY);
        BlockPos headPos  = basePos.atY(playerY + 1);
        BlockPos floorPos = basePos.atY(playerY - 1);

        BlockState body  = level.getBlockState(bodyPos);
        BlockState head  = level.getBlockState(headPos);
        BlockState floor = level.getBlockState(floorPos);

        boolean bodyClear  = isPassable(level, bodyPos, body);
        boolean headClear  = isPassable(level, headPos, head);
        boolean bodyWater  = body.is(Blocks.WATER);
        boolean isDanger   = isDanger(floor) || isDanger(body) || isDanger(head);

        // 1 格台阶检测：body 有固体方块但头顶净空，且 body 可站立 → 可通过跳跃到达
        // 例如玩家在 (0,Y-1,0)，前方 (0,Y-1,1) 的地板在 Y（而非 Y-1），
        // bodyPos=(0,Y,1) 就是台阶顶部。需要跳一下跨上去。
        // >=2 格高墙 body 和 head 都不 passable → stepDetected=false，walkable=false，直接堵死
        boolean stepDetected = !bodyClear && headClear && !isDanger
                            && isSolidFloor(level, bodyPos, body);

        float dropHeight;
        boolean isStep;
        if (stepDetected) {
            dropHeight = 0;
            isStep = true;
        } else {
            dropHeight = computeDrop(level, floorPos, floor);
            isStep = false;
        }

        int maxSafeFall = MAX_SAFE_FALL;
        boolean hasFloor = isSolidFloor(level, floorPos, floor)
                        || (dropHeight > 0 && dropHeight <= maxSafeFall);
        boolean walkable;
        if (stepDetected) {
            // 台阶顶部即是地板，玩家踩上去后脚在 playerY
            walkable = true;
        } else {
            walkable = bodyClear && headClear && !isDanger
                    && (hasFloor || bodyWater);
        }

        return new CellInfo(basePos, walkable, bodyWater, isDanger, dropHeight, isStep);
    }

    /** 向下搜索，计算掉落高度。若无地板则为大坑（返回 {@link #VOID_DROP_HEIGHT}）。 */
    private float computeDrop(ClientLevel level, BlockPos floorPos, BlockState floor) {
        if (isSolidFloor(level, floorPos, floor)) return 0;

        for (int dy = -1; dy >= -MAX_DROP_SEARCH; dy--) {
            BlockPos below = floorPos.offset(0, dy, 0);
            BlockState belowState = level.getBlockState(below);
            if (isSolidFloor(level, below, belowState) || isDanger(belowState)) {
                return Math.abs(dy);
            }
        }
        return VOID_DROP_HEIGHT;
    }

    // -----------------------------------------------------------------
    // 方块分类
    // -----------------------------------------------------------------

    /** 玩家是否能穿过（空气 / 水 / 无碰撞体积方块）。 */
    private boolean isPassable(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(Blocks.WATER)) return true;
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /** 脚下是否为可站立方块（有碰撞体积 && 非危险）。 */
    private boolean isSolidFloor(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.WATER)) return false;
        if (isDanger(state)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /** 是否是危险方块（岩浆 / 火 / 仙人掌 / 岩浆块 / 甜浆果丛 / 细雪 / 蛛网 / 营火）。 */
    private boolean isDanger(BlockState state) {
        return state.is(Blocks.LAVA)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.COBWEB)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE);
    }

    // -----------------------------------------------------------------
    // 数据记录
    // -----------------------------------------------------------------

    public record CellInfo(
        BlockPos pos,
        boolean walkable,
        boolean isWater,
        boolean isDanger,
        float dropHeight,
        boolean isStep
    ) {}
}
