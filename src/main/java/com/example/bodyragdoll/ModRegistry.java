package com.example.bodyragdoll;

import com.example.bodyragdoll.entity.CorpseEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BodyRagdoll.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, BodyRagdoll.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CorpseEntity>> CORPSE =
            ENTITIES.register("corpse", () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(10)   // 10 чанков: труп виден всем, кто рядом
                    .updateInterval(20)
                    .fireImmune()
                    .build("corpse"));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM_HURT = sound("scream_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM_DEATH = sound("scream_death");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(BodyRagdoll.MODID, name)));
    }

    private ModRegistry() {}
}
