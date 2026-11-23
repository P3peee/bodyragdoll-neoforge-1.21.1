package com.example.bodyragdoll.compat;

import com.example.bodyragdoll.BodyRagdoll;
import com.example.bodyragdoll.ServerEvents;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Мягкая интеграция с TaCZ (Timeless and Classics Zero) через рефлексию:
 * мод собирается и работает и без TaCZ, и не ломается от смены версий API.
 * Слушает событие "убит из оружия" и передаёт направление пули / хэдшот в рэгдолл.
 */
public final class TaczCompat {
    private static final String[] KILL_EVENTS = {
            "com.tacz.guns.api.event.common.EntityKillByGunEvent"
    };

    public static void init() {
        if (!ModList.get().isLoaded("tacz")) return;
        for (String name : KILL_EVENTS) {
            if (listen(name)) {
                BodyRagdoll.LOGGER.info("[bodyragdoll] TaCZ найден, подключаю событие {}", name);
                return;
            }
        }
        BodyRagdoll.LOGGER.warn("[bodyragdoll] TaCZ загружен, но событие убийства не найдено — "
                + "трупы всё равно работают, но направление выстрела берётся из урона.");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean listen(String className) {
        try {
            Class<?> cls = Class.forName(className, false, TaczCompat.class.getClassLoader());
            if (!Event.class.isAssignableFrom(cls)) return false;
            Consumer<Event> consumer = TaczCompat::onKill;
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) cls, (Consumer) consumer);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void onKill(Event event) {
        try {
            LivingEntity killed = call(event, LivingEntity.class, "getKilledEntity", "getKilled", "getEntity", "getTarget");
            if (killed == null) return;
            LivingEntity attacker = call(event, LivingEntity.class, "getAttacker", "getKiller", "getShooter");
            boolean head = callBool(event, "isHeadShot", "isHeadshot", "getHeadshot");
            float dmg = callFloat(event, 10f, "getBaseDamage", "getDamage");
            ServerEvents.onGunKill(killed, attacker, head, dmg);
        } catch (Throwable t) {
            BodyRagdoll.LOGGER.debug("[bodyragdoll] TaCZ event error", t);
        }
    }

    private static Object invoke(Object o, String name) {
        try {
            Method m = o.getClass().getMethod(name);
            return m.invoke(o);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static <T> T call(Object o, Class<T> type, String... names) {
        for (String n : names) {
            Object r = invoke(o, n);
            if (type.isInstance(r)) return type.cast(r);
        }
        return null;
    }

    private static boolean callBool(Object o, String... names) {
        for (String n : names) {
            Object r = invoke(o, n);
            if (r instanceof Boolean b) return b;
        }
        return false;
    }

    private static float callFloat(Object o, float def, String... names) {
        for (String n : names) {
            Object r = invoke(o, n);
            if (r instanceof Number num) return num.floatValue();
        }
        return def;
    }

    private TaczCompat() {}
}
