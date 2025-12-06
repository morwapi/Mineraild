package com.minerailed.registry;

import com.minerailed.MinerailedMod;
import com.minerailed.item.TrainSpawnerItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * アイテムの登録を管理するクラス
 * 全カスタムアイテムをここで一元管理
 */
public class ModItems {

    /**
     * 列車召喚アイテム
     * 右クリックで基本的な機関車を召喚する
     */
    public static final Item TRAIN_SPAWNER = registerItem("train_spawner",
            new TrainSpawnerItem(new FabricItemSettings().maxCount(1)));

    /**
     * アイテムを登録するヘルパーメソッド
     * 
     * @param name アイテムID（例: "train_spawner"）
     * @param item アイテムインスタンス
     * @return 登録されたアイテム
     */
    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(MinerailedMod.MOD_ID, name), item);
    }

    /**
     * 全アイテムの登録を実行
     * MinerailedMod.onInitialize()から呼び出される
     */
    public static void registerItems() {
        MinerailedMod.LOGGER.info("Minerailed アイテムを登録中...");
        // 上記のstatic初期化により自動的に登録される
    }
}
