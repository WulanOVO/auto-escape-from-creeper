package yibo.aefc.client.render;

import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.escape.EscapeController;

/**
 * 在逃跑期间于屏幕边缘渲染红色脉冲泛光提示。
 */
public final class EscapeVignetteRenderer {
    private EscapeVignetteRenderer() {
    }

    /** 外层色带厚度（像素） */
    private static final int OUTER_THICKNESS = 18;
    /** 内层色带厚度（像素） */
    private static final int INNER_THICKNESS = 14;
    /** 外层最大 alpha */
    private static final float OUTER_ALPHA_MAX = 0.32f;
    /** 内层最大 alpha */
    private static final float INNER_ALPHA_MAX = 0.18f;
    /** 最小 alpha */
    private static final float ALPHA_MIN = 0.15f;
    /** 闪烁频率（Hz） */
    private static final float BLINK_HZ = 3.0f;
    /** 红色分量 */
    private static final float COLOR_R = 1.0f;
    /** 绿色分量 */
    private static final float COLOR_G = 0.04f;
    /** 蓝色分量 */
    private static final float COLOR_B = 0.02f;

    /**
     * 渲染屏幕边缘红色脉冲泛光（若配置启用且正处于逃离状态）
     *
     * @param graphics     GUI 渲染上下文
     * @param deltaTracker 帧时间追踪器
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!AefcConfig.get().vignetteEnabled) return;

        EscapeController ctrl = EscapeController.getInstance();
        if (!ctrl.isActive() || ctrl.isShielding()) return;

        double time = Util.getMillis() / 1000.0;
        float pulse = (float) ((Math.sin(time * Math.PI * 2.0 * BLINK_HZ) + 1.0) / 2.0);

        float outerAlpha = ALPHA_MIN + pulse * (OUTER_ALPHA_MAX - ALPHA_MIN);
        float innerAlpha = ALPHA_MIN + pulse * (INNER_ALPHA_MAX - ALPHA_MIN);

        int outerColor = ARGB.colorFromFloat(outerAlpha, COLOR_R, COLOR_G, COLOR_B);
        int innerColor = ARGB.colorFromFloat(innerAlpha, COLOR_R, COLOR_G, COLOR_B);

        int w = graphics.guiWidth();
        int h = graphics.guiHeight();

        // 上边
        graphics.fill(0, 0, w, OUTER_THICKNESS, outerColor);
        graphics.fill(0, OUTER_THICKNESS, w, OUTER_THICKNESS + INNER_THICKNESS, innerColor);

        // 下边
        graphics.fill(0, h - OUTER_THICKNESS, w, h, outerColor);
        graphics.fill(0, h - OUTER_THICKNESS - INNER_THICKNESS, w, h - OUTER_THICKNESS, innerColor);

        // 左边
        graphics.fill(0, 0, OUTER_THICKNESS, h, outerColor);
        graphics.fill(OUTER_THICKNESS, 0, OUTER_THICKNESS + INNER_THICKNESS, h, innerColor);

        // 右边
        graphics.fill(w - OUTER_THICKNESS, 0, w, h, outerColor);
        graphics.fill(w - OUTER_THICKNESS - INNER_THICKNESS, 0, w - OUTER_THICKNESS, h, innerColor);
    }
}
