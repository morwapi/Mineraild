package com.minerailed.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 機関車エンティティ - 第1段階（豚ベース）
 * 
 * 成長段階に応じて豚→牛→ラヴェジャーと変化する機関車の基盤
 * 現在は最もシンプルな豚バージョンのみを実装
 */
public class LocomotiveEntity extends PigEntity {

    private int growthStage = 0;
    private int railsPlaced = 0;
    private float trainSpeed = 0.1f;
    private boolean autoMoveEnabled = true;
    private int moveCheckCounter = 0;

    // カーブでの連続方向転換を防ぐ
    private Direction lastDirection = null;
    private int directionChangeCooldown = 0;

    public LocomotiveEntity(EntityType<? extends PigEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
    }

    @Override
    protected void initGoals() {
        // AIゴールをクリアして豚が勝手に動かないようにする
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient || !autoMoveEnabled) {
            return;
        }

        moveCheckCounter++;
        if (moveCheckCounter < 10) {
            return;
        }
        moveCheckCounter = 0;

        tryMoveOnRail();
    }

    private void tryMoveOnRail() {
        // クールダウン中は方向を変えない
        if (directionChangeCooldown > 0) {
            directionChangeCooldown--;
        }

        BlockPos currentPos = this.getBlockPos();
        BlockState blockState = this.getWorld().getBlockState(currentPos);

        if (blockState == null || !(blockState.getBlock() instanceof RailBlock)) {
            BlockPos belowPos = currentPos.down();
            blockState = this.getWorld().getBlockState(belowPos);

            if (blockState == null || !(blockState.getBlock() instanceof RailBlock)) {
                this.setVelocity(Vec3d.ZERO);
                return;
            }
        }

        Direction railDirection = getRailDirection(blockState);

        if (railDirection != null) {
            // 方向が変わった場合、クールダウンを設定
            if (lastDirection != null && lastDirection != railDirection) {
                if (directionChangeCooldown > 0) {
                    // クールダウン中は前回の方向を維持
                    railDirection = lastDirection;
                } else {
                    // 方向変更を許可し、クールダウンを開始
                    directionChangeCooldown = 20; // 2秒間（20 Tick）
                    lastDirection = railDirection;
                }
            } else {
                lastDirection = railDirection;
            }

            Vec3d movement = Vec3d.of(railDirection.getVector()).multiply(trainSpeed);
            this.setVelocity(movement.x, this.getVelocity().y, movement.z);
            this.setYaw(railDirection.asRotation());
        }
    }

    private Direction getRailDirection(BlockState blockState) {
        if (!(blockState.getBlock() instanceof RailBlock)) {
            return null;
        }

        RailBlock railBlock = (RailBlock) blockState.getBlock();

        try {
            RailShape shape = blockState.get(railBlock.getShapeProperty());
            Vec3d velocity = this.getVelocity();
            Direction currentDirection = getDirectionFromVelocity(velocity);
            return getDirectionFromRailShape(shape, currentDirection);
        } catch (Exception e) {
            return Direction.NORTH;
        }
    }

    private Direction getDirectionFromVelocity(Vec3d velocity) {
        if (Math.abs(velocity.x) < 0.01 && Math.abs(velocity.z) < 0.01) {
            return Direction.fromRotation(this.getYaw());
        }

        double absX = Math.abs(velocity.x);
        double absZ = Math.abs(velocity.z);

        if (absX > absZ) {
            return velocity.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return velocity.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private Direction getDirectionFromRailShape(RailShape shape, Direction currentDirection) {
        switch (shape) {
            case NORTH_SOUTH:
                // 南北直線：南か北のみ
                return (currentDirection == Direction.SOUTH) ? Direction.SOUTH : Direction.NORTH;

            case EAST_WEST:
                // 東西直線：東か西のみ
                return (currentDirection == Direction.WEST) ? Direction.WEST : Direction.EAST;

            case ASCENDING_NORTH:
                return Direction.NORTH;
            case ASCENDING_SOUTH:
                return Direction.SOUTH;
            case ASCENDING_EAST:
                return Direction.EAST;
            case ASCENDING_WEST:
                return Direction.WEST;

            case SOUTH_EAST:
                // 南東カーブ：南→東、東→南
                if (currentDirection == Direction.SOUTH) {
                    return Direction.EAST;
                } else if (currentDirection == Direction.EAST) {
                    return Direction.SOUTH;
                }
                return Direction.EAST; // デフォルト

            case SOUTH_WEST:
                // 南西カーブ：南→西、西→南
                if (currentDirection == Direction.SOUTH) {
                    return Direction.WEST;
                } else if (currentDirection == Direction.WEST) {
                    return Direction.SOUTH;
                }
                return Direction.WEST; // デフォルト

            case NORTH_WEST:
                // 北西カーブ：北→西、西→北
                if (currentDirection == Direction.NORTH) {
                    return Direction.WEST;
                } else if (currentDirection == Direction.WEST) {
                    return Direction.NORTH;
                }
                return Direction.WEST; // デフォルト

            case NORTH_EAST:
                // 北東カーブ：北→東、東→北
                if (currentDirection == Direction.NORTH) {
                    return Direction.EAST;
                } else if (currentDirection == Direction.EAST) {
                    return Direction.NORTH;
                }
                return Direction.EAST; // デフォルト

            default:
                return currentDirection;
        }
    }

    public void addRailsPlaced(int count) {
        this.railsPlaced += count;
        checkGrowth();
    }

    private void checkGrowth() {
        int newStage = growthStage;
        if (railsPlaced >= 301) {
            newStage = 2;
        } else if (railsPlaced >= 101) {
            newStage = 1;
        }
        if (newStage != growthStage) {
            growthStage = newStage;
        }
    }

    public void setTrainSpeed(float speed) {
        this.trainSpeed = Math.max(0.0f, Math.min(1.0f, speed));
    }

    public int getGrowthStage() {
        return growthStage;
    }
}
