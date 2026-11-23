package com.example.bodyragdoll;

import com.example.bodyragdoll.compat.TaczCompat;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(BodyRagdoll.MODID)
public class BodyRagdoll {
    public static final String MODID = "bodyragdoll";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BodyRagdoll(IEventBus modBus, ModContainer container) {
        ModRegistry.ENTITIES.register(modBus);
        ModRegistry.SOUNDS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TaczCompat::init);
    }
}
