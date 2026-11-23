package com.example.bodyragdoll;

import com.example.bodyragdoll.entity.CorpseEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = BodyRagdoll.MODID)
public final class ServerEvents {

    private static final class Pending {
        ServerLevel level;
        int victimId;
        String typeId;
        UUID playerUuid;
        String playerName;
        double x, y, z;
        float yaw;
        Vec3 motion;
        Vec3 knock = Vec3.ZERO;
        float hit = 0.6f;
    }

    /** Попадание из оружия, пришедшее раньше смерти (на всякий случай). */
    private record GunHit(Vec3 impulse, float hit) {}

    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<UUID, GunHit> GUN_HITS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SCREAM = new HashMap<>();

    // ---------- смерть -> труп ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!Config.ENABLED.get()) return;

        scream(victim, true);

        if (victim instanceof ArmorStand) return;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        if (Config.BLACKLIST.get().contains(key.toString())) return;
        boolean isPlayer = victim instanceof Player;
        if (!isPlayer && !Config.MOBS.get()) return;

        Pending p = new Pending();
        p.level = level;
        p.victimId = victim.getId();
        p.typeId = key.toString();
        if (victim instanceof Player pl) {
            p.playerUuid = pl.getUUID();
            p.playerName = pl.getGameProfile().getName();
        }
        p.x = victim.getX();
        p.y = victim.getY();
        p.z = victim.getZ();
        p.yaw = victim.yBodyRot;
        p.motion = victim.getDeltaMovement();
        computeKnock(p, victim, event.getSource());

        GunHit g = GUN_HITS.remove(victim.getUUID());
        if (g != null) {
            p.knock = g.impulse();
            p.hit = g.hit();
        }
        PENDING.put(victim.getUUID(), p);
    }

    private static void computeKnock(Pending p, LivingEntity victim, DamageSource src) {
        double s = Config.IMPULSE.get();
        Vec3 dir = null;
        Entity direct = src.getDirectEntity();
        if (direct instanceof Projectile && direct.getDeltaMovement().lengthSqr() > 1.0E-4) {
            dir = direct.getDeltaMovement().normalize();
            p.hit = (float) Mth.clamp((direct.getY() - victim.getY()) / Math.max(0.1, victim.getBbHeight()), 0.0, 1.0);
        } else {
            Vec3 from = src.getSourcePosition();
            if (from != null) {
                Vec3 d = victim.position().subtract(from);
                d = new Vec3(d.x, 0, d.z);
                if (d.lengthSqr() > 1.0E-4) dir = d.normalize();
            }
        }
        if (dir == null) return;
        p.knock = new Vec3(dir.x * s, Math.max(dir.y * s, 0) + 0.1 * s, dir.z * s);
    }

    /** Вызывается TaCZ-совместимостью, когда сущность убита из оружия. */
    public static void onGunKill(LivingEntity killed, LivingEntity attacker, boolean headshot, float damage) {
        if (!(killed.level() instanceof ServerLevel)) return;
        Vec3 dir = Vec3.ZERO;
        if (attacker != null) {
            dir = killed.getBoundingBox().getCenter().subtract(attacker.getEyePosition()).normalize();
        }
        double mul = (1.0 + Math.min(damage, 30f) / 30.0) * (headshot ? 1.25 : 1.0);
        Vec3 imp = dir.scale(Config.IMPULSE.get() * mul);
        float hit = headshot ? 0.95f : 0.6f;

        Pending p = PENDING.get(killed.getUUID());
        if (p != null) {
            p.knock = imp;
            p.hit = hit;
        } else {
            GUN_HITS.put(killed.getUUID(), new GunHit(imp, hit));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() && GUN_HITS.isEmpty()) return;
        for (Pending p : PENDING.values()) spawn(p);
        PENDING.clear();
        GUN_HITS.clear();
    }

    private static void spawn(Pending p) {
        CorpseEntity c = ModRegistry.CORPSE.get().create(p.level);
        if (c == null) return;
        Vec3 imp = p.knock.add(p.motion);
        c.moveTo(p.x, p.y, p.z, p.yaw, 0f);
        c.setup(p.typeId, p.playerUuid, p.playerName, p.victimId, imp, p.hit, (int) p.level.getGameTime());
        p.level.addFreshEntity(c);
        enforceCap(p.level);
    }

    private static void enforceCap(ServerLevel level) {
        int max = Config.MAX_CORPSES.get();
        if (max <= 0) return;
        List<CorpseEntity> list = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(CorpseEntity.class), c -> true));
        if (list.size() <= max) return;
        list.sort(Comparator.comparingInt(CorpseEntity::getBirth));
        for (int i = 0; i < list.size() - max; i++) list.get(i).discard();
    }

    // ---------- крики ----------

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity v = event.getEntity();
        if (!(v.level() instanceof ServerLevel)) return;
        if (!Config.ENABLED.get() || v.isDeadOrDying()) return; // при смерти кричит onDeath
        if (v.getRandom().nextDouble() > Config.SCREAM_CHANCE.get()) return;
        scream(v, false);
    }

    private static boolean canScream(LivingEntity e) {
        if (!Config.SCREAMS.get()) return false;
        if (Config.SCREAM_ALL_MOBS.get()) return true;
        return e instanceof Player || e instanceof AbstractVillager || e instanceof Zombie
                || e instanceof AbstractIllager || e instanceof AbstractPiglin || e instanceof AbstractSkeleton;
    }

    private static void scream(LivingEntity e, boolean death) {
        if (!canScream(e)) return;
        long now = e.level().getGameTime();
        if (!death) {
            Long last = LAST_SCREAM.get(e.getUUID());
            if (last != null && now - last < Config.SCREAM_COOLDOWN.get()) return;
            if (LAST_SCREAM.size() > 512) LAST_SCREAM.clear();
            LAST_SCREAM.put(e.getUUID(), now);
        } else {
            LAST_SCREAM.remove(e.getUUID());
        }
        SoundEvent sound = (death ? ModRegistry.SCREAM_DEATH : ModRegistry.SCREAM_HURT).get();
        // у каждого "свой голос": высота тона зависит от UUID
        float pitch = 0.85f + (Math.floorMod(e.getUUID().hashCode(), 36)) / 100f
                + (e.getRandom().nextFloat() - 0.5f) * 0.1f;
        e.level().playSound(null, e.getX(), e.getEyeY(), e.getZ(), sound, e.getSoundSource(),
                Config.SCREAM_VOLUME.get().floatValue(), pitch);
    }

    private ServerEvents() {}
}
