package yibo.aefc.client.escape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.util.InventoryHelper;

/**
 * 自动逃离输入源：每 tick 调用 {@link EscapeNavigator} 获取智能逃跑方向，
 * 将世界方向转换为玩家局部移动向量并注入 {@link ClientInput}
 *
 * <p>跳跃决策：
 * <ul>
 *   <li>首帧起跳加速</li>
 *   <li>检查前方 1~3 格的 {@code isStep}：台阶 → 主动预判跳上；
 *       ≥2 格高墙 → walkable=false 直接堵死，不会走到这</li>
 *   <li>查头顶 {@code above(2)} 方块：若有方块挡住头顶但 {@code above(1)} 净空 → 顶头跳</li>
 *   <li>平路且无顶头机会 → 不跳</li>
 * </ul>
 *
 * <p>当所有逃跑方向均被排除时，若配置启用且携带盾牌，自动触发面朝苦力怕下蹲举盾的格挡
 */
public class EscapeInput extends ClientInput {
    private final LocalPlayer player;
    private final Creeper targetCreeper;
    private final EscapeNavigator navigator = new EscapeNavigator();

    private boolean firstTick = true;

    public EscapeInput(LocalPlayer player, Creeper targetCreeper) {
        this.player = player;
        this.targetCreeper = targetCreeper;
    }

    @Override
    public void tick() {
        if (EscapeController.getInstance().isReturning()) {
            this.moveVector = Vec2.ZERO;
            this.keyPresses = Input.EMPTY;
            return;
        }

        if (EscapeController.getInstance().isShielding()) {
            // 格挡：原地不动，下蹲
            this.moveVector = Vec2.ZERO;
            this.keyPresses = new Input(false, false, false, false, false, true, false);
            return;
        }

        // 从导航器获取最优世界方向（这一步会触发地形扫描，刷新 scanner 缓存）
        Vec2 worldDir = navigator.getEscapeDirection(targetCreeper);

        // 回传给 EscapeController，用于相机朝向
        EscapeController.getInstance().escapeWorldDir = worldDir;

        // 全排除 + 有盾 → 进入举盾格挡
        if (navigator.isTrapped()
            && AefcConfig.get().shieldBlockWhenTrapped
            && hasShield()) {
            EscapeController.getInstance().enterShielding(player, targetCreeper);
            this.moveVector = Vec2.ZERO;
            this.keyPresses = new Input(false, false, false, false, false, true, false);
            return;
        }

        // 世界方向 → 玩家局部坐标系
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        float cos = Mth.cos(yawRad);
        float sin = Mth.sin(yawRad);
        float localX = cos * worldDir.x + sin * worldDir.y;
        float localZ = -sin * worldDir.x + cos * worldDir.y;
        this.moveVector = new Vec2(localX, localZ).normalized();

        // 预判式跳跃决策
        boolean shouldJump = decideJump(worldDir);

        this.keyPresses = new Input(
            true,
            false,
            false,
            false,
            shouldJump,
            false,
            true
        );
    }

    /**
     * 预判式跳跃决策
     */
    private boolean decideJump(Vec2 worldDir) {
        if (!player.onGround()) return false;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;

        BlockPos playerBP = BlockPos.containing(player.position());
        int fx = Math.round(worldDir.x);
        int fz = Math.round(worldDir.y);

        // 首帧起跳
        if (firstTick) {
            firstTick = false;
            return true;
        }

        // 常规探测：沿移动方向逐格扫描台阶和高墙
        if (fx != 0 || fz != 0) {
            Boolean forwardResult = probeForward(playerBP, fx, fz);
            if (forwardResult != null) return forwardResult;
        }

        // 顶头跳：above(2) 实心 && above(1) 净空 → 跳起撞头顶住，水平速度保留
        boolean headClear = isPassable(level, playerBP.above(1));
        boolean bonkBlocked = !isPassable(level, playerBP.above(2));
        if (bonkBlocked && headClear) return true;

        // 兜底：horizontalCollision 时仍尝试跳（预判漏网或被推挤）
        return player.horizontalCollision;
    }

    /**
     * 沿移动方向逐格探测 1–3 格，返回跳跃决策。
     *
     * <ul>
     *   <li>遇到台阶（isStep==true）→ 返回 true（预判跳，不等到跟前）</li>
     *   <li>遇到不可行走格 → 中止探测，返回 null（路径阻断或高墙堵死）</li>
     *   <li>3 格内全是平路 → 返回 null（交由调用者按场景决定）</li>
     * </ul>
     */
    private Boolean probeForward(BlockPos playerBP, int fx, int fz) {
        for (int dist = 1; dist <= 3; dist++) {
            BlockPos frontBP = playerBP.offset(fx * dist, 0, fz * dist);
            TerrainScanner.CellInfo frontCell = navigator.getCachedCell(frontBP);
            if (frontCell == null) break; // 超出扫描范围
            if (frontCell.isStep()) return true;
            if (!frontCell.walkable()) break; // 路径不通（高墙/深渊等→walkable=false）
        }
        return null; // 3 格内全是平路
    }

    /**
     * 玩家是否能穿过（空气 / 水 / 无碰撞体积方块）。与 {@link TerrainScanner} 中判定一致。
     */
    private boolean isPassable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (state.is(Blocks.WATER)) return true;
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean hasShield() {
        return InventoryHelper.hasItem(player, Items.SHIELD);
    }
}
