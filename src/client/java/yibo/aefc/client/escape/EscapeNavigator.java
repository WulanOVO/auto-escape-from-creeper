package yibo.aefc.client.escape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 智能逃跑导航器 —— 整合地形扫描、安全评分、方向选择与动态重评估。
 *
 * <p>方向稳定性策略：
 * <ul>
 *   <li>方向粘性：选定方向后至少保持 {@value #MIN_TICKS_PER_DIRECTION} tick</li>
 *   <li>碰撞黑名单软过期：撞墙排除的方向保留 {@value #BLOCKED_BIN_TTL} tick 后自动释放，
 *       避免一次误判永久放弃整个扇区</li>
 *   <li>角度约束：根据到苦力怕的距离，排除"跑不过"的方向扇区</li>
 *   <li>仅在 {@code horizontalCollision} 时立即重算；平路每 {@value #MIN_TICKS_PER_DIRECTION} tick 重算</li>
 * </ul>
 */
public class EscapeNavigator {

    /** 选定方向后至少保持的 tick 数，避免平路上每 tick 重算 */
    private static final int MIN_TICKS_PER_DIRECTION = 5;
    /** 撞墙排除的方向扇区保留多少 tick 后过期重试 */
    private static final int BLOCKED_BIN_TTL = 6;

    private final TerrainScanner scanner = new TerrainScanner();
    private final DirectionPicker picker = new DirectionPicker();

    private Vec2 currentDirection = new Vec2(0, 1);
    /** bin → 剩余过期 tick。每 tick 自减，到 0 移除。 */
    private final Map<Integer, Integer> blockedBins = new HashMap<>();
    private int lastPickedBin = -1;
    private int ticksSinceLastChange;
    private boolean trapped;

    // -----------------------------------------------------------------
    // 公开接口
    // -----------------------------------------------------------------

    public Vec2 getEscapeDirection(Creeper creeper) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || creeper == null || !creeper.isAlive()) {
            return currentDirection;
        }

        // 软过期黑名单倒计时
        tickBlockedBins();

        boolean stuck = player.horizontalCollision;
        ticksSinceLastChange++;

        if (stuck) {
            onStuck();
            ClientLevel level = client.level;
            if (level != null) {
                recalculate(level, player.position(), creeper.position());
                ticksSinceLastChange = 0;
            }
        } else if (ticksSinceLastChange >= MIN_TICKS_PER_DIRECTION || lastPickedBin < 0) {
            ClientLevel level = client.level;
            if (level != null) {
                recalculate(level, player.position(), creeper.position());
                ticksSinceLastChange = 0;
            }
        }

        return currentDirection;
    }

    /** 所有逃跑方向均被排除（碰撞 + 角度约束覆盖全部扇区）。 */
    public boolean isTrapped() { return trapped; }

    /**
     * 查询最新一次扫描中某个格点的信息。供 EscapeInput 做跳跃预判时复用，
     * 避免重复读取方块状态。
     */
    public TerrainScanner.CellInfo getCachedCell(BlockPos pos) {
        return scanner.getCached(pos);
    }

    private void tickBlockedBins() {
        if (blockedBins.isEmpty()) return;
        var it = blockedBins.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                e.setValue(remaining);
            }
        }
    }

    private void onStuck() {
        if (lastPickedBin >= 0) {
            int n = DirectionPicker.numDirections();
            blockBin(lastPickedBin);
            blockBin(normalizeBin(lastPickedBin - 1, n));
            blockBin(normalizeBin(lastPickedBin + 1, n));
        }
        scanner.forceRescan();
    }

    /** 把扇区加入黑名单，已存在则取较大 TTL（避免被后到的较短 TTL 覆盖）。 */
    private void blockBin(int bin) {
        blockedBins.merge(bin, BLOCKED_BIN_TTL, Math::max);
    }

    /**
     * 重评估当前方向。
     * <p>根据当前位置和苦力怕位置，重新扫描地形并选择最佳方向。
     */
    private void recalculate(ClientLevel level, Vec3 playerPos, Vec3 creeperPos) {
        Map<BlockPos, TerrainScanner.CellInfo> grid = scanner.scan(level, playerPos);
        BlockPos centerBP = BlockPos.containing(playerPos);

        // 碰撞黑名单 + 角度约束 = 本轮实际排除的扇区
        Set<Integer> excluded = getCombinedExcluded(playerPos, creeperPos);

        Vec2 bestDir = picker.pickBest(grid, centerBP, creeperPos, excluded);

        if (bestDir == null) {
            // 所有扇区都被排除的紧急 fallback：直接远离苦力怕
            trapped = true;
            double dx = centerBP.getX() + 0.5 - creeperPos.x;
            double dz = centerBP.getZ() + 0.5 - creeperPos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.001) {
                currentDirection = new Vec2((float) (dx / len), (float) (dz / len));
            } else {
                currentDirection = new Vec2(0, 1);
            }
        } else {
            trapped = false;
            currentDirection = bestDir;
        }
        lastPickedBin = directionToBin(currentDirection);
    }

    /**
     * 合并碰撞黑名单和角度约束。
     *
     * <p>角度约束：根据初始距离 D 计算最大安全偏离角（见 {@link #maxSafeBins}），
     * 排除任何偏离"正反方向"超过该角度的扇区，防止 Navigator 选择
     * "从苦力怕身边绕过"这种数学上跑不过的方向。
     */
    private Set<Integer> getCombinedExcluded(Vec3 playerPos, Vec3 creeperPos) {
        Set<Integer> excluded = new HashSet<>(blockedBins.keySet());

        double dx = playerPos.x - creeperPos.x;
        double dz = playerPos.z - creeperPos.z;
        int awayBin = directionToBin(new Vec2((float) dx, (float) dz));
        double dist = Math.sqrt(dx * dx + dz * dz);

        int maxBins = maxSafeBins(dist);
        int n = DirectionPicker.numDirections();
        for (int b = 0; b < n; b++) {
            if (binDistance(b, awayBin, n) > maxBins) {
                excluded.add(b);
            }
        }

        return excluded;
    }

    /**
     * 根据到苦力怕的距离，返回"正反方向"两侧各允许保留几个扇区。
     */
    private int maxSafeBins(double dist) {
        double maxSafeAngle;
        if (dist <= 1.5)       maxSafeAngle = 87;
        else if (dist <= 2.5)  maxSafeAngle = 94;
        else if (dist <= 3.5)  maxSafeAngle = 99;
        else if (dist <= 4.5)  maxSafeAngle = 103;
        else if (dist <= 5.5)  maxSafeAngle = 107;
        else if (dist <= 6.5)  maxSafeAngle = 111;
        else                   maxSafeAngle = 115;

        double binAngleDeg = 360.0 / DirectionPicker.numDirections();
        return (int) (maxSafeAngle / binAngleDeg);
    }

    private int directionToBin(Vec2 dir) {
        int n = DirectionPicker.numDirections();
        double angle = Math.atan2(dir.y, dir.x);
        if (angle < 0) angle += 2.0 * Math.PI;
        int bin = (int) (angle / (2.0 * Math.PI / n));
        return normalizeBin(bin, n);
    }

    /** 环形距离（最短路径跨越的扇区数）。 */
    private static int binDistance(int a, int b, int n) {
        int diff = Math.abs(a - b);
        return Math.min(diff, n - diff);
    }

    private static int normalizeBin(int bin, int n) {
        return ((bin % n) + n) % n;
    }
}
