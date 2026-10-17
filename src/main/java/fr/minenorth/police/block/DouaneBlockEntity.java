package fr.minenorth.police.block;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.RadarHack;
import fr.minenorth.police.config.PoliceConfig;
import fr.minenorth.police.item.PoliceTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Poste de douane : toutes les 10 ticks, fouille l'inventaire des joueurs proches ; contrebande = alarme et alerte police. */
public class DouaneBlockEntity extends BlockEntity {
    /** Joueur -> tick de jeu avant lequel on ne le signale plus (évite de répéter l'alerte pendant qu'il reste devant la borne). */
    private final Map<UUID, Long> quiet = new HashMap<>();

    public DouaneBlockEntity(BlockPos pos, BlockState state) { super(ModBlocks.DOUANE_BE.get(), pos, state); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DouaneBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || level.getGameTime() % 10 != 0) return;
        be.scan(sl, pos);
    }

    private void scan(ServerLevel level, BlockPos pos) {
        PoliceConfig cfg = PoliceConfig.get();
        long now = level.getGameTime();
        quiet.values().removeIf(t -> t <= now);
        double r = cfg.douane_rayon;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(r, 2, r))) {
            if (p.isSpectator() || p.isCreative() || quiet.containsKey(p.getUUID())) continue;
            if (cfg.douane_ignorer_police && PoliceService.rank(p) >= 0) continue;
            int n = 0;
            for (List<ItemStack> part : List.of(p.getInventory().items, p.getInventory().armor, p.getInventory().offhand)) {
                for (ItemStack st : part) if (!st.isEmpty() && PoliceTool.matchesAny(st, cfg.douane_objets)) n += st.getCount();
            }
            if (n == 0) continue;
            quiet.put(p.getUUID(), now + cfg.douane_delai_secondes * 20L);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 1.2f, 2.0f);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0);
            p.displayClientMessage(Component.literal("§c§lLe poste de douane sonne !"), true);
            RadarHack.raise(level.getServer(), "Contrebande douane", PoliceService.display(level.getServer(), p.getUUID())
                    + " a déclenché le poste de douane (" + n + " objet(s) suspect(s)).", level.dimension().location().toString(),
                    pos.getX(), pos.getY(), pos.getZ(), cfg.douane_alerte_minutes);
        }
    }
}
