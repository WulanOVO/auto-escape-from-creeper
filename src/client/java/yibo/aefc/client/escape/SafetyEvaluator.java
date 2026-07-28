package yibo.aefc.client.escape;

/**
 * 安全性评分器：对地形扫描结果中的每个格点打分。
 *
 * <p>公式：<br>
 * {@code score = distanceBonus + walkableBonus − dropPenalty − stepPenalty}
 *
 * <p>评分越高 = 越适合作为逃跑目标格点。危险或不可行走的格点返回
 * {@link Float#NEGATIVE_INFINITY}。
 */
public class SafetyEvaluator {

    /** 最大安全坠落高度（格），超过此高度的坠落方向会被惩罚 */
    public static final float MAX_SAFE_FALL = 2.0F;
    private static final float WALKABLE_BONUS  = 80.0F;
    private static final float WATER_BONUS     = 25.0F;
    private static final float DISTANCE_FACTOR = 10.0F;
    private static final float DROP_FACTOR     = 150.0F;
    private static final float STEP_PENALTY    = 3.0F;

    /**
     * 计算格点的安全分。
     *
     * @param cell             格点信息
     * @param distToCreeper    格点中心到苦力怕的距离
     * @return 安全分，越大越好；不可行走时返回 {@link Float#NEGATIVE_INFINITY}
     */
    public float evaluate(TerrainScanner.CellInfo cell, double distToCreeper) {
        if (!cell.walkable() || cell.isDanger()) {
            return Float.NEGATIVE_INFINITY;
        }

        float score = 0.0F;

        // 距离奖励 —— 离苦力怕越远越好
        score += (float) distToCreeper * DISTANCE_FACTOR;

        // 可行走基础分
        score += cell.isWater() ? WATER_BONUS : WALKABLE_BONUS;

        // 坠落惩罚 —— 超过安全坠落高度后每格扣 DROP_FACTOR 分
        float maxSafeFall = MAX_SAFE_FALL;
        if (cell.dropHeight() > maxSafeFall) {
            score -= (cell.dropHeight() - maxSafeFall) * DROP_FACTOR;
        }

        // 台阶微惩罚 —— 单格扣 3 分，密集台阶方向总分自然偏低
        if (cell.isStep()) {
            score -= STEP_PENALTY;
        }

        return score;
    }
}
