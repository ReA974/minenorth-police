package fr.minenorth.police;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineNorthPolice.MOD_ID);

    /** Herse : entité plate de 2 x 2 blocs de hitbox, invisible pour la physique. */
    public static final RegistryObject<EntityType<HerseEntity>> HERSE = ENTITIES.register("herse",
            () -> EntityType.Builder.<HerseEntity>of(HerseEntity::new, MobCategory.MISC).sized(2.0f, 0.15f).clientTrackingRange(8)
                    .updateInterval(20).fireImmune().noSummon().build("herse"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}
