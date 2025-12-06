package com.minerailed.registry;

import com.minerailed.MinerailedMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

/**
 * クリエイティブモードのタブにアイテムを追加
 */
public class ModItemGroups {

    /**
     * クリエイティブタブへの登録
     */
    public static void registerItemGroups() {
        MinerailedMod.LOGGER.info("Minerailed アイテムをクリエイティブタブに追加中...");

        // ツールタブに列車召喚アイテムを追加
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(ModItems.TRAIN_SPAWNER);
        });
    }
}
