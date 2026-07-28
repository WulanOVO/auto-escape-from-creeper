package yibo.aefc.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置，使用 Gson 持久化到 config/aefc.json。
 */
public class AefcConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AEFC");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("aefc.json");

    private static AefcConfig INSTANCE = new AefcConfig();

    /** 是否在打开 UI 界面时检测苦力怕 */
    public boolean detectWhenScreenOpen = true;
    /** 是否检测背后的苦力怕 */
    public boolean detectBehindPlayer = true;
    /** 撤出安全距离后，是否自动回头 */
    public boolean autoLookBack = true;
    /** 无路可逃时（所有逃跑方向均被阻挡），若有盾牌则自动面向苦力怕下蹲举盾格挡 */
    public boolean shieldBlockWhenTrapped = true;
    /** 预计逃跑失败时举盾（有逃跑方向但时间不足，即将爆炸）*/
    public boolean shieldBlockWhenEscapeFails = true;

    /** 是否启用在苦力怕脚下放置流体以保护周围方块 */
    public boolean blockProtectionEnabled = false;
    /** 需要保护的关键方块 ID 列表（如 minecraft:chest），爆炸波及范围内包含其中任一方块时触发流体放置 */
    public List<String> protectedBlocks = new ArrayList<>();
    /** 允许使用岩浆桶保护方块（无水桶时才会用到，注意岩浆会销毁掉落物） */
    public boolean allowLavaProtection = true;

    public static AefcConfig get() {
        return INSTANCE;
    }

    /** 从磁盘加载配置，不存在则创建默认并保存 */
    public static void load() {
        if (Files.exists(PATH)) {
            try {
                String json = Files.readString(PATH);
                INSTANCE = GSON.fromJson(json, AefcConfig.class);
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Failed to load AEFC config, using defaults", e);
                INSTANCE = new AefcConfig();
                INSTANCE.save();
            }
        } else {
            INSTANCE.save();
        }
    }

    /** 将当前配置写入磁盘 */
    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("Failed to save AEFC config", e);
        }
    }
}
