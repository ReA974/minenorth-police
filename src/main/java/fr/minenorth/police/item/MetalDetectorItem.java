package fr.minenorth.police.item;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.config.PoliceConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Détecteur de métaux : clic droit sur un joueur proche = bip si son inventaire contient des objets de la liste de la config. */
public class MetalDetectorItem extends Item {
    public MetalDetectorItem() { super(new Item.Properties().stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer p)) return InteractionResultHolder.success(st);
        if (!PoliceTool.allowed(p)) return InteractionResultHolder.fail(st);
        PoliceConfig cfg = PoliceConfig.get();
        Entity t = PoliceTool.target(p, cfg.detecteur_portee, 8, e -> e instanceof ServerPlayer);
        if (!(t instanceof ServerPlayer target)) {
            p.displayClientMessage(Component.literal("§7Approchez le détecteur d'un citoyen."), true);
            return InteractionResultHolder.fail(st);
        }
        p.getCooldowns().addCooldown(this, 30);
        Map<String, Integer> found = new LinkedHashMap<>();
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            ItemStack it = target.getInventory().getItem(i);
            if (PoliceTool.matchesAny(it, cfg.detecteur_objets)) found.merge(it.getHoverName().getString(), it.getCount(), Integer::sum);
        }
        String name = PoliceService.display(p.server, target.getUUID());
        target.sendSystemMessage(Component.literal("§eLa police vous passe au détecteur de métaux."));
        if (found.isEmpty()) {
            level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 0.7f);
            p.displayClientMessage(Component.literal("§aRien détecté sur " + name + "."), true);
            return InteractionResultHolder.success(st);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
        level.playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0f, 1.8f);
        int total = 0;
        for (int c : found.values()) total += c;
        p.displayClientMessage(Component.literal("§c§lBIP ! §r§cMétal détecté sur " + name + " (" + total + ")"), true);
        if (cfg.detecteur_afficher_objets) {
            List<String> parts = new ArrayList<>();
            found.forEach((k, v) -> parts.add(v + "x " + k));
            p.sendSystemMessage(Component.literal("§b[Détecteur] §f" + name + " : §c" + String.join(", ", parts)));
        } else {
            p.sendSystemMessage(Component.literal("§b[Détecteur] §f" + name + " : §c" + total + " objet(s) métallique(s) détecté(s)."));
        }
        return InteractionResultHolder.success(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§8Clic droit sur un citoyen : fouille au détecteur"));
    }
}
