package yibo.aefc.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

/**
 * 背包工具类：查找物品槽位、判断是否持有。
 */
public class InventoryHelper {

    /** 伪槽位：物品在副手 */
    public static final int SLOT_OFFHAND = 40;

    private InventoryHelper() {}

    /**
     * 在玩家背包中查找指定物品。
     * 查找顺序：副手 → 主手（当前选中槽位） → 快捷栏 0~8。
     *
     * @return 槽位编号：{@value #SLOT_OFFHAND} 副手；0~8 快捷栏；-1 未找到
     */
    public static int findItem(LocalPlayer player, Item item) {
        if (player.getOffhandItem().is(item)) return SLOT_OFFHAND;
        if (player.getMainHandItem().is(item)) return player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    /** 玩家是否持有指定物品（副手、主手或快捷栏）。 */
    public static boolean hasItem(LocalPlayer player, Item item) {
        return findItem(player, item) >= 0;
    }
}
