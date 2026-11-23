package com.example.bodyragdoll;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED =
            B.comment("Включить мод").define("enabled", true);
    public static final ModConfigSpec.BooleanValue MOBS =
            B.comment("Делать ragdoll-трупы и для мобов (не только игроков)").define("mobs", true);
    public static final ModConfigSpec.IntValue MAX_CORPSES =
            B.comment("Макс. трупов в одном измерении (0 = без лимита). Самые старые удаляются.")
                    .defineInRange("maxCorpses", 100, 0, 5000);
    public static final ModConfigSpec.IntValue LIFETIME =
            B.comment("Сколько секунд лежит труп (0 = навсегда)")
                    .defineInRange("corpseLifetimeSeconds", 0, 0, 60 * 60 * 24 * 30);
    public static final ModConfigSpec.DoubleValue IMPULSE =
            B.comment("Сила отбрасывания тела при смерти (блоков/тик)")
                    .defineInRange("impulseStrength", 0.35, 0.0, 3.0);

    public static final ModConfigSpec.BooleanValue SCREAMS =
            B.comment("Крики при уроне и смерти").define("screams", true);
    public static final ModConfigSpec.BooleanValue SCREAM_ALL_MOBS =
            B.comment("Кричат вообще все мобы (иначе только игроки, жители, зомби, скелеты, пиглины, illager'ы)")
                    .define("screamAllMobs", false);
    public static final ModConfigSpec.DoubleValue SCREAM_VOLUME =
            B.defineInRange("screamVolume", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.IntValue SCREAM_COOLDOWN =
            B.comment("Пауза между криками одной сущности (тики)").defineInRange("screamCooldownTicks", 30, 0, 600);
    public static final ModConfigSpec.DoubleValue SCREAM_CHANCE =
            B.comment("Шанс крика при уроне (0..1)").defineInRange("screamChance", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST =
            B.comment("Сущности без трупа")
                    .defineListAllowEmpty("blacklist",
                            List.of("minecraft:ender_dragon", "minecraft:armor_stand", "minecraft:wither"),
                            () -> "minecraft:pig", o -> o instanceof String);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
