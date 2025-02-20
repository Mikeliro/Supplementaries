package net.mehvahdjukaar.supplementaries.block.tiles;

import net.mehvahdjukaar.supplementaries.block.blocks.BellowsBlock;
import net.mehvahdjukaar.supplementaries.common.CommonUtil;
import net.mehvahdjukaar.supplementaries.common.ModTags;
import net.mehvahdjukaar.supplementaries.configs.ServerConfigs;
import net.mehvahdjukaar.supplementaries.setup.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BellowsBlockTile extends BlockEntity {

    public float height = 0;
    public float prevHeight = 0;
    private long offset = 0;
    public boolean isPressed = false;

    public BellowsBlockTile(BlockPos pos, BlockState state) {
        super(ModRegistry.BELLOWS_TILE.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition);
    }

    private AABB getHalfBoundingBox(Direction dir) {
        return new AABB(this.worldPosition)
                .contract(-0.5 * dir.getStepX(), -0.5 * dir.getStepY(), -0.5 * dir.getStepZ());
    }

    //TODO: rewrite some of this

    private void moveCollidedEntities(Level level) {
        Direction dir = this.getDirection().getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        for (int j = 0; j < 2; j++) {
            AABB halfBoundingBox = this.getHalfBoundingBox(dir);
            List<Entity> list = level.getEntities(null, halfBoundingBox);
            if (!list.isEmpty()) {
                for (Entity entity : list) {
                    if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
                        AABB entityBB = entity.getBoundingBox();
                        double dy = 0.0D;
                        double dz = 0.0D;
                        float f = this.height + 0.01f;
                        switch (dir) {
                            case SOUTH -> {
                                dz = halfBoundingBox.maxZ + f - entityBB.minZ;
                                if (dz < 0) continue;
                            }
                            case NORTH -> {
                                dz = halfBoundingBox.minZ - f - entityBB.maxZ;
                                if (dz > 0) continue;
                            }
                            case UP -> {
                                dy = halfBoundingBox.maxY + f - entityBB.minY;
                                if (dy < 0) continue;
                            }
                            case DOWN -> {
                                dy = halfBoundingBox.minY - f - entityBB.maxY;
                                if (dy > 0) continue;
                            }
                        }
                        entity.move(MoverType.SHULKER_BOX, new Vec3(0, dy, dz));
                    }
                }
            }
            dir = dir.getOpposite();
        }
    }

    private void pushEntities(Direction facing, float period, float range, Level level) {

        double velocity = ServerConfigs.cached.BELLOWS_BASE_VEL_SCALING / period; // Affects acceleration
        double maxVelocity = ServerConfigs.cached.BELLOWS_MAX_VEL; // Affects max speed

        AABB facingBox = CommonUtil.getDirectionBB(this.worldPosition, facing, (int) range);
        List<Entity> list = level.getEntitiesOfClass(Entity.class, facingBox);

        for (Entity entity : list) {

            if (!this.inLineOfSight(entity, facing, level)) continue;
            if (facing == Direction.UP) maxVelocity *= 0.5D;
            AABB entityBB = entity.getBoundingBox();
            double dist;
            double b;
            switch (facing) {
                default -> {
                    b = worldPosition.getZ() + 1;
                    if (entityBB.minZ < b) continue;
                    dist = entity.getZ() - b;
                }
                case NORTH -> {
                    b = worldPosition.getZ();
                    if (entityBB.maxZ > b) continue;
                    dist = b - entity.getZ();
                }
                case EAST -> {
                    b = worldPosition.getX() + 1;
                    if (entityBB.minX < b) continue;
                    dist = entity.getX() - b;
                }
                case WEST -> {
                    b = worldPosition.getX();
                    if (entityBB.maxX > b) continue;
                    dist = b - entity.getX();
                }
                case UP -> {
                    b = worldPosition.getY() + 1;
                    if (entityBB.minY < b) continue;
                    dist = entity.getY() - b;
                }
                case DOWN -> {
                    b = worldPosition.getY();
                    if (entityBB.maxY > b) continue;
                    dist = b - entity.getY();
                }
            }
            //dist, vel>0
            velocity *= (range - dist) / range;

            if (Math.abs(entity.getDeltaMovement().get(facing.getAxis())) < maxVelocity) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(facing.getStepX() * velocity, facing.getStepY() * velocity, facing.getStepZ() * velocity));
                if (ServerConfigs.cached.BELLOWS_FLAG) entity.hurtMarked = true;
            }
        }
    }

    private void blowParticles(float air, Direction facing, Level level) {
        if (level.random.nextInt(2) == 0 && level.random.nextFloat() < air &&
                !Block.canSupportCenter(level, this.worldPosition.relative(facing), facing.getOpposite())) {
            this.spawnParticle(level, this.worldPosition);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends BlockEntity> void tickFurnaces(BlockPos frontPos, Level level, T tile) {
        BlockState state = level.getBlockState(frontPos);
        if (tile != null && state.is(ModTags.BELLOWS_TICKABLE_TAG)) {
            BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) state.getTicker(level, tile.getType());
            if (ticker != null) {
                ticker.tick(level, frontPos, state, tile);
            }
        }
    }

    private void tickFurnaces(BlockPos frontPos, Level level) {
        BlockEntity te = level.getBlockEntity(frontPos);
        this.tickFurnaces(frontPos, level, te);
    }

    private void refreshFire(int n, Direction facing, BlockPos frontPos, Level level) {
        for (int i = 0; i < n; i++) {
            BlockState fb = level.getBlockState(frontPos);
            if (fb.getBlock() instanceof FireBlock) {
                int age = fb.getValue(FireBlock.AGE);
                if (age != 0) {
                    level.setBlock(frontPos, fb.setValue(FireBlock.AGE,
                            Mth.clamp(age - 7, 0, 15)), 4);
                }
            }
            frontPos = frontPos.relative(facing);
        }
    }

    //TODO: optimize this (also for flywheel)
    public static void tick(Level pLevel, BlockPos pPos, BlockState pState, BellowsBlockTile tile) {

        int power = pState.getValue(BellowsBlock.POWER);
        tile.prevHeight = tile.height;

        if (power != 0 && !(tile.offset == 0 && tile.height != 0)) {
            long time = pLevel.getGameTime();
            if (tile.offset == 0) {
                tile.offset = time;
            }

            float period = ((float) ServerConfigs.cached.BELLOWS_PERIOD) - (power - 1) * ((float) ServerConfigs.cached.BELLOWS_POWER_SCALING);
            Direction facing = pState.getValue(BellowsBlock.FACING);

            //slope of animation. for particles and pushing entities
            float arg = (float) Math.PI * 2 * (((time - tile.offset) / period) % 1);
            float sin = Mth.sin(arg);
            float cos = Mth.cos(arg);
            final float dh = 1 / 16f;//0.09375f;
            tile.height = dh * cos - dh;

            //client. particles
            if (pLevel.isClientSide) {
                tile.blowParticles(sin, facing, pLevel);
            }
            //server
            else {
                float range = ServerConfigs.cached.BELLOWS_RANGE;
                //push entities (only if pushing air)
                if (sin > 0) {
                    tile.pushEntities(facing, period, range, pLevel);
                }

                BlockPos frontPos = pPos.relative(facing);

                //speeds up furnaces
                if (time % (10 - (power / 2)) == 0) {
                    tile.tickFurnaces(frontPos, pLevel);
                }

                //refresh fire blocks
                //update more frequently block closed to it
                //fire updates (previous random tick) at a minimum of 30 ticks
                int n = 0;
                for (int a = 0; a <= range; a++) {
                    if (time % (15L * (a + 1)) != 0) {
                        n = a;
                        break;
                    }
                }
                //only first 4 block will ultimately be kept active. this could change with random ticks if unlucky
                tile.refreshFire(n, facing, frontPos, pLevel);

            }
        } else if (tile.isPressed) {
            float minH = -2 / 16f;
            tile.height = Math.max(tile.height - 0.01f, minH);

            if (tile.height > minH) {
                Direction facing = pState.getValue(BellowsBlock.FACING);

                if (pLevel.isClientSide) {
                    tile.blowParticles(0.8f, facing, pLevel);
                } else {
                    float range = ServerConfigs.cached.BELLOWS_RANGE;
                    tile.pushEntities(facing, ServerConfigs.cached.BELLOWS_PERIOD, range, pLevel);

                    BlockPos frontPos = pPos.relative(facing);

                    if (tile.height % 0.04 == 0) {
                        tile.tickFurnaces(frontPos, pLevel);
                    }

                    if (tile.height % 0.15 == 0) {
                        tile.refreshFire((int) range, facing, frontPos, pLevel);
                    }
                }
            }
        }
        //resets counter when powered off
        else {
            tile.offset = 0;
            if (tile.height < 0)
                tile.height = Math.min(tile.height + 0.01f, 0);
        }
        if (tile.prevHeight != 0 && tile.height != 0) {
            tile.moveCollidedEntities(pLevel);
        }
        tile.isPressed = false;
    }

    public boolean inLineOfSight(Entity entity, Direction facing, Level level) {
        int x = facing.getStepX() * (Mth.floor(entity.getX()) - this.worldPosition.getX());
        int y = facing.getStepY() * (Mth.floor(entity.getY()) - this.worldPosition.getY());
        int z = facing.getStepZ() * (Mth.floor(entity.getZ()) - this.worldPosition.getZ());
        boolean flag = true;

        for (int i = 1; i < Math.abs(x + y + z); i++) {

            if (Block.canSupportCenter(level, this.worldPosition.relative(facing, i), facing.getOpposite())) {
                flag = false;
            }
        }
        return flag;
    }

    public void spawnParticle(Level world, BlockPos pos) {
        Direction dir = this.getDirection();
        double xo = dir.getStepX();
        double yo = dir.getStepY();
        double zo = dir.getStepZ();
        double x = xo * 0.5 + pos.getX() + 0.5 + (world.random.nextFloat() - 0.5) / 3d;
        double y = yo * 0.5 + pos.getY() + 0.5 + (world.random.nextFloat() - 0.5) / 3d;
        double z = zo * 0.5 + pos.getZ() + 0.5 + (world.random.nextFloat() - 0.5) / 3d;

        double vel = 0.125F + world.random.nextFloat() * 0.2F;

        double velX = xo * vel;
        double velY = yo * vel;
        double velZ = zo * vel;

        world.addParticle(ParticleTypes.SMOKE, x, y, z, velX, velY, velZ);
    }

    public Direction getDirection() {
        return this.getBlockState().getValue(BellowsBlock.FACING);
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        this.offset = compound.getLong("Offset");
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        super.save(compound);
        compound.putLong("Offset", this.offset);
        return compound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, 0, this.getUpdateTag());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.save(new CompoundTag());
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        this.load(pkt.getTag());
    }

    public void onSteppedOn(Entity entityIn) {
        if (this.isPressed) return;
        double b = entityIn.getBoundingBox().getSize();
        if (b > 0.8 && this.getBlockState().getValue(BellowsBlock.FACING).getAxis() != Direction.Axis.Y) {
            this.isPressed = true;
        }
    }
}