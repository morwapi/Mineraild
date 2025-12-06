package com.minerailed.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 機関車エンティティ - 第1段階（豚ベース）
 * 
 * 成長段階に応じて豚→牛→ラヴェジャーと変化する機関車の基盤
 * 車両連結システムを含む
 */
public class LocomotiveEntity extends PigEntity {

    /** 最大連結車両数（機関車含まず） */
    private static final int MAX_COUPLED_CARTS = 5;

    /** 連結距離の最大値 */
    private static final double MAX_COUPLING_DISTANCE = 3.0;

    private int growthStage = 0;
    private int railsPlaced = 0;
    private float trainSpeed = 0.04f;
    private boolean autoMoveEnabled = true;
    private int moveCheckCounter = 0;

    // カーブでの連続方向転換を防ぐ
    private Direction lastDirection = null;
    private int directionChangeCooldown = 0;

    // クラフト関連
    private int craftingCooldown = 0;
    private static final int CRAFTING_INTERVAL_TICKS = 100; // 5秒ごとにチェック

    // TNT配布関連
    private int tntDistributionTimer = 0;
    private static final int TNT_DISTRIBUTION_INTERVAL = 3600; // 3分 = 20t * 60s * 3

    // 連結クールダウン（二重クリック/イベント防止）
    private long lastCouplingTime = 0;
    private static final long COUPLING_COOLDOWN_MS = 500;

    // 連結された車両のリスト
    private final List<UUID> coupledCartUUIDs = new ArrayList<>();
    private final List<Entity> coupledCarts = new ArrayList<>();

    public LocomotiveEntity(EntityType<? extends PigEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();

        // 連結リストを明示的に初期化
        coupledCartUUIDs.clear();
        coupledCarts.clear();
        com.minerailed.MinerailedMod.LOGGER.info("機関車生成: UUID=" + this.getUuid() + ", 連結リスト初期化完了");
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

        // リストの同期チェック（不整合がある場合、再解決を試みる）
        if (coupledCarts.size() != coupledCartUUIDs.size()) {
            resolveCartReferences();
        }

        // 移動更新頻度制限を撤廃（滑らかな移動）
        // moveCheckCounter++;
        // if (moveCheckCounter < 10) {
        // return;
        // }
        // moveCheckCounter = 0;

        tryMoveOnRail();
        moveCoupledCarts(); // 毎Tick更新
        handleCrafting(); // 自動クラフト処理
        handleTntDistribution(); // TNT配布処理
    }

    /**
     * TNTトロッコが連結されている場合、定期的にTNTを配布
     */
    private void handleTntDistribution() {
        boolean hasTntCart = false;
        for (Entity cart : coupledCarts) {
            if (cart instanceof TntMinecartEntity) {
                hasTntCart = true;
                break;
            }
        }

        if (hasTntCart) {
            tntDistributionTimer++;
            if (tntDistributionTimer >= TNT_DISTRIBUTION_INTERVAL) {
                tntDistributionTimer = 0;
                distributeItemToAllPlayers(new ItemStack(Items.TNT, 1));
                com.minerailed.MinerailedMod.LOGGER.info("TNT定期配布実行");
            }
        } else {
            // TNTカートがない場合はタイマーリセット
            tntDistributionTimer = 0;
        }
    }

    /**
     * 全プレイヤーにアイテムを配布
     */
    private void distributeItemToAllPlayers(ItemStack stack) {
        if (this.getWorld().isClient)
            return;

        net.minecraft.server.MinecraftServer server = this.getWorld().getServer();
        if (server == null)
            return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            ItemStack toGive = stack.copy();
            if (!player.getInventory().insertStack(toGive)) {
                // インベントリに入らない場合はドロップ
                player.dropItem(toGive, false);
            }
        }
    }

    /**
     * 自動クラフト処理
     * チェスト付きトロッコとカマド付きトロッコが連結されている場合、
     * 原木と鉄の原石からレールを製造する
     */
    private void handleCrafting() {
        if (craftingCooldown > 0) {
            craftingCooldown--;
            return;
        }

        // リセット
        craftingCooldown = CRAFTING_INTERVAL_TICKS;

        // 必要な車両があるかチェック
        boolean hasFurnace = false;
        List<ChestMinecartEntity> chestCarts = new ArrayList<>();

        for (Entity cart : coupledCarts) {
            if (cart instanceof FurnaceMinecartEntity) {
                hasFurnace = true;
            } else if (cart instanceof ChestMinecartEntity) {
                chestCarts.add((ChestMinecartEntity) cart);
            }
        }

        if (!hasFurnace || chestCarts.isEmpty()) {
            return;
        }

        // 材料を探して消費
        // 必要素材: 原木(Logs) x3, 鉄の原石(Raw Iron) x3 -> レール(Rail) x1
        // ※バニラのレシピは鉄インゴットだが、要望により原石を使用

        for (ChestMinecartEntity chestCart : chestCarts) {
            Inventory inv = chestCart; // ChestMinecartEntity implements Inventory

            int logCount = 0;
            int ironCount = 0;
            List<Integer> logSlots = new ArrayList<>();
            List<Integer> ironSlots = new ArrayList<>();

            // インベントリをスキャン
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty())
                    continue;

                if (stack.isIn(ItemTags.LOGS)) {
                    logCount += stack.getCount();
                    logSlots.add(i);
                } else if (stack.isOf(Items.RAW_IRON)) {
                    ironCount += stack.getCount();
                    ironSlots.add(i);
                }
            }

            // 材料が足りているか
            if (logCount >= 3 && ironCount >= 3) {
                // 消費実行
                consumeItems(inv, logSlots, 3);
                consumeItems(inv, ironSlots, 3);

                // レール生成
                ItemStack railResult = new ItemStack(Items.RAIL, 1);
                addStackToInventory(inv, railResult);

                // エフェクト代わりのログ/メッセージ（余裕があればパーティクルも）
                com.minerailed.MinerailedMod.LOGGER.info("レール製造完了: " + this.getUuid());
                // 1回につき1個作成でループを抜ける（複数作成したい場合は継続）
                // ここでは1サイクルで1個作成とする
                return;
            }
        }
    }

    private void consumeItems(Inventory inv, List<Integer> slots, int countToConsume) {
        int remaining = countToConsume;
        for (int slot : slots) {
            ItemStack stack = inv.getStack(slot);
            int take = Math.min(remaining, stack.getCount());

            stack.decrement(take);
            remaining -= take;

            if (remaining <= 0)
                break;
        }
    }

    private void addStackToInventory(Inventory inv, ItemStack stack) {
        // 既存のスタックにマージ
        for (int i = 0; i < inv.size(); i++) {
            ItemStack slotStack = inv.getStack(i);
            if (ItemStack.canCombine(slotStack, stack)) {
                int available = slotStack.getMaxCount() - slotStack.getCount();
                int move = Math.min(available, stack.getCount());
                slotStack.increment(move);
                stack.decrement(move);
                if (stack.isEmpty())
                    return;
            }
        }

        // 空きスロットに入れる
        if (!stack.isEmpty()) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i).isEmpty()) {
                    inv.setStack(i, stack.copy());
                    stack.setCount(0);
                    return;
                }
            }
        }

        // インベントリがいっぱいの場合は、アイテムエンティティとしてドロップする処理が必要だが
        // 簡易実装として消滅（あるいは機関車のログに出す）
        if (!stack.isEmpty()) {
            com.minerailed.MinerailedMod.LOGGER.warn("インベントリが一杯でレールが入らない");
        }
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

    /**
     * 連結された車両を機関車と一緒に動かす
     */
    private void moveCoupledCarts() {
        if (coupledCartUUIDs.isEmpty()) {
            return;
        }

        Vec3d locoPos = this.getPos();
        Vec3d locoVelocity = this.getVelocity();

        // 各車両を機関車の後ろに配置
        for (int i = 0; i < coupledCarts.size(); i++) {
            Entity cart = coupledCarts.get(i);
            if (cart == null || !cart.isAlive()) {
                continue;
            }

            // 車両の位置を計算（機関車の後ろに1.5ブロックずつ離して配置）
            double offset = -1.5 * (i + 1);
            Vec3d targetPos = locoPos.add(
                    lastDirection != null ? Vec3d.of(lastDirection.getVector()).multiply(offset)
                            : new Vec3d(0, 0, offset));

            // 車両を目標位置に移動
            cart.setPosition(targetPos.x, cart.getY(), targetPos.z);
            cart.setVelocity(locoVelocity);

            // 向きも同期
            cart.setYaw(this.getYaw());
        }
    }

    /**
     * UUIDから実際のエンティティ参照を解決
     */
    private void resolveCartReferences() {
        coupledCarts.clear();
        if (coupledCartUUIDs.isEmpty())
            return;

        List<UUID> toRemove = new ArrayList<>();
        net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) this.getWorld();

        for (UUID uuid : coupledCartUUIDs) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && entity.isAlive() && entity instanceof AbstractMinecartEntity) {
                coupledCarts.add(entity);
            } else {
                // 見つからない場合は保持するが、長時間見つからない場合のロジックも検討必要
                // ここではデバッグログのみ
                com.minerailed.MinerailedMod.LOGGER.debug("解決失敗: UUID=" + uuid + " (Entity not found or dead)");

                // ※自己修復：もし「連結しているはず(isCoupled=true)」なのに実体がないなら、
                // プレイヤーの操作で矛盾が生じるので、本来は削除すべきか？
                // しかしチャンクロード待ちの可能性もあるので、自動削除は慎重に。
            }
        }

        // デバッグ: 解決状況
        if (coupledCarts.size() != coupledCartUUIDs.size()) {
            com.minerailed.MinerailedMod.LOGGER
                    .warn("不整合: UUIDs=" + coupledCartUUIDs.size() + ", Entities=" + coupledCarts.size());
        }
    }

    /**
     * 車両を連結
     * 
     * @param cart 連結する車両
     * @return 連結成功したらtrue
     */
    public boolean addCoupledCart(Entity cart) {
        // クールダウンチェック
        long now = System.currentTimeMillis();
        if (now - lastCouplingTime < COUPLING_COOLDOWN_MS) {
            return false;
        }
        lastCouplingTime = now;

        // Null安全性チェック
        if (cart == null || !(cart instanceof AbstractMinecartEntity)) {
            return false;
        }

        // 最大数チェック
        if (coupledCartUUIDs.size() >= MAX_COUPLED_CARTS) {
            return false;
        }

        // 既に連結済みかチェック
        if (coupledCartUUIDs.contains(cart.getUuid())) {
            return false;
        }

        // 距離チェックの緩和
        // 実体に依存すると取得できない場合があるため、機関車からの距離で計算する
        // 許容距離 = 基本距離(3.0) + (連結数 * 2.0)
        double allowedDistance = MAX_COUPLING_DISTANCE + (coupledCartUUIDs.size() * 2.0);
        if (this.squaredDistanceTo(cart) > allowedDistance * allowedDistance) {
            com.minerailed.MinerailedMod.LOGGER.info("連結失敗: 距離が離れすぎています (Distance="
                    + Math.sqrt(this.squaredDistanceTo(cart)) + ", Allowed=" + allowedDistance + ")");
            return false;
        }

        // 連結実行
        coupledCartUUIDs.add(cart.getUuid());
        coupledCarts.add(cart); // 即時追加

        // TNTトロッコの場合、初回特典として着火具を配布
        if (cart instanceof TntMinecartEntity) {
            distributeItemToAllPlayers(new ItemStack(Items.FLINT_AND_STEEL, 1));
            com.minerailed.MinerailedMod.LOGGER.info("TNTトロッコ連結特典: 着火具配布");
        }

        com.minerailed.MinerailedMod.LOGGER
                .info("車両連結成功: CartUUID=" + cart.getUuid() + ",CurrentCount=" + coupledCartUUIDs.size());
        return true;
    }

    /**
     * 車両を解除
     * 
     * @param cart 解除する車両
     * @return 解除成功したらtrue
     */
    public boolean removeCoupledCart(Entity cart) {
        // クールダウンチェック
        long now = System.currentTimeMillis();
        if (now - lastCouplingTime < COUPLING_COOLDOWN_MS) {
            return false;
        }
        lastCouplingTime = now;

        if (cart == null) {
            return false;
        }

        boolean removed = coupledCartUUIDs.remove(cart.getUuid());
        coupledCarts.remove(cart);

        if (removed) {
            com.minerailed.MinerailedMod.LOGGER.info("車両解除: " + cart.getUuid());
        }
        return removed;
    }

    /**
     * 指定されたエンティティが連結されているかチェック
     */
    public boolean isCoupled(Entity cart) {
        boolean result = cart != null && coupledCartUUIDs.contains(cart.getUuid());
        com.minerailed.MinerailedMod.LOGGER.info("isCoupledチェック: cart=" + (cart != null ? cart.getUuid() : "null")
                + ", 機関車UUID=" + this.getUuid()
                + ", result=" + result
                + ", リストサイズ=" + coupledCartUUIDs.size());
        return result;
    }

    /**
     * 連結された車両の数を取得
     */
    public int getCoupledCartCount() {
        return coupledCartUUIDs.size();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        // 連結車両のUUIDを保存
        NbtList cartList = new NbtList();
        for (UUID uuid : coupledCartUUIDs) {
            NbtCompound cartNbt = new NbtCompound();
            cartNbt.putUuid("UUID", uuid);
            cartList.add(cartNbt);
        }
        nbt.put("CoupledCarts", cartList);

        // その他のデータ
        nbt.putInt("GrowthStage", growthStage);
        nbt.putInt("RailsPlaced", railsPlaced);
        nbt.putFloat("TrainSpeed", trainSpeed);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        // 連結車両のUUIDを読み込み
        coupledCartUUIDs.clear();
        coupledCarts.clear(); // エンティティ参照もクリア

        NbtList cartList = nbt.getList("CoupledCarts", 10); // 10 = NBT_COMPOUND
        for (int i = 0; i < cartList.size(); i++) {
            NbtCompound cartNbt = cartList.getCompound(i);
            UUID uuid = cartNbt.getUuid("UUID");
            coupledCartUUIDs.add(uuid);
        }

        // その他のデータ
        this.growthStage = nbt.getInt("GrowthStage");
        this.railsPlaced = nbt.getInt("RailsPlaced");
        this.trainSpeed = nbt.getFloat("TrainSpeed");
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
        // デバッグログ: カーブ判定の詳細（想定外のカーブ挙動を追跡）
        if (shape != RailShape.NORTH_SOUTH && shape != RailShape.EAST_WEST &&
                !shape.name().startsWith("ASCENDING")
                && currentDirection != getDirectionFromRailShapeInternal(shape, currentDirection)) {
            com.minerailed.MinerailedMod.LOGGER.info("カーブ判定変更: Shape=" + shape + ", InDir=" + currentDirection
                    + ", OutDir=" + getDirectionFromRailShapeInternal(shape, currentDirection));
        }
        return getDirectionFromRailShapeInternal(shape, currentDirection);
    }

    // 実際のロジックを別メソッドに分離
    private Direction getDirectionFromRailShapeInternal(RailShape shape, Direction currentDirection) {
        switch (shape) {
            case NORTH_SOUTH:
                return (currentDirection == Direction.SOUTH) ? Direction.SOUTH : Direction.NORTH;
            case EAST_WEST:
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
                // 南東カーブ（南と東がつながっている）
                if (currentDirection == Direction.NORTH)
                    return Direction.EAST; // 北へ進む（南から進入）→ 東へ
                if (currentDirection == Direction.WEST)
                    return Direction.SOUTH; // 西へ進む（東から進入）→ 南へ
                return currentDirection;

            case SOUTH_WEST:
                // 南西カーブ（南と西がつながっている）
                if (currentDirection == Direction.NORTH)
                    return Direction.WEST; // 北へ進む（南から進入）→ 西へ
                if (currentDirection == Direction.EAST)
                    return Direction.SOUTH; // 東へ進む（西から進入）→ 南へ
                return currentDirection;

            case NORTH_WEST:
                // 北西カーブ（北と西がつながっている）
                if (currentDirection == Direction.SOUTH)
                    return Direction.WEST; // 南へ進む（北から進入）→ 西へ
                if (currentDirection == Direction.EAST)
                    return Direction.NORTH; // 東へ進む（西から進入）→ 北へ
                return currentDirection;

            case NORTH_EAST:
                // 北東カーブ（北と東がつながっている）
                if (currentDirection == Direction.SOUTH)
                    return Direction.EAST; // 南へ進む（北から進入）→ 東へ
                if (currentDirection == Direction.WEST)
                    return Direction.NORTH; // 西へ進む（東から進入）→ 北へ
                return currentDirection;

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
