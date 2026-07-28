package yibo.aefc.client.escape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import yibo.aefc.client.config.AefcConfig;
import yibo.aefc.client.util.InventoryHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 方块保护器：在苦力怕点燃后、开始逃跑前，检测爆炸波及范围内是否存在
 * 受保护方块。若包含且物品栏中有水桶（优先），则在苦力怕脚底放置流体
 * 以抵消爆炸破坏。
 *
 * <p>苦力怕已在水里则跳过；岩浆桶需配置开启才使用。
 * <p>放置流程：瞄准目标方块 → 设置玩家朝向 → useItem 发送（包内含朝向，
 * 服务端据此做射线检测，不受交互距离限制）。
 */
public class BlockProtector {

    /** 爆炸波及范围扫描半径（格），覆盖普通苦力怕（~4 格）和高压苦力怕（~6 格） */
    private static final int EXPLOSION_SCAN_RADIUS = 6;

    private BlockProtector() {}

    /**
     * 尝试在苦力怕脚下放置流体以保护周围方块。
     */
    public static void tryProtect(Creeper creeper) {
        if (!AefcConfig.get().blockProtectionEnabled) return;
        if (creeper == null || !creeper.isAlive()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) return;

        var protectedBlocks = AefcConfig.get().protectedBlocks;
        if (protectedBlocks == null || protectedBlocks.isEmpty()) return;

        // 1. 苦力怕已在水里 → 爆炸已被削弱，无需再放
        BlockPos creeperFeet = BlockPos.containing(creeper.position());
        if (level.getBlockState(creeperFeet).is(Blocks.WATER)) return;

        // 2. 检测爆炸范围内是否有受保护方块
        if (!hasProtectedBlockNearby(level, creeper, protectedBlocks)) return;

        // 3. 查找水桶（优先）或岩浆桶（需配置开启）
        int waterSlot = InventoryHelper.findItem(player, Items.WATER_BUCKET);
        int lavaSlot = AefcConfig.get().allowLavaProtection
            ? InventoryHelper.findItem(player, Items.LAVA_BUCKET) : -1;
        int bucketSlot = waterSlot >= 0 ? waterSlot : lavaSlot;
        if (bucketSlot < 0) return;

        // 4. 在苦力怕脚底找可放置位置
        BlockPos targetPos = findPlaceablePosition(level, creeperFeet);
        if (targetPos == null) return;

        // 5. 切到桶 → 瞄准 → useItem 放置 → 恢复
        int prevSlot = player.getInventory().getSelectedSlot();
        boolean isOffhand = (bucketSlot == InventoryHelper.SLOT_OFFHAND);

        try {
            if (!isOffhand) {
                player.getInventory().setSelectedSlot(bucketSlot);
            }

            InteractionHand hand = isOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

            Vec3 targetPoint = new Vec3(
                targetPos.getX() + 0.5,
                targetPos.below().getY() + 1.0,
                targetPos.getZ() + 0.5
            );
            lookAt(player, targetPoint);

            client.gameMode.useItem(player, hand);
            client.options.keyUse.setDown(false);
        } finally {
            if (!isOffhand && bucketSlot != prevSlot) {
                player.getInventory().setSelectedSlot(prevSlot);
            }
        }
    }

    private static void lookAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(
            Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz))
        );

        yaw = Mth.wrapDegrees(yaw);
        if (yaw < 0) yaw += 360;

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    private static BlockPos findPlaceablePosition(ClientLevel level, BlockPos creeperFeet) {
        if (isReplaceable(level.getBlockState(creeperFeet))) return creeperFeet;
        BlockPos above = creeperFeet.above();
        if (isReplaceable(level.getBlockState(above))) return above;
        return null;
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /** 扫描爆炸半径球体内是否存在保护列表中的方块（支持直接 ID 和 #tag）。 */
    private static boolean hasProtectedBlockNearby(
        ClientLevel level, Creeper creeper, List<String> entries
    ) {
        // 预解析：分开直接方块 ID 和标签
        Set<String> directIds = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();

        for (String entry : entries) {
            if (entry.startsWith("#")) {
                try {
                    tags.add(TagKey.create(Registries.BLOCK,
                        Identifier.parse(entry.substring(1))));
                } catch (Exception ignored) {
                    // 无效标签格式，跳过
                }
            } else {
                directIds.add(entry);
            }
        }

        BlockPos center = BlockPos.containing(creeper.position());
        int r = EXPLOSION_SCAN_RADIUS;
        int r2 = r * r;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                int maxDzSq = r2 - dx * dx - dy * dy;
                if (maxDzSq < 0) continue;
                int maxDz = (int) Math.sqrt(maxDzSq);

                for (int dz = -maxDz; dz <= maxDz; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);

                    if (directIds.contains(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                    )) {
                        return true;
                    }

                    for (var tag : tags) {
                        if (state.is(tag)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
