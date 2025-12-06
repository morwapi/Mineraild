package com.minerailed.registry;

import com.minerailed.MinerailedMod;
import com.minerailed.entity.LocomotiveEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * エンティティの登録を管理するクラス
 */
public class ModEntities {

    /**
     * 機関車エンティティタイプ
     */
    public static final EntityType<LocomotiveEntity> LOCOMOTIVE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MinerailedMod.MOD_ID, "locomotive"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, LocomotiveEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.9f)) // 豚と同じサイズ
                    .build());

    /**
     * 全エンティティの登録を実行
     */
    public static void registerEntities() {
        MinerailedMod.LOGGER.info("Minerailed エンティティを登録中...");

        // エンティティ属性の登録（重要！これがないとNullPointerExceptionが発生）
        FabricDefaultAttributeRegistry.register(LOCOMOTIVE, PigEntity.createPigAttributes());

        MinerailedMod.LOGGER.info("Minerailed エンティティ登録完了");
    }
}
