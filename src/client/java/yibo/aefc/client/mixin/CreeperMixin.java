package yibo.aefc.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.escape.EscapeController;

/**
 * 在苦力怕刚点燃（swell 0→1）时检测触发条件。
 * <p>
 * 通过追踪上一帧 swell 值，仅当 swell 从 0 跳到 1（真正点燃）时触发，
 * 排除 swell 从 2 降到 1（取消爆炸过程）的错误触发。
 * <p>
 * 触发条件（全部满足时触发）：
 * <ul>
 *   <li>苦力怕在玩家 7 格内</li>
 *   <li>玩家当前打开了 UI 界面 或苦力怕在玩家视野范围外</li>
 *   <li>玩家处于生存/冒险模式（非创造/旁观）</li>
 *   <li>玩家不在游泳或鞘翅飞行状态</li>
 * </ul>
 */
@Mixin(Creeper.class)
public class CreeperMixin {
    @Shadow
    private int swell;

    /** 上一帧的 swell 值，用于区分"点燃"(0→1)和"取消爆炸"(2→1) */
    @Unique
    private int prevSwell = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        Creeper self = (Creeper) (Object) this;
        if (!self.level().isClientSide()) return;

        // 仅 swell 从 0 跳到 1（刚点燃）时触发，排除从 2 降到 1（取消爆炸）的情况
        if (prevSwell != 0 || this.swell != 1) {
            prevSwell = this.swell;
            return;
        }
        prevSwell = this.swell;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Player player = client.player;

        // 仅生存/冒险模式触发；游泳/鞘翅时不触发
        if (player.isCreative() || player.isSpectator()) return;
        if (player.isSwimming() || player.isFallFlying()) return;

        double distSqr = self.distanceToSqr(player);
        if (distSqr > 49.0) return;

        AefcConfig cfg = AefcConfig.get();
        boolean screenOpen = client.screen != null;

        boolean triggeredByScreen = screenOpen && cfg.detectWhenScreenOpen;
        boolean triggeredByBehind = false;

        if (!screenOpen && cfg.detectBehindPlayer) {
            Vec3 toCreeper = self.position().subtract(player.position()).normalize();
            Vec3 lookDir = player.getLookAngle();
            triggeredByBehind = lookDir.dot(toCreeper) < 0;
        }

        if (!triggeredByScreen && !triggeredByBehind) return;

        // 逃跑！
        EscapeController.getInstance().startEscape(self);
    }
}
