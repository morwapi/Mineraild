package com.minerailed.item;

import com.minerailed.entity.LocomotiveEntity;
import com.minerailed.registry.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 列車召喚アイテム
 * 右クリックで機関車エンティティを召喚する
 */
public class TrainSpawnerItem extends Item {

    public TrainSpawnerItem(Settings settings) {
        super(settings);
    }

    /**
     * ブロックに対して使用したとき（右クリック）
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();

        com.minerailed.MinerailedMod.LOGGER.info("TrainSpawner使用: isClient=" + world.isClient);

        // クライアント側では何もしない（サーバー側のみ処理）
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        BlockPos pos = context.getBlockPos().up(); // ブロックの上に召喚
        PlayerEntity player = context.getPlayer();

        // Null安全性チェック
        if (player == null) {
            com.minerailed.MinerailedMod.LOGGER.warn("プレイヤーがnullです");
            return ActionResult.FAIL;
        }

        try {
            com.minerailed.MinerailedMod.LOGGER.info("機関車を作成中... 位置: " + pos);

            // 機関車エンティティを作成
            LocomotiveEntity locomotive = new LocomotiveEntity(ModEntities.LOCOMOTIVE, world);
            locomotive.refreshPositionAndAngles(
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5,
                    player.getYaw(),
                    0.0f);

            com.minerailed.MinerailedMod.LOGGER.info("エンティティをスポーン中...");

            // ワールドに追加
            boolean spawned = world.spawnEntity(locomotive);

            com.minerailed.MinerailedMod.LOGGER.info("スポーン結果: " + spawned);

            // クリエイティブモードでない場合はアイテムを消費
            if (!player.getAbilities().creativeMode) {
                context.getStack().decrement(1);
            }

            return spawned ? ActionResult.SUCCESS : ActionResult.FAIL;
        } catch (Exception e) {
            com.minerailed.MinerailedMod.LOGGER.error("機関車召喚エラー", e);
            return ActionResult.FAIL;
        }
    }

    /**
     * 空中で使用したとき
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // クライアント側では何もしない
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        // プレイヤーの足元に召喚
        BlockPos pos = player.getBlockPos();

        LocomotiveEntity locomotive = new LocomotiveEntity(ModEntities.LOCOMOTIVE, world);
        locomotive.refreshPositionAndAngles(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5,
                player.getYaw(),
                0.0f);

        world.spawnEntity(locomotive);

        // クリエイティブモードでない場合はアイテムを消費
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack);
    }
}
