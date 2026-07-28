package yibo.aefc.client.escape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;

/**
 * 方向选择器：将安全分热力图按方向聚合，选出最优逃跑方向。
 *
 * <p>将扫描网格中的所有可行走格点分配到 {@link #numDirections()} 个方向扇区中，
 * 按距离加权积分后选出总分最高的扇区，输出归一化世界方向向量。
 *
 * <p>调用方可传入 {@code excluded} 集合（碰撞黑名单 + 角度约束），落在排除集合中
 * 的扇区不参与评分也不参与选优。若所有扇区都被排除，返回 {@code null}，由
 * {@link EscapeNavigator} 决定 fallback（通常直接远离苦力怕）。
 *
 * <p>方向分辨率 {@value #DIRECTION_RESOLUTION} 扇区（22.5°/扇区）。
 */
public class DirectionPicker {

    /** 方向采样分辨率（扇区数） */
    public static final int DIRECTION_RESOLUTION = 16;

    private final SafetyEvaluator evaluator = new SafetyEvaluator();

    /** 当前方向分辨率。 */
    public static int numDirections() {
        return DIRECTION_RESOLUTION;
    }

    /**
     * 从扫描网格和苦力怕位置中选出最优逃跑方向。
     *
     * @param grid       地形扫描网格
     * @param centerBP   玩家所在方块坐标（网格中心）
     * @param creeperPos 苦力怕位置
     * @param excluded   需排除的扇区编号集合（可为空）
     * @return 归一化世界方向向量 {@code (dx, dz)}；若所有扇区都被排除返回 {@code null}
     */
    public Vec2 pickBest(Map<BlockPos, TerrainScanner.CellInfo> grid,
                          BlockPos centerBP, Vec3 creeperPos,
                          Set<Integer> excluded) {
        int n = numDirections();
        float[] dirScores = new float[n];
        double binAngle = 2.0 * Math.PI / n;

        for (var entry : grid.entrySet()) {
            BlockPos bp = entry.getKey();
            TerrainScanner.CellInfo cell = entry.getValue();
            if (!cell.walkable()) continue;

            int gx = bp.getX() - centerBP.getX();
            int gz = bp.getZ() - centerBP.getZ();
            if (gx == 0 && gz == 0) continue;

            // 玩家到该格点的 Chebyshev 距离（曼哈顿网格中的"步数"），近格权重更高
            double chebDist = Math.max(Math.abs(gx), Math.abs(gz));
            double weight = 1.0 / chebDist;

            // 该格点中心到苦力怕的距离
            double distToCreeper = Math.sqrt(
                (bp.getX() + 0.5 - creeperPos.x) * (bp.getX() + 0.5 - creeperPos.x)
              + (bp.getZ() + 0.5 - creeperPos.z) * (bp.getZ() + 0.5 - creeperPos.z)
            );

            float cellScore = evaluator.evaluate(cell, distToCreeper);

            // 将格点分配到方向扇区
            double angle = Math.atan2(gz, gx);
            if (angle < 0) angle += 2.0 * Math.PI;
            int bin = (int) (angle / binAngle);
            if (bin >= n) bin = 0;

            if (excluded != null && excluded.contains(bin)) continue;
            dirScores[bin] += (float) (cellScore * weight);
        }

        // 找出未被排除的最高分扇区
        int best = -1;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (excluded != null && excluded.contains(i)) continue;
            if (dirScores[i] > bestScore) {
                bestScore = dirScores[i];
                best = i;
            }
        }

        // 全部排除 → 交由 Navigator fallback
        if (best < 0) return null;

        double bestAngle = best * binAngle + binAngle / 2.0;
        return new Vec2((float) Math.cos(bestAngle), (float) Math.sin(bestAngle));
    }
}
