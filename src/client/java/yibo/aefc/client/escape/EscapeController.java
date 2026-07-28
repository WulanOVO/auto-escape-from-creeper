package yibo.aefc.client.escape;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.util.InventoryHelper;

/**
 * 管理自动逃离的完整生命周期
 */
public class EscapeController {
    private static final EscapeController INSTANCE = new EscapeController();

    /**
     * 逃跑状态机：
     * <ul>
     *   <li>{@link #IDLE} —— 空闲，未触发逃跑</li>
     *   <li>{@link #ESCAPING} —— 逃跑中，输入被 EscapeInput 接管，相机盯逃跑方向</li>
     *   <li>{@link #RETURNING} —— 苦力怕取消爆炸后回头看向它（autoLookBack 开启时进入）</li>
     *   <li>{@link #SHIELDING} —— 无路可逃或即将爆炸时举盾格挡，原地不动面朝苦力怕</li>
     * </ul>
     */
    private enum State {IDLE, ESCAPING, RETURNING, SHIELDING}

    private State state = State.IDLE;
    private Creeper targetCreeper;
    private ClientInput originalInput;

    /**
     * 举盾前选中的快捷栏槽位（结束后恢复），-1 表示无需恢复
     */
    private int preShieldSlot = -1;
    /**
     * 逃跑已持续的 tick 数（从 ESCAPING 状态进入开始计数）
     */
    private int escapeTicks;
    /**
     * swell 持续减少的 tick 数
     */
    private int swellDecreasingTicks;
    /**
     * 苦力怕死后盾牌额外保持的 tick 数
     */
    private int shieldHoldTicks;

    /**
     * 当前逃跑的世界方向（由 EscapeInput 每 tick 更新，相机以此为目标）
     */
    public Vec2 escapeWorldDir = new Vec2(0, 1);

    /**
     * swell 持续减少多少 tick 后判定为已抵达安全距离（利用苦力怕自身爆炸取消判定）
     */
    private static final int SWELL_DECREASE_TICKS = 2;
    /**
     * 最后 N tick 若仍未逃到安全距离，触发提前举盾。
     *
     * <p>注意：举盾需要5 tick才能生效，需预留时间
     */
    private static final int PANIC_SHIELD_TICKS = 24;
    /**
     * 逃跑超时 tick 数（约 2 秒），防止卡死。
     * 远大于苦力怕 30 tick 引爆周期，仅作为状态机兜底。
     */
    private static final int MAX_ESCAPE_TICKS = 40;

    /**
     * 逃跑时转头速度（度/秒）
     */
    private static final float ESCAPE_TURN_SPEED = 800.0F;
    /**
     * 回头时转头速度（度/秒）
     */
    private static final float RETURN_TURN_SPEED = 900.0F;
    /**
     * 视角接近目标到多少度视为"转完"
     */
    private static final float ROTATION_DONE_THRESHOLD = 1.5F;

    /**
     * 上一帧时间戳（ns）
     */
    private long lastNanoTime;

    private EscapeController() {
    }

    public static EscapeController getInstance() {
        return INSTANCE;
    }

    public boolean isReturning() {
        return state == State.RETURNING;
    }

    public boolean isShielding() {
        return state == State.SHIELDING;
    }

    /**
     * 是否处于活跃状态（逃跑或回头中）。
     * 用于外部判断是否应该屏蔽玩家鼠标输入。
     */
    public boolean isActive() {
        return state != State.IDLE;
    }

    /**
     * 在客户端初始化时注册 tick 和渲染监听器。
     */
    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LevelRenderEvents.START_MAIN.register(this::onStartRender);
    }

    /**
     * 启动逃离。已在逃中且新苦力怕更近时切换目标。
     */
    public void startEscape(Creeper creeper) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (state == State.RETURNING) return;

        if (state == State.ESCAPING && targetCreeper != null && targetCreeper.isAlive()) {
            double currentDist = targetCreeper.distanceToSqr(player);
            double newDist = creeper.distanceToSqr(player);
            if (newDist >= currentDist) return;
        }

        closeScreen(client, player);

        // 方块保护：在逃跑前尝试在苦力怕脚下放置流体（水桶优先）
        BlockProtector.tryProtect(creeper);

        player.sendOverlayMessage(
            Component.translatable("message.aefc.escape.start")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        );

        originalInput = player.input;
        targetCreeper = creeper;
        escapeTicks = 0;
        swellDecreasingTicks = 0;
        player.input = new EscapeInput(player, creeper);
        state = State.ESCAPING;
        lastNanoTime = System.nanoTime();
    }

    /**
     * 进入举盾格挡状态：切到盾牌、下蹲、右键举盾、面向苦力怕。
     */
    public void enterShielding(LocalPlayer player, Creeper creeper) {
        if (state == State.SHIELDING) return;
        // 调用方各自检查对应配置项（shieldBlockWhenTrapped / shieldBlockWhenEscapeFails）

        targetCreeper = creeper;

        // 查找盾牌（副手 / 主手 / 快捷栏）
        int shieldSlot = InventoryHelper.findItem(player, Items.SHIELD);
        if (shieldSlot < 0) return;

        boolean isOffhand = (shieldSlot == InventoryHelper.SLOT_OFFHAND);

        if (!isOffhand && shieldSlot != player.getInventory().getSelectedSlot()) {
            preShieldSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(shieldSlot);
        }

        InteractionHand hand = isOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Minecraft.getInstance().gameMode.useItem(player, hand);
        Minecraft.getInstance().options.keyUse.setDown(true);

        state = State.SHIELDING;
        shieldHoldTicks = 0;
        lastNanoTime = System.nanoTime();
    }

    private void onClientTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            stop();
            return;
        }

        // 苦力怕死了/没了 → 结束
        if (state != State.IDLE) {
            escapeTicks++;

            // 超时保护（40 tick = 2 秒，远大于引爆周期 30 tick）
            if (escapeTicks > MAX_ESCAPE_TICKS) {
                stop();
                return;
            }

            if (targetCreeper == null || !targetCreeper.isAlive()) {
                if (state == State.SHIELDING) {
                    if (shieldHoldTicks <= 0) {
                        // 多举盾 5 tick
                        shieldHoldTicks = 6;
                    }

                    shieldHoldTicks--;
                    if (shieldHoldTicks > 0) return;

                    stop();
                } else {
                    stop();
                }
                return;
            }
        }

        if (state == State.ESCAPING || state == State.SHIELDING) {
            // 当苦力怕 swell 开始减少时，说明苦力怕自身判定玩家已离开爆炸范围
            int swellDir = targetCreeper.getSwellDir();
            if (swellDir < 0) {
                swellDecreasingTicks++;
                if (swellDecreasingTicks >= SWELL_DECREASE_TICKS) {
                    if (state == State.SHIELDING) {
                        releaseShield(); // SHIELDING → RETURNING 前先清理盾牌
                    }
                    if (AefcConfig.get().autoLookBack) {
                        state = State.RETURNING;
                    } else {
                        stop();
                    }
                    return;
                }
            } else {
                swellDecreasingTicks = 0;
            }

            // 即将爆炸时仍未逃到安全距离 → 提前举盾（由 shieldBlockWhenEscapeFails 控制）
            if (
                state == State.ESCAPING &&
                escapeTicks >= PANIC_SHIELD_TICKS &&
                AefcConfig.get().shieldBlockWhenEscapeFails &&
                hasShield(player)
            ) {
                enterShielding(player, targetCreeper);
            }
        }
    }

    private void onStartRender(LevelTerrainRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || state == State.IDLE) return;

        if (targetCreeper == null || !targetCreeper.isAlive()) return;

        long now = System.nanoTime();
        float dt = (now - lastNanoTime) / 1_000_000_000f;
        lastNanoTime = now;
        if (dt <= 0 || dt > 0.1f) return;

        float maxDegrees = (state == State.ESCAPING ? ESCAPE_TURN_SPEED : RETURN_TURN_SPEED) * dt;

        // 计算目标 yaw 和 pitch
        double dx, dz;
        if (state == State.RETURNING || state == State.SHIELDING) {
            dx = targetCreeper.getX() - player.getX();
            dz = targetCreeper.getZ() - player.getZ();
        } else {
            // 逃跑时相机盯实际移动方向（保证 sprint 速度最大化）
            dx = escapeWorldDir.x;
            dz = escapeWorldDir.y;
        }
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        float targetPitch;
        if (state == State.RETURNING || state == State.SHIELDING) {
            double dy = targetCreeper.getEyeY() - player.getEyeY();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            targetPitch = (float) Math.toDegrees(Math.atan2(-dy, hDist));
        } else {
            targetPitch = 0.0F; // 逃跑时平视前方
        }

        // 计算 yaw / pitch 差值
        float yawDiff = Mth.wrapDegrees(targetYaw - player.getYRot());
        float pitchDiff = targetPitch - player.getXRot();

        // 合并角距离（近似球面距离）
        float totalDist = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        if (totalDist < ROTATION_DONE_THRESHOLD) {
            if (state == State.RETURNING) {
                stop();
            }
            return;
        }

        // 按比例分配转速
        float scale = Math.min(maxDegrees, totalDist) / totalDist;
        player.turn(yawDiff * scale / 0.15f, pitchDiff * scale / 0.15f);
    }

    private boolean hasShield(LocalPlayer player) {
        return InventoryHelper.hasItem(player, Items.SHIELD);
    }

    private void closeScreen(Minecraft client, LocalPlayer player) {
        if (player.hasContainerOpen()) {
            player.closeContainer();
        } else if (client.screen != null) {
            client.setScreen(null);
        }
    }

    private void releaseShield() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.stopUsingItem();
            client.options.keyUse.setDown(false);
            if (preShieldSlot >= 0) {
                client.player.getInventory().setSelectedSlot(preShieldSlot);
                preShieldSlot = -1;
            }
        }
    }

    private void stop() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            if (state == State.SHIELDING) {
                releaseShield();
            }
            if (originalInput != null) {
                client.player.input = originalInput;
            }
        }
        state = State.IDLE;
        targetCreeper = null;
        originalInput = null;
    }
}
