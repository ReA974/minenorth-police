package fr.minenorth.police;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons du mod (fichiers dans assets/minenorthpolice/sounds, déclarés dans sounds.json). */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MineNorthPolice.MOD_ID);
    public static final RegistryObject<SoundEvent> TASER = SOUNDS.register("taser",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MineNorthPolice.MOD_ID, "taser")));

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
