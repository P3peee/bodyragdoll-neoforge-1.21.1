package com.example.bodyragdoll.entity;

import com.example.bodyragdoll.Config;
import com.example.bodyragdoll.physics.RagdollSim;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Труп. Это настоящая сущность: сервер хранит её в мире (переживает рестарт)
 * и рассылает всем игрокам поблизости. Физику каждый клиент считает сам, но детерминированно.
 */
public class CorpseEntity extends Entity {
    private static final EntityDataAccessor<String> TYPE_ID = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> PLAYER_UUID = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> PLAYER_NAME = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Vector3f> IMPULSE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> HIT = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BIRTH = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VICTIM = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);

    /** id умирающей сущности -> до какого тика её не рисуем (чтобы не было двойника). Только клиент. */
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();

    public RagdollSim sim;          // только клиент
    public Object renderCache;      // только клиент

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(TYPE_ID, "minecraft:pig");
        b.define(PLAYER_UUID, Optional.empty());
        b.define(PLAYER_NAME, "");
        b.define(IMPULSE, new Vector3f());
        b.define(HIT, 0.6f);
        b.define(BIRTH, 0);
        b.define(VICTIM, -1);
    }

    public void setup(String typeId, UUID playerUuid, String playerName, int victimId, Vec3 impulse, float hit, int birth) {
        entityData.set(TYPE_ID, typeId);
        entityData.set(PLAYER_UUID, Optional.ofNullable(playerUuid));
        entityData.set(PLAYER_NAME, playerName == null ? "" : playerName);
        entityData.set(IMPULSE, new Vector3f((float) impulse.x, (float) impulse.y, (float) impulse.z));
        entityData.set(HIT, hit);
        entityData.set(BIRTH, birth);
        entityData.set(VICTIM, victimId);
    }

    public String getTypeId() { return entityData.get(TYPE_ID); }
    public Optional<UUID> getPlayerUuid() { return entityData.get(PLAYER_UUID); }
    public String getPlayerName() { return entityData.get(PLAYER_NAME); }
    public Vector3f getImpulse() { return entityData.get(IMPULSE); }
    public float getHit() { return entityData.get(HIT); }
    public int getBirth() { return entityData.get(BIRTH); }
    public int getVictimId() { return entityData.get(VICTIM); }

    public double modelScale() {
        return "minecraft:player".equals(getTypeId()) ? 0.9375 : 1.0;
    }

    @Override
    public void tick() {
        if (level().isClientSide) clientTick();
        else serverTick();
    }

    private void serverTick() {
        int life = Config.LIFETIME.get();
        if (life > 0 && tickCount % 20 == 0 && (int) level().getGameTime() - getBirth() > life * 20) {
            discard();
        }
    }

    private void clientTick() {
        if (getVictimId() < 0) return; // данные ещё не пришли
        markHidden(getVictimId(), level().getGameTime() + 60);

        if (sim == null) {
            if (!level().hasChunkAt(blockPosition())) return;
            sim = new RagdollSim(getX(), getY(), getZ(), getYRot(), modelScale());
            Vector3f v = getImpulse();
            sim.launch(v.x, v.y, v.z, getHit(), getUUID().getLeastSignificantBits());
            // догоняем симуляцию, если мы зашли позже момента смерти
            int age = Math.max(0, (int) level().getGameTime() - getBirth());
            for (int i = Math.min(age, 300); i > 0 && !sim.asleep; i--) sim.step(level());
            sim.snapshot();
        } else if (!sim.asleep) {
            sim.step(level());
        }
    }

    public static void markHidden(int id, long until) {
        if (HIDDEN.size() > 256) HIDDEN.clear();
        HIDDEN.put(id, until);
    }

    public static boolean isHidden(LivingEntity e) {
        if (!e.isDeadOrDying()) return false;
        Long t = HIDDEN.get(e.getId());
        return t != null && e.level().getGameTime() <= t;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(6.0);
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Type", getTypeId());
        getPlayerUuid().ifPresent(u -> tag.putUUID("Player", u));
        tag.putString("Name", getPlayerName());
        Vector3f v = getImpulse();
        tag.putFloat("Ix", v.x);
        tag.putFloat("Iy", v.y);
        tag.putFloat("Iz", v.z);
        tag.putFloat("Hit", getHit());
        tag.putInt("Birth", getBirth());
        tag.putInt("Victim", getVictimId());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Type")) entityData.set(TYPE_ID, tag.getString("Type"));
        if (tag.hasUUID("Player")) entityData.set(PLAYER_UUID, Optional.of(tag.getUUID("Player")));
        entityData.set(PLAYER_NAME, tag.getString("Name"));
        entityData.set(IMPULSE, new Vector3f(tag.getFloat("Ix"), tag.getFloat("Iy"), tag.getFloat("Iz")));
        entityData.set(HIT, tag.contains("Hit") ? tag.getFloat("Hit") : 0.6f);
        entityData.set(BIRTH, tag.getInt("Birth"));
        entityData.set(VICTIM, tag.contains("Victim") ? tag.getInt("Victim") : 0);
    }
}
