package com.minerailed.handler;

import com.minerailed.entity.LocomotiveEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * 車両連結のためのイベントハンドラー
 * プレイヤーがトロッコをシフト+右クリックしたときに、近くの機関車と連結/解除を行う
 */
public class CouplingHandler {

    /** 機関車を探す範囲（ブロック） */
    private static final double SEARCH_RANGE = 5.0;

    /**
     * イベントハンドラーを登録
     */
    public static void register() {
        UseEntityCallback.EVENT.register(CouplingHandler::onUseEntity);
    }

    /**
     * エンティティを右クリックしたときの処理
     */
    private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity,
            EntityHitResult hitResult) {

        // メインハンドのみで処理（二重実行防止）
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        // トロッコ以外は無視
        if (!(entity instanceof AbstractMinecartEntity)) {
            return ActionResult.PASS;
        }

        // シフト+右クリックでない場合は通常の動作（乗る）
        if (!player.isSneaking()) {
            return ActionResult.PASS;
        }

        AbstractMinecartEntity minecart = (AbstractMinecartEntity) entity;
        com.minerailed.MinerailedMod.LOGGER
                .info("CouplingHandler: トロッコ(UUID=" + minecart.getUuid() + ")に対してシフト右クリック検知");

        // 近くの機関車を探す
        LocomotiveEntity locomotive = findNearbyLocomotive(world, minecart);

        if (locomotive == null) {
            // 機関車が見つからない
            // com.minerailed.MinerailedMod.LOGGER.info("CouplingHandler: 近くに機関車が見つかりません");
            // player.sendMessage(Text.literal("§c近くに機関車がありません（5ブロック以内）"), true);
            return ActionResult.PASS;
        }

        // クライアント側：処理成功としてマークし、バニラのGUIオープン等を防ぐ
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        com.minerailed.MinerailedMod.LOGGER.info("CouplingHandler: 機関車発見(UUID=" + locomotive.getUuid() + ")");

        // 既に連結されているかチェック
        if (locomotive.isCoupled(minecart)) {
            com.minerailed.MinerailedMod.LOGGER.info("CouplingHandler: 既に連結済み -> 解除試行");
            // 連結解除
            if (locomotive.removeCoupledCart(minecart)) {
                player.sendMessage(Text.literal("§a車両を解除しました"), true);
            }
            return ActionResult.SUCCESS;
        } else {
            com.minerailed.MinerailedMod.LOGGER.info("CouplingHandler: 未連結 -> 連結試行");
            // 連結試行
            boolean success = locomotive.addCoupledCart(minecart);
            if (success) {
                player.sendMessage(
                        Text.literal("§a車両を連結しました (" + locomotive.getCoupledCartCount() + "/5)"),
                        true);
                return ActionResult.SUCCESS;
            } else {
                com.minerailed.MinerailedMod.LOGGER
                        .info("CouplingHandler: 連結失敗 (Count=" + locomotive.getCoupledCartCount() + ")");
                // 連結失敗（最大数到達など）
                if (locomotive.getCoupledCartCount() >= 5) {
                    player.sendMessage(Text.literal("§c連結数が最大です（5/5)"), true);
                } else {
                    player.sendMessage(Text.literal("§c連結できません（距離が遠すぎる可能性）"), true);
                }
                return ActionResult.FAIL;
            }
        }
    }

    /**
     * 近くの機関車を探す
     */
    private static LocomotiveEntity findNearbyLocomotive(World world, Entity target) {
        Box searchBox = new Box(
                target.getX() - SEARCH_RANGE,
                target.getY() - SEARCH_RANGE,
                target.getZ() - SEARCH_RANGE,
                target.getX() + SEARCH_RANGE,
                target.getY() + SEARCH_RANGE,
                target.getZ() + SEARCH_RANGE);

        List<LocomotiveEntity> locomotives = world.getEntitiesByClass(
                LocomotiveEntity.class,
                searchBox,
                locomotive -> locomotive.isAlive());

        // 最も近い機関車を返す
        if (locomotives.isEmpty()) {
            return null;
        }

        LocomotiveEntity nearest = locomotives.get(0);
        double nearestDist = target.squaredDistanceTo(nearest);

        for (LocomotiveEntity loco : locomotives) {
            double dist = target.squaredDistanceTo(loco);
            if (dist < nearestDist) {
                nearest = loco;
                nearestDist = dist;
            }
        }

        return nearest;
    }
}
