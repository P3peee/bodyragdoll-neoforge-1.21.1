package com.example.bodyragdoll.client;

import com.example.bodyragdoll.BodyRagdoll;
import com.example.bodyragdoll.ModRegistry;
import com.example.bodyragdoll.entity.CorpseEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

public final class ClientEvents {

    @EventBusSubscriber(modid = BodyRagdoll.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModRegistry.CORPSE.get(), CorpseRenderer::new);
        }
    }

    @EventBusSubscriber(modid = BodyRagdoll.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        /** Прячем ванильную анимацию смерти — вместо неё рисуется рэгдолл-труп. */
        @SubscribeEvent
        public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
            if (CorpseEntity.isHidden(event.getEntity())) {
                event.setCanceled(true);
            }
        }
    }

    private ClientEvents() {}
}
