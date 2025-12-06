package com.minerailed.client;

import com.minerailed.MinerailedMod;
import com.minerailed.client.renderer.LocomotiveEntityRenderer;
import com.minerailed.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * クライアント専用の初期化処理
 * レンダリング、HUD、キー操作などクライアントサイドのみで実行される処理を管理
 */
public class MinerailedClient implements ClientModInitializer {

    /**
     * クライアント初期化処理
     */
    @Override
    public void onInitializeClient() {
        MinerailedMod.LOGGER.info("Minerailed クライアント初期化中...");

        // エンティティレンダラーの登録
        EntityRendererRegistry.register(ModEntities.LOCOMOTIVE, LocomotiveEntityRenderer::new);

        // TODO: HUDレンダラーの登録
        // TODO: キーバインドの登録
        // TODO: パーティクル効果の登録

        MinerailedMod.LOGGER.info("Minerailed クライアント初期化完了");
    }
}
