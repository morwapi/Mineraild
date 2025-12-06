package com.minerailed.client.renderer;

import com.minerailed.entity.LocomotiveEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PigEntityRenderer;

/**
 * 機関車エンティティのレンダラー
 * 現在は豚のレンダラーをそのまま使用
 */
public class LocomotiveEntityRenderer extends PigEntityRenderer {

    public LocomotiveEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }
}
