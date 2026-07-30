package yibo.aefc.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.escape.EscapeController;
import yibo.aefc.client.render.EscapeVignetteRenderer;

public class AefcMod implements ClientModInitializer {
    public static final String MOD_ID = "aefc";
    public static final Logger LOGGER = LoggerFactory.getLogger("AEFC");

    @Override
    public void onInitializeClient() {
        AefcConfig.load();
        EscapeController.getInstance().init();

        // 注册屏幕边缘红色泛光 HUD 元素（渲染在聊天层之后，即大多数 HUD 之上）
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(MOD_ID, "escape_vignette"),
            EscapeVignetteRenderer::render
        );
    }
}
