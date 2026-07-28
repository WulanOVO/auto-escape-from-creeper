package yibo.aefc.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.escape.EscapeController;

public class AefcMod implements ClientModInitializer {
    public static final String MOD_ID = "aefc";
    public static final Logger LOGGER = LoggerFactory.getLogger("AEFC");

    @Override
    public void onInitializeClient() {
        AefcConfig.load();
        EscapeController.getInstance().init();
    }
}