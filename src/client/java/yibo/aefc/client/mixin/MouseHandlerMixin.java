package yibo.aefc.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yibo.aefc.client.escape.EscapeController;

/**
 * 在自动逃跑期间屏蔽玩家的鼠标转动输入，
 * 防止玩家与 mod 抢控制权导致视角抽动。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(double mousea, CallbackInfo ci) {
        if (EscapeController.getInstance().isActive()) {
            ci.cancel();
        }
    }
}
