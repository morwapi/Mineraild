package com.minerailed;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minerailed Mod のメインクラス
 * Unrailedライクなゲームモードを既存のMinecraftアイテムで再現
 */
public class MinerailedMod implements ModInitializer {
    /**
     * Mod ID - 全リソースとレジストリで使用
     */
    public static final String MOD_ID = "minerailed";

    /**
     * ロガー - デバッグとエラー出力用
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Mod初期化処理
     * サーバー・クライアント共通で実行される
     */
    @Override
    public void onInitialize() {
        LOGGER.info("Minerailed Mod を初期化中...");

        // アイテム登録
        com.minerailed.registry.ModItems.registerItems();

        // エンティティ登録
        com.minerailed.registry.ModEntities.registerEntities();

        // クリエイティブタブ登録
        com.minerailed.registry.ModItemGroups.registerItemGroups();

        // TODO: イベントハンドラーの登録
        // TODO: ネットワークパケットの登録

        LOGGER.info("Minerailed Mod の初期化完了");
    }
}
